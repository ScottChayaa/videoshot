package com.xenyaa.videoshot.poc

import org.json.JSONObject

/** 印出規格第二節第 4 點要確認的欄位。 */
object PlayerResponse {
    fun summarize(r: JSONObject): String {
        val ps = r.optJSONObject("playabilityStatus")
        val vd = r.optJSONObject("videoDetails")
        val mf = r.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
        val spec = r.optJSONObject("storyboards")
            ?.optJSONObject("playerStoryboardSpecRenderer")
            ?.optString("spec")
        return buildString {
            appendLine("  playability=${ps?.optString("status")} reason=${ps?.optString("reason")}")
            appendLine(
                "  videoDetails: title=${vd?.optString("title")} author=${vd?.optString("author")} " +
                    "lengthSeconds=${vd?.optString("lengthSeconds")} isLive=${vd?.optBoolean("isLiveContent")}"
            )
            appendLine(
                "  microformat: publishDate=${mf?.optString("publishDate")} " +
                    "uploadDate=${mf?.optString("uploadDate")} isUnlisted=${mf?.opt("isUnlisted")}"
            )
            append("  storyboard spec: ${spec?.take(80) ?: "（無）"}")
        }
    }
}
