package com.xenyaa.videoshot.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DetailScreenTest {

    @get:Rule val compose = createComposeRule()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun shot(id: Long, atSec: Double, desc: String? = null) = ShotRow(
        id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-01", place = "宜蘭", description = desc,
    )

    private var focused: ShotRow? = null
    private var edited: ShotRow? = null
    private var continueTapped = false
    private var batchEditTapped = false
    private var deleteTapped = false
    private var backTapped = false

    private fun show(state: DetailViewModel.State) {
        compose.setContent {
            VideoshotTheme {
                DetailScreen(
                    state = state,
                    loader = loader,
                    onBack = { backTapped = true },
                    onPlayerReady = {},
                    onPlayerReleased = {},
                    onRetryPlayer = {},
                    onFocus = { focused = it },
                    onEdit = { edited = it },
                    onContinueCapture = { continueTapped = true },
                    onBatchEdit = { batchEditTapped = true },
                    onDeleteVideo = { deleteTapped = true },
                )
            }
        }
    }

    @Test
    fun 標題與收藏張數顯示出來() {
        show(
            DetailViewModel.State(
                loading = false, title = "旅行影片",
                shots = listOf(shot(1, 10.0), shot(2, 40.0)),
                focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithText("旅行影片").assertIsDisplayed()
        compose.onNodeWithText("這支影片的收藏 (2)").assertIsDisplayed()
    }

    @Test
    fun 播放器不可用時顯示中性訊息而不是空白() {
        show(
            DetailViewModel.State(
                loading = false, title = "旅行影片", shots = listOf(shot(1, 10.0)), focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Unavailable("這支影片抓不到，可能是私人影片、已被刪除或需要登入。", retryable = false),
            )
        )
        compose.onNodeWithText("這支影片抓不到，可能是私人影片、已被刪除或需要登入。").assertIsDisplayed()
    }

    @Test
    fun 點縮圖會跳播() {
        show(
            DetailViewModel.State(
                loading = false, title = "t", shots = listOf(shot(1, 10.0), shot(2, 65.0)), focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithContentDescription("片段縮圖 01:05").performClick()
        assertEquals(2L, focused?.id)
    }

    @Test
    fun 編輯鈕觸發編輯而不是跳播() {
        show(
            DetailViewModel.State(
                loading = false, title = "t", shots = listOf(shot(1, 10.0)), focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithContentDescription("編輯 00:10 的圖資").performClick()
        assertEquals(1L, edited?.id)
        assertEquals(null, focused)
    }

    @Test
    fun 資訊面板的編輯鈕編輯目前聚焦的那一張() {
        show(
            DetailViewModel.State(
                loading = false, title = "t", shots = listOf(shot(1, 10.0, desc = "下水前")), focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithText("編輯這張的圖資").performClick()
        assertEquals(1L, edited?.id)
    }

    @Test
    fun 選單三個動作都接得到() {
        show(
            DetailViewModel.State(
                loading = false, title = "t", shots = listOf(shot(1, 10.0)), focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("繼續取這支的圖").performClick()
        assertEquals(true, continueTapped)

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("批次編輯圖資").performClick()
        assertEquals(true, batchEditTapped)

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("刪除整支收藏").performClick()
        // 確認框攔一次，還沒真的觸發
        assertEquals(false, deleteTapped)
        compose.onNodeWithText("刪除").performClick()
        assertEquals(true, deleteTapped)
    }

    @Test
    fun 刪除確認框點名截圖張數() {
        show(
            DetailViewModel.State(
                loading = false, title = "t",
                shots = listOf(
                    shot(1, 10.0).copy(source = "manual"),
                    shot(2, 20.0),
                ),
                focusedShotId = 1L,
                player = DetailViewModel.PlayerAvailability.Ready(true),
            )
        )
        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("刪除整支收藏").performClick()
        compose.onNodeWithText("將刪除這支影片的 2 張收藏(含 1 張截圖)，YouTube 原片不受影響。").assertIsDisplayed()
    }
}
