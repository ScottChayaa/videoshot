package com.xenyaa.videoshot.core.draft

import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftPayloadTest {

    private val payload = DraftPayload(
        videoId = "v1",
        step = 3,
        level = 3,
        frameCount = 148,
        selected = listOf(3, 17, 148),
        manual = listOf(DraftManual(cell = 148, atSec = 92.5, fileName = "abc.webp", fromGallery = true)),
        details = mapOf(
            "3" to DraftDetails("2025-07-12", "冬山河", null, listOf("玩水"), applied = true),
        ),
    )

    @Test
    fun 編出來再解回去是同一份() {
        assertEquals(payload, DraftCodec.decode(DraftCodec.encode(payload)))
    }

    @Test
    fun 壞掉的json回null而不是丟例外() {
        assertNull(DraftCodec.decode("{ 這不是 json"))
        assertNull(DraftCodec.decode(""))
    }

    @Test
    fun 多了不認識的欄位也解得回來() {
        // 換版之後舊 app 讀到新 app 寫的草稿，不該整個報廢
        val json = DraftCodec.encode(payload).replaceFirst("{", """{"未來才有的欄位":1,""")
        assertEquals("v1", DraftCodec.decode(json)!!.videoId)
    }

    @Test
    fun 只存檔名不存路徑() {
        assertTrue(DraftCodec.encode(payload).contains("abc.webp"))
        assertTrue(!DraftCodec.encode(payload).contains("/"))
    }

    @Test
    fun 存了spec與影片資訊也編得回來() {
        val withPage = payload.copy(
            sbSpec = "https://i.ytimg.com/sb/v1/storyboard3_L\$L/\$N.jpg|48#27#100#10#10#0#default#rs\$abc",
            meta = DraftMeta("宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true),
        )
        assertEquals(withPage, DraftCodec.decode(DraftCodec.encode(withPage)))
    }

    @Test
    fun 舊版草稿沒有spec與影片資訊也解得回來() {
        val old = """{"videoId":"v1","step":2,"level":3,"frameCount":4,"selected":[1]}"""
        val decoded = DraftCodec.decode(old)!!
        assertNull(decoded.sbSpec)
        assertNull(decoded.meta)
        assertEquals(listOf(1), decoded.selected)
    }

    @Test
    fun 影片資訊與watch_page的型別互轉() {
        val meta = com.xenyaa.videoshot.core.youtube.VideoMeta("v1", "標題", "ch", "2025-07-12", 600, "public", false)
        assertEquals(meta, meta.toDraftMeta().toVideoMeta("v1"))
    }

    @Test
    fun 圖資與core的型別互轉() {
        val details = ShotDetails("2025-07-12", "冬山河", "下水前", listOf("玩水"), applied = true)
        assertEquals(details, details.toDraftDetails().toShotDetails())
    }
}
