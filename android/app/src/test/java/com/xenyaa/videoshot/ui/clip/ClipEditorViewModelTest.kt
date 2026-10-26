package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.core.timeline.newClipDraft
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun photo(id: Long) = ClipSource(id, "content://media/$id", SourceKind.PHOTO, 1_000L * id, null, 1080, 1920, 0)

    private fun mediaItem(id: Long) = MediaItem(
        id = id, uri = "content://media/$id", kind = MediaKind.PHOTO,
        takenAtMs = 1_000L * id, durationMs = null, width = 1080, height = 1920, rotation = 0,
    )

    private class FakeMediaLibrary(private val all: List<MediaItem>) : MediaLibrary {
        override suspend fun list(filter: MediaFilter): List<MediaItem> = all
    }

    private class FakeClipDeps(
        override val mediaLibrary: MediaLibrary,
        var granted: Set<String>,
        override val cacheRepo: CacheRepo,
    ) : ClipDeps {
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = granted
        override fun now() = 1234L
    }

    private val full = mediaPermissionsFor(34).filter { !it.endsWith("VISUAL_USER_SELECTED") }.toSet()
    private val repo = FakeCacheRepo()

    /** 草稿在 [repo] 裡、素材庫有 [present] 個 id；回傳 ViewModel（還沒 load）與存好草稿後的存檔次數基準。 */
    private suspend fun vmWith(
        draft: ClipDraft?,
        present: List<Long> = draft?.sources?.map { it.id } ?: emptyList(),
        granted: Set<String> = full,
    ): ClipEditorViewModel {
        if (draft != null) repo.saveClipDraft(draft, 0)
        return ClipEditorViewModel(FakeClipDeps(FakeMediaLibrary(present.map(::mediaItem)), granted, repo))
    }

    private suspend fun ClipEditorViewModel.loaded(): ClipEditorViewModel {
        load()
        dispatcher.scheduler.advanceUntilIdle()
        return this
    }

    private val ClipEditorViewModel.s get() = state.value

    private val one = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1)))
    private val two = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1), photo(2)))
    private val three = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1), photo(2), photo(3)))

    @Test fun 沒有草稿就讀完且草稿為空() = runTest {
        val vm = vmWith(null).loaded()
        assertFalse(vm.s.loading)
        assertNull(vm.s.draft)
    }

    @Test fun 續做時素材不見了拿掉並提示() = runTest {
        val vm = vmWith(three, present = listOf(1L, 2L))
        val base = repo.clipDraftSaveCount
        vm.loaded()
        assertEquals(base + 1, repo.clipDraftSaveCount)
        assertEquals(MediaAccess.FULL, vm.s.access)
        assertEquals(listOf(1L, 2L), vm.s.draft!!.timeline.segments.map { it.sourceId })
        assertEquals("有 1 個素材已不存在，已從時間軸移除", vm.s.notice)
        assertEquals(vm.s.draft, repo.currentClipDraft())
        vm.noticeShown()
        assertNull(vm.s.notice)
    }

    @Test fun 素材都在時不修剪也不存檔() = runTest {
        val vm = vmWith(three)
        val base = repo.clipDraftSaveCount
        vm.loaded()
        assertEquals(three, vm.s.draft)
        assertNull(vm.s.notice)
        assertEquals(base, repo.clipDraftSaveCount)
    }

    @Test fun 素材全不見時是空時間軸不當掉() = runTest {
        val vm = vmWith(two, present = emptyList()).loaded()
        assertTrue(vm.s.isEmpty)
        assertFalse(vm.s.canDelete)
        assertFalse(vm.s.canSplit)
        assertEquals("有 2 個素材已不存在，已從時間軸移除", vm.s.notice)
    }

    @Test fun 沒權限續做不修剪也不存檔() = runTest {
        val vm = vmWith(three, present = emptyList(), granted = emptySet())
        val base = repo.clipDraftSaveCount
        vm.loaded()
        assertEquals(MediaAccess.DENIED, vm.s.access)
        assertEquals(three, vm.s.draft)
        assertNull(vm.s.notice)
        assertFalse(vm.s.loading)
        assertEquals(base, repo.clipDraftSaveCount)
    }

    @Test fun 部分權限也會修剪() = runTest {
        val partial = setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        val vm = vmWith(three, present = listOf(1L, 2L), granted = partial).loaded()
        assertEquals(MediaAccess.PARTIAL, vm.s.access)
        assertEquals(2, vm.s.draft!!.timeline.segments.size)
    }

    @Test fun 分割後立刻存草稿而且可以復原() = runTest {
        val vm = vmWith(one).loaded()
        val base = repo.clipDraftSaveCount
        vm.seekTo(1200)
        vm.split()
        assertEquals(2, vm.s.draft!!.timeline.segments.size)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(base + 1, repo.clipDraftSaveCount)
        assertEquals(vm.s.draft, repo.currentClipDraft())
        assertTrue(vm.s.canUndo)
        vm.undo()
        assertEquals(1, vm.s.draft!!.timeline.segments.size)
        assertTrue(vm.s.canRedo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(base + 2, repo.clipDraftSaveCount)
        vm.redo()
        assertEquals(2, vm.s.draft!!.timeline.segments.size)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(vm.s.draft, repo.currentClipDraft())
    }

    @Test fun 不能分割時按分割沒有作用() = runTest {
        val vm = vmWith(one).loaded()
        val base = repo.clipDraftSaveCount
        vm.seekTo(0)
        vm.split()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(one, vm.s.draft)
        assertEquals(base, repo.clipDraftSaveCount)
    }

    @Test fun 沒選取時刪播放頭所在段() = runTest {
        val vm = vmWith(two).loaded()
        vm.seekTo(3500)
        vm.delete()
        assertEquals(listOf(1L), vm.s.draft!!.timeline.segments.map { it.sourceId })
        // 播放頭 3500 超出新總長 3000 → 夾到最後一段開頭
        assertEquals(0L, vm.s.playheadMs)
    }

    @Test fun 播放頭在最尾端時刪最後一段() = runTest {
        val vm = vmWith(two).loaded()
        vm.seekTo(6000)
        vm.delete()
        assertEquals(listOf(1L), vm.s.draft!!.timeline.segments.map { it.sourceId })
        assertEquals(0L, vm.s.playheadMs)
    }

    @Test fun 選了就刪選的那段() = runTest {
        val vm = vmWith(two).loaded()
        vm.seekTo(100)
        vm.select(1)
        assertEquals(1, vm.s.selected)
        vm.delete()
        assertEquals(listOf(1L), vm.s.draft!!.timeline.segments.map { it.sourceId })
        assertNull(vm.s.selected)
    }

    @Test fun 復原時選取清掉() = runTest {
        val vm = vmWith(two).loaded()
        vm.delete()
        vm.select(0)
        vm.undo()
        assertNull(vm.s.selected)
    }

    @Test fun 選取超出範圍視為沒選() = runTest {
        val vm = vmWith(two).loaded()
        vm.select(5)
        assertNull(vm.s.selected)
        vm.select(-1)
        assertNull(vm.s.selected)
    }

    @Test fun 刪光後不能刪也不能分割() = runTest {
        val vm = vmWith(two).loaded()
        vm.delete()
        vm.delete()
        assertTrue(vm.s.isEmpty)
        assertFalse(vm.s.canDelete)
        assertFalse(vm.s.canSplit)
        assertTrue(vm.s.canUndo)
        dispatcher.scheduler.advanceUntilIdle()
        val base = repo.clipDraftSaveCount
        vm.delete() // 空時間軸再刪：什麼都不做、不存檔
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(base, repo.clipDraftSaveCount)
        assertEquals(0L, vm.s.playheadMs)
    }

    @Test fun 播放頭在交界時不能分割() = runTest {
        val vm = vmWith(two).loaded()
        vm.seekTo(3000)
        assertFalse(vm.s.canSplit)
        vm.seekTo(1200)
        assertTrue(vm.s.canSplit)
    }

    @Test fun seekTo夾在零到總長() = runTest {
        val vm = vmWith(two).loaded()
        vm.seekTo(-5)
        assertEquals(0L, vm.s.playheadMs)
        vm.seekTo(99_999)
        assertEquals(6000L, vm.s.playheadMs)
    }

    @Test fun 移動播放頭與選取不存檔() = runTest {
        val vm = vmWith(two).loaded()
        val base = repo.clipDraftSaveCount
        vm.seekTo(500)
        vm.select(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(base, repo.clipDraftSaveCount)
        assertEquals(two.history, vm.s.draft!!.history)
    }

    @Test fun 連續編輯的存檔依序而且最後一份是最新的() = runTest {
        val vm = vmWith(three).loaded()
        vm.delete()
        vm.delete()
        vm.undo()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(vm.s.draft, repo.currentClipDraft())
    }

    @Test fun 超過上限標記() = runTest {
        val over = ClipDraft(
            ClipOrientation.PORTRAIT,
            listOf(photo(1)),
            EditHistory(Timeline(listOf(Segment(1, 0, 181_000)))),
        )
        val vm = vmWith(over).loaded()
        assertTrue(vm.s.overLimit)
        val ok = ClipDraft(
            ClipOrientation.LANDSCAPE,
            listOf(photo(1)),
            EditHistory(Timeline(listOf(Segment(1, 0, 181_000)))),
        )
        assertFalse(ClipEditorState(draft = ok).overLimit)
        assertFalse(ClipEditorState().overLimit)
    }
}
