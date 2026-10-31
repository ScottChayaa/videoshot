package com.xenyaa.videoshot.core.timeline

/**
 * 剪輯上傳完成後問要不要刪原檔的問句：「要刪除這次用到的 3 張照片、2 支影片嗎？」；
 * 只有一種素材就只寫那一種，兩種都是 0（都已不在手機裡）回 null，不問刪除。
 */
fun clipDeletePrompt(photos: Int, videos: Int): String? {
    val parts = listOfNotNull(
        "$photos 張照片".takeIf { photos > 0 },
        "$videos 支影片".takeIf { videos > 0 },
    )
    if (parts.isEmpty()) return null
    return "要刪除這次用到的 ${parts.joinToString("、")}嗎？"
}
