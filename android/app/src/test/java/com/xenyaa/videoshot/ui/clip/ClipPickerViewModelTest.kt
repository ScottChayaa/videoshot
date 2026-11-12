package com.xenyaa.videoshot.ui.clip

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.media.mediaPermissionsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(id: Long, kind: MediaKind) = MediaItem(
        id = id, uri = "content://media/$id", kind = kind,
        takenAtMs = 1_000L * id, durationMs = if (kind == MediaKind.VIDEO) 5_000L else null,
        width = 1080, height = 1920, rotation = 0,
    )

    /** 照片 1、2、3、4、5、7；影片 10、20、30。 */
    private class FakeMediaLibrary(private val all: MutableList<MediaItem>) : MediaLibrary {
        var listCalls = 0
        fun remove(id: Long) { all.removeAll { it.id == id } }
        fun add(item: MediaItem) { all += item }
        override suspend fun list(filter: MediaFilter): List<MediaItem> {
            listCalls++
            return when (filter) {
                MediaFilter.ALL -> all.toList()
                MediaFilter.VIDEO -> all.filter { it.kind == MediaKind.VIDEO }
                MediaFilter.PHOTO -> all.filter { it.kind == MediaKind.PHOTO }
            }
        }
    }

    private class FakeClipDeps(
        override val mediaLibrary: MediaLibrary,
        var granted: Set<String>,
    ) : ClipDeps {
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = granted
        override val cacheRepo: CacheRepo = FakeCacheRepo()
        override fun now() = 0L
    }

    private val library = FakeMediaLibrary(
        mutableListOf(
            item(30, MediaKind.VIDEO), item(20, MediaKind.VIDEO), item(10, MediaKind.VIDEO),
            item(7, MediaKind.PHOTO), item(5, MediaKind.PHOTO), item(4, MediaKind.PHOTO),
            item(3, MediaKind.PHOTO), item(2, MediaKind.PHOTO), item(1, MediaKind.PHOTO),
        ),
    )

    private val fullFor34 = mediaPermissionsFor(34).filter { !it.endsWith("VISUAL_USER_SELECTED") }.toSet()
    private val deps = FakeClipDeps(library, fullFor34)

    private fun vm() = ClipPickerViewModel(deps, ZoneOffset.UTC)

    @Test fun 一開始先判定權限再讀全部() = runTest {
        val vm = vm()
        assertEquals(MediaAccess.FULL, vm.state.value.access)
        assertNull(vm.state.value.items)
        advanceUntilIdle()
        assertEquals(library.list(MediaFilter.ALL), vm.state.value.items)
        assertEquals(PickerFilter.ALL, vm.state.value.filter)
    }

    @Test fun 沒權限不讀清單() = runTest {
        deps.granted = emptySet()
        val vm = vm()
        advanceUntilIdle()
        assertEquals(MediaAccess.DENIED, vm.state.value.access)
        assertNull(vm.state.value.items)
        assertEquals(0, library.listCalls)
        vm.setFilter(PickerFilter.PORTRAIT_VIDEO)
        advanceUntilIdle()
        assertNull(vm.state.value.items)
        assertEquals(0, library.listCalls)
    }

    @Test fun 清除拿掉所有選取與總長() {
        val vm = vm()
        vm.toggle(10); vm.toggle(1)
        vm.clearSelection()
        assertEquals(emptyList<Long>(), vm.state.value.selected)
        assertEquals(0L, vm.state.value.selectedMs)
    }

    @Test fun 選取與取消() {
        val vm = vm()
        vm.toggle(10); vm.toggle(20); vm.toggle(30); vm.toggle(20)
        assertEquals(listOf(10L, 30L), vm.state.value.selected)
    }

    /** 2026 年 [month] 月 5 日中午（UTC）。 */
    private fun at(month: Int) = LocalDateTime.of(2026, month, 5, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun photoAt(id: Long, month: Int, width: Int = 1080, height: Int = 1920) =
        MediaItem(id, "content://media/$id", MediaKind.PHOTO, at(month), null, width, height, 0)

    @Test fun 月份清單由新到舊() = runTest {
        library.add(photoAt(100, 11)); library.add(photoAt(101, 10))
        val vm = vm()
        advanceUntilIdle()
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(1970, 1)), vm.state.value.months)
    }

    @Test fun 快速選取追加那個月的全部_不重複_不動別的月() = runTest {
        library.add(photoAt(100, 11)); library.add(photoAt(101, 11)); library.add(photoAt(102, 11)); library.add(photoAt(103, 10))
        val vm = vm()
        vm.toggle(100)
        vm.autoPick(YearMonth.of(2026, 11), AutoPick.ANY)
        advanceUntilIdle()
        assertEquals(listOf(100L, 101L, 102L), vm.state.value.selected)
        assertEquals(9_000L, vm.state.value.selectedMs)
        vm.autoPick(YearMonth.of(2026, 11), AutoPick.ANY)
        advanceUntilIdle()
        assertEquals(listOf(100L, 101L, 102L), vm.state.value.selected)
    }

    @Test fun 快速選取依方向_照片也算() = runTest {
        library.add(photoAt(40, 11, width = 1920, height = 1080))
        library.add(MediaItem(41, "content://media/41", MediaKind.VIDEO, at(11), 5_000, 1920, 1080, 0))
        // 直拍存成橫的、轉 90 度：算直式
        library.add(MediaItem(42, "content://media/42", MediaKind.VIDEO, at(11), 5_000, 1920, 1080, 90))
        library.add(photoAt(43, 11))
        val vm = vm()
        vm.autoPick(YearMonth.of(2026, 11), AutoPick.LANDSCAPE)
        advanceUntilIdle()
        assertEquals(listOf(40L, 41L), vm.state.value.selected)
        vm.autoPick(YearMonth.of(2026, 11), AutoPick.PORTRAIT)
        advanceUntilIdle()
        assertEquals(listOf(40L, 41L, 42L, 43L), vm.state.value.selected)
    }

    @Test fun 已選總長照片各3秒加影片長度_切到別的分頁也算得到() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(1); vm.toggle(10); vm.toggle(2)
        assertEquals(3_000L + 5_000L + 3_000L, vm.state.value.selectedMs)
        vm.setFilter(PickerFilter.PHOTO)
        advanceUntilIdle()
        vm.toggle(2)
        assertEquals(3_000L + 5_000L, vm.state.value.selectedMs)
        vm.reset()
        assertEquals(0L, vm.state.value.selectedMs)
    }

    @Test fun 被刪掉的已選素材不算進總長() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(1); vm.toggle(10)
        library.remove(10)
        vm.refreshAccess()
        advanceUntilIdle()
        assertEquals(3_000L, vm.state.value.selectedMs)
    }

    @Test fun 切分頁不丟選取() = runTest {
        val vm = vm()
        vm.toggle(1); vm.toggle(2); vm.toggle(3)
        vm.setFilter(PickerFilter.PHOTO)
        assertNull(vm.state.value.items)
        vm.toggle(4); vm.toggle(5)
        advanceUntilIdle()
        assertEquals(library.list(MediaFilter.PHOTO), vm.state.value.items)
        vm.setFilter(PickerFilter.ALL)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), vm.state.value.selected)
    }

    @Test fun 從設定改成允許回來後重新讀清單() = runTest {
        deps.granted = emptySet()
        val vm = vm()
        advanceUntilIdle()
        vm.refreshAccess()
        assertEquals(MediaAccess.DENIED, vm.state.value.access)
        deps.granted = fullFor34
        vm.refreshAccess()
        advanceUntilIdle()
        assertEquals(MediaAccess.FULL, vm.state.value.access)
        assertNotNull(vm.state.value.items)
    }

    @Test fun 被刪掉的已選素材重讀後移除() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(7); vm.toggle(5)
        library.remove(7)
        vm.refreshAccess()
        advanceUntilIdle()
        assertFalse(7L in vm.state.value.selected)
        assertEquals(listOf(5L), vm.state.value.selected)
    }

    @Test fun 單一分頁重讀時不會誤刪別的種類的已選素材() = runTest {
        val vm = vm()
        advanceUntilIdle()           // 讀過全部，認得每個素材的種類
        vm.toggle(7); vm.toggle(10)  // 照片 7、影片 10
        vm.setFilter(PickerFilter.PORTRAIT_VIDEO)
        advanceUntilIdle()
        library.remove(10)
        vm.refreshAccess()
        advanceUntilIdle()
        assertEquals(listOf(7L), vm.state.value.selected)
    }

    @Test fun 部分允許時也會重讀() = runTest {
        deps.granted = setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        val vm = vm()
        advanceUntilIdle()
        assertEquals(MediaAccess.PARTIAL, vm.state.value.access)
        assertNotNull(vm.state.value.items)
        val before = library.listCalls
        vm.refreshAccess()
        advanceUntilIdle()
        assertEquals(before + 1, library.listCalls)
    }

    @Test fun 直影片與橫影片分頁依畫面方向分開_旋轉過的照轉後算_正方形兩邊都沒有() = runTest {
        library.add(item(40, MediaKind.VIDEO).copy(width = 1920, height = 1080))
        library.add(item(50, MediaKind.VIDEO).copy(width = 1920, height = 1080, rotation = 90))
        library.add(item(60, MediaKind.VIDEO).copy(width = 1080, height = 1080))
        val vm = vm()
        vm.setFilter(PickerFilter.PORTRAIT_VIDEO)
        advanceUntilIdle()
        assertEquals(listOf(30L, 20L, 10L, 50L), vm.state.value.items!!.map { it.id })
        vm.setFilter(PickerFilter.LANDSCAPE_VIDEO)
        advanceUntilIdle()
        assertEquals(listOf(40L), vm.state.value.items!!.map { it.id })
    }

    @Test fun 在直影片分頁重讀不會拿掉已選的橫影片() = runTest {
        library.add(item(40, MediaKind.VIDEO).copy(width = 1920, height = 1080))
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(40); vm.toggle(10)
        vm.setFilter(PickerFilter.PORTRAIT_VIDEO)
        advanceUntilIdle()
        vm.refreshAccess()
        advanceUntilIdle()
        assertEquals(listOf(40L, 10L), vm.state.value.selected)
    }

    @Test fun reset清空選取並回到全部() {
        val vm = vm()
        vm.toggle(1); vm.setFilter(PickerFilter.PORTRAIT_VIDEO); vm.reset()
        assertEquals(emptyList<Long>(), vm.state.value.selected)
        assertEquals(PickerFilter.ALL, vm.state.value.filter)
    }

    @Test fun 建立草稿時拿掉已經不存在的選取() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(1); vm.toggle(2)
        library.remove(2)
        val d = vm.buildDraft(ClipOrientation.PORTRAIT)!!
        assertEquals(listOf(1L), d.sources.map { it.id })
        assertEquals(ClipOrientation.PORTRAIT, d.orientation)
        assertEquals(listOf(1L), d.timeline.segments.map { it.sourceId })
    }

    @Test fun 選取全都不存在時不建立草稿_並清掉選取重讀() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(1)
        library.remove(1)
        assertNull(vm.buildDraft(ClipOrientation.PORTRAIT))
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), vm.state.value.selected)
        assertEquals(library.list(MediaFilter.ALL), vm.state.value.items)
    }

    @Test fun 重讀到整份空清單時不建立草稿_但保留選取() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(1); vm.toggle(2)
        // 整份讀到空多半是讀取失敗，不是選的素材剛好全被刪光：選取留著，使用者可以再按一次
        listOf(30L, 20L, 10L, 7L, 5L, 4L, 3L, 2L, 1L).forEach(library::remove)
        assertNull(vm.buildDraft(ClipOrientation.PORTRAIT))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.state.value.selected)
    }

    @Test fun 建立草稿帶著素材的欄位而且依拍攝時間由舊到新() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.toggle(10); vm.toggle(3)   // 影片 10（takenAt 10000）、照片 3（takenAt 3000）
        val d = vm.buildDraft(ClipOrientation.LANDSCAPE)!!
        assertEquals(listOf(3L, 10L), d.timeline.segments.map { it.sourceId })
        val video = d.sources.first { it.id == 10L }
        assertEquals(SourceKind.VIDEO, video.kind)
        assertEquals(5_000L, video.durationMs)
        assertEquals("content://media/10", video.uri)
        assertEquals(1080, video.width)
    }
}
