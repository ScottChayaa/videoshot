package com.xenyaa.videoshot.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCaptureTest {

    @Test
    fun 預設會成功並回傳指定的秒數() = runTest {
        val c = FakeCapture(atSec = 42.5)
        val r = c.capture()
        assertTrue(r.toString(), r is CaptureResult.Success)
        assertEquals(42.5, (r as CaptureResult.Success).atSec, 0.0)
    }

    @Test
    fun 可以指定下一次的結果() = runTest {
        val c = FakeCapture()
        c.nextResult = CaptureResult.BlackFrame
        assertEquals(CaptureResult.BlackFrame, c.capture())
    }

    @Test
    fun 會記錄被呼叫幾次() = runTest {
        val c = FakeCapture()
        c.capture()
        c.capture()
        assertEquals(2, c.calls)
    }
}
