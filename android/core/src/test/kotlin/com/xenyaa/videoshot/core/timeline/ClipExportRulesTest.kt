package com.xenyaa.videoshot.core.timeline

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.Assert.assertEquals

class ClipExportRulesTest {
    private val tz = ZoneId.of("Asia/Taipei")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).atZone(tz).toInstant().toEpochMilli()
    private fun photo(id: Long, t: Long) = ClipSource(id, "content://p/$id", SourceKind.PHOTO, t, null, 4000, 3000, 0)

    @Test fun 標題是起訖日期() = assertEquals("videoshot 2026/10/20～10/27", defaultClipTitle(listOf(photo(1, at(2026, 10, 27)), photo(2, at(2026, 10, 20))), tz))
    @Test fun 同一天只寫一天() = assertEquals("videoshot 2026/10/18", defaultClipTitle(listOf(photo(1, at(2026, 10, 18)), photo(2, at(2026, 10, 18))), tz))
    @Test fun 跨年兩邊都寫年份() = assertEquals("videoshot 2025/12/30～2026/01/02", defaultClipTitle(listOf(photo(1, at(2025, 12, 30)), photo(2, at(2026, 1, 2))), tz))
    @Test fun 沒有素材只寫videoshot() = assertEquals("videoshot", defaultClipTitle(emptyList(), tz))

    @Test fun 段落全刪光的素材不算用到() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1), photo(2, 2)))
        val trimmed = d.copy(history = d.history.apply(d.timeline.delete(0)))
        assertEquals(listOf(2L), trimmed.usedSources().map { it.id })
    }

    @Test fun 同一個素材分割成兩段只算一次() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1)))
        val split = d.timeline.splitAt(1500)!!
        assertEquals(2, split.segments.size)
        val used = d.copy(history = d.history.apply(split)).usedSources()
        assertEquals(1, used.size)
        assertEquals(1L, used[0].id)
    }

    @Test fun 用到的素材依拍攝時間再依id由舊到新() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(3, 5), photo(1, 9), photo(2, 5)))
        assertEquals(listOf(2L, 3L, 1L), d.usedSources().map { it.id })
    }

    @Test fun 空間門檻是預估大小兩倍() {
        assertEquals(75_000_000L, estimatedExportBytes(60_000))
        assertEquals(1_500_000_000L, requiredFreeBytes(600_000))
    }
}
