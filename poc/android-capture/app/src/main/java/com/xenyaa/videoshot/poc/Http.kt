package com.xenyaa.videoshot.poc

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Http {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /** 回傳（解壓後的內容, 實際傳輸的位元組數）。 */
    fun get(url: String): Pair<String, Int> = request(url, null)

    fun postJson(url: String, body: String): Pair<String, Int> = request(url, body)

    private fun request(url: String, body: String?): Pair<String, Int> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
        // 明確要求 gzip：HttpURLConnection 就不會自動解壓，才量得到實際傳輸量
        conn.setRequestProperty("Accept-Encoding", "gzip")
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
        val text = if (conn.contentEncoding == "gzip") {
            GZIPInputStream(raw.inputStream()).bufferedReader().use { it.readText() }
        } else {
            String(raw)
        }
        if (code !in 200..299) throw IOException("HTTP $code: ${text.take(200)}")
        return text to raw.size
    }
}
