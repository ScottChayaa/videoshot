package com.xenyaa.videoshot.data.repo.model

import com.xenyaa.videoshot.core.timeline.ClipDraft

/**
 * 剪輯輸出任務的狀態；資料庫裡存小寫（`queued`／`rendering`／`rendered`／`uploading`／`waiting_quota`／`processing`／`done`／`failed`）。
 * [UPLOADING] 是上傳到 YouTube 中，[WAITING_QUOTA] 是撞到每日上傳上限、等隔天重置後接著傳（不算失敗），
 * [PROCESSING] 是已上傳、等 YouTube 產生縮圖，[DONE] 是等到縮圖了。
 */
enum class EditTaskState {
    QUEUED, RENDERING, RENDERED, UPLOADING, WAITING_QUOTA, PROCESSING, DONE, FAILED;

    val dbValue: String get() = name.lowercase()

    companion object {
        /** 不認識的字串（理論上不會有）當成失敗，讓使用者可以重試或刪掉。 */
        fun fromDb(value: String): EditTaskState = entries.firstOrNull { it.dbValue == value } ?: FAILED
    }
}

/** 任務失敗在哪一步；資料庫裡存小寫（`render`／`upload`／`storyboard`），【重試】依它決定回到哪個狀態。 */
enum class EditTaskStep {
    RENDER, UPLOAD, STORYBOARD;

    val dbValue: String get() = name.lowercase()

    companion object {
        /** 不認識的字串（理論上不會有）當成輸出，重試時回到等待輸出。 */
        fun fromDb(value: String): EditTaskStep = entries.firstOrNull { it.dbValue == value } ?: RENDER
    }
}

/**
 * 一個背景輸出任務（cache.db 的 `edit_task`）。
 * [draft] 是按下【開始】當下的剪輯草稿，復原與重做紀錄已經清掉、只留目前的時間軸。
 * [galleryUri] 有值代表輸出後存進了相簿（只會出現在 [EditTaskState.RENDERED]）。
 * [failedStep] 只在 [EditTaskState.FAILED] 有意義（舊資料沒記就是 null，當成輸出）；
 * [uploadUrl] 是 YouTube 可續傳上傳的工作階段網址（上傳中才有）；[videoId] 是上傳完成後 YouTube 給的影片編號。
 */
data class EditTask(
    val id: Long,
    val state: EditTaskState,
    /** 0～100 */
    val progress: Int,
    val title: String,
    val draft: ClipDraft,
    val autoUpload: Boolean,
    val failReason: String?,
    /** 輸出時已不存在而略過的素材個數 */
    val skippedSources: Int,
    val galleryUri: String?,
    /** Unix 毫秒 */
    val createdAt: Long,
    val failedStep: EditTaskStep? = null,
    val uploadUrl: String? = null,
    val videoId: String? = null,
    /**
     * Unix 毫秒；[EditTaskState.PROCESSING] 期間不更新（等縮圖的計時從進入處理中那一刻起算），
     * [EditTaskState.WAITING_QUOTA] 期間也不更新（它是撞到上限的時間，重置時間從它算）
     */
    val updatedAt: Long = createdAt,
) {
    val durationMs: Long get() = draft.timeline.durationMs
}
