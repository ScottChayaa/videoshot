package com.xenyaa.videoshot.data.repo.model

import com.xenyaa.videoshot.core.paging.ShotCursor

/**
 * 畫面看到的一張圖。**刻意不含 webp** —— 首頁一次撈 50 筆，
 * 帶上 6 KB 的 BLOB 會把清單拖垮；圖一律經 thumbs 模組的 thumbFor(shot) 取得。
 */
data class ShotRow(
    val id: Long,
    val videoId: String,
    val atSec: Double,
    val source: String,
    val frameIndex: Int?,
    val sbLevel: Int?,
    val eventDate: String,
    val place: String?,
    val description: String?,
)

data class MonthCount(val month: String, val count: Int)

data class Page<T>(val items: List<T>, val next: ShotCursor?)

/** 取圖精靈第三步完成時要入庫的一張圖。webp 只有 source='manual' 才有值。 */
data class NewShot(
    val atSec: Double,
    val source: String,
    val frameIndex: Int?,
    val sbLevel: Int?,
    val eventDate: String,
    val place: String?,
    val description: String?,
    val webp: ByteArray?,
) {
    override fun equals(other: Any?): Boolean = this === other || (
        other is NewShot && atSec == other.atSec && source == other.source &&
            frameIndex == other.frameIndex && sbLevel == other.sbLevel && eventDate == other.eventDate &&
            place == other.place && description == other.description &&
            (if (webp == null) other.webp == null else webp.contentEquals(other.webp))
        )

    override fun hashCode(): Int =
        listOf(atSec, source, frameIndex, sbLevel, eventDate, place, description).hashCode() * 31 +
            (webp?.contentHashCode() ?: 0)
}

/**
 * 批次編輯的差異。**null 代表「這個欄位沒動過，不要覆蓋」** ——
 * 這是規格第五節第三步「只套用動過的欄位」的實作依據，不可以用空字串代替 null。
 * tagIds 非 null 時是**整組覆蓋**（先解除全部關聯再重建）。
 */
data class ShotPatch(
    val eventDate: String?,
    val place: String?,
    val description: String?,
    val tagIds: List<Long>?,
)
