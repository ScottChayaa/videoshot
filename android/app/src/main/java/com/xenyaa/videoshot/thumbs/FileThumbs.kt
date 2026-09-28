package com.xenyaa.videoshot.thumbs

import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @param root 縮圖根目錄，正式環境是 `filesDir/thumbs`（**不可用 cacheDir**，規格第四節）
 * @param loadManualImage 讀 shot_image 的 BLOB。傳函式而不是整個 repo，
 *        是為了讓 thumbs 只依賴它真正需要的那一件事，測試也不必準備資料庫。
 * @param isLost 這一格是否已經被回填作業判定為 `lost`（規格第十一節）。預留圖跟封面圖
 *        兩者都是「檔案還不存在」，差別在於前者已經確定抓不回來、後者還在排隊——
 *        沒有這個查詢的話 UI 沒辦法分辨兩種情況都會落到 [ThumbSource.Cover]。
 */
class FileThumbs(
    private val root: File,
    private val io: CoroutineDispatcher,
    private val loadManualImage: suspend (Long) -> ByteArray?,
    private val isLost: suspend (ThumbKey) -> Boolean = { false },
) : Thumbs {

    override fun fileOf(key: ThumbKey): File = File(root, key.relativePath())

    override fun exists(key: ThumbKey): Boolean = fileOf(key).exists()

    override suspend fun thumbFor(shot: ShotRow): ThumbSource = withContext(io) {
        if (shot.source == "manual") {
            // 手動圖無法從 YouTube 重建，不見了就是不見了 —— 退回封面圖會誤導使用者
            return@withContext loadManualImage(shot.id)
                ?.let { ThumbSource.Bytes(it) }
                ?: ThumbSource.Placeholder
        }
        val level = shot.sbLevel
        val frameIndex = shot.frameIndex
        if (level == null || frameIndex == null) {
            // storyboard 來源卻缺了推導路徑必要的欄位 —— 資料不一致，但不該當機
            return@withContext ThumbSource.Placeholder
        }
        val key = ThumbKey(shot.videoId, level, frameIndex)
        val file = fileOf(key)
        when {
            file.exists() -> ThumbSource.LocalFile(file)
            isLost(key) -> ThumbSource.Placeholder
            else -> ThumbSource.Cover(shot.videoId)
        }
    }

    override suspend fun delete(key: ThumbKey): Unit = withContext(io) {
        fileOf(key).delete()
        Unit
    }

    override suspend fun deleteVideo(videoId: String): Unit = withContext(io) {
        File(root, videoId).deleteRecursively()
        Unit
    }
}
