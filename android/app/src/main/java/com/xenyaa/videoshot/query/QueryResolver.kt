package com.xenyaa.videoshot.query

import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.query.RuleBasedParser
import kotlinx.coroutines.withTimeoutOrNull

sealed interface QuerySource {
    data object Rule : QuerySource
    data object Gemini : QuerySource
}

data class ResolvedQuery(val parsed: ParsedQuery, val source: QuerySource)

/**
 * 文字查詢解析的協調者（規格第八節）：有金鑰且在線就試 Gemini（5 秒逾時），
 * 沒有金鑰、逾時、或 Gemini 回 null 一律退回規則式——這條路徑永遠有結果，不會讓查詢卡住。
 *
 * @param geminiKey 讀目前的 Gemini 金鑰；`AppContainer` 接 `AppSettings.geminiKey.first()`
 * @param vocabulary 每次查詢都重新取得——標籤與地點會隨著使用者持續入庫而變多，
 *        不快取的話新加的標籤要等 app 重啟才解析得到，那種「有時候解得出、有時候解不出」
 *        的落差比每次查詢多讀一次資料庫（本機幾萬筆等級）的成本更值得付
 */
class QueryResolver(
    private val gemini: GeminiClient,
    private val geminiKey: suspend () -> String?,
    private val vocabulary: suspend () -> QueryVocabulary,
    private val timeoutMs: Long = 5_000,
) {
    suspend fun resolve(text: String): ResolvedQuery {
        val key = geminiKey()
        if (key != null) {
            val viaGemini = withTimeoutOrNull(timeoutMs) { gemini.parse(text, key) }
            if (viaGemini != null) return ResolvedQuery(viaGemini, QuerySource.Gemini)
        }
        return ResolvedQuery(RuleBasedParser.parse(text, vocabulary()), QuerySource.Rule)
    }
}
