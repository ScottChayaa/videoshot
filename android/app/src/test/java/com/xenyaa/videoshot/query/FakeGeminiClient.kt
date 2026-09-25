package com.xenyaa.videoshot.query

import com.xenyaa.videoshot.core.query.ParsedQuery
import kotlinx.coroutines.delay

/**
 * 測試替身。[fixed] 非 null 時 [parse] 立刻回傳它；null 時模擬「Gemini 解不出來」。
 * [delayMs] 給 `QueryResolverTest` 模擬逾時——搭配 `runTest` 的虛擬時間，不會真的等待。
 */
class FakeGeminiClient(private val fixed: ParsedQuery?, private val delayMs: Long = 0) : GeminiClient {
    var lastKey: String? = null
        private set

    override suspend fun parse(text: String, apiKey: String): ParsedQuery? {
        lastKey = apiKey
        if (delayMs > 0) delay(delayMs)
        return fixed
    }
}
