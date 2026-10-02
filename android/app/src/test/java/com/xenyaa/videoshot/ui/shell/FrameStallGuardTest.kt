package com.xenyaa.videoshot.ui.shell

import android.view.Choreographer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-10-01／02 實機（2107113SG，MIUI 13）「開 App 白畫面 90 秒以上」的回歸測試之一：
 * 視窗取得焦點時一定要補要一幀，失去焦點時不要（背景不該白耗電）。
 * 為什麼要補這一幀見 [FrameStallGuard] 的 KDoc。
 */
class FrameStallGuardTest {

    private val posted = mutableListOf<Choreographer.FrameCallback>()
    private val guard = FrameStallGuard(postFrame = { posted += it })

    @Test
    fun 取得焦點時補要一幀() {
        guard.onWindowFocusChanged(hasFocus = true)
        assertEquals(1, posted.size)
    }

    @Test
    fun 失去焦點時不要求幀() {
        guard.onWindowFocusChanged(hasFocus = false)
        assertEquals(0, posted.size)
    }

    @Test
    fun 每次重新取得焦點都會再補一次() {
        // 回到前景時 surface 會重建，同樣的掉幀可能再發生一次
        guard.onWindowFocusChanged(hasFocus = true)
        guard.onWindowFocusChanged(hasFocus = false)
        guard.onWindowFocusChanged(hasFocus = true)
        assertEquals(2, posted.size)
    }
}
