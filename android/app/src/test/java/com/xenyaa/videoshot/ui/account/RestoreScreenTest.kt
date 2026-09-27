package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RestoreScreenTest {

    @get:Rule val compose = createComposeRule()
    private val backup = RemoteBackup("a", "library-a.db.gz", 1_700_000_000L, 2048, 1, 42, "Pixel", "sha")

    private fun setContent(
        step: RestoreStep,
        onPick: (RemoteBackup) -> Unit = {},
        onConfirm: () -> Unit = {},
        onDismissConfirm: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme { RestoreScreen(step, onBack = {}, onPick, onConfirm, onDismissConfirm, onRetry) }
        }
    }

    @Test
    fun Picking時列出備份張數與裝置名稱() {
        setContent(RestoreStep.Picking(listOf(backup)))
        compose.onNodeWithText("42 張 · Pixel · 2.0 KB").assertIsDisplayed()
    }

    @Test
    fun 點一份備份會呼叫onPick() {
        var picked: RemoteBackup? = null
        setContent(RestoreStep.Picking(listOf(backup)), onPick = { picked = it })
        compose.onNodeWithText("42 張 · Pixel · 2.0 KB").performClick()
        assert(picked == backup)
    }

    @Test
    fun Confirming顯示破壞性提示_按還原呼叫onConfirm() {
        var confirmed = false
        setContent(RestoreStep.Confirming(backup), onConfirm = { confirmed = true })
        compose.onNodeWithText("本機目前的收藏會被取代，未完成的取圖草稿也會捨棄。這個動作不能復原。").assertIsDisplayed()
        compose.onNodeWithText("還原").performClick()
        assert(confirmed)
    }

    @Test
    fun Failed顯示原因與重試按鈕() {
        var retried = false
        setContent(RestoreStep.Failed("雜湊不符"), onRetry = { retried = true })
        compose.onNodeWithText("雜湊不符").assertIsDisplayed()
        compose.onNodeWithText("重試").performClick()
        assert(retried)
    }
}
