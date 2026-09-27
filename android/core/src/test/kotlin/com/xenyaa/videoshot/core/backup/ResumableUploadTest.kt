package com.xenyaa.videoshot.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumableUploadTest {

    @Test
    fun 切出第一塊() {
        val chunk = nextChunk(fromByte = 0, total = 1000, chunkSize = 256)
        assertEquals(0L, chunk.start)
        assertEquals(255L, chunk.endInclusive)
        assertEquals(256L, chunk.length)
        assertEquals("bytes 0-255/1000", chunk.contentRangeHeader())
    }

    @Test
    fun 最後一塊不超過總長度() {
        val chunk = nextChunk(fromByte = 900, total = 1000, chunkSize = 256)
        assertEquals(900L, chunk.start)
        assertEquals(999L, chunk.endInclusive)
        assertEquals(100L, chunk.length)
    }

    @Test
    fun 檔案小於一個chunk時一次就送完() {
        val chunk = nextChunk(fromByte = 0, total = 100, chunkSize = 256)
        assertEquals(0L, chunk.start)
        assertEquals(99L, chunk.endInclusive)
    }

    @Test
    fun 沒有Range標頭代表完全沒收到() {
        assertEquals(0L, parseReceivedBytes(null))
    }

    @Test
    fun 解析已收到的位元組數() {
        assertEquals(256L, parseReceivedBytes("bytes=0-255"))
        assertEquals(1000L, parseReceivedBytes("bytes=0-999"))
    }
}
