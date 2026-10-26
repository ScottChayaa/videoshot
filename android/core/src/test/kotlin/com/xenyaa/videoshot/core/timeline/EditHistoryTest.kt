package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test fun 刪光再一路復原與重做() {
        val start = Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 3000)))
        var h = EditHistory(start).apply(start.delete(0))
        h = h.apply(h.current.delete(0))
        assertTrue(h.current.segments.isEmpty())
        h = h.undo()!!.undo()!!
        assertEquals(start, h.current)
        assertNull(h.undo())
        h = h.redo()!!.redo()!!
        assertTrue(h.current.segments.isEmpty())
        assertNull(h.redo())
    }

    @Test fun 新操作清空重做() {
        val start = Timeline(listOf(Segment(1, 0, 3000)))
        val h = EditHistory(start).apply(start.splitAt(1000)!!).undo()!!.apply(start.delete(0))
        assertFalse(h.canRedo)
    }

    @Test fun 復原紀錄最多五十步() {
        var h = EditHistory(Timeline(listOf(Segment(1, 0, 600_000))))
        repeat(60) { i -> h = h.apply(h.current.splitAt((i + 1) * 1000L)!!) }
        assertEquals(50, h.undo.size)
    }

    @Test fun 超過上限丟最舊的() {
        val first = Timeline(listOf(Segment(1, 0, 600_000)))
        var h = EditHistory(first)
        repeat(60) { i -> h = h.apply(h.current.splitAt((i + 1) * 1000L)!!) }
        assertFalse(h.undo.contains(first))
    }

    @Test fun canUndo與canRedo() {
        val start = Timeline(listOf(Segment(1, 0, 3000)))
        val h = EditHistory(start)
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        val h2 = h.apply(start.delete(0))
        assertTrue(h2.canUndo)
        assertFalse(h2.canRedo)
        assertTrue(h2.undo()!!.canRedo)
    }
}
