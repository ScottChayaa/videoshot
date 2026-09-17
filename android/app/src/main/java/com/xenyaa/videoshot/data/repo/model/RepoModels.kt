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
    /**
     * 標籤名。**repo 負責在同一個交易裡解析成 tag id，不存在就建**
     * —— 在交易外先建好的話，整批回滾之後會留下沒有任何圖引用的空標籤。
     */
    val tagNames: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean = this === other || (
        other is NewShot && atSec == other.atSec && source == other.source &&
            frameIndex == other.frameIndex && sbLevel == other.sbLevel && eventDate == other.eventDate &&
            place == other.place && description == other.description && tagNames == other.tagNames &&
            (if (webp == null) other.webp == null else webp.contentEquals(other.webp))
        )

    override fun hashCode(): Int =
        listOf(atSec, source, frameIndex, sbLevel, eventDate, place, description, tagNames).hashCode() * 31 +
            (webp?.contentHashCode() ?: 0)
}

/**
 * 批次編輯的差異。**null 代表「這個欄位沒動過，不要覆蓋」** ——
 * 這是規格第五節第三步「只套用動過的欄位」的實作依據，不可以用空字串代替 null。
 * **空字串代表清空**（寫入時存成 null），與 `:core` 的 `DetailsPatch` 同一個約定。
 * tagIds／tagNames 非 null 時是**整組覆蓋**（先解除全部關聯再重建）。
 */
data class ShotPatch(
    val eventDate: String?,
    val place: String?,
    val description: String?,
    val tagIds: List<Long>?,
    /**
     * 標籤以**名稱**給定時用這個 —— 就地編輯的畫面只有名稱。
     * repo 在同一個交易內解析成 id，查不到就新建（`kind = 'other'`），
     * 與 `commitPicks` 同樣的理由：在交易外先建好的話，回滾後會留下沒有任何圖引用的空標籤。
     * 與 [tagIds] **互斥**，同時給會丟 IllegalArgumentException。
     */
    val tagNames: List<String>? = null,
)

/** 首頁月份標籤列的一個項目。kind 是 `'place'` 或 `'tag'`（規格第四節：tag.kind 不再有 place）。 */
data class MonthFacet(val name: String, val kind: String, val count: Int)

/** 「最近取過的影片」清單的一列。資料直接查 video 表，不另存一份歷史（規格第五節第一步）。 */
data class RecentVideo(
    val videoId: String,
    val title: String,
    val addedAt: Long,
    val shotCount: Int,
)
