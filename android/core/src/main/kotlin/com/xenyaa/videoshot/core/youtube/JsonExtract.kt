package com.xenyaa.videoshot.core.youtube

/**
 * 從一段文字裡找出 marker 之後的第一個完整 JSON 物件。
 *
 * 為什麼不用正規表示式：playerResponse 有上百層巢狀，而且字串欄位裡本來就帶著
 * `{`、`}`、跳脫引號。只有逐字元配對括號、並且正確略過字串內容，才不會提前收尾。
 */
fun extractJsonObject(text: String, marker: String): String? {
    val at = text.indexOf(marker)
    if (at < 0) return null
    val start = text.indexOf('{', at)
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
    }
    return null   // 括號沒有配對完就結束 —— 頁面被截斷了
}
