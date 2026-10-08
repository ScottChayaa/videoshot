package com.xenyaa.videoshot.core.query

/**
 * 查詢解析的統一輸出（規格第八節）。規則式（[RuleBasedParser]）與 Gemini 兩條路徑都吐這個形狀，
 * 下游的檢索 SQL（`:app` 的 `LibraryRepo.searchByQuery`）不必知道是哪一條路徑解出來的。
 *
 * @param dateFrom／dateTo ISO 8601 日期字串（`YYYY-MM-DD`）；規則式恆為 null——
 *        它聽不懂「去年夏天」這類自然語言日期，那是 Gemini 的增值（規格第八節）
 * @param places 地點候選（通常 0～1 個：一張圖只有一個 `place` 欄位，但列表讓上游可以一次給多個候選）
 * @param tags 標籤候選（標籤名，不是 id——名稱轉 id 是 repo 的事）
 * @param keywords 其餘的自由文字關鍵字；規則式通常只有 0～1 個（整句去掉地點／標籤命中之後的剩餘字串）
 */
data class ParsedQuery(
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val places: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

/** 規則式解析要用的詞彙表：目前圖庫裡所有的地點與標籤（含別名）。 */
data class QueryVocabulary(
    val places: List<String>,
    val tags: List<TagAlias>,
    /** 地點本名 → 別名（`place.aliases`，檢索時視同本名，規格第四節）。 */
    val placeAliases: Map<String, List<String>> = emptyMap(),
)

/** 一個標籤的可比對詞：正式名稱＋別名（`tag.aliases`，檢索時視同 name，規格第四節）。 */
data class TagAlias(val name: String, val aliases: List<String> = emptyList())
