package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipCompositionTest {
    @Test fun 橫拍照片放進直式只佔中間一條() {
        val s = fitScale(4000, 3000, 0, 1080, 1920)
        assertEquals(1f, s.x, 0.001f)
        assertEquals(0.421875f, s.y, 0.001f) // 1080×810 / 1080×1920
        assertTrue(needsBlurBackground(s))
    }

    @Test fun 直拍影片放進直式剛好填滿() {
        val s = fitScale(1080, 1920, 0, 1080, 1920)
        assertEquals(FitScale(1f, 1f), s)
        assertFalse(needsBlurBackground(s))
    }

    @Test fun 旋轉90度的影片寬高對調() = assertEquals(FitScale(1f, 1f), fitScale(1920, 1080, 90, 1080, 1920))

    @Test fun 旋轉270度的影片寬高對調() = assertEquals(FitScale(1f, 1f), fitScale(1920, 1080, 270, 1080, 1920))

    @Test fun 直拍放進橫式左右留模糊() {
        val s = fitScale(1080, 1920, 0, 1920, 1080)
        assertEquals(0.31640625f, s.x, 0.001f)
        assertEquals(1f, s.y, 0.001f)
    }

    @Test fun 差不到百分之二不加模糊() {
        assertFalse(needsBlurBackground(FitScale(1f, 0.985f)))
        assertTrue(needsBlurBackground(FitScale(1f, 0.97f)))
    }

    @Test fun 橫拍照片縮到直式的完整大小() =
        assertEquals(1080 to 810, fitSize(fitScale(4000, 3000, 0, 1080, 1920), 1080, 1920))

    @Test fun 直拍影片縮到橫式的完整大小() =
        assertEquals(608 to 1080, fitSize(fitScale(1080, 1920, 0, 1920, 1080), 1920, 1080)) // 607.5 四捨五入

    @Test fun 方向相同時完整大小就是輸出尺寸() =
        assertEquals(1080 to 1920, fitSize(fitScale(1080, 1920, 0, 1080, 1920), 1080, 1920))

    private fun photo(id: Long) = ClipSource(id, "content://p/$id", SourceKind.PHOTO, id, null, 4000, 3000, 0)

    private fun draft(sources: List<ClipSource>, segments: List<Segment>) =
        ClipDraft(ClipOrientation.PORTRAIT, sources, EditHistory(Timeline(segments)))

    @Test fun 空時間軸沒有可用段落() = assertEquals(emptyList<Any>(), usableSegments(draft(listOf(photo(1)), emptyList())))

    @Test fun 素材都不存在時沒有可用段落() =
        assertEquals(emptyList<Any>(), usableSegments(draft(emptyList(), listOf(Segment(1, 0, 3000)))))

    @Test fun 引用不存在素材的段落被略過() {
        val keep = Segment(1, 0, 3000)
        val d = draft(listOf(photo(1)), listOf(keep, Segment(2, 0, 3000)))
        assertEquals(listOf(keep to photo(1)), usableSegments(d))
    }
}
