package com.xenyaa.videoshot.core.backup

/** 這次要送出的區塊：位元組範圍 `[start, endInclusive]`，`total` 是整份檔案大小。 */
data class UploadChunk(val start: Long, val endInclusive: Long, val total: Long) {
    val length: Long get() = endInclusive - start + 1

    /** Drive 可續傳上傳一個 chunk 要帶的 `Content-Range` 標頭值。 */
    fun contentRangeHeader(): String = "bytes $start-$endInclusive/$total"
}

/** 切出下一塊：從 [fromByte] 開始，最多 [chunkSize] 個位元組，不超過 [total]。 */
fun nextChunk(fromByte: Long, total: Long, chunkSize: Long): UploadChunk {
    require(fromByte < total) { "fromByte ($fromByte) 必須小於 total ($total)" }
    val endInclusive = minOf(fromByte + chunkSize - 1, total - 1)
    return UploadChunk(fromByte, endInclusive, total)
}

/**
 * 從 Drive 回應的 308（續傳查詢或部分上傳成功）解析已收到的位元組數
 * （規格第十節「可續傳上傳」；Google Drive 文件：`Range` 標頭格式固定是 `bytes=0-<lastByteReceived>`）。
 * Drive 完全沒收到任何位元組時**不會回 `Range` 標頭**——這種情況回 0（從頭開始）。
 */
fun parseReceivedBytes(rangeHeader: String?): Long {
    if (rangeHeader == null) return 0L
    val match = Regex("""bytes=0-(\d+)""").find(rangeHeader) ?: return 0L
    return match.groupValues[1].toLong() + 1
}
