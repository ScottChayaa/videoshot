package com.xenyaa.videoshot.core.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvergeTest {

    /** 用位元數當距離：把前 n 位設成 1，與 0L 的漢明距離就是 n。 */
    private fun hashWithDistance(n: Int): Long = if (n == 0) 0L else (1L shl n) - 1

    private fun fp(vararg distances: Int) =
        distances.mapIndexed { i, d -> Fingerprint(i, hashWithDistance(d)) }

    @Test
    fun `空清單回傳空結果`() {
        val r = converge(emptyList(), FilterStrength.MEDIUM)
        assertEquals(emptyList<Int>(), r.kept)
        assertEquals(0, r.hiddenCount)
    }

    @Test
    fun `只有一張一定保留`() {
        val r = converge(listOf(Fingerprint(0, 123L)), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(0, r.hiddenCount)
    }

    @Test
    fun `第一張永遠保留`() {
        val r = converge(fp(0, 0, 0), FilterStrength.LOW)
        assertTrue(r.kept.contains(0))
    }

    @Test
    fun `完全相同的連續畫面只留最早的一張`() {
        val r = converge(fp(0, 0, 0, 0, 0), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(4, r.hiddenCount)
    }

    @Test
    fun `距離超過門檻就保留`() {
        val r = converge(fp(0, 7), FilterStrength.MEDIUM)
        assertEquals(listOf(0, 1), r.kept)
    }

    @Test
    fun `距離等於門檻視為相似`() {
        val r = converge(fp(0, 6), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(1, r.hiddenCount)
    }

    @Test
    fun `比較的對象是最近一張被保留的而不是前一張`() {
        val r = converge(fp(0, 4, 8), FilterStrength.MEDIUM)
        assertEquals(listOf(0, 2), r.kept)
        assertEquals(1, r.hiddenCount)
    }

    @Test
    fun `強度越高藏得越多`() {
        val input = fp(0, 4, 8, 12, 16, 20)
        val high = converge(input, FilterStrength.HIGH).kept.size
        val medium = converge(input, FilterStrength.MEDIUM).kept.size
        val low = converge(input, FilterStrength.LOW).kept.size
        assertTrue("高=$high 中=$medium 低=$low", high <= medium && medium <= low)
        assertTrue("高強度應該真的藏掉一些", high < low)
    }

    @Test
    fun `保留的順序與輸入的時間順序一致`() {
        val r = converge(fp(0, 20, 40, 60), FilterStrength.LOW)
        assertEquals(r.kept.sorted(), r.kept)
    }

    @Test
    fun `保留數加隱藏數等於總數`() {
        val input = fp(0, 2, 9, 3, 30, 1, 7)
        for (s in FilterStrength.entries) {
            val r = converge(input, s)
            assertEquals(s.name, input.size, r.kept.size + r.hiddenCount)
        }
    }

    @Test
    fun `回傳的是原始的 frameIndex 而不是清單位置`() {
        val input = listOf(Fingerprint(10, 0L), Fingerprint(11, 0L), Fingerprint(12, -1L))
        val r = converge(input, FilterStrength.MEDIUM)
        assertEquals(listOf(10, 12), r.kept)
    }

    @Test
    fun `三個強度的門檻值就是規格訂的`() {
        assertEquals(10, FilterStrength.HIGH.threshold)
        assertEquals(6, FilterStrength.MEDIUM.threshold)
        assertEquals(3, FilterStrength.LOW.threshold)
    }
}
