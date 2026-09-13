package com.xenyaa.videoshot.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPageParserTest {

    private fun page(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/watchpages/$name")) { "找不到測試頁面 $name" }
            .bufferedReader().use { it.readText() }

    // ---- 括號配對 ----

    @Test
    fun `擷取 marker 之後的第一個完整物件`() {
        val html = """<script>var x = {"a":1,"b":{"c":2}};</script>"""
        assertEquals("""{"a":1,"b":{"c":2}}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `字串裡的大括號不算層次`() {
        val html = """var x = {"a":"}{}{","b":1};"""
        assertEquals("""{"a":"}{}{","b":1}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `跳脫的引號不會提前結束字串`() {
        val html = """var x = {"a":"say \"hi\" }","b":2};"""
        assertEquals("""{"a":"say \"hi\" }","b":2}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `反斜線本身被跳脫時不影響後面的引號`() {
        val html = """var x = {"a":"c:\\","b":3};"""
        assertEquals("""{"a":"c:\\","b":3}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `找不到 marker 或括號不完整時回傳 null`() {
        assertNull(extractJsonObject("沒有東西", "var x = "))
        assertNull(extractJsonObject("var x = 不是物件", "var x = "))
        assertNull(extractJsonObject("""var x = {"a":1""", "var x = "))
    }

    // ---- 真實頁面 ----

    @Test
    fun `吃得下真實錄製的 watch page`() {
        val r = parseWatchPage("aqz-KE-bpKQ", page("ok-real.html"))
        assertEquals(FetchResult.OK, r.result)
        val meta = r.meta!!
        assertEquals("aqz-KE-bpKQ", meta.videoId)
        assertEquals("Blender", meta.channelTitle)
        assertEquals(635, meta.durationSec)
        assertEquals("public", meta.privacy)
        assertTrue(meta.title.contains("Big Buck Bunny"))
        assertTrue("publishedAt=${meta.publishedAt}", meta.publishedAt.startsWith("2014-11-10"))
        assertTrue(meta.playableInEmbed)
        assertTrue(
            "spec=${r.storyboardSpec?.take(60)}",
            r.storyboardSpec!!.startsWith("https://i.ytimg.com/sb/aqz-KE-bpKQ/"),
        )
    }

    @Test
    fun `影片不能播就是 video_unavailable`() {
        val r = parseWatchPage("gone", page("unavailable.html"))
        assertEquals(FetchResult.VIDEO_UNAVAILABLE, r.result)
        assertNull(r.meta)
        assertNull(r.storyboardSpec)
    }

    // 與參考實作（mockups/server.mjs）的一處刻意差異：規格第二節第 4 點。
    @Test
    fun `年齡限制即使有 videoDetails 與 spec 也算 video_unavailable`() {
        val r = parseWatchPage("age1", page("login-required.html"))
        assertEquals(FetchResult.VIDEO_UNAVAILABLE, r.result)
        assertNull(r.storyboardSpec)
    }

    @Test
    fun `能播但沒有 storyboard 就是 no_storyboard`() {
        val r = parseWatchPage("short1", page("no-storyboard.html"))
        assertEquals(FetchResult.NO_STORYBOARD, r.result)
        assertEquals("剛上傳的短片", r.meta!!.title)
        assertEquals(8, r.meta!!.durationSec)
        assertNull(r.storyboardSpec)
    }

    @Test
    fun `不公開影片的 privacy 是 unlisted`() {
        val r = parseWatchPage("KUdmrPVssFA", page("unlisted.html"))
        assertEquals(FetchResult.OK, r.result)
        assertEquals("unlisted", r.meta!!.privacy)
        assertEquals(false, r.meta!!.playableInEmbed)
        assertEquals("2026-07-30T01:58:30-07:00", r.meta!!.publishedAt)
    }

    @Test
    fun `頁面結構變了就是 parse_failed`() {
        val r = parseWatchPage("x", page("garbage.html"))
        assertEquals(FetchResult.PARSE_FAILED, r.result)
        assertNull(r.meta)
    }

    @Test
    fun `解析器不會自己回傳 fetch_failed`() {
        val all = listOf("ok-real.html", "unavailable.html", "login-required.html", "no-storyboard.html", "garbage.html")
        val results = all.map { parseWatchPage("x", page(it)).result }
        assertTrue(results.toString(), FetchResult.FETCH_FAILED !in results)
    }

    @Test
    fun `空字串不會當機`() {
        assertEquals(FetchResult.PARSE_FAILED, parseWatchPage("x", "").result)
    }
}
