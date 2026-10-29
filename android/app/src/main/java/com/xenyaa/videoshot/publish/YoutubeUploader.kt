package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.core.backup.UploadChunk
import com.xenyaa.videoshot.core.publish.UploadErrorKind
import java.io.File

/** 可續傳上傳一次 HTTP 呼叫的結果：還沒收完（已收到幾個位元組），或已收完（影片編號）。 */
sealed interface UploadStatus {
    data class Partial(val receivedBytes: Long) : UploadStatus
    data class Done(val videoId: String) : UploadStatus
}

/** 非 2xx／308 的回應；[kind] 是 `classifyUploadError` 的分類，由流程層決定重試、重新授權或失敗。 */
class UploadHttpException(val code: Int, val kind: UploadErrorKind) : Exception("上傳失敗: HTTP $code（$kind）")

/**
 * YouTube Data API `videos.insert` 的可續傳上傳，每個方法只做一次 HTTP 請求。
 * 分段迴圈、重試、等網路是流程層的事；`IOException` 原樣往外丟，由流程層判斷是不是斷網。
 */
interface YoutubeUploader {
    /** 開一個可續傳工作階段（非公開、說明留空），回傳上傳網址（`Location`）。 */
    suspend fun startSession(token: String, title: String, sizeBytes: Long): String

    /** 查工作階段目前收到幾個位元組；已收完時回影片編號。 */
    suspend fun query(sessionUrl: String, token: String, sizeBytes: Long): UploadStatus

    /** 送 [chunk] 指定的那一段。 */
    suspend fun putChunk(sessionUrl: String, token: String, file: File, chunk: UploadChunk): UploadStatus
}
