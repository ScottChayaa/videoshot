package com.xenyaa.videoshot.core.player

import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMathTest {

    @Test fun 滑過整個寬度等於整支影片() =
        assertEquals(600.0, scrubTargetSec(0.0, 1000f, 1000f, 600.0), 1e-9)

    @Test fun 從目前位置起算() =
        assertEquals(400.0, scrubTargetSec(100.0, 500f, 1000f, 600.0), 1e-9)

    @Test fun 往左滑是倒退() =
        assertEquals(40.0, scrubTargetSec(100.0, -100f, 1000f, 600.0), 1e-9)

    @Test fun 敏感度會縮放跳的距離() =
        assertEquals(150.0, scrubTargetSec(0.0, 1000f, 1000f, 600.0, sensitivity = 0.25), 1e-9)

    @Test fun 夾在零到總長() {
        assertEquals(0.0, scrubTargetSec(10.0, -1000f, 1000f, 600.0), 1e-9)
        assertEquals(600.0, scrubTargetSec(590.0, 1000f, 1000f, 600.0), 1e-9)
    }

    @Test fun 寬度或總長不合理就不動() {
        assertEquals(12.0, scrubTargetSec(12.0, 300f, 0f, 600.0), 1e-9)
        assertEquals(12.0, scrubTargetSec(12.0, 300f, 1000f, 0.0), 1e-9)
    }

    @Test fun 寬高比門檻是一點三() {
        assertTrue(isLandscapeVideo(1920, 1080))   // 16:9
        assertTrue(isLandscapeVideo(1440, 1080))   // 4:3 ≈ 1.333
        assertTrue(isLandscapeVideo(1300, 1000))   // 剛好 1.3
        assertFalse(isLandscapeVideo(1299, 1000))
        assertFalse(isLandscapeVideo(1080, 1080))  // 正方形
        assertFalse(isLandscapeVideo(1080, 1920))  // 直的
    }

    @Test fun 讀不到寬高當成橫的() {
        assertTrue(isLandscapeVideo(null, null))
        assertTrue(isLandscapeVideo(0, 0))
        assertTrue(isLandscapeVideo(1920, 0))
    }

    @Test fun 最近的格子() {
        val at = listOf(0.0, 10.0, 20.0, 30.0)
        assertEquals(0, nearestFrameIndex(at, 4.9))
        assertEquals(1, nearestFrameIndex(at, 5.1))
        assertEquals(3, nearestFrameIndex(at, 999.0))
        assertEquals(0, nearestFrameIndex(at, -5.0))
    }

    @Test fun 沒有格子就沒有預覽() = assertNull(nearestFrameIndex(emptyList(), 3.0))

    @Test fun 橫向視窗限高百分之四十() {
        assertEquals(320f, playerMaxHeightDp(1280f, 800f)!!, 1e-3f)
        assertNull(playerMaxHeightDp(800f, 1280f))   // 直向不限
        assertNull(playerMaxHeightDp(800f, 800f))
    }

    private fun level(n: Int) = StoryboardLevel(n, 160, 90, 100, 5, 5, 2000, "s$n")

    @Test fun 預覽層級偏好低一階的_L2() {
        val spec = StoryboardSpec("b", "q", listOf(level(0), level(1), level(2), level(3)))
        assertEquals(2, previewLevelOf(spec)!!.level)
    }

    @Test fun 沒有_L2_就退回最高可用層級() {
        val spec = StoryboardSpec("b", "q", listOf(level(0), level(1)))
        assertEquals(1, previewLevelOf(spec)!!.level)
    }
}
