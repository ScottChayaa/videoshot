package com.xenyaa.videoshot.media

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** 手機裡的照片與影片清單。畫面與 ViewModel 只碰這個介面，不直接碰 `ContentResolver`。 */
interface MediaLibrary {
    /**
     * 依 [filter] 列出素材，已排好序（新的在前）。
     * 沒有權限時回空清單，不丟例外。
     */
    suspend fun list(filter: MediaFilter): List<MediaItem>
}

/**
 * 以 MediaStore 實作的 [MediaLibrary]，只讀。
 *
 * 分別查照片表與影片表、不查 `Files` 表：`DATE_TAKEN` 在 API 29 以下只存在這兩張表。
 * 部分存取（Android 14）時系統只讓查詢看見使用者選的那些，這裡不需要另外處理。
 */
class MediaStoreLibrary(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
) : MediaLibrary {

    override suspend fun list(filter: MediaFilter): List<MediaItem> = withContext(io) {
        val photos = if (filter != MediaFilter.VIDEO) query(MediaKind.PHOTO) else emptyList()
        val videos = if (filter != MediaFilter.PHOTO) query(MediaKind.VIDEO) else emptyList()
        sortNewestFirst(photos + videos)
    }

    private fun query(kind: MediaKind): List<MediaItem> {
        val isVideo = kind == MediaKind.VIDEO
        val table = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // DATE_TAKEN 在 MediaColumns 上是 API 29 才有；兩張表各自的 ImageColumns／VideoColumns 一直都有
        val takenName = if (isVideo) MediaStore.Video.VideoColumns.DATE_TAKEN else MediaStore.Images.ImageColumns.DATE_TAKEN
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(takenName)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            // 影片表在 API 29 以下沒有 ORIENTATION 欄；影片的旋轉讀 0
            if (!isVideo) add(MediaStore.Images.ImageColumns.ORIENTATION)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val items = mutableListOf<MediaItem>()
        try {
            resolver.query(table, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val takenCol = c.getColumnIndexOrThrow(takenName)
                val modifiedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val orientationCol = if (isVideo) -1 else c.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION)
                val durationCol = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    items += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(table, id).toString(),
                        kind = kind,
                        takenAtMs = takenAtOf(
                            dateTakenMs = if (c.isNull(takenCol)) null else c.getLong(takenCol),
                            dateModifiedSec = c.getLong(modifiedCol),
                        ),
                        durationMs = if (isVideo) c.getLong(durationCol).coerceAtLeast(0L) else null,
                        width = c.getInt(widthCol),
                        height = c.getInt(heightCol),
                        rotation = if (orientationCol >= 0 && !c.isNull(orientationCol)) c.getInt(orientationCol) else 0,
                    )
                }
            }
        } catch (_: SecurityException) {
            // 沒有權限：回空清單，由畫面顯示「需要相片和影片的存取權」
            return emptyList()
        }
        return items
    }
}
