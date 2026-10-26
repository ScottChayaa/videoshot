package com.xenyaa.videoshot.ui.clip

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

    private fun vm() = ClipPickerViewModel(deps)

    @Test fun 一開始先判定權限再讀全部() = runTest {
        val vm = vm()
        assertEquals(MediaAccess.FULL, vm.state.value.access)
        assertNull(vm.state.value.items)
        advanceUntilIdle()
        assertEquals(library.list(MediaFilter.ALL), vm.state.value.items)
        assertEquals(MediaFilter.ALL, vm.state.value.filter)
    }

    @Test fun 沒權限不讀清單() = runTest {
        deps.granted = emptySet()
        val vm = vm()
        advanceUntilIdle()
        assertEquals(MediaAccess.DENIED, vm.state.value.access)
        assertNull(vm.state.value.items)
        assertEquals(0, library.listCalls)
        vm.setFilter(MediaFilter.VIDEO)
        advanceUntilIdle()
        assertNull(vm.state.value.items)
        assertEquals(0, library.listCalls)
    }

    @Test fun 選取有序號而且取消後遞補() {
        val vm = vm()
        vm.toggle(10); vm.toggle(20); vm.toggle(30); vm.toggle(20)
        assertEquals(listOf(10L, 30L), vm.state.value.selected)
        assertEquals(2, vm.orderOf(30))
        assertEquals(1, vm.orderOf(10))
        assertNull(vm.orderOf(20))
    }

    @Test fun 切分頁不丟選取() = runTest {
        val vm = vm()
        vm.toggle(1); vm.toggle(2); vm.toggle(3)
        vm.setFilter(MediaFilter.PHOTO)
        assertNull(vm.state.value.items)
        vm.toggle(4); vm.toggle(5)
        advanceUntilIdle()
        assertEquals(library.list(MediaFilter.PHOTO), vm.state.value.items)
        vm.setFilter(MediaFilter.ALL)
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
        vm.setFilter(MediaFilter.VIDEO)
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

    @Test fun reset清空選取並回到全部() {
        val vm = vm()
        vm.toggle(1); vm.setFilter(MediaFilter.VIDEO); vm.reset()
        assertEquals(emptyList<Long>(), vm.state.value.selected)
        assertEquals(MediaFilter.ALL, vm.state.value.filter)
    }
}
