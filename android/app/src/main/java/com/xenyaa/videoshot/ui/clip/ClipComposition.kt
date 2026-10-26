package com.xenyaa.videoshot.ui.clip

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import kotlin.math.roundToInt

/** 素材完整放進輸出框時，佔輸出框寬（[x]）與高（[y]）的比例；方向相同時其中一邊是 1。 */
data class FitScale(val x: Float, val y: Float)

/** 模糊背景的 sigma（像素）。 */
private const val BLUR_SIGMA = 30f

/** 照片段輸出的幀率。 */
private const val PHOTO_FRAME_RATE = 30

/**
 * 素材（[rotation] 為 90 或 270 時寬高對調）完整放進 [outWidth]×[outHeight] 時，
 * 佔輸出框寬與高的比例。尺寸不明（任一個 ≤ 0）當成剛好填滿。純計算，不依賴 Media3。
 */
fun fitScale(srcWidth: Int, srcHeight: Int, rotation: Int, outWidth: Int, outHeight: Int): FitScale {
    val turned = ((rotation % 360) + 360) % 360
    val w = if (turned == 90 || turned == 270) srcHeight else srcWidth
    val h = if (turned == 90 || turned == 270) srcWidth else srcHeight
    if (w <= 0 || h <= 0 || outWidth <= 0 || outHeight <= 0) return FitScale(1f, 1f)
    val scale = minOf(outWidth.toFloat() / w, outHeight.toFloat() / h)
    return FitScale(w * scale / outWidth, h * scale / outHeight)
}

/** 任一邊比輸出框小超過 2% 才加模糊背景；差不到 2% 不加，省一層運算。 */
fun needsBlurBackground(scale: FitScale): Boolean = scale.x < 0.98f || scale.y < 0.98f

/**
 * 素材完整放進 [outWidth]×[outHeight] 時的像素大小（寬, 高），四捨五入。純計算，不依賴 Media3。
 * 模糊背景的第一步先把素材縮到這個大小，清晰原圖才不會變形。
 */
fun fitSize(scale: FitScale, outWidth: Int, outHeight: Int): Pair<Int, Int> =
    Pair((outWidth * scale.x).roundToInt(), (outHeight * scale.y).roundToInt())

/** 時間軸上用得到的段落，連同各自的素材；引用到 [ClipDraft.sources] 裡不存在的素材的段落略過。純邏輯。 */
fun usableSegments(draft: ClipDraft): List<Pair<Segment, ClipSource>> {
    val sources = draft.sources.associateBy { it.id }
    return draft.timeline.segments.mapNotNull { seg -> sources[seg.sourceId]?.let { seg to it } }
}

/**
 * 把目前的時間軸轉成 Media3 的 [Composition]：一條音視訊序列，每段一個 [EditedMediaItem]。
 *
 * - 影片段用 clipping 取 `startMs..endMs`；照片段用 `setImageDurationMs` 撐出長度、30 fps。
 * - 每段的視訊效果：素材放進輸出框有留白時（[needsBlurBackground]），依序
 *   ① 等比縮到完整大小（[fitSize]）；② `GaussianBlurWithFrameOverlaid`——Media3 1.10.1 的做法是
 *   輸出框 ＝ 輸入框 ÷ scaleSharp、清晰原圖以輸入框的原大小疊在模糊背景正中央；
 *   ③ 拉伸到輸出尺寸（只吸收四捨五入造成的 1 px 誤差）。否則等比縮放填滿。
 * - 序列用 [EditedMediaItemSequence.withAudioAndVideoFrom]：沒有聲音的照片段會自動補靜音，
 *   跟有聲音的影片段接得上。
 *
 * 沒有任何可用段落（時間軸是空的，或段落的素材都不存在）時回 null——
 * Media3 的序列不接受空清單，會丟 `IllegalArgumentException`。
 */
@OptIn(UnstableApi::class)
fun clipComposition(draft: ClipDraft): Composition? {
    val out = draft.orientation
    val items = usableSegments(draft).map { (seg, source) -> editedItem(source, seg, out.width, out.height) }
    if (items.isEmpty()) return null
    return Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(items)).build()
}

@OptIn(UnstableApi::class)
private fun editedItem(source: ClipSource, seg: Segment, outWidth: Int, outHeight: Int): EditedMediaItem {
    val uri = source.uri.toUri()
    val builder = when (source.kind) {
        SourceKind.VIDEO -> {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startMs)
                .setEndPositionMs(seg.endMs)
                .build()
            EditedMediaItem.Builder(
                MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build(),
            ).also { b -> source.durationMs?.takeIf { it > 0 }?.let { b.setDurationUs(it * 1000) } }
        }
        SourceKind.PHOTO -> EditedMediaItem.Builder(
            MediaItem.fromUri(uri).buildUpon().setImageDurationMs(seg.lengthMs).build(),
        ).setDurationUs(seg.lengthMs * 1000).setFrameRate(PHOTO_FRAME_RATE)
    }
    val scale = fitScale(source.width, source.height, source.rotation, outWidth, outHeight)
    val videoEffects = if (needsBlurBackground(scale)) {
        val (fitW, fitH) = fitSize(scale, outWidth, outHeight)
        listOf(
            Presentation.createForWidthAndHeight(fitW, fitH, Presentation.LAYOUT_SCALE_TO_FIT),
            GaussianBlurWithFrameOverlaid(BLUR_SIGMA, fitW / outWidth.toFloat(), fitH / outHeight.toFloat()),
            Presentation.createForWidthAndHeight(outWidth, outHeight, Presentation.LAYOUT_STRETCH_TO_FIT),
        )
    } else {
        listOf(Presentation.createForWidthAndHeight(outWidth, outHeight, Presentation.LAYOUT_SCALE_TO_FIT))
    }
    return builder.setEffects(Effects(emptyList(), videoEffects)).build()
}
