package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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
class ThumbTileTest {
    @get:Rule val compose = createComposeRule()

    /** 一律回預留圖，畫面會畫出圖示與秒數。 */
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

    private val shot = ShotRow(
        id = 1, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = "2026-03-05", place = null, description = null,
    )

    /** 設計文件決定 5：首頁與資料夾內容的縮圖是正方形。 */
    @Test fun 縮圖是正方形() {
        compose.setContent {
            VideoshotTheme { Box(Modifier.width(120.dp)) { ThumbTile(shot, loader, {}, Modifier.testTag("t")) } }
        }
        compose.onNodeWithTag("t").assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(120.dp)
    }

    @Test fun 點一下會開啟且唸得出名稱() {
        var opened = false
        compose.setContent { VideoshotTheme { ThumbTile(shot, loader, { opened = true }, Modifier.width(120.dp)) } }
        compose.onNodeWithContentDescription("片段縮圖 01:05").performClick()
        assertTrue(opened)
    }

    @Test fun 預留圖標出影片秒數() {
        compose.setContent { VideoshotTheme { ThumbTile(shot, loader, {}, Modifier.width(120.dp)) } }
        compose.onNodeWithText("01:05").assertExists()
    }

    /**
     * 快速捲動時每一格剛進畫面的那一幀：快取裡已經有圖，就直接畫圖，
     * 不先畫一幀「圖示＋秒數」再換掉（那一幀的文字排版是捲動慢幀的大宗，2026-10-22 實機 atrace）。
     *
     * 時鐘不自動前進，畫面停在第一次組合的結果：舊版 `produceState(null)` 這時一定還是預留圖。
     */
    @Test fun 快取有圖時第一幀就畫圖_不畫預留圖() {
        val cached = ThumbLoader(
            thumbs = object : Thumbs {
                override suspend fun thumbFor(shot: ShotRow) = ThumbSource.LocalFile(File("/a.webp"))
                override fun fileOf(key: ThumbKey) = File("/unused")
                override fun exists(key: ThumbKey) = false
                override suspend fun delete(key: ThumbKey) = Unit
                override suspend fun deleteVideo(videoId: String) = Unit
            },
            decodeFile = { ImageBitmap(4, 4) }, decodeBytes = { null }, cover = { null },
        )
        runBlocking { cached.load(shot) }
        compose.mainClock.autoAdvance = false
        compose.setContent { VideoshotTheme { ThumbTile(shot, cached, {}, Modifier.width(120.dp)) } }
        compose.onNodeWithText("01:05").assertDoesNotExist()
        compose.onNodeWithTag(PLACEHOLDER_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * 還在載入（解碼沒回來）時畫灰色骨架塊，不畫預留圖的圖示與秒數 ——
     * 通常幾幀後就換成真圖，秒數文字閃一下體感不好（2026-10-22 scott）。拿不到圖才畫預留圖（上面那一則）。
     */
    @Test fun 載入中畫骨架_不畫秒數() {
        val never = CompletableDeferred<ImageBitmap?>()
        val loading = ThumbLoader(
            thumbs = object : Thumbs {
                override suspend fun thumbFor(shot: ShotRow) = ThumbSource.LocalFile(File("/a.webp"))
                override fun fileOf(key: ThumbKey) = File("/unused")
                override fun exists(key: ThumbKey) = false
                override suspend fun delete(key: ThumbKey) = Unit
                override suspend fun deleteVideo(videoId: String) = Unit
            },
            decodeFile = { never.await() }, decodeBytes = { null }, cover = { null },
        )
        compose.setContent { VideoshotTheme { ThumbTile(shot, loading, {}, Modifier.width(120.dp)) } }
        compose.onNodeWithTag(SKELETON_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("01:05").assertDoesNotExist()
        compose.onNodeWithTag(PLACEHOLDER_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
        // 載入中也唸得出名稱
        compose.onNodeWithContentDescription("片段縮圖 01:05").assertExists()
    }

    @Test fun 預留圖可以只畫圖示不畫秒數() {
        compose.setContent {
            VideoshotTheme { ThumbImage(shot, loader, Modifier.width(120.dp), showTimeOnPlaceholder = false) }
        }
        compose.onNodeWithText("01:05").assertDoesNotExist()
        compose.onNodeWithTag(PLACEHOLDER_ICON_TAG, useUnmergedTree = true).assertExists()
    }
}
