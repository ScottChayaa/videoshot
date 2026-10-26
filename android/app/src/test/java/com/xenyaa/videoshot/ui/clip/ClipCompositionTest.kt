package com.xenyaa.videoshot.ui.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipCompositionTest {
    @Test fun 橫拍照片放進直式只佔中間一條() {
        val s = fitScale(4000, 3000, 0, 1080, 1920)
        assertEquals(1f, s.x, 0.001f)
        assertEquals(0.421875f, s.y, 0.001f) // 1080×810 / 1080×1920
        assertTrue(needsBlurBackground(s))
    }

    @Test fun 直拍影片放進直式剛好填滿() {
        val s = fitScale(1080, 1920, 0, 1080, 1920)
        assertEquals(FitScale(1f, 1f), s)
        assertFalse(needsBlurBackground(s))
    }

    @Test fun 旋轉90度的影片寬高對調() = assertEquals(FitScale(1f, 1f), fitScale(1920, 1080, 90, 1080, 1920))

    @Test fun 旋轉270度的影片寬高對調() = assertEquals(FitScale(1f, 1f), fitScale(1920, 1080, 270, 1080, 1920))

    @Test fun 直拍放進橫式左右留模糊() {
        val s = fitScale(1080, 1920, 0, 1920, 1080)
        assertEquals(0.31640625f, s.x, 0.001f)
        assertEquals(1f, s.y, 0.001f)
    }

    @Test fun 差不到百分之二不加模糊() {
        assertFalse(needsBlurBackground(FitScale(1f, 0.985f)))
        assertTrue(needsBlurBackground(FitScale(1f, 0.97f)))
    }
}
