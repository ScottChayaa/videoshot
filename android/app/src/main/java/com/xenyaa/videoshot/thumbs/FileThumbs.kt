package com.xenyaa.videoshot.thumbs

import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @param root 縮圖根目錄，正式環境是 `filesDir/thumbs`（**不可用 cacheDir**，規格第四節）
 * @param loadManualImage 讀 shot_image 的 BLOB。傳函式而不是整個 repo，
 *        是為了讓 thumbs 只依賴它真正需要的那一件事，測試也不必準備資料庫。
 */
class FileThumbs(
    private val root: File,
    private val io: CoroutineDispatcher,
    private val loadManualImage: suspend (Long) -> ByteArray?,
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
        val file = fileOf(ThumbKey(shot.videoId, level, frameIndex))
        if (file.exists()) ThumbSource.LocalFile(file) else ThumbSource.Cover(shot.videoId)
    }
}
