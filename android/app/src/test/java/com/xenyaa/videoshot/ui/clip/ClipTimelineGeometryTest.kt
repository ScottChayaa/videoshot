package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

/** 時間軸條的座標換算：1 秒 40dp、段與段之間 2dp。 */
class ClipTimelineGeometryTest {
    private val timeline = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 5000), Segment(3, 0, 1000)))

    @Test fun 一秒四十dp() {
        assertEquals(40f, timelineXDp(Timeline(listOf(Segment(1, 0, 3000))), 1000), 0.001f)
    }

    @Test fun 後面的段要加上前面的間隔() {
        // 第 2 段起點：3 秒＝120dp，加 1 個 2dp 間隔
        assertEquals(122f, timelineXDp(timeline, 3000), 0.001f)
        // 第 3 段起點：8 秒＝320dp，加 2 個間隔
        assertEquals(324f, timelineXDp(timeline, 8000), 0.001f)
    }

    @Test fun 總長位置落在最後一段的尾端() {
        assertEquals(324f + 40f, timelineXDp(timeline, 9000), 0.001f)
    }

    @Test fun 空時間軸在零() {
        assertEquals(0f, timelineXDp(Timeline(emptyList()), 5000), 0.001f)
        assertEquals(0L, timelineMsAt(Timeline(emptyList()), 100f))
    }

    @Test fun 座標換回毫秒() {
        assertEquals(1000L, timelineMsAt(timeline, 40f))
        assertEquals(3000L, timelineMsAt(timeline, 122f))
        assertEquals(4000L, timelineMsAt(timeline, 162f))
    }

    @Test fun 落在間隔上算後一段的起點() {
        assertEquals(3000L, timelineMsAt(timeline, 121f))
    }

    @Test fun 超出兩端夾回零與總長() {
        assertEquals(0L, timelineMsAt(timeline, -50f))
        assertEquals(9000L, timelineMsAt(timeline, 9999f))
    }

    @Test fun 來回換算不變() {
        listOf(0L, 1234L, 3000L, 7777L, 8000L, 9000L).forEach {
            assertEquals(it, timelineMsAt(timeline, timelineXDp(timeline, it)))
        }
    }
}
