package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.flow.Flow

/**
 * cache.db 的唯一對外入口（規格第三節模組邊界第 1 條）。
 * 這裡面的東西**都能重建**，所以不進備份；還原備份時整個清掉（`clearAll`）。
 */
interface CacheRepo {
    suspend fun putThumbStates(states: List<ThumbStateEntity>)
    suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity?
    /** 回填作業要處理的：還缺、而且已經到重試時間的。 */
    suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity>
    suspend fun forgetVideoThumbs(videoId: String)
    /** 刪掉一格的回填狀態。不清的話，回填作業會把已經刪掉的那一格又抓回來。 */
    suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int)

    /** 回填進度／「無法取回 N 張」用的計數。@param state 'ok' | 'missing' | 'lost' */
    suspend fun countByState(state: String): Int

    /** 全部 lost 的格子——帳號頁【刪除這些收藏】要靠這份清單反查是哪幾張 shot。 */
    suspend fun lostThumbs(): List<ThumbStateEntity>

    /** 【稍後重試】：全部 lost 的格子重設成 missing，立刻可以重試。 */
    suspend fun resetLostToMissing(now: Long)

    suspend fun saveDraft(draft: DraftEntity)
    suspend fun currentDraft(): DraftEntity?
    suspend fun clearDraft()

    /** 存剪輯草稿（永遠一列，覆蓋舊的）。@param now Unix 毫秒。 */
    suspend fun saveClipDraft(draft: ClipDraft, now: Long)

    /** 目前的剪輯草稿；沒有、或存的內容解不開（此時順手把那一列刪掉）都回 null。 */
    suspend fun currentClipDraft(): ClipDraft?
    suspend fun clearClipDraft()

    /**
     * 按【開始】：把 [draft] 變成一個輸出任務（`queued`、進度 0）並清掉剪輯草稿，
     * **同一個交易**——任務寫不進去時草稿原封不動。只留目前的時間軸、不留復原與重做紀錄。
     * @param autoUpload 輸出完成後是否自動上傳到 YouTube。
     * @param now Unix 毫秒。@return 新任務的編號。
     */
    suspend fun startEditTask(draft: ClipDraft, title: String, autoUpload: Boolean, now: Long): Long

    /** 全部任務，新的在前。內容解不開的任務（理論上不會有）不列。 */
    fun editTasks(): Flow<List<EditTask>>

    /** 單一任務；不存在（或已被刪掉）時發出 null。 */
    fun editTask(id: Long): Flow<EditTask?>

    /** 還沒處理完、要有工作在跑的任務（`queued`／`rendering`／`uploading`）編號，舊的在前。 */
    suspend fun pendingEditTaskIds(): List<Long>

    /** 已上傳、等 YouTube 產生縮圖（`processing`）的任務編號，舊的在前。 */
    suspend fun processingEditTaskIds(): List<Long>

    /** `done` 的任務個數（導覽列「剪輯」的紅點）。 */
    fun doneEditTaskCount(): Flow<Int>

    /** 以下 `setEditTask…`、[requeueEditTask]、[retryEditTask] 對不存在的編號什麼都不做（不會把已刪的任務寫回來）。 */
    suspend fun setEditTaskProgress(id: Long, state: EditTaskState, progress: Int, now: Long)
    suspend fun setEditTaskRendered(id: Long, skipped: Int, galleryUri: String?, now: Long)

    /** 輸出完要自動上傳：改成 `uploading`、進度 0、記下略過的素材個數。 */
    suspend fun setEditTaskReadyToUpload(id: Long, skipped: Int, now: Long)

    /** 記下（或用 null 清掉）可續傳上傳的工作階段網址。 */
    suspend fun setEditTaskUploadUrl(id: Long, url: String?, now: Long)

    /** 上傳完成：改成 `processing`、進度 100、記下 [videoId]、清掉上傳網址、失敗原因與失敗步驟。 */
    suspend fun setEditTaskUploaded(id: Long, videoId: String, now: Long)

    /** 等到縮圖了：改成 `done`。 */
    suspend fun setEditTaskDone(id: Long, now: Long)

    /** 任務失敗：記下原因與失敗的 [step]（【重試】依它決定回到哪個狀態）。 */
    suspend fun setEditTaskFailed(id: Long, reason: String, step: EditTaskStep, now: Long)
    suspend fun setEditTaskAutoUpload(id: Long, on: Boolean, now: Long)

    /**
     * app 被殺或開不了前景服務時用：只把 `rendering` 改回 `queued`、進度 0、清掉失敗原因。
     * 其他狀態不動——`uploading` 要靠存著的上傳網址接著傳，退回 `queued` 會重新輸出。
     */
    suspend fun requeueEditTask(id: Long, now: Long)

    /**
     * 【重試】：只對 `failed` 的任務，依失敗的步驟回到那一步的開頭並清掉失敗原因與步驟——
     * `render`（或沒記）→ `queued`、進度 0；`upload` → `uploading`、進度 0（上傳網址保留，接著傳）；
     * `storyboard` → `processing`（`updated_at` 設成 [now]，重新計時）。
     * @return 新的狀態；任務不存在或不是失敗則回 null、不動它。
     */
    suspend fun retryEditTask(id: Long, now: Long): EditTaskState?
    suspend fun deleteEditTask(id: Long)

    /** 把這幾個地點或標籤的最近使用時間記成 [usedAt]（Unix 秒）；已有的覆蓋（規格第六節「首頁」）。 */
    suspend fun touchFacets(refs: Collection<FacetRef>, usedAt: Long)

    /** 全部的最近使用時間。 */
    suspend fun facetRecent(): Map<FacetRef, Long>

    /** 合併地點或標籤：目標取兩者中較新的時間，來源那一列刪掉（規格第九節）。來源沒有紀錄就什麼都不動。 */
    suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long)

    /** 刪除地點或標籤時連同使用時間刪掉。 */
    suspend fun forgetFacetRecent(kind: Int, id: Long)

    /**
     * 還原備份之後呼叫：裝置本地狀態作廢（回填作業會重新掃一遍缺圖；還原後地點與標籤的編號對不上，最近使用時間也不能沿用）。
     * **剪輯草稿（`edit_draft`）刻意不清**：它引用的是這台手機的照片與影片，還原圖庫不影響這些素材。
     */
    suspend fun clearAll()
}
