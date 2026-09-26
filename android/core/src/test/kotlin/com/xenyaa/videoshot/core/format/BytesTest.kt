package com.xenyaa.videoshot.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class BytesTest {

    @Test
    fun 小於1024用位元組() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun 剛好1024換算成KB() {
        assertEquals("1.0 KB", formatBytes(1024))
    }

    @Test
    fun 換算到MB一位小數() {
        assertEquals("12.3 MB", formatBytes((12.3 * 1024 * 1024).toLong()))
    }

    @Test
    fun 換算到GB() {
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
}
