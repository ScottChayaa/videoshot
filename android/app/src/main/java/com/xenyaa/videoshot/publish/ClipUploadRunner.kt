package com.xenyaa.videoshot.publish

import android.util.Log
import com.xenyaa.videoshot.backup.GoogleAuthRevoked
import com.xenyaa.videoshot.core.backup.nextChunk
import com.xenyaa.videoshot.core.publish.NETWORK_WAIT_MS
import com.xenyaa.videoshot.core.publish.UPLOAD_CHUNK_BYTES
import com.xenyaa.videoshot.core.publish.UploadErrorKind
import com.xenyaa.videoshot.core.publish.youtubeTitle
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.edit.ClipExportNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

/**
 * 一個剪輯任務上傳到 YouTube 的流程：`uploading` 的任務把 `dirFor(taskId)/out.mp4` 一段一段傳上去，
 * 拿到影片編號就進 `processing`、刪掉成品、排第一次等縮圖的輪詢。依賴全部可換，純 JVM 可測。
 *
 * - **不重新輸出、不從 0 重傳**：工作階段網址開好就先存進任務；之後不論斷網、被殺、取消、授權失效，
 *   下次都先查那個網址已收到幾個位元組再接著傳（查到已收完就直接進處理中，YouTube 上不會多一支）。
 * - 任務在上傳途中被刪掉：之後的寫入都不會把它寫回來（`CacheRepo` 對不存在的編號不動作），
 *   下一段之前發現就停、刪成品、不通知。
 * - 取消（`CancellationException`）往外丟，成品與網址都留著：系統停掉的工作下次要接著傳；
 *   使用者取消時由呼叫端刪資料夾。
 */
