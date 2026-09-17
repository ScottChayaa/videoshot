package com.xenyaa.videoshot.core.details

/**
 * 一張圖在取圖精靈第三步的圖資（規格第五節「第三步：定義圖資」的欄位表）。
 *
 * @param eventDate 首頁分組的依據，預設帶入 YouTube 的上傳日期（`YYYY-MM-DD`）
 * @param tags **一律經 [normalizeTags]**：去重、去空白、排序。順序不該讓兩張本來相同的圖看起來不同
 * @param applied 套用過（畫面上的綠點）。【未填的】快捷鍵勾的就是這個為 false 的那些
 */
data class ShotDetails(
    val eventDate: String,
    val place: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val applied: Boolean = false,
)

/**
 * 抽屜裡「動過什麼」的差異。
 *
 * **null 代表這個欄位沒動過，不要覆蓋**（規格第五節套用語意表）——
 * 第 1 輪標了「露營」、第 2 輪標了「玩水」，之後全選只想補一個地點時，
 * 純粹的「全部蓋掉」會把兩組標籤一起洗掉。
 *
 * **空字串代表清空**（套用後存成 null）。沒有這條約定的話，「把地點刪掉」與
 * 「沒碰地點」在這個型別裡長得一模一樣。
 */
data class DetailsPatch(
    val eventDate: String? = null,
    val place: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = eventDate == null && place == null && description == null && tags == null

    /**
     * 「將更新：地點、標籤」那一行要列的欄位名。
     *
     * 順序**固定且與抽屜由上到下一致**（時間／地點／標籤／描述）——
     * 依使用者修改的先後排列的話，同一組修改每次讀起來都不一樣。
     */
    val changedLabels: List<String>
        get() = buildList {
            if (eventDate != null) add("時間")
            if (place != null) add("地點")
            if (tags != null) add("標籤")
            if (description != null) add("描述")
        }
}

/** 套用一次抽屜的修改。沒動過的欄位原封不動；空字串的欄位清成 null。 */
fun ShotDetails.apply(patch: DetailsPatch): ShotDetails = copy(
    eventDate = patch.eventDate?.takeIf { it.isNotBlank() } ?: eventDate,
    place = if (patch.place != null) patch.place.ifBlank { null } else place,
    description = if (patch.description != null) patch.description.ifBlank { null } else description,
    tags = if (patch.tags != null) normalizeTags(patch.tags) else tags,
    applied = applied || !patch.isEmpty,
)

/** 只套用到 [cells] 裡的格子，其餘原封不動（規格第五節；案例 8）。 */
fun applyToCells(
    all: Map<Int, ShotDetails>,
    cells: Set<Int>,
    patch: DetailsPatch,
): Map<Int, ShotDetails> =
    all.mapValues { (cell, details) -> if (cell in cells) details.apply(patch) else details }

/**
 * 勾選中那幾張的某個欄位的共同值。
 *
 * **不能用「不一致就回 null」** —— `place` 本身就可以是 null，
 * 那樣「大家都沒填地點」與「地點各不相同」會變成同一件事，
 * 而前者該直接顯示空白、後者該顯示〈多個值〉。
 */
sealed interface Common<out T> {
    /** 沒有勾選任何一張 —— 抽屜收合。 */
    data object None : Common<Nothing>

    data class One<out T>(val value: T) : Common<T>

    /** 值不一致 —— 顯示〈多個值〉佔位字樣，不動它就不會變。 */
    data object Mixed : Common<Nothing>
}

fun <T> commonOf(values: List<T>): Common<T> = when {
    values.isEmpty() -> Common.None
    values.all { it == values.first() } -> Common.One(values.first())
    else -> Common.Mixed
}

/**
 * 標籤的唯一正規化入口：去空白、丟掉空字串、去重、排序。
 *
 * 排序是為了讓「同一組標籤」有唯一的表示法 —— 沒有它，
 * `[玩水, 阿明]` 與 `[阿明, 玩水]` 在抽屜裡會顯示成〈多個值〉。
 */
fun normalizeTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

private val EVENT_DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * `event_date` 唯一合法的格式：`YYYY-MM-DD`（規格第五節欄位表）。
 *
 * 就地編輯的畫面是自由文字輸入，沒有這一關的話，空字串或 `3/5` 這種打字都能存進
 * `library.db`（`shot.event_date` 是 `TEXT NOT NULL`，不會被 `NOT NULL` 擋下來）——
 * 那張圖之後就沒有任何月份篩選找得到它。取圖精靈的 [eventDateOf] 也是同一個判斷，
 * 抽成這裡是唯一的判斷入口，不要各自寫一份正則表達式。
 */
fun isValidEventDate(date: String): Boolean = date.matches(EVENT_DATE_PATTERN)

/**
 * 時間欄位的預設值：YouTube 上傳日期的日期部分（規格第五節欄位表）。
 *
 * @param publishedAt ISO 8601 原字串，如 `2014-11-10T06:05:55-08:00`
 * @param fallback 解不出來時用的值（呼叫端給「今天」）—— 影片播不了時 `meta` 是 null，
 *        欄位總得有個值，空白的日期比不上一個使用者一眼看得出要改的值
 */
fun eventDateOf(publishedAt: String, fallback: String): String {
    val head = publishedAt.take(10)
    return if (isValidEventDate(head)) head else fallback
}
