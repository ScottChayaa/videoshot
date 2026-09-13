package com.xenyaa.videoshot.core.url

/** YouTube 的影片 ID 是 11 碼 base64url。 */
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

/** 路徑型的網址：youtu.be/{id}、/shorts/{id}、/embed/{id}、/v/{id}。 */
private val PATH_FORMS = Regex(
    "(?:youtu\\.be/|youtube\\.com/(?:shorts/|embed/|v/))([A-Za-z0-9_-]{11})"
)

/** 查詢參數型：?v={id} 或 &v={id}。 */
private val QUERY_FORM = Regex("[?&]v=([A-Za-z0-9_-]{11})")

/**
 * 從使用者貼上的字串取出 videoId（規格第五節第一步）。
 *
 * 接受純 videoId、`youtu.be/xxx`、`watch?v=`（可帶 `&t=`、`&list=`）、`shorts/`、`embed/`。
 * **只認 YouTube 的網域** —— 別的網站的 `?v=` 參數不是影片 ID。
 * 解不出來回 `null`，呼叫端就地顯示可接受的格式，不換頁。
 */
fun parseVideoId(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null

    // 純 videoId：整串剛好就是 11 碼
    if (VIDEO_ID.matches(text)) return text

    val isYouTube = text.contains("youtube.com", ignoreCase = true) ||
        text.contains("youtu.be", ignoreCase = true)
    if (!isYouTube) return null

    PATH_FORMS.find(text)?.let { return it.groupValues[1] }
    QUERY_FORM.find(text)?.let { return it.groupValues[1] }
    return null
}
