package com.xenyaa.videoshot.poc

import org.json.JSONObject

object WatchPageProbe {
    /** InnerTube 探測要沿用 watch page 上的 client 版本與 API key。 */
    @Volatile var clientVersion: String? = null
    @Volatile var apiKey: String? = null

    fun run(videoId: String): String {
        val (html, wireBytes) = Http.get("https://www.youtube.com/watch?v=$videoId")
        clientVersion = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val json = JsonExtract.objectAfter(html, "ytInitialPlayerResponse = ")
            ?: return "WatchPage：找不到 ytInitialPlayerResponse（傳輸 ${wireBytes}B, html ${html.length} 字元）"
        return "WatchPage 傳輸=${wireBytes}B html=${html.length}字元 clientVersion=$clientVersion\n" +
            PlayerResponse.summarize(JSONObject(json))
    }
}
