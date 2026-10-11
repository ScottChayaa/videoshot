package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.library.dao.TagNameKind
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

/** Lightbox 頂列（返回、日期、地點）、標籤列與底部的兩行描述。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LightboxInfoTest {

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

    private val shot = ShotRow(
        id = 7, videoId = "abc123", atSec = 95.6, source = "storyboard", frameIndex = 3, sbLevel = 3,
        eventDate = "2026-08-03", place = "加勒比海", description = "夜潛第一次見到巨型犀牛蝦\n犀牛蝦好大",
    )

    private var tagReads = 0
    private var closed = 0

    private fun show(
        items: List<ShotRow> = listOf(shot),
        tagsOf: suspend (ShotRow) -> List<TagNameKind> = {
            tagReads++
            listOf(TagNameKind("Emiru", "person"), TagNameKind("夜潛", "topic"))
        },
        refreshKey: () -> Int = { 0 },
    ) {
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = items, hasMore = false, startIndex = 0, loader = loader,
                    hintSeen = true, onHintSeen = {}, onClose = { closed++ }, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions(),
                    tagsOf = tagsOf,
                    tagsRefreshKey = refreshKey(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun 左上角是返回箭頭() {
        show()
        compose.onNodeWithContentDescription("關閉").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun 頂列顯示日期與地點_不顯示影片秒數() {
        show()
        compose.onNodeWithText("2026年8月3日").assertIsDisplayed()
        compose.onNodeWithText("加勒比海").assertIsDisplayed()
        compose.onNodeWithText("01:35").assertDoesNotExist()
    }

    @Test
    fun 沒有地點就不畫地點小膠囊() {
        show(items = listOf(shot.copy(place = null)))
        compose.onNodeWithText("加勒比海").assertDoesNotExist()
        compose.onNodeWithText("2026年8月3日").assertIsDisplayed()
    }

    @Test
    fun 標籤列顯示這張圖的標籤() {
        show()
        compose.onNodeWithText("Emiru").assertIsDisplayed()
        compose.onNodeWithText("夜潛").assertIsDisplayed()
    }

    @Test
    fun 標籤讀不到時不擋看圖() {
        show(tagsOf = { error("db 壞了") })
        compose.onNodeWithText("2026年8月3日").assertIsDisplayed()
        compose.onNodeWithText("Emiru").assertDoesNotExist()
    }

    @Test
    fun 底部顯示描述() {
        show()
        compose.onNodeWithText("夜潛第一次見到巨型犀牛蝦\n犀牛蝦好大").assertIsDisplayed()
    }

    @Test
    fun 編輯存檔後重讀標籤() {
        var key by mutableIntStateOf(0)
        show(refreshKey = { key })
        val before = tagReads
        key++
        compose.waitForIdle()
        assertEquals(before + 1, tagReads)
    }
}
