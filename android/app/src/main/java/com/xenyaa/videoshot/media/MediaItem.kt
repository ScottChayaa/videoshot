package com.xenyaa.videoshot.media

/** 本機素材的種類：剪輯只收照片與影片。 */
enum class MediaKind { PHOTO, VIDEO }

/** 選素材畫面上方的分頁：全部／影片／照片。 */
enum class MediaFilter { ALL, VIDEO, PHOTO }

/**
 * 手機裡的一個照片或影片素材（來自 MediaStore）。
 *
 * [uri] 存字串（`content://…`）而不是 `android.net.Uri`：這個型別要讓純 JVM 測試直接建構，
 * 排序與篩選的邏輯也就不必依賴 Android。
 * [id] 是 MediaStore `files` 表的 `_ID`——照片與影片同在一張表，不會撞號。
 * [durationMs] 只有影片有值，照片是 null。
 * [rotation] 是順時針旋轉角度（0／90／180／270）。
 */
data class MediaItem(
    val id: Long,
    val uri: String,
    val kind: MediaKind,
    val takenAtMs: Long,
    val durationMs: Long?,
    val width: Int,
    val height: Int,
    val rotation: Int,
)
