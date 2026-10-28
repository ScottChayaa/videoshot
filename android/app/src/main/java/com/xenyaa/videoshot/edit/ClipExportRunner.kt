package com.xenyaa.videoshot.edit

import android.util.Log
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.pruneMissing
import com.xenyaa.videoshot.core.timeline.requiredFreeBytes
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 輸出任務進行時要通知使用者的事（實作在 Task 6，這裡只管什麼時候叫）。 */
interface ClipExportNotifier {
    fun progress(task: EditTask, percent: Int)

    /** 輸出完成：停在輸出完成，或已存進相簿（看 [EditTask.galleryUri]）。[task] 是寫完結果之後重讀的。 */
    fun finished(task: EditTask)

    fun failed(task: EditTask, reason: String)
}

/**
 * 一個輸出任務從頭到尾的流程：檢查素材與空間 → 輸出 → 存相簿或停在輸出完成 → 失敗處理。
 * 依賴全部可換，純 JVM 可測；背景工作（WorkManager）只是呼叫 [run]。
 *
 * - 任務在輸出途中被刪掉：之後所有寫入都不會把它寫回來（`CacheRepo` 的 `setEditTask…` 對不存在的編號不動作），
 *   成品刪掉、也不通知。
 * - 成品固定在 `dirFor(taskId)/out.mp4`。失敗、取消、存相簿後都不留半成品。
 */
class ClipExportRunner(
    private val repo: CacheRepo,
    private val library: MediaLibrary,
    private val exporter: ClipExporter,
    private val saver: MediaSaver,
    private val notifier: ClipExportNotifier,
    /** 現在有沒有讀手機相片與影片的權限（使用者隨時可以在系統設定收回）。 */
    private val canReadMedia: () -> Boolean,
    private val dirFor: (Long) -> File,
    private val freeBytes: () -> Long,
    private val now: () -> Long,
) {

    /** 輸出編號 [taskId] 的任務。任務不存在、或不是等待中／輸出中就什麼都不做。取消時把取消丟出去。 */
    suspend fun run(taskId: Long) {
        val task = repo.editTask(taskId).first() ?: return
        if (task.state != EditTaskState.QUEUED && task.state != EditTaskState.RENDERING) return

        // 上次被中斷（app 被殺、權限被收回）可能留著半成品：這次不管成不成都用不到，先清掉
        val out = File(dirFor(taskId), OUTPUT_NAME)
        out.delete()

        // 素材：沒權限時 MediaStore 仍列得出 app 自己存進相簿的影片，清單不是空的，所以先直接看權限；
        // 清單整份空但草稿有素材也是讀不到（查詢失敗）。兩種都不能當成素材全被刪掉而去修剪
        if (!canReadMedia()) return fail(task, REASON_CANNOT_READ)
        val items = library.list(MediaFilter.ALL)
        if (items.isEmpty() && task.draft.sources.isNotEmpty()) return fail(task, REASON_CANNOT_READ)
        val pruned = task.draft.pruneMissing(items.mapTo(HashSet()) { it.id })
        val draft = pruned.draft
        if (draft.timeline.segments.isEmpty()) return fail(task, REASON_ALL_MISSING)

        // 空間：門檻是預估大小的兩倍
        val need = requiredFreeBytes(draft.timeline.durationMs)
        val free = freeBytes()
        if (free < need) return fail(task, reasonNoSpace(need - free))

        // 輸出
        out.parentFile?.mkdirs()
        repo.setEditTaskProgress(taskId, EditTaskState.RENDERING, 0, now())
        notifier.progress(task.copy(state = EditTaskState.RENDERING, progress = 0), 0)

        // 取消時（含輸出完成後、存相簿途中）都要把 app 裡的成品刪掉：取消導出只會刪任務、不會刪 edits/{id}/，
        // 留著就是一支看不到也永遠清不掉的完整成品。唯一的例外是「停在輸出完成」已經寫進去之後，那份成品是有效的。
        var kept = false
        try {
            if (!export(task, draft, out)) {
                out.delete()
                return fail(task, REASON_EXPORT_FAILED)
            }

            // 輸出途中任務可能被刪掉：重讀，沒了就收掉成品、不通知
            val current = repo.editTask(taskId).first()
            if (current == null) {
                out.delete()
                return
            }

            if (current.autoUpload) {
                repo.setEditTaskRendered(taskId, pruned.removedSources, null, now())
                kept = true
            } else {
                val uri = saver.saveVideo(out, current.title)
                out.delete() // 存進相簿就不留 app 裡那份；沒存成的重試也是從頭輸出
                if (uri == null) return fail(task, REASON_EXPORT_FAILED)
                repo.setEditTaskRendered(taskId, pruned.removedSources, uri, now())
            }
        } catch (e: CancellationException) {
            if (!kept) out.delete()
            throw e
        } catch (e: Exception) {
            // 沒預期的錯（例如存相簿或資料庫丟例外）：記下來、刪半成品、任務標失敗並通知，不讓排隊的工作跟著壞掉
            Log.w(TAG, "輸出任務 $taskId 發生沒預期的錯", e)
            if (!kept) out.delete()
            return fail(task, REASON_EXPORT_FAILED)
        }
        val done = repo.editTask(taskId).first() ?: return // 存相簿途中又被刪掉
        notifier.finished(done)
    }

    /**
     * 呼叫 [exporter]，進度有變才寫資料庫與通知。成功回 true，輸出失敗回 false；取消往外丟。
     * 進度的寫入在另一個 coroutine 排隊做（[ClipExporter] 的回呼不是 suspend），
     * 回來之前一定寫完，之後的「輸出完成」或「失敗」才不會被遲到的進度蓋掉。
     */
    private suspend fun export(task: EditTask, draft: ClipDraft, out: File): Boolean =
        coroutineScope {
            val queue = Channel<Int>(Channel.UNLIMITED)
            val writer = launch {
                for (percent in queue) {
                    repo.setEditTaskProgress(task.id, EditTaskState.RENDERING, percent, now())
                    notifier.progress(task.copy(state = EditTaskState.RENDERING, progress = percent), percent)
                }
            }
            val last = AtomicInteger(0) // 開始時已寫過 0%
            try {
                exporter.export(draft, out) { percent ->
                    if (last.getAndSet(percent) != percent) queue.trySend(percent)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ClipExportException 的訊息是 Media3 的英文，不給使用者看（只記 log）；其他意外一律當輸出失敗
                Log.w(TAG, "輸出任務 ${task.id} 失敗", e)
                false
            } finally {
                queue.close()
                if (writer.isActive) writer.join()
            }
        }

    /** 任務失敗：寫入原因、通知。任務已被刪掉就不通知。 */
    private suspend fun fail(task: EditTask, reason: String) {
        repo.setEditTaskFailed(task.id, reason, now())
        val failed = repo.editTask(task.id).first() ?: return
        notifier.failed(failed, reason)
    }

    private companion object {
        const val OUTPUT_NAME = "out.mp4"
        const val TAG = "VsClipExport"
    }
}
