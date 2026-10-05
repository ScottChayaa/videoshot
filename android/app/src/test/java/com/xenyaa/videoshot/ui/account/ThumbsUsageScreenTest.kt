package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ThumbsUsageScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 縮圖每列張數_標出目前的值_點了回報選到的張數() {
        var picked: Int? = null
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 3,
                    onSelectThumbColumns = { picked = it },
                    backfillProgress = BackfillProgress(done = 0, total = 0, lostCount = 0),
                    backfillActionError = null,
                    onBack = {},
                    onRetryLost = {},
                    onDeleteLost = {},
                    onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("3 張").assertIsSelected()
        compose.onNodeWithText("4 張（預設）").assertIsNotSelected()
        compose.onNodeWithText("2 張").performClick()
        assertEquals(2, picked)
    }

    @Test
    fun 顯示回填進度_並附上用行動網路繼續() {
        var continued = false
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 320, total = 1200, lostCount = 0),
                    backfillActionError = null,
                    onBack = {},
                    onRetryLost = {},
                    onDeleteLost = {},
                    onContinueOnMobileData = { continued = true },
                )
            }
        }
        compose.onNodeWithText("縮圖回填中 320 / 1200").assertIsDisplayed()
        compose.onNodeWithText("用行動網路繼續").performClick()
        assert(continued)
    }

    @Test
    fun 有無法取回的張數時顯示兩顆按鈕_點擊會呼叫callback() {
        var retried = false
        var deleted = false
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 100, total = 100, lostCount = 12),
                    backfillActionError = null,
                    onBack = {},
                    onRetryLost = { retried = true },
                    onDeleteLost = { deleted = true },
                    onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("無法取回 12 張").assertIsDisplayed()
        compose.onNodeWithText("稍後重試").performClick()
        assert(retried)
        compose.onNodeWithText("刪除這些收藏").performClick()
        // 「刪除這些收藏」先跳確認框，onDeleteLost 要等確認框裡的【刪除】按下去才觸發。
        compose.onNodeWithText("刪除這些收藏？").assertIsDisplayed()
        assert(!deleted)
        compose.onNodeWithText("刪除").performClick()
        assert(deleted)
    }

    @Test
    fun 沒有無法取回的張數時不顯示這兩顆按鈕() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 100, total = 100, lostCount = 0),
                    backfillActionError = null,
                    onBack = {},
                    onRetryLost = {},
                    onDeleteLost = {},
                    onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("稍後重試").assertDoesNotExist()
    }

    /**
     * `BackfillProgress.total` 是 ok ＋ missing，而 ok 會一直累積（取圖精靈裁成功的每一張也
     * 算一列 ok），所以 total > 0 在一般裝置上永遠成立——條件必須是「還有沒做完的」，否則
     * 「縮圖回填中 N / N」與【用行動網路繼續】會永久掛在畫面上（最終審查 Important 發現）。
     */
    @Test
    fun 回填做完就不再顯示進度與用行動網路繼續() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 1200, total = 1200, lostCount = 0),
                    backfillActionError = null,
                    onBack = {},
                    onRetryLost = {},
                    onDeleteLost = {},
                    onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("縮圖回填中 1200 / 1200").assertDoesNotExist()
        compose.onNodeWithText("用行動網路繼續").assertDoesNotExist()
    }

    @Test
    fun 用量一列顯示已使用與數值() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1536,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 10, total = 10, lostCount = 0),
                    backfillActionError = null,
                    onBack = {}, onRetryLost = {}, onDeleteLost = {}, onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("已使用").assertIsDisplayed()
        compose.onNodeWithText("1.5 KB").assertIsDisplayed()
        compose.onNodeWithText("已收藏的縮圖沒有容量上限；這裡只顯示目前佔用的空間。").assertIsDisplayed()
    }

    @Test
    fun 回填中顯示細進度條() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 320, total = 1200, lostCount = 0),
                    backfillActionError = null,
                    onBack = {}, onRetryLost = {}, onDeleteLost = {}, onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithTag("backfillProgressBar").assertIsDisplayed().assertHeightIsEqualTo(4.dp)
    }

    @Test
    fun 回填做完時沒有進度條() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 1200, total = 1200, lostCount = 0),
                    backfillActionError = null,
                    onBack = {}, onRetryLost = {}, onDeleteLost = {}, onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithTag("backfillProgressBar").assertDoesNotExist()
    }

    @Test
    fun 回填中又有無法取回時進度與三顆按鈕並存_錯誤訊息照樣顯示() {
        compose.setContent {
            VideoshotTheme {
                ThumbsUsageScreen(
                    usageBytes = 1024,
                    thumbColumns = 4,
                    onSelectThumbColumns = {},
                    backfillProgress = BackfillProgress(done = 5, total = 20, lostCount = 3),
                    backfillActionError = "排程失敗",
                    onBack = {}, onRetryLost = {}, onDeleteLost = {}, onContinueOnMobileData = {},
                )
            }
        }
        compose.onNodeWithText("縮圖回填中 5 / 20").assertIsDisplayed()
        compose.onNodeWithText("用行動網路繼續").assertIsDisplayed()
        compose.onNodeWithText("稍後重試").assertIsDisplayed()
        compose.onNodeWithText("刪除這些收藏").assertIsDisplayed()
        compose.onNodeWithText("排程失敗").assertIsDisplayed()
    }
}
