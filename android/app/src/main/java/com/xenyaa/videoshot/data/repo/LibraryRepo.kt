package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
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
    /**
     * 首頁時間軸。
     * @param upToMonth `YYYY-MM`；非 null 時只回該月（含）以前的收藏（手冊 §二 的可清除狀態列）
     */
    suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String? = null): Page<ShotRow>
    suspend fun monthCounts(): List<MonthCount>
    suspend fun shotsOfVideo(videoId: String): List<ShotRow>
    suspend fun shotById(id: Long): ShotRow?

    /** 符合同一個篩選條件的總張數 —— Lightbox 的「共 M 張」（規格第六節）。 */
    suspend fun shotCount(upToMonth: String? = null): Int

    /** 某個月出現過的地點與標籤，附張數。 */
    suspend fun monthFacets(month: String): List<MonthFacet>

    /** 一張圖的標籤名。就地編輯要把現值帶進抽屜。 */
    suspend fun tagsOfShot(shotId: Long): List<String>

    /**
     * 取圖精靈完成入庫。整批在同一個交易裡：影片列 upsert、每張 shot、手動圖的 webp、
     * **以及標籤（名稱查不到就在交易內新建，`kind` 為 `'other'`）**。
     * 任何一張失敗（例如撞到已收藏的格子）就整批回滾，不留半套。回傳新建的 shot id（順序同輸入）。
     */
    suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long>

    /** 抽屜的既有地點建議（規格第五節欄位表）。 */
    suspend fun distinctPlaces(): List<String>

    /** 抽屜的既有標籤建議。 */
    suspend fun allTagNames(): List<String>

    /** 批次套用圖資。patch 裡為 null 的欄位代表沒動過，不覆蓋。 */
    suspend fun patchShots(ids: List<Long>, patch: ShotPatch)

    /** 刪單張。該影片最後一張被刪掉時順帶刪 video 列（規格第六節）。 */
    suspend fun deleteShot(id: Long)

    /** 刪整支收藏。 */
    suspend fun deleteVideo(videoId: String)

    /** 建資料夾。同層不重名、深度上限 5、名稱上限 50 —— 違反時丟 IllegalArgumentException。 */
    suspend fun createFolder(parentId: Long?, name: String): Long

    /** 改名。同層不重名（不跟自己比）、名稱上限 50 —— 違反時丟 IllegalArgumentException。 */
    suspend fun renameFolder(id: Long, name: String)

    /** 刪資料夾：子資料夾與所有關聯一併刪，**圖不動**（規格第六節）。 */
    suspend fun deleteFolder(id: Long)

    /** 單一資料夾，附層數；不存在回 null。 */
    suspend fun folderNode(id: Long): FolderNode?

    /** 整棵樹（【加入分類】的 sheet 要一次畫完）。順序未定義，畫面自己排。 */
    suspend fun folderTree(): List<FolderNode>

    /**
     * 某一層的資料夾卡片（含子孫張數、最近加入時間、最多 4 張預覽）。
     * `parentId = null` 是根層 —— 清單頁要的就是它；資料夾頁上半用該資料夾的 id。
     *
     * **沒有排序也沒有篩選**：兩者都在 `:core` 的 `FolderSort` 與畫面的 Store 做（見階段 8 計畫 Task 2）。
     */
    suspend fun folderCards(parentId: Long?): List<FolderCard>

    /** 手動補圖的 WebP 位元組；不是手動圖或圖不見了就回 null。 */
    suspend fun shotImage(shotId: Long): ByteArray?

    /** 最近取過圖的影片，附各片的收藏張數。 */
    suspend fun recentVideos(limit: Int): List<RecentVideo>

    /**
     * 這支影片在**某個 storyboard 層級**已經收藏的格號 —— 第二步據此標示鎖定格。
     *
     * 層級是查詢條件的一部分：frameIndex 只在該層級之內有意義
     * （規格第四節把縮圖鍵為 `{videoId}/L{level}/{frameIndex}`）。先前在 L2 取過圖、
     * 這次解析到 L3 的話，兩邊的格號互不相干，不能混在一起。
     */
    suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int>

    /** 資料夾**本層**的圖,新加入在前,keyset 分頁。 */
    suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage

    /** 資料夾本層的總張數 ——從資料夾頁開 Lightbox 時的「共 M 張」。 */
    suspend fun folderShotCount(folderId: Long): Int

    /** 這張圖在哪些資料夾(【加入分類】的勾勾)。 */
    suspend fun foldersOf(shotId: Long): Set<Long>

    /** 加入資料夾。已經在裡面的話什麼都不做。@param atSec 只有測試會指定 */
    suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long = System.currentTimeMillis() / 1000)

    /** 移出資料夾。圖本身不動。 */
    suspend fun removeShotFromFolder(shotId: Long, folderId: Long)
}
