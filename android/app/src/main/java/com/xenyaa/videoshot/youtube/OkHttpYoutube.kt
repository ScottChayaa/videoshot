package com.xenyaa.videoshot.youtube

import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.core.youtube.parseWatchPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 桌機版 Chrome 的 UA。行動版 UA 會拿到結構不同的頁面，解析器吃不了。 */
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"

class OkHttpYoutube(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher,
    /** 測試用：把網址指到 MockWebServer。正式環境用預設值。 */
    private val watchUrl: (String) -> String = { "https://www.youtube.com/watch?v=$it" },
) : Youtube {

    override suspend fun watchPage(videoId: String): WatchPage = withContext(io) {
        val request = Request.Builder()
            .url(watchUrl(videoId))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use WatchPage(FetchResult.FETCH_FAILED, null, null)
                }
                parseWatchPage(videoId, response.body.string())
            }
        } catch (e: IOException) {
            // 沒網路、DNS 失敗、逾時 —— 都是環境問題，不是解析器壞了
            WatchPage(FetchResult.FETCH_FAILED, null, null)
        }
    }

    override suspend fun sheet(url: String): ByteArray = withContext(io) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw SheetForbidden(url)
            if (!response.isSuccessful) throw IOException("sheet HTTP ${response.code}：$url")
            response.body.bytes()
        }
    }
}
