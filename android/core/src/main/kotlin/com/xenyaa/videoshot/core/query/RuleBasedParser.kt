package com.xenyaa.videoshot.core.query

/**
 * 規則式查詢解析（規格第八節）：以既有的地點與標籤（含別名）對輸入句做**最長比對**，
 * 命中者成為 place／tag 條件，其餘字串當關鍵字。它聽不懂「去年夏天」這類自然語言日期
 * —— 那是 Gemini 的增值，不是這個解析器的職責（[ParsedQuery.dateFrom]／[dateTo] 恆為 null）。
 *
 * **最長比對優先**：候選詞（地點 ∪ 標籤名 ∪ 標籤別名）依字串長度由長到短排序後依序嘗試，
 * 避免「小橘」這種短別名的子字串搶在「我家的貓」這種完整別名之前命中。
 */
object RuleBasedParser {

    private data class Candidate(val term: String, val place: String?, val tagName: String?)

    /** 剩餘字串邊緣的贅字——不是內容，是「其餘字串」機械式切出來之後常見的收尾字。 */
    private val TRIM_CHARS = charArrayOf('，', '。', '的', '、', ',', '.', ' ', '\t', '\n')

    fun parse(text: String, vocabulary: QueryVocabulary): ParsedQuery {
        val candidates = buildList {
            for (place in vocabulary.places) {
                if (place.isNotBlank()) add(Candidate(place, place, null))
            }
            for (tag in vocabulary.tags) {
                if (tag.name.isNotBlank()) add(Candidate(tag.name, null, tag.name))
                for (alias in tag.aliases) {
                    if (alias.isNotBlank()) add(Candidate(alias, null, tag.name))
                }
            }
        }.sortedByDescending { it.term.length }

        var remaining = text
        var place: String? = null
        val tagNames = linkedSetOf<String>()
        for (candidate in candidates) {
            if (!remaining.contains(candidate.term)) continue
            if (candidate.place != null) {
                if (place != null) continue // 一張圖只有一個地點欄位，第一個命中的地點候選就夠
                place = candidate.place
            } else {
                tagNames += candidate.tagName!!
            }
            remaining = remaining.replaceFirst(candidate.term, "")
        }

        val keyword = remaining.trim { it in TRIM_CHARS }
        return ParsedQuery(
            places = listOfNotNull(place),
            tags = tagNames.toList(),
            keywords = if (keyword.isBlank()) emptyList() else listOf(keyword),
        )
    }
}
