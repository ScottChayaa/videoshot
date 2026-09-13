package com.xenyaa.videoshot.core.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardParseTest {

    @Test
    fun `解析出 base URL、sqp 與四個 level`() {
        val spec = Storyboard.parse(REAL_SPEC)
        assertNotNull(spec)
        assertEquals(4, spec!!.levels.size)
        assertEquals("-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg==", spec.sqp)
        assertEquals("https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L\$L/\$N.jpg", spec.baseUrl)
    }

    @Test
    fun `L3 的參數正確`() {
        val l3 = Storyboard.parse(REAL_SPEC)!!.levels[3]
        assertEquals(
            StoryboardLevel(
                level = 3,
                width = 320,
                height = 180,
                frameCount = 25,
                cols = 3,
                rows = 3,
                intervalMs = 1000,
                sigh = "rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"
            ),
            l3
        )
    }

    @Test
    fun `level 編號等於它在 spec 裡的順序`() {
        val levels = Storyboard.parse(REAL_SPEC)!!.levels
        assertEquals(listOf(0, 1, 2, 3), levels.map { it.level })
    }

    @Test
    fun `格式不符回傳 null`() {
        assertNull(Storyboard.parse(""))
        assertNull(Storyboard.parse("https://example.com/no-levels"))
        assertNull(Storyboard.parse("not-a-url|1#2"))
    }

    @Test
    fun `欄位不足或不是數字的 level 會被略過`() {
        val spec = Storyboard.parse("https://x/\$L/\$N.jpg?sqp=a|1#2#3|320#180#x#3#3#1000#M\$M#s|320#180#25#3#3#1000#M\$M#ok")
        assertEquals(1, spec!!.levels.size)
        assertEquals("ok", spec.levels[0].sigh)
        assertEquals(2, spec.levels[0].level)
    }
}
