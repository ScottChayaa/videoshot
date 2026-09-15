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
    fun 圖資與core的型別互轉() {
        val details = ShotDetails("2025-07-12", "冬山河", "下水前", listOf("玩水"), applied = true)
        assertEquals(details, details.toDraftDetails().toShotDetails())
    }
}