class ClipUploadRunner(
    private val repo: CacheRepo,
    private val uploader: YoutubeUploader,
    /**
     * `youtube.upload` 的存取權杖；授權被撤銷丟 [GoogleAuthRevoked]。
     * `forceRefresh` 是 true 時上一個權杖被拒絕了（401），不能再回快取裡的那一個。
     */
    private val token: suspend (forceRefresh: Boolean) -> String,
    /** 等網路回來，最多 [timeoutMs]；等到回 true。 */
    private val awaitNetwork: suspend (timeoutMs: Long) -> Boolean,
    /** 排等 YouTube 產生縮圖的輪詢。 */
    private val schedulePoll: (taskId: Long, delayMs: Long) -> Unit,
    private val notifier: ClipExportNotifier,
    private val dirFor: (Long) -> File,
    private val now: () -> Long,
    private val sleep: suspend (Long) -> Unit = ::delay,
) {

    /** 上傳編號 [taskId] 的任務。任務不存在、或不是上傳中就什麼都不做。取消時把取消丟出去。 */
    suspend fun run(taskId: Long) {
        val task = repo.editTask(taskId).first() ?: return
        if (task.state != EditTaskState.UPLOADING) return
        val out = File(dirFor(taskId), OUTPUT_NAME)

        // 上次最後一段傳完、寫進處理中之前被中斷：不用再傳
        task.videoId?.let { return uploaded(taskId, it, out) }

        val size = out.length() // 檔案不存在時是 0
        if (size == 0L) return fail(taskId, out, REASON_OUTPUT_MISSING, EditTaskStep.RENDER)

        val videoId = try {
            upload(task, out, size)
        } catch (e: UploadFailure) {
            return fail(taskId, out, e.reason, e.step)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 沒預期的錯：記下來、任務標上傳失敗（成品與網址留著，重試接著傳），不讓排隊的工作跟著壞掉
            Log.w(TAG, "上傳任務 $taskId 發生沒預期的錯", e)
            return fail(taskId, out, REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
        }
        if (videoId == null) { // 上傳途中任務被刪掉
            out.delete()
            return
        }
        uploaded(taskId, videoId, out)
    }

    /**
     * 分段上傳到拿到影片編號為止；任務途中被刪掉回 null。要讓任務失敗時丟 [UploadFailure]。
     * 斷網與伺服器暫時錯誤都先查工作階段的起點再接著傳（沒收下的那段可能只收了一部分）。
     */
    private suspend fun upload(task: EditTask, out: File, size: Long): String? {
        val id = task.id
        var accessToken = fetchToken(forceRefresh = false)
        var url = task.uploadUrl
        var needQuery = url != null
        var offset = 0L
        var lastPercent = task.progress
        // 連續失敗的計數：分段有往前推才歸零，避免「查詢成功、分段又失敗」無限重來
        var authRetried = false
        var sessionRestarted = false
        var transientFails = 0
        var networkFails = 0

        while (true) {
            if (repo.editTask(id).first() == null) return null

            // 要查詢時起點還不確定（offset 不是真的）：先不寫，免得接著傳時進度先退回 0%
            val percent = (offset * 100 / size).toInt()
            if (!(needQuery && url != null) && percent != lastPercent) {
                lastPercent = percent
                repo.setEditTaskProgress(id, EditTaskState.UPLOADING, percent, now())
                notifier.progress(task.copy(state = EditTaskState.UPLOADING, progress = percent), percent)
            }

            var putting = false
            val status = try {
                val sessionUrl = url
                when {
                    sessionUrl == null -> {
                        val newUrl = uploader.startSession(accessToken, youtubeTitle(task.title), size)
                        // 先存網址再傳：之後被殺或斷網才能從這個工作階段接著傳
                        repo.setEditTaskUploadUrl(id, newUrl, now())
                        url = newUrl
                        UploadStatus.Partial(0)
                    }
                    needQuery -> uploader.query(sessionUrl, accessToken, size)
                    else -> {
                        putting = true
                        uploader.putChunk(sessionUrl, accessToken, out, nextChunk(offset, size, UPLOAD_CHUNK_BYTES))
                    }
                }
            } catch (e: IOException) {
                // 讀檔失敗（成品被刪或變短）也是 IOException：那不是斷網，等網路也沒用
                if (out.length() != size) throw UploadFailure(REASON_OUTPUT_MISSING, EditTaskStep.RENDER)
                Log.i(TAG, "上傳任務 $id 斷線，等網路回來", e)
                networkFails++
                // 網路說回來了卻一直連不上（例如要登入的 Wi-Fi）：照伺服器錯誤的節奏退避，不空轉
                if (networkFails > MAX_RETRIES) throw UploadFailure(REASON_NO_NETWORK, EditTaskStep.UPLOAD)
                if (!awaitNetwork(NETWORK_WAIT_MS)) throw UploadFailure(REASON_NO_NETWORK, EditTaskStep.UPLOAD)
                sleep(backoffMs(networkFails))
                needQuery = true
                continue
            } catch (e: UploadHttpException) {
                when (e.kind) {
                    UploadErrorKind.AUTH -> {
                        if (authRetried) throw UploadFailure(REASON_AUTH, EditTaskStep.UPLOAD)
                        authRetried = true
                        accessToken = fetchToken(forceRefresh = true)
                    }
                    UploadErrorKind.SESSION_GONE -> {
                        if (sessionRestarted) throw UploadFailure(REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
                        sessionRestarted = true
                        url = null
                        offset = 0
                        repo.setEditTaskUploadUrl(id, null, now())
                    }
                    UploadErrorKind.TRANSIENT -> {
                        transientFails++
                        if (transientFails > MAX_RETRIES) throw UploadFailure(REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
                        sleep(backoffMs(transientFails))
                    }
                    else -> throw UploadFailure(reasonFor(e.kind), EditTaskStep.UPLOAD)
                }
                needQuery = true
                continue
            }

            when (status) {
                is UploadStatus.Done -> return status.videoId
                is UploadStatus.Partial -> {
                    if (putting && status.receivedBytes > offset) {
                        authRetried = false
                        transientFails = 0
                        networkFails = 0
                    } else if (putting) {
                        // 分段沒前進也沒報錯：當成一次暫時錯誤，不然會一直重傳同一段
                        transientFails++
                        if (transientFails > MAX_RETRIES) throw UploadFailure(REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
                        sleep(backoffMs(transientFails))
                    }
                    offset = status.receivedBytes
                    // 收滿了卻沒給影片編號（不該發生）：當上傳失敗，重試時查詢會拿到真正的結果
                    if (offset >= size) throw UploadFailure(REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
                    needQuery = false
                }
            }
        }
    }

    /**
     * 拿存取權杖。被撤銷 → 授權失效；其他錯（Play 服務離線時的 `ExecutionException`／`ApiException` 等）
     * 當成斷網：等網路回來再拿一次，還是失敗才算上傳失敗。
     */
    private suspend fun fetchToken(forceRefresh: Boolean): String {
        try {
            return token(forceRefresh)
        } catch (e: GoogleAuthRevoked) {
            throw UploadFailure(REASON_AUTH, EditTaskStep.UPLOAD)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "拿不到 YouTube 權杖，等網路回來", e)
        }
        if (!awaitNetwork(NETWORK_WAIT_MS)) throw UploadFailure(REASON_NO_NETWORK, EditTaskStep.UPLOAD)
        try {
            return token(forceRefresh)
        } catch (e: GoogleAuthRevoked) {
            throw UploadFailure(REASON_AUTH, EditTaskStep.UPLOAD)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "網路回來後仍拿不到 YouTube 權杖", e)
            throw UploadFailure(REASON_UPLOAD_FAILED, EditTaskStep.UPLOAD)
        }
    }

    /** 拿到影片編號：先記下（之後被殺也不會重傳）、再刪成品、排第一次等縮圖。任務已被刪就不排。 */
    private suspend fun uploaded(taskId: Long, videoId: String, out: File) {
        repo.setEditTaskUploaded(taskId, videoId, now())
        out.delete()
        if (repo.editTask(taskId).first() == null) return
        schedulePoll(taskId, FIRST_POLL_DELAY_MS)
    }

    /**
     * 任務失敗：寫入原因與步驟、通知；成品與網址留著讓重試接著傳。
     * 失敗在輸出（成品不見了）時清掉網址：重新輸出是另一個檔，舊的工作階段不能再用。
     * 任務已被刪就刪成品、不通知。
     */
    private suspend fun fail(taskId: Long, out: File, reason: String, step: EditTaskStep) {
        if (step == EditTaskStep.RENDER) repo.setEditTaskUploadUrl(taskId, null, now())
        repo.setEditTaskFailed(taskId, reason, step, now())
        val failed = repo.editTask(taskId).first()
        if (failed == null) {
            out.delete()
            return
        }
        notifier.failed(failed, reason)
    }

    /** 要讓任務失敗的原因（流程內部用，[run] 接住後寫進任務）。 */
    private class UploadFailure(val reason: String, val step: EditTaskStep) : Exception(reason)

    private companion object {
        const val OUTPUT_NAME = "out.mp4"
        const val TAG = "VsClipUpload"

        /** 進處理中後第一次查縮圖的時間（storyboard 前 5 分鐘每 30 秒查一次）。 */
        const val FIRST_POLL_DELAY_MS = 30_000L

        /** 伺服器暫時錯誤與斷線後連不上的重試次數；退避 1、2、4、8、16 秒。 */
        const val MAX_RETRIES = 5

        fun backoffMs(attempt: Int): Long = 1_000L shl (attempt - 1)
    }
}
