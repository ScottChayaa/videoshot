package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

/** 時間軸條的座標換算：1 秒 40dp、段與段之間 2dp。 */
class ClipTimelineGeometryTest {
    private val timeline = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 5000), Segment(3, 0, 1000)))

    @Test fun 一秒四十dp() {
        assertEquals(40f, TimelineGeometry(Timeline(listOf(Segment(1, 0, 3000)))).xDp(1000), 0.001f)
    }

    @Test fun 後面的段要加上前面的間隔() {
        // 第 2 段起點：3 秒＝120dp，加 1 個 2dp 間隔
        assertEquals(122f, TimelineGeometry(timeline).xDp(3000), 0.001f)
        // 第 3 段起點：8 秒＝320dp，加 2 個間隔
        assertEquals(324f, TimelineGeometry(timeline).xDp(8000), 0.001f)
    }

    @Test fun 總長位置落在最後一段的尾端() {
        assertEquals(324f + 40f, TimelineGeometry(timeline).xDp(9000), 0.001f)
    }

    @Test fun 空時間軸在零() {
        assertEquals(0f, TimelineGeometry(Timeline(emptyList())).xDp(5000), 0.001f)
        assertEquals(0L, TimelineGeometry(Timeline(emptyList())).msAt(100f))
    }

    @Test fun 座標換回毫秒() {
        assertEquals(1000L, TimelineGeometry(timeline).msAt(40f))
        assertEquals(3000L, TimelineGeometry(timeline).msAt(122f))
        assertEquals(4000L, TimelineGeometry(timeline).msAt(162f))
    }

    @Test fun 落在間隔上算後一段的起點() {
        assertEquals(3000L, TimelineGeometry(timeline).msAt(121f))
    }

    @Test fun 超出兩端夾回零與總長() {
        assertEquals(0L, TimelineGeometry(timeline).msAt(-50f))
        assertEquals(9000L, TimelineGeometry(timeline).msAt(9999f))
    }

    @Test fun 來回換算不變() {
        listOf(0L, 1234L, 3000L, 7777L, 8000L, 9000L).forEach {
            assertEquals(it, TimelineGeometry(timeline).msAt(TimelineGeometry(timeline).xDp(it)))
        }
    }

    @Test fun 可見範圍用二分搜尋找出() {
        val g = TimelineGeometry(timeline)
        // 內容座標 130..330 跨過第 2、3 段（第 2 段 122..322，第 3 段 324..364）
        assertEquals(1, g.firstEndingAfter(130f))
        assertEquals(2, g.lastStartingBefore(330f))
        assertEquals(0, g.firstEndingAfter(-10f))
        assertEquals(3, g.firstEndingAfter(999f)) // 都在左邊
        assertEquals(-1, g.lastStartingBefore(-10f)) // 都在右邊
    }

    @Test fun 長時間軸的座標也快速且正確() {
        val long = Timeline(List(2000) { Segment(it.toLong(), 0, 1000) })
        val g = TimelineGeometry(long)
        // 第 1000 段起點：1000 秒＝40000dp，加 1000 個間隔
        assertEquals(42_000f, g.xDp(1_000_000), 0.01f)
        assertEquals(1_000_000L, g.msAt(42_000f))
    }
}
