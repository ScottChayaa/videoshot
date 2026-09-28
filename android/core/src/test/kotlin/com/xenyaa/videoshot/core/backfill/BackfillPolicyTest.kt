package com.xenyaa.videoshot.core.backfill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackfillPolicyTest {

    @Test
    fun 退避以天為單位指數成長() {
        assertEquals(1 * 86_400L, BackfillPolicy.backoffSeconds(1))
        assertEquals(2 * 86_400L, BackfillPolicy.backoffSeconds(2))
        assertEquals(4 * 86_400L, BackfillPolicy.backoffSeconds(3))
        assertEquals(8 * 86_400L, BackfillPolicy.backoffSeconds(4))
        assertEquals(16 * 86_400L, BackfillPolicy.backoffSeconds(5))
    }

    @Test
    fun 下次重試時間是現在加上退避秒數() {
        assertEquals(1_000L + 86_400L, BackfillPolicy.nextTryAt(nowSec = 1_000L, attempts = 1))
    }

    @Test
    fun 超過門檻才放棄() {
        assertFalse(BackfillPolicy.shouldGiveUp(attempts = BackfillPolicy.MAX_ATTEMPTS))
        assertTrue(BackfillPolicy.shouldGiveUp(attempts = BackfillPolicy.MAX_ATTEMPTS + 1))
    }
}
