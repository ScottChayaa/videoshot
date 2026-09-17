package com.xenyaa.videoshot.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    @Test
    fun 黑白是最大值_21比1() {
        assertEquals(21.0, contrastRatio(0xFFFFFF, 0x000000), 0.01)
    }

    @Test
    fun 同色是1比1() {
        assertEquals(1.0, contrastRatio(0x4F46E5, 0x4F46E5), 0.001)
    }

    @Test
    fun 順序不影響比值() {
        assertEquals(contrastRatio(0xEEF0F6, 0x4B5563), contrastRatio(0x4B5563, 0xEEF0F6), 0.0001)
    }

    @Test
    fun 白色的相對亮度是1() {
        assertEquals(1.0, relativeLuminance(0xFFFFFF), 0.001)
        assertEquals(0.0, relativeLuminance(0x000000), 0.001)
    }

    /** mockup 的註解說 v1 的 #6B7280 在 #EEF0F6 上是 4.0:1 未過 AA，換成 #4B5563 才過。 */
    @Test
    fun 原型換掉次要文字色的理由成立() {
        assertTrue(contrastRatio(0xEEF0F6, 0x6B7280) < 4.5)
        assertTrue(contrastRatio(0xEEF0F6, 0x4B5563) >= 4.5)
    }
}
