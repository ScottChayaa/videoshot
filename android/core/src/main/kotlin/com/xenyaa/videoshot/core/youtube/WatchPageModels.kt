package com.xenyaa.videoshot.core.youtube

/**
 * 抓一次 watch page 的結果分類（規格第二節第 4 點的失敗分類表）。
 * 判定順序很重要：先確認影片能不能播，再談有沒有 storyboard。
 */
enum class FetchResult {
    /** 抓得到 spec */
    OK,

    /** 影片不存在、私人、已刪除、地區限制，或需要登入（年齡限制） */
    VIDEO_UNAVAILABLE,

    /** 影片本身沒有 storyboard（過短、直播中、剛上傳） */
    NO_STORYBOARD,

    /** 連 videoDetails 與 playabilityStatus 都撈不到 —— 頁面結構變了，解析器失效 */
    PARSE_FAILED,

    /** HTTP 非 2xx 或網路錯誤。這一類由 :app 的 youtube 模組產生，解析器不會回傳它 */
    FETCH_FAILED,
}

/**
 * watch page 能提供的欄位（規格第二節第 4 點）。
 * **沒有拍攝日期** —— 那是 Data API 的 recordingDate，本版放棄。
 */
data class VideoMeta(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    /** ISO 8601 原字串，如 2014-11-10T06:05:55-08:00 */
    val publishedAt: String,
    val durationSec: Int,
    /** 'public' | 'unlisted' | 'unknown' */
    val privacy: String,
    /** 決定播放器載入 embed 還是 m.youtube.com（規格第二節第 5 點） */
    val playableInEmbed: Boolean,
)

/**
 * @param meta 只有 OK 與 NO_STORYBOARD 會有值 —— 影片播不了時那些欄位沒有意義
 * @param storyboardSpec 只有 OK 會有值
 */
data class WatchPage(
    val result: FetchResult,
    val meta: VideoMeta?,
    val storyboardSpec: String?,
)
