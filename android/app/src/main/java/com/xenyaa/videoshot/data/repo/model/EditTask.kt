package com.xenyaa.videoshot.data.repo.model

import com.xenyaa.videoshot.core.timeline.ClipDraft

/** 剪輯輸出任務的狀態；資料庫裡存小寫（`queued`／`rendering`／`rendered`／`failed`）。 */
enum class EditTaskState {
    QUEUED, RENDERING, RENDERED, FAILED;

    val dbValue: String get() = name.lowercase()

    companion object {
        /** 不認識的字串（理論上不會有）當成失敗，讓使用者可以重試或刪掉。 */
        fun fromDb(value: String): EditTaskState = entries.firstOrNull { it.dbValue == value } ?: FAILED
    }
}

/**
 * 一個背景輸出任務（cache.db 的 `edit_task`）。
 * [draft] 是按下【開始】當下的剪輯草稿，復原與重做紀錄已經清掉、只留目前的時間軸。
 * [galleryUri] 有值代表輸出後存進了相簿（只會出現在 [EditTaskState.RENDERED]）。
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
) {
    val durationMs: Long get() = draft.timeline.durationMs
}
