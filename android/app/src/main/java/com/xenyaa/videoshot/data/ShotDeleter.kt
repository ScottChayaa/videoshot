package com.xenyaa.videoshot.data

import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 刪一張收藏的**全部痕跡**（規格第十三節案例 23）。
 *
 * 為什麼是獨立的協調者而不是 `LibraryRepo.deleteShot` 多做幾件事：
 * 一張圖的痕跡橫跨三個模組（`library.db`、`thumbs/` 的檔案、`cache.db` 的 `thumb_state`），
 * 而「repo 只管 library.db」是規格第三節訂的邊界。repo 去碰檔案系統，
 * 之後備份（階段 12）與回填（階段 13）就再也說不清楚誰擁有那些檔案。
 *
 * **順序是刻意的**：先讀出要刪的鍵 → 刪 DB → 刪檔 → 清 thumb_state。
 * DB 是唯一無法重建的那一份，先讓它成功；檔案或 cache 清失敗只是留下垃圾，圖庫仍然一致。
 */
class ShotDeleter(
    private val library: LibraryRepo,
    private val thumbs: Thumbs,
    private val cache: CacheRepo,
    private val io: CoroutineDispatcher,
) {
    suspend fun delete(shotId: Long): Unit = withContext(io) {
        val shot = library.shotById(shotId) ?: return@withContext
        val level = shot.sbLevel
        val frameIndex = shot.frameIndex

        // shot_tag／shot_folder／shot_image 由外鍵連動，shot_fts 由觸發器連動；
        // 最後一張時 repo 會順帶刪掉 video 列（規格第六節）
        library.deleteShot(shotId)

        // DB 已經改完、唯一無法重建的那份已經成功。這段之後只是清檔案與 cache ——
        // 失敗只留下垃圾（孤兒 webp、多餘的 thumb_state），圖庫本身仍然一致，
        // 不該讓這裡的例外往上炸、把已經成功的 DB 刪除也搞得像失敗了一樣。
        runCatching {
            val videoGone = library.shotsOfVideo(shot.videoId).isEmpty()
            if (videoGone) {
                // 整支都沒了：連目錄一起刪，別留下一堆沒有任何圖引用的 webp
                thumbs.deleteVideo(shot.videoId)
                cache.forgetVideoThumbs(shot.videoId)
            } else if (level != null && frameIndex != null) {
                thumbs.delete(ThumbKey(shot.videoId, level, frameIndex))
                cache.forgetThumb(shot.videoId, level, frameIndex)
            }
        }
    }
}
