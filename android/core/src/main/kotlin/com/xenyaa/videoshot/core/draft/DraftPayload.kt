package com.xenyaa.videoshot.core.draft

import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.normalizeTags
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 取圖精靈草稿的內容（規格第五節「草稿：中途離開」）。
 *
 * **只記最近一支影片**，存在 `cache.db` 的 `draft` 列（永遠只有一列）＋ `drafts/{videoId}/` 目錄。
 * 草稿**不進備份** —— 它能重建（重貼一次網址就好），而規格第四節的原則是
 * 「無法重建的在 `library.db`，DB 外面的都能重建」。
 *
 * **不存 dHash 指紋。** 規格原本列了它，用意是續做時不必重算；改成把第二步的 sheet
 * 留在 `drafts/{videoId}/sheets/L{level}/`、續做時本機有就不重新下載之後，重算指紋是純 CPU，
 * 比維護一份會與 sheet 不同步的快取可靠 —— 兩者不一致時（換了層級、sheet 被清掉一半）
 * 指紋是錯的而且看不出來。
 *
 * @param frameCount 第二步的 storyboard 格數 —— **手動格號的分界**，還原時少了它就分不出哪些是手動圖
 * @param details 鍵是格號的字串形式（JSON 的 map key 只能是字串）
 */
@Serializable
data class DraftPayload(
    val videoId: String,
    /** 1 | 2 | 3 */
    val step: Int,
    val level: Int,
    val frameCount: Int,
    val selected: List<Int> = emptyList(),
    val manual: List<DraftManual> = emptyList(),
    val details: Map<String, DraftDetails> = emptyMap(),
)

/**
 * 一張手動補圖。
 *
 * **存檔名不存路徑**（規格第四節「跨平台的資料契約」第 1 條）——
 * 目錄由 `drafts/{videoId}/manual/` 推導。存絕對路徑的話，app 換了資料目錄就全部失效。
 */
@Serializable
data class DraftManual(
    val cell: Int,
    val atSec: Double,
    val fileName: String,
    val fromGallery: Boolean,
)

@Serializable
data class DraftDetails(
    val eventDate: String,
    val place: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val applied: Boolean = false,
)

fun DraftDetails.toShotDetails(): ShotDetails =
    ShotDetails(eventDate, place, description, normalizeTags(tags), applied)

fun ShotDetails.toDraftDetails(): DraftDetails =
    DraftDetails(eventDate, place, description, tags, applied)

object DraftCodec {

    /**
     * `ignoreUnknownKeys` 是刻意的：換版之後舊 app 讀到新 app 寫的草稿，
     * 不該因為多一個欄位就整份報廢 —— 草稿壞掉的代價是使用者重挑一次 18 張圖。
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: DraftPayload): String = json.encodeToString(payload)

    /**
     * @return 解不出來回 null。**不丟例外** —— 草稿是可以重建的東西，
     *         壞掉只該讓「要繼續嗎」這個問句不出現，不該讓 app 開不起來
     */
    fun decode(text: String): DraftPayload? = try {
        json.decodeFromString<DraftPayload>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
