package com.xenyaa.videoshot.core.timeline

import kotlinx.serialization.Serializable

/** 照片在時間軸上預設佔的長度（毫秒）。 */
const val PHOTO_SEGMENT_MS = 3000L

/** 分割後每一段至少要有的長度（毫秒）；離段落邊界不到這個距離就不能分割。 */
const val MIN_SEGMENT_MS = 100L

/** 素材種類。 */
@Serializable
enum class SourceKind { PHOTO, VIDEO }

/**
 * 剪輯用的素材：跟 app 的 `MediaItem` 同欄位，`:core` 不能依賴 app，所以另立一份。
 *
 * `durationMs` 只有影片有值；`rotation` 是度數。
 */
@Serializable
data class ClipSource(
    val id: Long,
    val uri: String,
    val kind: SourceKind,
    val takenAtMs: Long,
    val durationMs: Long?,
    val width: Int,
    val height: Int,
    val rotation: Int,
)

/**
 * 時間軸上的一段：取自素材 [sourceId] 的 [startMs] 到 [endMs]（毫秒）。
 * 照片段 `startMs = 0`、`endMs = 3000`；分割後兩段各取一部分。
 */
@Serializable
data class Segment(val sourceId: Long, val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * 剪輯的時間軸：依序排列的片段。所有操作都回傳新的時間軸，不改原本的。
 * 純邏輯，時間一律毫秒 `Long`。
 */
@Serializable
data class Timeline(val segments: List<Segment>) {
    /** 全部片段加起來的長度。 */
    val durationMs: Long get() = segments.sumOf { it.lengthMs }

    /**
     * 時間軸上 [positionMs] 落在哪一段，回傳（段落索引, 段內偏移）。
     * 剛好在兩段交界屬於**後一段**（偏移 0）；超出範圍（含 `positionMs == durationMs`）回 null。
     */
    fun locate(positionMs: Long): Pair<Int, Long>? {
        if (positionMs < 0) return null
        var start = 0L
        segments.forEachIndexed { i, s ->
            if (positionMs < start + s.lengthMs) return i to (positionMs - start)
            start += s.lengthMs
        }
        return null
    }

    /** 第 [index] 段在時間軸上的起點。 */
    fun startOf(index: Int): Long = segments.take(index).sumOf { it.lengthMs }

    /** [positionMs] 在某段之內，且離該段的頭尾都至少 [MIN_SEGMENT_MS]，才能分割。 */
    fun canSplitAt(positionMs: Long): Boolean {
        val (i, offset) = locate(positionMs) ?: return false
        return offset >= MIN_SEGMENT_MS && segments[i].lengthMs - offset >= MIN_SEGMENT_MS
    }

    /** 在 [positionMs] 把所在的段切成兩段；不能分割時回 null。 */
    fun splitAt(positionMs: Long): Timeline? {
        if (!canSplitAt(positionMs)) return null
        val (i, offset) = locate(positionMs)!!
        val s = segments[i]
        val cut = s.startMs + offset
        return Timeline(
            segments.take(i) + Segment(s.sourceId, s.startMs, cut) + Segment(s.sourceId, cut, s.endMs) + segments.drop(i + 1),
        )
    }

    /** 刪掉第 [index] 段。 */
    fun delete(index: Int): Timeline = Timeline(segments.filterIndexed { i, _ -> i != index })
}

/**
 * 由素材建出初始時間軸：依拍攝時間由舊到新（同時間依 id 由小到大），
 * 照片佔 [PHOTO_SEGMENT_MS]、影片佔 `0..durationMs`；長度為 null 或不大於 0 的影片略過。
 */
fun initialTimeline(sources: List<ClipSource>): Timeline = Timeline(
    sources
        .sortedWith(compareBy<ClipSource> { it.takenAtMs }.thenBy { it.id })
        .mapNotNull { s ->
            when (s.kind) {
                SourceKind.PHOTO -> Segment(s.id, 0, PHOTO_SEGMENT_MS)
                SourceKind.VIDEO -> s.durationMs?.takeIf { it > 0 }?.let { Segment(s.id, 0, it) }
            }
        },
)
