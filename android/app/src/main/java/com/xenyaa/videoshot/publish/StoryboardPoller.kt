package com.xenyaa.videoshot.publish

import android.util.Log
import com.xenyaa.videoshot.core.publish.storyboardPollDelayMs
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.edit.ClipExportNotifier
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 影片上傳完（YouTube 處理中）之後，查 watch page 看 YouTube 產生 storyboard 了沒：有了就完成並通知；
 * 還沒有就依經過時間排下一次（[schedule]：任務編號、延遲毫秒），逾時（60 分鐘）失敗「YouTube 還沒產生縮圖」。
 *
 * - 剛上傳的影片抓不到頁面、說沒有 storyboard 或說影片不可用都是常態，一律當作「還沒有」。
 * - 計時從進入處理中那一刻的 `updated_at` 起算（處理中期間不寫它）。
 * - 任務不是處理中（被刪、被取消、已完成或失敗）就什麼都不做，也不再排下一次，這一串自然結束。
 * - 沒預期的例外（資料庫、排程）記 log 後照間隔再排下一次，不讓這一串斷掉；取消照丟出去。
 */
class StoryboardPoller(
    private val repo: CacheRepo,
    private val youtube: Youtube,
    private val notifier: ClipExportNotifier,
    private val schedule: (taskId: Long, delayMs: Long) -> Unit,
    private val now: () -> Long,
) {

    suspend fun poll(taskId: Long) {
        var task: EditTask? = null
        try {
            task = repo.editTask(taskId).first() ?: return
            val videoId = task.videoId
            if (task.state != EditTaskState.PROCESSING || videoId == null) return

            val page = youtube.watchPage(videoId)
            if (page.result == FetchResult.OK && page.storyboardSpec != null) {
                repo.setEditTaskDone(taskId, now())
                repo.editTask(taskId).first()?.let(notifier::done) // 等的途中被刪掉就不通知
                return
            }
            scheduleNextOrFail(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "等縮圖的任務 $taskId 發生沒預期的錯，稍後再查", e)
            val delay = task?.let { storyboardPollDelayMs(now() - it.updatedAt) } ?: FALLBACK_DELAY_MS
            schedule(taskId, delay)
        }
    }

    private suspend fun scheduleNextOrFail(task: EditTask) {
        val delay = storyboardPollDelayMs(now() - task.updatedAt)
        if (delay != null) {
            schedule(task.id, delay)
            return
        }
        repo.setEditTaskFailed(task.id, REASON_NO_STORYBOARD, EditTaskStep.STORYBOARD, now())
        repo.editTask(task.id).first()?.let { notifier.failed(it, REASON_NO_STORYBOARD) }
    }

    private companion object {
        const val TAG = "VsStoryboardPoll"

        /** 例外發生時任務讀不到、或已經逾時：用這個間隔再試，免得這一串斷掉。 */
        const val FALLBACK_DELAY_MS = 120_000L
    }
}
