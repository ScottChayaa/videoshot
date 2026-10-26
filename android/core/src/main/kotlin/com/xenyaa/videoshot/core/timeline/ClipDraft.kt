package com.xenyaa.videoshot.core.timeline

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 進行中的剪輯草稿：輸出方向、用到的素材、時間軸與復原紀錄。
 * 整份可序列化成 JSON（見 [ClipDraftCodec]），存在 `cache.db` 的 `edit_draft`。
 */
@Serializable
data class ClipDraft(
    val orientation: ClipOrientation,
    val sources: List<ClipSource>,
    val history: EditHistory,
) {
    /** 目前的時間軸。 */
    val timeline: Timeline get() = history.current
}

/** 由素材建出新草稿；`sources` 只留初始時間軸用得到的（長度不明的影片略過）。 */
fun newClipDraft(orientation: ClipOrientation, sources: List<ClipSource>): ClipDraft {
    val timeline = initialTimeline(sources)
    val used = timeline.segments.map { it.sourceId }.toSet()
    return ClipDraft(orientation, sources.filter { it.id in used }, EditHistory(timeline))
}

@Serializable
private data class DraftEnvelope(val v: Int, val draft: ClipDraft? = null)

/** [ClipDraft] 與 JSON 互轉；JSON 帶版本欄位 `v`，解不開或版本不認識回 null、不丟例外。 */
object ClipDraftCodec {
    private const val VERSION = 1
    private val format = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(d: ClipDraft): String = format.encodeToString(DraftEnvelope.serializer(), DraftEnvelope(VERSION, d))

    fun decode(json: String): ClipDraft? = try {
        val e = format.decodeFromString(DraftEnvelope.serializer(), json)
        if (e.v == VERSION) e.draft else null
    } catch (_: Exception) {
        null
    }
}

/** [pruneMissing] 的結果：修剪後的草稿，與被拿掉的素材個數。 */
data class Pruned(val draft: ClipDraft, val removedSources: Int)

/**
 * 續做時，拿掉 [existingIds] 裡沒有的素材（手機裡已刪除的照片或影片），
 * 連同現在時間軸上它們的段落。有拿掉任何東西時復原與重做紀錄一起清空
 * （舊紀錄裡還引用著不存在的素材）；沒拿掉就原樣回傳。
 */
fun ClipDraft.pruneMissing(existingIds: Set<Long>): Pruned {
    val kept = sources.filter { it.id in existingIds }
    val removed = sources.size - kept.size
    if (removed == 0) return Pruned(this, 0)
    val pruned = Timeline(timeline.segments.filter { it.sourceId in existingIds })
    return Pruned(copy(sources = kept, history = EditHistory(pruned)), removed)
}
