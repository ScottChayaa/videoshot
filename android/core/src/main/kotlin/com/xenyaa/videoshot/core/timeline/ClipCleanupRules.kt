package com.xenyaa.videoshot.core.timeline

/**
 * 剪輯上傳完成後問要不要刪原檔的問句：「要刪除已上傳完成的 5 個素材嗎？」（照片與影片合計）；
 * 兩種都是 0（都已不在手機裡）回 null，不問刪除。
 */
fun clipDeletePrompt(photos: Int, videos: Int): String? {
    val total = photos + videos
    if (total == 0) return null
    return "要刪除已上傳完成的 $total 個素材嗎？"
}
