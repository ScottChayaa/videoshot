package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipOrientationTest {
    @Test fun 直式是1080乘1920上限3分鐘() {
        assertEquals(1080 to 1920, ClipOrientation.PORTRAIT.width to ClipOrientation.PORTRAIT.height)
        assertEquals(180_000L, ClipOrientation.PORTRAIT.maxDurationMs)
    }

    @Test fun 橫式是1920乘1080上限10分鐘() {
        assertEquals(1920 to 1080, ClipOrientation.LANDSCAPE.width to ClipOrientation.LANDSCAPE.height)
        assertEquals(600_000L, ClipOrientation.LANDSCAPE.maxDurationMs)
    }
}
