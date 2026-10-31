package com.xenyaa.videoshot.ui.common

import android.util.Log
import androidx.compose.ui.platform.UriHandler

/** YouTube 服務條款（YouTube API 開發者政策要求上傳的 app 顯示這個連結）。 */
const val YOUTUBE_TERMS_URL = "https://www.youtube.com/t/terms"

/** videoshot 的隱私權政策，跟 Google 同意畫面上連的是同一個網址（規格第十節「品牌」）。 */
const val PRIVACY_POLICY_URL = "https://videoshot.scottchayaa.com/privacy"

/** 開外部網頁；手機上沒有能開網頁的 app 時什麼都不做，不當機。 */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }.onFailure { Log.w("VsLink", "開不了 $url", it) }
}
