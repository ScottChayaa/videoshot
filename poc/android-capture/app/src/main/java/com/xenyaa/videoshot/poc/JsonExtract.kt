package com.xenyaa.videoshot.poc

/** 從 HTML 裡找出 marker 之後的第一個完整 JSON 物件（括號配對，略過字串內容）。 */
object JsonExtract {
    fun objectAfter(text: String, marker: String): String? {
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
        return null
    }
}
