package com.xenyaa.videoshot.core.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DHashTest {

    private fun gray(f: (Int, Int) -> Int) =
        IntArray(DHASH_WIDTH * DHASH_HEIGHT) { i -> f(i % DHASH_WIDTH, i / DHASH_WIDTH) }

    @Test
    fun `同一張圖的指紋完全相同`() {
        val g = gray { x, y -> (x * 17 + y * 31) % 256 }
        assertEquals(dHash(g), dHash(g.copyOf()))
    }

    @Test
    fun `全白與全黑的指紋都是 0`() {
        assertEquals(0L, dHash(gray { _, _ -> 255 }))
        assertEquals(0L, dHash(gray { _, _ -> 0 }))
    }

    @Test
    fun `由左至右漸亮時每一位都是 1`() {
        assertEquals(-1L, dHash(gray { x, _ -> x * 28 }))
    }

    @Test
    fun `整體加亮不改變指紋`() {
        val base = gray { x, y -> 40 + x * 10 + y }
        val brighter = gray { x, y -> 80 + x * 10 + y }
        assertEquals(dHash(base), dHash(brighter))
    }

    @Test
    fun `不同的圖給出不同的指紋`() {
        val a = gray { x, y -> (x * 29 + y * 7) % 256 }
        val b = gray { x, y -> (x * 7 + y * 29) % 256 }
        assertNotEquals(dHash(a), dHash(b))
    }

    @Test
    fun `改一個像素只動到少數幾位`() {
        val f = { x: Int, y: Int -> 50 + x * 20 + y * 3 }
        val a = gray(f)
        val b = gray(f).also { it[DHASH_WIDTH * 3 + 4] = 250 }
        val d = hammingDistance(dHash(a), dHash(b))
        assertTrue("距離應該很小，實際 $d", d in 1..2)
    }

    @Test
    fun `完全無關的雜訊圖距離很大`() {
        val r = Random(42)
        val a = gray { _, _ -> r.nextInt(256) }
        val b = gray { _, _ -> r.nextInt(256) }
        assertTrue(hammingDistance(dHash(a), dHash(b)) > 20)
    }

    @Test
    fun `漢明距離的基本性質`() {
        assertEquals(0, hammingDistance(0L, 0L))
        assertEquals(64, hammingDistance(0L, -1L))
        assertEquals(1, hammingDistance(0L, 1L))
        assertEquals(hammingDistance(123L, 456L), hammingDistance(456L, 123L))
    }

    @Test
    fun `長度不對就丟例外`() {
        val wrong = runCatching { dHash(IntArray(70)) }
        assertTrue(wrong.exceptionOrNull() is IllegalArgumentException)
    }
}
