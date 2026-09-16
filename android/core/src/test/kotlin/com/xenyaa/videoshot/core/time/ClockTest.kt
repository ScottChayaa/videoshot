package com.xenyaa.videoshot.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test fun 補零到兩位() = assertEquals("01:05", formatClock(65.4))
    @Test fun 負數與零都回零() {
        assertEquals("00:00", formatClock(0.0))
        assertEquals("00:00", formatClock(-3.0))
    }
    @Test fun 超過一小時只進位到分() = assertEquals("75:30", formatClock(4530.0))
}
