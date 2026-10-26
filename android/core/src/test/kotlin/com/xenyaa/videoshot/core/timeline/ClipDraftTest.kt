package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipDraftTest {
    private fun photo(id: Long, t: Long) = ClipSource(id, "content://p/$id", SourceKind.PHOTO, t, null, 4000, 3000, 0)
    private fun video(id: Long, t: Long, d: Long) = ClipSource(id, "content://v/$id", SourceKind.VIDEO, t, d, 1920, 1080, 0)

    @Test fun 編碼再解碼一樣() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1), video(2, 2, 5000)))
        val d2 = d.copy(history = d.history.apply(d.timeline.splitAt(4000)!!))
        assertEquals(d2, ClipDraftCodec.decode(ClipDraftCodec.encode(d2)))
    }

    @Test fun 壞掉的JSON回null() {
        assertNull(ClipDraftCodec.decode("{oops"))
        assertNull(ClipDraftCodec.decode("""{"v":99}"""))
    }

    @Test fun 編碼帶版本欄位() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1)))
        assertTrue(ClipDraftCodec.encode(d).contains("\"v\":1"))
    }

    @Test fun 缺少內容或空字串回null() {
        assertNull(ClipDraftCodec.decode(""))
        assertNull(ClipDraftCodec.decode("""{"v":1}"""))
    }

    @Test fun 初始草稿只留時間軸用得到的素材() {
        val d = newClipDraft(ClipOrientation.LANDSCAPE, listOf(photo(1, 1), video(2, 2, 0)))
        assertEquals(listOf(1L), d.sources.map { it.id })
        assertEquals(listOf(1L), d.timeline.segments.map { it.sourceId })
        assertFalse(d.history.canUndo)
    }

    @Test fun 素材不見了拿掉段落並清空紀錄() {
        val d = newClipDraft(ClipOrientation.LANDSCAPE, listOf(photo(1, 1), video(2, 2, 5000), photo(3, 3)))
            .let { it.copy(history = it.history.apply(it.timeline.delete(2))) }
        val p = d.pruneMissing(setOf(1L, 3L))
        assertEquals(1, p.removedSources)
        assertEquals(listOf(1L), p.draft.timeline.segments.map { it.sourceId })
        assertFalse(p.draft.history.canUndo)
        assertEquals(listOf(1L, 3L), p.draft.sources.map { it.id })
    }

    @Test fun 素材不見了連重做紀錄也清空() {
        val d0 = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1), photo(2, 2)))
        val d = d0.copy(history = d0.history.apply(d0.timeline.delete(0)).undo()!!)
        assertTrue(d.history.canRedo)
        val p = d.pruneMissing(setOf(1L))
        assertFalse(p.draft.history.canRedo)
        assertFalse(p.draft.history.canUndo)
    }

    @Test fun 素材都在就原樣() {
        val d = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1)))
        assertEquals(Pruned(d, 0), d.pruneMissing(setOf(1L, 99L)))
    }

    @Test fun 素材都在時保留復原紀錄() {
        val d0 = newClipDraft(ClipOrientation.PORTRAIT, listOf(photo(1, 1), photo(2, 2)))
        val d = d0.copy(history = d0.history.apply(d0.timeline.delete(0)))
        val p = d.pruneMissing(setOf(1L, 2L))
        assertTrue(p.draft.history.canUndo)
    }
}
