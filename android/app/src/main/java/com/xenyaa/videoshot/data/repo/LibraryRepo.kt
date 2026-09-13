package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow

/**
 * library.db 的唯一對外入口（規格第三節模組邊界第 1 條）。
 * 這個套件以外不得出現任何 DAO 或 RoomDatabase 的引用。
 */
interface LibraryRepo {
    suspend fun homeFeed(after: ShotCursor?, limit: Int): Page<ShotRow>
    suspend fun monthCounts(): List<MonthCount>
    suspend fun shotsOfVideo(videoId: String): List<ShotRow>
    suspend fun shotById(id: Long): ShotRow?

    /**
     * 取圖精靈完成入庫。整批在同一個交易裡：影片列 upsert、每張 shot、手動圖的 webp。
     * 任何一張失敗（例如撞到已收藏的格子）就整批回滾，不留半套。回傳新建的 shot id（順序同輸入）。
     */
    suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long>

    /** 批次套用圖資。patch 裡為 null 的欄位代表沒動過，不覆蓋。 */
    suspend fun patchShots(ids: List<Long>, patch: ShotPatch)

    /** 刪單張。該影片最後一張被刪掉時順帶刪 video 列（規格第六節）。 */
    suspend fun deleteShot(id: Long)

    /** 刪整支收藏。 */
    suspend fun deleteVideo(videoId: String)

    /** 建資料夾。同層不重名、深度上限 5、名稱上限 50 —— 違反時丟 IllegalArgumentException。 */
    suspend fun createFolder(parentId: Long?, name: String): Long

    /** 手動補圖的 WebP 位元組；不是手動圖或圖不見了就回 null。 */
    suspend fun shotImage(shotId: Long): ByteArray?

    /** 最近取過圖的影片，附各片的收藏張數。 */
    suspend fun recentVideos(limit: Int): List<RecentVideo>

    /** 這支影片已經收藏的 storyboard 格號 —— 第二步據此標示鎖定格。 */
    suspend fun takenFrameIndexes(videoId: String): Set<Int>
}
