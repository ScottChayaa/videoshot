package com.xenyaa.videoshot.ui.clip

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 合成的降幀效果：Uri 是 Android 類別，所以跟 [ClipCompositionTest] 的純邏輯測試分開，用 Robolectric。 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipCompositionExportTest {
    private val video = ClipSource(1, "content://v/1", SourceKind.VIDEO, 1, 5000, 1080, 1920, 0)
    private val photo = ClipSource(2, "content://p/2", SourceKind.PHOTO, 2, null, 4000, 3000, 0)
    private val draft = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(video, photo),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000), Segment(2, 0, 3000)))),
    )

    private fun videoEffects(composition: Composition?, index: Int): List<Effect> =
        composition!!.sequences[0].editedMediaItems[index].effects.videoEffects

    @Test fun 輸出時影片段第一個效果是降幀() {
        val effects = videoEffects(clipComposition(draft, 30f), 0)
        assertTrue(effects.first() is FrameDropEffect)
        assertTrue(effects.drop(1).any { it is Presentation })
    }

    @Test fun 預覽不降幀() {
        assertFalse(videoEffects(clipComposition(draft), 0).any { it is FrameDropEffect })
    }

    @Test fun 照片段不加降幀() {
        assertFalse(videoEffects(clipComposition(draft, 30f), 1).any { it is FrameDropEffect })
    }
}
