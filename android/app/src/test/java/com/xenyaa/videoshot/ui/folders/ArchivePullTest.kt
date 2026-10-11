package com.xenyaa.videoshot.ui.folders

import org.junit.Assert.assertEquals
import org.junit.Test

/** 下拉進入封存相簿的光暈與拉伸數值（scott 2026-10-11 實機調過：拉伸門檻 9%、最多 12%，光暈最深 20%）。 */
class ArchivePullTest {

    @Test
    fun 沒拉時不拉伸也沒有光暈() {
        assertEquals(1f, archiveStretchScale(0f), 1e-6f)
        assertEquals(0f, archiveGlowAlpha(0f), 1e-6f)
    }

    @Test
    fun 門檻時拉長9趴_光暈20趴() {
        assertEquals(1.09f, archiveStretchScale(1f), 1e-6f)
        assertEquals(0.20f, archiveGlowAlpha(1f), 1e-6f)
    }

    @Test
    fun 超過門檻最多拉長12趴_光暈不再變深() {
        assertEquals(1.12f, archiveStretchScale(1.5f), 1e-6f)
        assertEquals(1.12f, archiveStretchScale(3f), 1e-6f)
        assertEquals(0.20f, archiveGlowAlpha(3f), 1e-6f)
    }

    @Test
    fun 拉一半時線性() {
        assertEquals(1.045f, archiveStretchScale(0.5f), 1e-6f)
        assertEquals(0.10f, archiveGlowAlpha(0.5f), 1e-6f)
    }
}
