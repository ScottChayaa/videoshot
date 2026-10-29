package com.xenyaa.videoshot.edit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.xenyaa.videoshot.MainActivity
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState

/** 點通知回到 app 的剪輯頁：MainActivity 看到這個 extra 就切到剪輯（畫面端的處理是之後的任務）。 */
const val EXTRA_OPEN_CLIP = "com.xenyaa.videoshot.OPEN_CLIP"

/** 前景服務的進度通知。同一時間只有一支在輸出，所以固定一個編號；內容隨進度更新。 */
const val NOTIFY_PROGRESS_ID = 4300

/** 結果通知（完成、存相簿、失敗）。每個任務一個編號；輸出工作結束後前景通知會被拿掉，這個留著。 */
fun resultNotificationId(taskId: Long): Int = 4400 + taskId.toInt()

const val CHANNEL_PROGRESS = "clip_export_progress"
const val CHANNEL_RESULT = "clip_export_result"

/**
 * 前景服務類型：Android 15 起 mediaProcessing（[uploads]＝這個工作還要上傳時再加 dataSync）、
 * 10～14 dataSync、9 以下沒有類型。
 */
fun foregroundServiceTypeFor(sdkInt: Int, uploads: Boolean): Int? = when {
    sdkInt >= 35 && uploads ->
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    sdkInt >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
    sdkInt >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    else -> null
}

/**
 * 輸出的通知：進度通知兼前景服務通知（[foregroundInfo]）、完成與失敗的結果通知。
 * 沒有通知權限時 `notify` 會被系統忽略，輸出照跑。
 */
class AndroidClipExportNotifier(context: Context) : ClipExportNotifier {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    init {
        // 建頻道是冪等的；放在建構時，之後任何一則通知都已經有頻道
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "影片輸出進度", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "影片輸出結果", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** 使用者正停在這個任務的導出畫面（null＝沒有）：結果已經在畫面上，不另外跳通知，舊的那則（例如重試前的失敗）也收掉。 */
    @Volatile
    var viewingTaskId: Long? = null
        set(value) {
            field = value
            if (value != null) dismissResult(value)
        }

    /** 收掉這個任務的結果通知（任務被刪掉、或使用者正在看它）。 */
    fun dismissResult(taskId: Long) = manager.cancel(resultNotificationId(taskId))

    /**
     * 輸出工作開始時給 `setForeground` 的資訊；通知與之後的進度更新是同一則（[NOTIFY_PROGRESS_ID]）。
     * 同一個工作輸出完接著上傳，所以有勾自動上傳（或已經在上傳中）就一開始帶上網路傳輸的類型。
     */
    fun foregroundInfo(task: EditTask, sdkInt: Int = Build.VERSION.SDK_INT): ForegroundInfo {
        val notification = progressNotification(task, task.progress)
        val type = foregroundServiceTypeFor(sdkInt, uploads = task.autoUpload || task.state == EditTaskState.UPLOADING)
        return if (type != null) ForegroundInfo(NOTIFY_PROGRESS_ID, notification, type)
        else ForegroundInfo(NOTIFY_PROGRESS_ID, notification)
    }

    override fun progress(task: EditTask, percent: Int) {
        manager.notify(NOTIFY_PROGRESS_ID, progressNotification(task, percent))
    }

    override fun finished(task: EditTask) {
        val saved = task.galleryUri != null
        val title = if (saved) "影片已存到相簿" else "影片輸出完成"
        val text = if (saved) savedNotificationText(task.title, task.skippedSources)
        else doneNotificationText(task.title, task.skippedSources)
        notifyResult(task, title, text)
    }

    override fun done(task: EditTask) {
        notifyResult(task, "已完成，點一下開始取圖", savedNotificationText(task.title, task.skippedSources))
    }

    override fun failed(task: EditTask, reason: String) {
        notifyResult(task, failedTitle(task.failedStep), reason)
    }

    private fun progressNotification(task: EditTask, percent: Int) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (task.state == EditTaskState.UPLOADING) "上傳中 $percent%" else "輸出影片中 $percent%")
            .setContentText(task.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(openClipIntent())
            .build()

    private fun notifyResult(task: EditTask, title: String, text: String) {
        if (viewingTaskId == task.id) return dismissResult(task.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openClipIntent())
            .build()
        manager.notify(resultNotificationId(task.id), notification)
    }

    private fun openClipIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CLIP, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
