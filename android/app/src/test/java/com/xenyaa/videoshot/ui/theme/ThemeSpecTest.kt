package com.xenyaa.videoshot.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 主題挑哪一套配色。
 *
 * 節慶主題（聖誕、春節、萬聖節…）**只需要做淺色一套**：`dark` 留 null 時，
 * 深色模式沿用它自己的淺色，而不是掉回預設色系 —— 使用者選了聖誕卻在晚上看到靛藍，
 * 會以為主題壞了。
 */
class ThemeSpecTest {

    private val light = Palettes.DEFAULT.light
    private val dark = requireNotNull(Palettes.DEFAULT.dark) { "預設色系一定要有深色" }

    private val 兩套都有 = ThemeSpec(id = "both", label = "兩套", light = light, dark = dark)
    private val 只有淺色 = ThemeSpec(id = "light-only", label = "只有淺色", light = light)

    @Test
    fun 淺色模式一律用淺色那套() {
        assertSame(light, 兩套都有.paletteFor(dark = false))
        assertSame(light, 只有淺色.paletteFor(dark = false))
    }

    @Test
    fun 深色模式有深色就用深色() {
        assertSame(dark, 兩套都有.paletteFor(dark = true))
    }

    @Test
    fun 沒有深色的主題在深色模式沿用自己的淺色() {
        assertSame(只有淺色.light, 只有淺色.paletteFor(dark = true))
    }

    /** Material3 的底層 scheme 要跟著實際用的那一套走，不是跟著系統走。 */
    @Test
    fun 沒有深色的主題在深色模式不套用深色的_ColorScheme() {
        assertEquals(false, 只有淺色.usesDarkScheme(dark = true))
        assertEquals(true, 兩套都有.usesDarkScheme(dark = true))
        assertEquals(false, 兩套都有.usesDarkScheme(dark = false))
    }

    @Test
    fun 預設色系仍然兩套都有() {
        assertEquals("換主題的人可以不做深色，但預設色系不行", true, Palettes.DEFAULT.dark != null)
    }
}
