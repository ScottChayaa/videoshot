package com.xenyaa.videoshot.poc

import org.json.JSONObject

/** P-3：youtubei/v1/player 是否帶 storyboard spec、流量多大。 */
object InnerTubeProbe {
    fun run(videoId: String): String {
        val version = WatchPageProbe.clientVersion
            ?: return "InnerTube：請先按 WatchPage 取得 clientVersion"
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", version)
            .put("hl", "zh-TW")
        val body = JSONObject()
            .put("videoId", videoId)
            .put("context", JSONObject().put("client", client))
            .toString()
        val keyParam = WatchPageProbe.apiKey?.let { "&key=$it" } ?: ""
        val (text, wireBytes) = Http.postJson(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false$keyParam",
            body
        )
        return "InnerTube 傳輸=${wireBytes}B json=${text.length}字元\n" + PlayerResponse.summarize(JSONObject(text))
    }
}
