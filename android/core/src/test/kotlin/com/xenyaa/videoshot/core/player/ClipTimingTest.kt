package com.xenyaa.videoshot.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTimingTest {

    @Test fun 一般情況從截圖秒數播三秒() =
        assertEquals(ClipRange(30.0, 33.0), clipRange(30.0, 600.0))

    @Test fun 超過片尾只播到片尾() =
        assertEquals(ClipRange(598.5, 600.0), clipRange(598.5, 600.0))

    @Test fun 片尾剩不到一秒時往前推_湊滿三秒() =
        // 片尾截的圖：只剩 0.4 秒的循環會變成瘋狂跳回，往前推成最後三秒
        assertEquals(ClipRange(597.0, 600.0), clipRange(599.6, 600.0))

    @Test fun 截圖秒數超過總長也往前推() =
        // watch page 的總長是整數秒（小數被截掉），截圖秒數可能比它還大
        assertEquals(ClipRange(597.0, 600.0), clipRange(600.4, 600.0))

    @Test fun 影片比三秒還短就整支循環() =
        assertEquals(ClipRange(0.0, 2.0), clipRange(1.5, 2.0))

    @Test fun 讀不到總長就照截圖秒數播三秒() =
        assertEquals(ClipRange(30.0, 33.0), clipRange(30.0, 0.0))

    @Test fun 負的截圖秒數夾成零() =
        assertEquals(ClipRange(0.0, 3.0), clipRange(-1.0, 600.0))

    @Test fun 常數照規格() {
        assertEquals(1000L, CLIP_DELAY_MS)
        assertEquals(3.0, CLIP_LENGTH_SEC, 0.0)
        assertTrue(CLIP_MIN_LENGTH_SEC < CLIP_LENGTH_SEC)
    }
}
