package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private fun photo(id: Long, t: Long) = ClipSource(id, "content://p/$id", SourceKind.PHOTO, t, null, 4000, 3000, 0)
    private fun video(id: Long, t: Long, d: Long) = ClipSource(id, "content://v/$id", SourceKind.VIDEO, t, d, 1920, 1080, 0)

    @Test fun 初始時間軸由舊到新照片三秒() {
        val tl = initialTimeline(listOf(video(2, 200, 10_000), photo(1, 100), photo(3, 300)))
        assertEquals(listOf(1L, 2L, 3L), tl.segments.map { it.sourceId })
        assertEquals(listOf(3000L, 10_000L, 3000L), tl.segments.map { it.lengthMs })
        assertEquals(16_000L, tl.durationMs)
    }

    @Test fun 沒有長度的影片略過() =
        assertEquals(1, initialTimeline(listOf(video(1, 1, 0), photo(2, 2))).segments.size)

    @Test fun 長度為空的影片與負值都略過() {
        val tl = initialTimeline(listOf(video(1, 1, -5), ClipSource(2, "u", SourceKind.VIDEO, 2, null, 1, 1, 0)))
        assertTrue(tl.segments.isEmpty())
    }

    @Test fun 同時間依id由小到大() {
        val tl = initialTimeline(listOf(photo(5, 100), photo(2, 100), photo(9, 50)))
        assertEquals(listOf(9L, 2L, 5L), tl.segments.map { it.sourceId })
    }

    @Test fun 交界屬於後一段() {
        val tl = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 10_000)))
        assertEquals(1 to 0L, tl.locate(3000))
        assertEquals(0 to 2999L, tl.locate(2999))
        assertNull(tl.locate(13_000))
    }

    @Test fun 負的位置與空時間軸回null() {
        assertNull(Timeline(listOf(Segment(1, 0, 3000))).locate(-1))
        assertNull(Timeline(emptyList()).locate(0))
    }

    @Test fun startOf是前面各段長度總和() {
        val tl = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 500, 10_000)))
        assertEquals(0L, tl.startOf(0))
        assertEquals(3000L, tl.startOf(1))
    }

    @Test fun 分割影片段第二段從分割點開始() {
        val tl = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 10_000))).splitAt(7000)!!
        assertEquals(listOf(Segment(1, 0, 3000), Segment(2, 0, 4000), Segment(2, 4000, 10_000)), tl.segments)
        assertEquals(13_000L, tl.durationMs)
    }

    @Test fun 交界與太靠近邊界不能分割() {
        val tl = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 10_000)))
        assertFalse(tl.canSplitAt(3000))
        assertFalse(tl.canSplitAt(3050))
        assertFalse(tl.canSplitAt(2950))
        assertTrue(tl.canSplitAt(3100))
        assertNull(tl.splitAt(3000))
    }

    @Test fun 超出範圍不能分割() {
        val tl = Timeline(listOf(Segment(1, 0, 3000)))
        assertFalse(tl.canSplitAt(3000))
        assertFalse(tl.canSplitAt(-10))
    }

    @Test fun 分割照片段兩段長度相加仍是三秒() {
        val tl = Timeline(listOf(Segment(1, 0, 3000))).splitAt(1200)!!
        assertEquals(listOf(Segment(1, 0, 1200), Segment(1, 1200, 3000)), tl.segments)
    }

    @Test fun 刪除一段() = assertEquals(
        listOf(Segment(2, 0, 10_000)),
        Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 10_000))).delete(0).segments,
    )
}
