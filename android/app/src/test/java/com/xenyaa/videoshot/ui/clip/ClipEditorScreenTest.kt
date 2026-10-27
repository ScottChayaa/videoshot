package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ClipEditorScreenTest {
    @get:Rule val compose = createComposeRule()

    /** 不碰 Media3 的假預覽：只記下被呼叫了什麼。 */
    private class FakePreview(playing: Boolean = false, position: Long = 0) : ClipPreview {
        val calls = mutableListOf<String>()
        override val positionMs: MutableStateFlow<Long> = MutableStateFlow(position)
        override val isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(playing)
        override fun show(draft: ClipDraft, positionMs: Long) { calls += "show" }
        override fun seekTo(positionMs: Long) { calls += "seekTo" }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun release() {}
    }

    private fun video(id: Long, durationMs: Long) =
        ClipSource(id, "content://media/$id", SourceKind.VIDEO, 1_000L * id, durationMs, 1080, 1920, 0)

    private fun photo(id: Long) = ClipSource(id, "content://media/$id", SourceKind.PHOTO, 1_000L * id, null, 1080, 1920, 0)

    /** 一支影片撐滿 [durationMs] 的單段時間軸。 */
    private fun state(
        orientation: ClipOrientation = ClipOrientation.PORTRAIT,
        durationMs: Long = 60_000,
        playheadMs: Long = 0,
        canUndo: Boolean = false,
        canRedo: Boolean = false,
        selected: Int? = null,
        access: MediaAccess = MediaAccess.FULL,
    ): ClipEditorState {
        val timeline = Timeline(listOf(Segment(1, 0, durationMs)))
        return stateOf(orientation, listOf(video(1, durationMs)), timeline, playheadMs, canUndo, canRedo, selected, access)
    }

    /** 兩張照片各 3 秒、播放頭在 0（第 1 段起點）。 */
    private fun twoPhotos(selected: Int? = null) =
        stateOf(
            ClipOrientation.PORTRAIT, listOf(photo(1), photo(2)),
            Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 3000))), 0, false, false, selected, MediaAccess.FULL,
        )

    private fun emptyState(canUndo: Boolean = false) =
        stateOf(ClipOrientation.PORTRAIT, emptyList(), Timeline(emptyList()), 0, canUndo, false, null, MediaAccess.FULL)

    private fun stateOf(
        orientation: ClipOrientation,
        sources: List<ClipSource>,
        timeline: Timeline,
        playheadMs: Long,
        canUndo: Boolean,
        canRedo: Boolean,
        selected: Int?,
        access: MediaAccess,
    ): ClipEditorState {
        val history = EditHistory(
            current = timeline,
            undo = if (canUndo) listOf(Timeline(emptyList())) else emptyList(),
            redo = if (canRedo) listOf(Timeline(emptyList())) else emptyList(),
        )
        return ClipEditorState(
            loading = false, access = access, draft = ClipDraft(orientation, sources, history),
            playheadMs = playheadMs, selected = selected,
        )
    }

    private class Calls {
        var split = 0; var delete = 0; var undo = 0; var redo = 0; var export = 0; var playPause = 0; var back = 0
        var requestAccess = 0; var openSettings = 0
        val selects = mutableListOf<Int?>()
        val seeks = mutableListOf<Long>()
    }

    private fun show(
        state: ClipEditorState,
        preview: ClipPreview = FakePreview(),
        calls: Calls = Calls(),
        canAskAgain: Boolean = true,
    ) {
        compose.setContent {
            VideoshotTheme {
                ClipEditorScreen(
                    state = state,
                    preview = preview,
                    thumbLoader = MediaThumbLoader(decode = { null }),
                    onBack = { calls.back++ },
                    onExport = { calls.export++ },
                    onPlayPause = { calls.playPause++ },
                    onSeek = { calls.seeks += it },
                    onSelect = { calls.selects += it },
                    onSplit = { calls.split++ },
                    onDelete = { calls.delete++ },
                    onUndo = { calls.undo++ },
                    onRedo = { calls.redo++ },
                    onRequestAccess = { calls.requestAccess++ },
                    onOpenSettings = { calls.openSettings++ },
                    canAskAgain = canAskAgain,
                )
            }
        }
    }

    @Test fun 頂欄有標題總長與導出() {
        show(state(durationMs = 108_000))
        listOf("編輯", "總長 1:48", "導出").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun 超過上限總長變紅並說明() {
        show(state(ClipOrientation.PORTRAIT, durationMs = 181_000))
        compose.onNodeWithText("總長 3:01（超過 3 分鐘上限）").assertIsDisplayed()
    }

    @Test fun 橫式超過上限說明是10分鐘() {
        show(state(ClipOrientation.LANDSCAPE, durationMs = 601_000))
        compose.onNodeWithText("總長 10:01（超過 10 分鐘上限）").assertIsDisplayed()
    }

    @Test fun 剛好等於上限不算超過() {
        show(state(ClipOrientation.PORTRAIT, durationMs = 180_000))
        compose.onNodeWithText("總長 3:00").assertIsDisplayed()
    }

    @Test fun 播放列顯示時間與復原重做() {
        show(state(playheadMs = 12_000, durationMs = 108_000))
        compose.onNodeWithText("0:12 / 1:48").assertIsDisplayed()
        compose.onNodeWithContentDescription("復原").assertIsDisplayed()
        compose.onNodeWithContentDescription("重做").assertIsDisplayed()
    }

    @Test fun 播放中時間跟著預覽的位置() {
        show(state(playheadMs = 0, durationMs = 108_000), FakePreview(playing = true, position = 30_000))
        compose.onNodeWithText("0:30 / 1:48").assertIsDisplayed()
    }

    @Test fun 沒在播放時播放鈕唸播放() {
        show(state())
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        compose.onNodeWithContentDescription("暫停").assertDoesNotExist()
    }

    @Test fun 播放中播放鈕唸暫停() {
        show(state(), FakePreview(playing = true))
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
    }

    @Test fun 不能分割時分割停用() {
        show(state(playheadMs = 0)) // 播放頭在段落起點，離邊界不到 100 ms
        compose.onNodeWithText("分割").assertIsNotEnabled()
    }

    @Test fun 能分割時分割可按() {
        show(state(playheadMs = 30_000))
        compose.onNodeWithText("分割").assertIsEnabled()
    }

    @Test fun 沒有紀錄時復原重做停用() {
        show(state(canUndo = false, canRedo = false))
        compose.onNodeWithContentDescription("復原").assertIsNotEnabled()
        compose.onNodeWithContentDescription("重做").assertIsNotEnabled()
    }

    @Test fun 有紀錄時復原重做可按() {
        show(state(canUndo = true, canRedo = true))
        compose.onNodeWithContentDescription("復原").assertIsEnabled()
        compose.onNodeWithContentDescription("重做").assertIsEnabled()
    }

    @Test fun 空時間軸有說明且導出停用() {
        show(emptyState())
        compose.onNodeWithText("時間軸是空的").assertIsDisplayed()
        compose.onNodeWithText("導出").assertIsNotEnabled()
        compose.onNodeWithText("分割").assertIsNotEnabled()
        compose.onNodeWithText("刪除").assertIsNotEnabled()
    }

    @Test fun 空時間軸有復原紀錄才提示可以復原() {
        show(emptyState(canUndo = true))
        compose.onNodeWithText("按 ↶ 復原可以找回剛刪掉的段落").assertIsDisplayed()
    }

    @Test fun 空時間軸沒有復原紀錄不提示復原() {
        show(emptyState(canUndo = false))
        compose.onNodeWithText("按 ↶ 復原可以找回剛刪掉的段落").assertDoesNotExist()
    }

    @Test fun 點分割刪除復原重做導出返回播放都回報() {
        val calls = Calls()
        show(state(playheadMs = 30_000, canUndo = true, canRedo = true), calls = calls)
        compose.onNodeWithText("分割").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.onNodeWithContentDescription("復原").performClick()
        compose.onNodeWithContentDescription("重做").performClick()
        compose.onNodeWithText("導出").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("播放").performClick()
        assertEquals(
            listOf(1, 1, 1, 1, 1, 1, 1),
            listOf(calls.split, calls.delete, calls.undo, calls.redo, calls.export, calls.back, calls.playPause),
        )
    }

    @Test fun 點時間軸上的段回報選取() {
        val calls = Calls()
        show(twoPhotos(), calls = calls)
        compose.onNodeWithContentDescription("第 2 段，照片，0:03").performClick()
        assertEquals(listOf<Int?>(1), calls.selects)
    }

    @Test fun 點已選的段取消選取() {
        val calls = Calls()
        show(twoPhotos(selected = 0), calls = calls)
        compose.onNodeWithContentDescription("第 1 段，照片，0:03").assertIsSelected().performClick()
        assertEquals(listOf<Int?>(null), calls.selects)
    }

    @Test fun 沒有權限時換成權限空狀態() {
        val calls = Calls()
        show(state(access = MediaAccess.DENIED), calls = calls)
        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()
        compose.onNodeWithText("允許存取").performClick()
        assertEquals(1, calls.requestAccess)
        compose.onNodeWithText("分割").assertDoesNotExist()
    }

    @Test fun 權限被永久拒絕時顯示前往設定() {
        val calls = Calls()
        show(state(access = MediaAccess.DENIED), calls = calls, canAskAgain = false)
        compose.onNodeWithText("前往設定").performClick()
        assertEquals(1, calls.openSettings)
    }

    @Test fun 部分允許時照常編輯() {
        show(state(access = MediaAccess.PARTIAL))
        compose.onNodeWithText("分割").assertIsDisplayed()
    }

    @Test fun 播放鈕只回報不直接動預覽() {
        val preview = FakePreview(playing = true)
        val calls = Calls()
        show(state(), preview, calls)
        compose.onNodeWithContentDescription("暫停").performClick()
        assertTrue(preview.calls.isEmpty())
        assertEquals(1, calls.playPause)
    }
}
