package com.xenyaa.videoshot.query

import com.xenyaa.videoshot.core.query.ParsedQuery

/**
 * Gemini 查詢解析（規格第八節)。呼叫失敗（網路、逾時、非 2xx、解不出 JSON）一律回 `null`——
 * 呼叫端（[QueryResolver]）看到 `null` 就退回規則式，這裡不丟例外。
 */
interface GeminiClient {
    suspend fun parse(text: String, apiKey: String): ParsedQuery?
}

/**
 * 查詢解析的 prompt（規格第十六節開放項目：「Gemini 查詢解析的 prompt 需實際迭代」，
 * 本階段給出第一版，实際用真的金鑰跑過、確認品質留給階段 11 之後——見本文件 Global Constraints）。
 *
 * 要求模型只回 JSON、欄位對齊 [ParsedQuery] 的 snake_case 版本，並用一個例句示範地點／標籤／
 * 關鍵字怎麼拆——這個例句直接取自規格第八節的示範。
 */
internal fun geminiQueryPrompt(text: String): String = """
你是幫個人相簿 app 解析查詢句的工具。使用者輸入一句話描述想找的照片，你要拆解成：
地點（places）、標籤或主題（tags）、其餘關鍵字（keywords），以及日期範圍
（date_from／date_to，格式 YYYY-MM-DD；看不出明確日期就給 null，不要用「最近」「去年」這種模糊詞代替）。

只能回傳一個 JSON 物件，不要有其他文字或說明，格式如下：
{"date_from": null, "date_to": null, "places": [], "tags": [], "keywords": []}

範例——輸入「加勒比海夜潛看到的大蝦」應該回：
{"date_from": null, "date_to": null, "places": ["加勒比海"], "tags": ["夜潛"], "keywords": ["大蝦"]}

使用者輸入：「$text」
""".trimIndent()
