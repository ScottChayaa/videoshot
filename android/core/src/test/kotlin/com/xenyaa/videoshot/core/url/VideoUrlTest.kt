package com.xenyaa.videoshot.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoUrlTest {

    private val id = "aqz-KE-bpKQ"

    @Test
    fun `純 videoId`() {
        assertEquals(id, parseVideoId(id))
    }

    @Test
    fun `watch 網址`() {
        assertEquals(id, parseVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, parseVideoId("http://youtube.com/watch?v=$id"))
        assertEquals(id, parseVideoId("https://m.youtube.com/watch?v=$id"))
        assertEquals(id, parseVideoId("www.youtube.com/watch?v=$id"))
    }

    @Test
    fun `帶時間戳或其他參數`() {
        assertEquals(id, parseVideoId("https://www.youtube.com/watch?v=$id&t=42s"))
        assertEquals(id, parseVideoId("https://www.youtube.com/watch?list=PL123&v=$id"))
        assertEquals(id, parseVideoId("https://youtu.be/$id?t=90"))
    }

    @Test
    fun `短網址`() {
        assertEquals(id, parseVideoId("https://youtu.be/$id"))
        assertEquals(id, parseVideoId("youtu.be/$id"))
    }

    @Test
    fun `Shorts`() {
        assertEquals(id, parseVideoId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, parseVideoId("https://youtube.com/shorts/$id?feature=share"))
    }

    @Test
    fun `embed 網址`() {
        assertEquals(id, parseVideoId("https://www.youtube.com/embed/$id?playsinline=1"))
    }

    @Test
    fun `前後空白會被去掉`() {
        assertEquals(id, parseVideoId("  https://youtu.be/$id  "))
        assertEquals(id, parseVideoId("\n$id\t"))
    }

    @Test
    fun `videoId 允許的字元`() {
        assertEquals("_-aBcD1234x", parseVideoId("_-aBcD1234x"))
    }

    @Test
    fun `解不出來回 null`() {
        assertNull(parseVideoId(""))
        assertNull(parseVideoId("   "))
        assertNull(parseVideoId("這不是網址"))
        assertNull(parseVideoId("https://example.com/watch?v=$id"))
        assertNull(parseVideoId("https://www.youtube.com/"))
        assertNull(parseVideoId("https://www.youtube.com/watch?v="))
    }

    @Test
    fun `長度不對的不是 videoId`() {
        assertNull(parseVideoId("tooshort"))
        assertNull(parseVideoId("waaaaaaaaaaytoolong"))
        assertNull(parseVideoId("has space12"))
    }

    @Test
    fun `頻道網址不會被誤判成 videoId`() {
        assertNull(parseVideoId("https://www.youtube.com/@SomeChannel"))
        assertNull(parseVideoId("https://www.youtube.com/channel/UCCyLrmEMB0_1R03isyUMOtQ"))
    }
}
