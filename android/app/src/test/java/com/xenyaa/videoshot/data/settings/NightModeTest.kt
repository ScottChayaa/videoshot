package com.xenyaa.videoshot.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NightModeTest {

    @Test
    fun 跟隨系統時聽系統的() {
        assertEquals(true, NightMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, NightMode.SYSTEM.isDark(systemDark = false))
    }

    /** 手動覆寫就不再看系統 —— 這是「在 app 裡鎖成淺色」的全部意思。 */
    @Test
    fun 手動覆寫不理系統() {
        assertEquals(false, NightMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, NightMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun 認不得的舊值退回跟隨系統() {
        assertEquals(NightMode.SYSTEM, NightMode.byId(null))
        assertEquals(NightMode.SYSTEM, NightMode.byId("這個模式已經不存在了"))
        assertEquals(NightMode.DARK, NightMode.byId("dark"))
    }
}
