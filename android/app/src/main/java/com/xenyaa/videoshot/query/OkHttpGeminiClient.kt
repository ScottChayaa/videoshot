package com.xenyaa.videoshot.query

import com.xenyaa.videoshot.core.query.ParsedQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig(),
) {
    @Serializable data class Content(val parts: List<Part>)
    @Serializable data class Part(val text: String)
    @Serializable data class GenerationConfig(val responseMimeType: String = "application/json")
}

@Serializable
private data class GeminiResponse(val candidates: List<Candidate> = emptyList()) {
    @Serializable data class Candidate(val content: Content? = null)
    @Serializable data class Content(val parts: List<Part> = emptyList())
    @Serializable data class Part(val text: String? = null)
}

/** Gemini 回傳的內層 JSON——欄位命名對齊 `geminiQueryPrompt` 要求模型輸出的形狀。 */
@Serializable
private data class GeminiQueryResult(
    val date_from: String? = null,
    val date_to: String? = null,
    val places: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

class OkHttpGeminiClient(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher,
    /** 測試用：把網址指到 MockWebServer。正式環境用預設值。 */
    private val apiUrl: (model: String) -> String =
        { model -> "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" },
) : GeminiClient {

    override suspend fun parse(text: String, apiKey: String): ParsedQuery? = withContext(io) {
        val body = json.encodeToString(
            GeminiRequest(contents = listOf(GeminiRequest.Content(listOf(GeminiRequest.Part(geminiQueryPrompt(text)))))),
        )
        val request = Request.Builder()
            .url("${apiUrl("gemini-flash-latest")}?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseResponse(response.body.string())
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun parseResponse(raw: String): ParsedQuery? = runCatching {
        val envelope = json.decodeFromString<GeminiResponse>(raw)
        val text = envelope.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null
        val result = json.decodeFromString<GeminiQueryResult>(text)
        ParsedQuery(
            dateFrom = result.date_from,
            dateTo = result.date_to,
            places = result.places,
            tags = result.tags,
            keywords = result.keywords,
        )
    }.getOrNull()
}
