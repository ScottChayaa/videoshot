package com.xenyaa.videoshot.ui.clip

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
 * 素材完整放進 [outWidth]×[outHeight] 時的像素大小（寬, 高），四捨五入、至少 1（極端長寬比會四捨五入成 0，Presentation 不接受）。純計算，不依賴 Media3。
 * [sharpSize] 由它算出清晰原圖的大小。
 */
fun fitSize(scale: FitScale, outWidth: Int, outHeight: Int): Pair<Int, Int> =
    Pair((outWidth * scale.x).roundToInt().coerceAtLeast(1), (outHeight * scale.y).roundToInt().coerceAtLeast(1))

/** 清晰原圖至少佔輸出框（短的那一邊方向）的比例：太扁的橫圖放大到這麼高，左右裁掉（scott 2026-10-27）。 */
private const val MIN_SHARP_FRACTION = 1f / 3

/** 清晰原圖的大小：完整放進輸出框，但至少佔 [MIN_SHARP_FRACTION]，不足就放大、超出輸出框的部分裁掉。純計算。 */
fun sharpSize(scale: FitScale, outWidth: Int, outHeight: Int): Pair<Int, Int> =
    fitSize(FitScale(maxOf(scale.x, MIN_SHARP_FRACTION), maxOf(scale.y, MIN_SHARP_FRACTION)), outWidth, outHeight)

/** 時間軸上用得到的段落，連同各自的素材；引用到 [ClipDraft.sources] 裡不存在的素材的段落略過。純邏輯。 */
fun usableSegments(draft: ClipDraft): List<Pair<Segment, ClipSource>> {
    val sources = draft.sources.associateBy { it.id }
    return draft.timeline.segments.mapNotNull { seg -> sources[seg.sourceId]?.let { seg to it } }
}

/**
 * 把目前的時間軸轉成 Media3 的 [Composition]：一條音視訊序列，每段一個 [EditedMediaItem]。
 *
 * - 影片段用 clipping 取 `startMs..endMs`；照片段用 `setImageDurationMs` 撐出長度、30 fps。
 * - 每段的視訊效果：素材放進輸出框有留白時（[needsBlurBackground]）用 [BlurBackgroundEffect]
 *   畫模糊背景＋置中的清晰原圖（[sharpSize]）；否則等比縮放填滿。
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
        val (sharpW, sharpH) = sharpSize(scale, outWidth, outHeight)
        listOf(
            Presentation.createForWidthAndHeight(sharpW, sharpH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            BlurBackgroundEffect(outWidth, outHeight),
        )
    } else {
        listOf(Presentation.createForWidthAndHeight(outWidth, outHeight, Presentation.LAYOUT_SCALE_TO_FIT))
    }
    return builder.setEffects(Effects(emptyList(), videoEffects)).build()
}
