package com.xenyaa.videoshot.core.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 只比對變數名，不含 `=` 與兩側空白 —— extractJsonObject 會接著找下一個 `{`。
 * 真實頁面是 `var ytInitialPlayerResponse = {`，但只要 YouTube 改動一個空白字元
 * 就讓整個解析器失效，這個脆弱度不值得。
 */
private const val MARKER = "ytInitialPlayerResponse"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject.bool(key: String): Boolean? =
    this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

/**
 * 把 watch page 的 HTML 解析成結果分類 ＋ metadata ＋ storyboard spec。
 *
 * **純函式，不碰網路** —— 抓取是 :app 的 youtube 模組的事。
 * 這樣才能用錄製的頁面做 JVM 單元測試（規格第十三節）。
 */
fun parseWatchPage(videoId: String, html: String): WatchPage {
    val raw = extractJsonObject(html, MARKER)
        ?: return WatchPage(FetchResult.PARSE_FAILED, null, null)
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: return WatchPage(FetchResult.PARSE_FAILED, null, null)

    val playability = root.obj("playabilityStatus")
    val details = root.obj("videoDetails")

    // 連兩個都撈不到 → 頁面結構變了，不是影片有問題
    if (playability == null && details == null) {
        return WatchPage(FetchResult.PARSE_FAILED, null, null)
    }

    // 判定順序：先確認能不能播，再談有沒有 storyboard（規格第二節第 4 點）
    val status = playability?.str("status")
    if (status != "OK") {
        // LOGIN_REQUIRED（年齡限制）即使帶著 videoDetails 與 spec 也歸這一類 ——
        // app 內的播放器一樣播不了，進第二步沒有意義。
        return WatchPage(FetchResult.VIDEO_UNAVAILABLE, null, null)
    }
    if (details == null) {
        return WatchPage(FetchResult.PARSE_FAILED, null, null)
    }

    val microformat = root.obj("microformat")?.obj("playerMicroformatRenderer")
    val meta = VideoMeta(
        videoId = videoId,
        title = details?.str("title").orEmpty(),
        channelTitle = details?.str("author").orEmpty(),
        publishedAt = microformat?.str("publishDate") ?: microformat?.str("uploadDate").orEmpty(),
        durationSec = details?.str("lengthSeconds")?.toIntOrNull() ?: 0,
        privacy = if (microformat?.bool("isUnlisted") == true) "unlisted" else "public",
        playableInEmbed = playability?.bool("playableInEmbed") ?: false,
    )

    val spec = root.obj("storyboards")?.obj("playerStoryboardSpecRenderer")?.str("spec")
    // 沒有 spec 不是錯誤：影片過短、直播中、剛上傳都會這樣。
    // metadata 仍然要給，第一步照樣能進第二步，只是縮圖牆空白（規格第七節）。
    return if (spec.isNullOrBlank()) {
        WatchPage(FetchResult.NO_STORYBOARD, meta, null)
    } else {
        WatchPage(FetchResult.OK, meta, spec)
    }
}
