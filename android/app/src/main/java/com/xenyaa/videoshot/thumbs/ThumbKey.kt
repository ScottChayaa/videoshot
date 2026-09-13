package com.xenyaa.videoshot.thumbs

/**
 * 縮圖的**邏輯識別碼**（規格第四節「跨平台的資料契約」第 1 條）。
 *
 * DB 裡不存任何檔案路徑 —— 這個識別碼由 shot 的 (video_id, sb_level, frame_index) 推導，
 * 而「識別碼對應到哪種儲存」只在 thumbs 模組內決定。
 * 換到別的平台（瀏覽器的 OPFS、iOS 的檔案）時，只有下面這個 relativePath 要重寫。
 */
data class ThumbKey(val videoId: String, val level: Int, val frameIndex: Int)

fun ThumbKey.relativePath(): String = "$videoId/L$level/$frameIndex.webp"
