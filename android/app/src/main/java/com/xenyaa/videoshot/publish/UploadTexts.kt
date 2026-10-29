package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.core.publish.UploadErrorKind

/** 上傳與等縮圖失敗的原因（直接顯示給使用者）。 */
const val REASON_NO_NETWORK = "沒有網路連線"
const val REASON_AUTH = "Google 授權已失效，請按重試重新授權"
const val REASON_NO_CHANNEL = "這個 Google 帳號還沒有 YouTube 頻道"
const val REASON_QUOTA = "今天的上傳額度用完了，請明天再試"
const val REASON_CHANNEL_LIMIT = "這個頻道今天的上傳次數已達上限，請明天再試"
const val REASON_OUTPUT_MISSING = "輸出的影片不見了，請按重試重新輸出"
const val REASON_UPLOAD_FAILED = "上傳失敗"
const val REASON_NO_STORYBOARD = "YouTube 還沒產生縮圖"

/** YouTube 錯誤分類對應的失敗原因；沒有專屬說法的一律「上傳失敗」。 */
fun reasonFor(kind: UploadErrorKind): String = when (kind) {
    UploadErrorKind.AUTH -> REASON_AUTH
    UploadErrorKind.NO_CHANNEL -> REASON_NO_CHANNEL
    UploadErrorKind.QUOTA -> REASON_QUOTA
    UploadErrorKind.CHANNEL_LIMIT -> REASON_CHANNEL_LIMIT
    UploadErrorKind.SESSION_GONE, UploadErrorKind.TRANSIENT, UploadErrorKind.FATAL -> REASON_UPLOAD_FAILED
}
