package com.xenyaa.videoshot.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.ui.testing.near
import com.xenyaa.videoshot.ui.testing.pixelsAround
import com.xenyaa.videoshot.ui.theme.Palettes
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

    // 失敗原因的顏色測試要量像素（pixelsAround），需要 Activity 版的 rule；其餘用法與 createComposeRule 相同
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
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

    @Test
    fun Picking多份備份各自成為可點的列() {
        val older = backup.copy(id = "b", createdAtEpochSec = 1_600_000_000L, shotCount = 7, deviceName = "Galaxy", sizeBytes = 4096)
        setContent(RestoreStep.Picking(listOf(backup, older)))
        compose.onNodeWithText("42 張 · Pixel · 2.0 KB").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("7 張 · Galaxy · 4.0 KB").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun Picking沒有備份時顯示空狀態文字() {
        setContent(RestoreStep.Picking(emptyList()))
        compose.onNodeWithText("Drive 上還沒有任何備份").assertIsDisplayed()
    }

    @Test
    fun Loading與Restoring顯示各自的讀取中文字() {
        setContent(RestoreStep.Loading)
        compose.onNodeWithText("正在讀取備份清單…").assertIsDisplayed()
    }

    @Test
    fun Confirming按取消呼叫onDismissConfirm_不呼叫onConfirm() {
        var dismissed = false
        var confirmed = false
        setContent(RestoreStep.Confirming(backup), onConfirm = { confirmed = true }, onDismissConfirm = { dismissed = true })
        compose.onNodeWithText("取消").performClick()
        assert(dismissed && !confirmed)
    }

    /** 還原失敗不是破壞性動作：原因用 warn 色，不是紅色（紅色只給取代本機收藏的【還原】）。 */
    @Test
    fun Failed原因用warn色而不是紅色() {
        setContent(RestoreStep.Failed("雜湊不符"))
        val grid = compose.pixelsAround(compose.onNodeWithText("雜湊不符"), margin = 0)
        fun count(c: Color): Int {
            var n = 0
            for (y in 0 until grid.height) for (x in 0 until grid.width) if (near(grid[x, y], c, 0.2f)) n++
            return n
        }
        val colors = Palettes.DEFAULT.light
        assert(count(colors.warn) > 0) { "失敗原因應是 warn 色" }
        assert(count(colors.danger) == 0) { "失敗原因不該出現紅色" }
    }
}
