package com.xenyaa.videoshot.wizard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftSweepTest {

    @get:Rule val tmp = TemporaryFolder()

    private val now = 1_000_000_000_000L

    /** 建一個影片目錄（含一張 sheet），最後修改時間設成 [modified]。 */
    private fun videoDir(name: String, modified: Long = now - 60_000): File =
        File(tmp.root, name).apply {
            File(this, "sheets/L3").mkdirs()
            File(this, "sheets/L3/M0.jpg").writeBytes(byteArrayOf(1))
            setLastModified(modified)
        }

    @Test
    fun 沒有草稿指著的舊目錄整個刪掉() {
        val a = videoDir("a")
        val b = videoDir("b")
        val n = sweepDraftDirs(tmp.root, keep = { setOf("b") }, olderThanMillis = now)
        assertEquals(1, n)
        assertFalse(a.exists())
        assertTrue(File(b, "sheets/L3/M0.jpg").exists())
    }

    @Test
    fun 這次啟動之後才動過的目錄不刪() {
        val fresh = videoDir("fresh", modified = now + 1_000)
        assertEquals(0, sweepDraftDirs(tmp.root, keep = { emptySet() }, olderThanMillis = now))
        assertTrue(fresh.exists())
    }

    @Test
    fun 每個目錄刪之前都重新問要留哪些() {
        videoDir("a")
        videoDir("b")
        val asked = mutableListOf<Int>()
        sweepDraftDirs(tmp.root, keep = { asked += 1; emptySet() }, olderThanMillis = now)
        assertEquals(2, asked.size)
    }

    @Test
    fun 資料夾不存在或是檔案都不出錯() {
        assertEquals(0, sweepDraftDirs(File(tmp.root, "沒有這個"), keep = { emptySet() }, olderThanMillis = now))
        File(tmp.root, "stray.txt").writeText("x")
        assertEquals(0, sweepDraftDirs(tmp.root, keep = { emptySet() }, olderThanMillis = now))
        assertTrue(File(tmp.root, "stray.txt").exists())
    }
}
