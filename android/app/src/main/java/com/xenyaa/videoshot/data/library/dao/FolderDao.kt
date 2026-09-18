package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long

    /**
     * 加入資料夾。**重複加入直接忽略**——勾選是冪等的，第二次勾不該把「什麼時候加進來的」洗掉
     * （那會讓資料夾內的排序莫名其妙跳動）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: ShotFolderEntity)

    @Query("DELETE FROM shot_folder WHERE shot_id = :shotId AND folder_id = :folderId")
    suspend fun unlink(shotId: Long, folderId: Long)

    @Query("SELECT folder_id FROM shot_folder WHERE shot_id = :shotId")
    suspend fun folderIdsOf(shotId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM shot_folder WHERE folder_id = :folderId")
    suspend fun shotCountIn(folderId: Long): Int

    /** 本層的第一頁：新加入在前。走 index_shot_folder_folder_id_added_at。 */
    @Query(
        """
        SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, s.place, s.description
        FROM shot_folder sf JOIN shot s ON s.id = sf.shot_id
        WHERE sf.folder_id = :folderId
        ORDER BY sf.added_at DESC, sf.shot_id DESC LIMIT :limit
        """
    )
    suspend fun shotsFirst(folderId: Long, limit: Int): List<ShotRowProjection>

    /** 接續頁：（added_at, shot_id）嚴格小於游標。同一秒加入的多張圖靠 shot_id 分先後。 */
    @Query(
        """
        SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, s.place, s.description
        FROM shot_folder sf JOIN shot s ON s.id = sf.shot_id
        WHERE sf.folder_id = :folderId
          AND (sf.added_at < :addedAt OR (sf.added_at = :addedAt AND sf.shot_id < :shotId))
        ORDER BY sf.added_at DESC, sf.shot_id DESC LIMIT :limit
        """
    )
    suspend fun shotsAfter(folderId: Long, addedAt: Long, shotId: Long, limit: Int): List<ShotRowProjection>

    /** 分頁游標要的 added_at ——投影裡沒有這一欄（那是 shot 的欄位以外的東西）。 */
    @Query("SELECT added_at FROM shot_folder WHERE folder_id = :folderId AND shot_id = :shotId")
    suspend fun addedAtOf(folderId: Long, shotId: Long): Long?

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM folder")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM shot_folder WHERE shot_id = :shotId")
    suspend fun linkCountOfShot(shotId: Long): Int

    @Query("SELECT COUNT(*) FROM folder WHERE name = :name AND parent_id IS :parentId")
    suspend fun countSameNameInLayer(parentId: Long?, name: String): Int

    @Query("SELECT parent_id FROM folder WHERE id = :id")
    suspend fun parentOf(id: Long): Long?

    @Query("UPDATE folder SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** 同層重名檢查，但**排除自己**——改名對話框沒改名字就按儲存時不該被自己擋下來。 */
    @Query("SELECT COUNT(*) FROM folder WHERE name = :name AND parent_id IS :parentId AND id != :exceptId")
    suspend fun countSameNameInLayerExcept(parentId: Long?, name: String, exceptId: Long): Int

    /**
     * 整棵樹，每個節點帶著層數（根層＝1，與 `createFolder` 的深度檢查同一個基準）。
     * 一次撈完：樹是幾十個節點的事，逐層查會變成畫面每展開一層就打一次 DB。
     */
    @Query(
        """
        WITH RECURSIVE walk(id, parent_id, name, depth) AS (
            SELECT id, parent_id, name, 1 FROM folder WHERE parent_id IS NULL
            UNION ALL
            SELECT f.id, f.parent_id, f.name, w.depth + 1
            FROM folder f JOIN walk w ON f.parent_id = w.id
        )
        SELECT id, parent_id, name, depth FROM walk
        """
    )
    suspend fun tree(): List<FolderNodeProjection>

    @Query(
        """
        WITH RECURSIVE walk(id, parent_id, name, depth) AS (
            SELECT id, parent_id, name, 1 FROM folder WHERE parent_id IS NULL
            UNION ALL
            SELECT f.id, f.parent_id, f.name, w.depth + 1
            FROM folder f JOIN walk w ON f.parent_id = w.id
        )
        SELECT id, parent_id, name, depth FROM walk WHERE id = :id
        """
    )
    suspend fun nodeById(id: Long): FolderNodeProjection?

    /**
     * 某一層的卡片：資料夾本身 ＋ 含子孫的張數 ＋ 含子孫最近加入時間。
     *
     * `tree` 把每個子孫對應回它所屬的那張卡片；`links` 先 `GROUP BY card_id, shot_id`，
     * 同一張圖同時在父與子才只算一次（規格：計數含子孫層，但圖只有一張）。
     */
    @Query(
        """
        WITH RECURSIVE tree(card_id, id) AS (
            SELECT id, id FROM folder WHERE parent_id IS :parentId
            UNION ALL
            SELECT t.card_id, f.id FROM folder f JOIN tree t ON f.parent_id = t.id
        ),
        links AS (
            SELECT t.card_id AS card_id, sf.shot_id AS shot_id, MAX(sf.added_at) AS added_at
            FROM shot_folder sf JOIN tree t ON t.id = sf.folder_id
            GROUP BY t.card_id, sf.shot_id
        )
        SELECT c.id AS id, c.name AS name, c.created_at AS created_at,
               (SELECT COUNT(*) FROM links l WHERE l.card_id = c.id) AS shot_count,
               (SELECT MAX(l.added_at) FROM links l WHERE l.card_id = c.id) AS last_added_at
        FROM folder c WHERE c.parent_id IS :parentId
        """
    )
    suspend fun cardsIn(parentId: Long?): List<FolderCardProjection>

    /**
     * 上一個查詢那幾張卡片的預覽圖，每張卡最多 4 張、最近加入在前。
     *
     * 用 window function 一次撈完（`sqlite-bundled` 帶的是新版 SQLite，不看 Android 版本），
     * 不是每張卡各查一次 —— 規格第六節：「本機 SQLite 下，預覽拼貼與含子孫計數都是單次查詢的事」。
     */
    @Query(
        """
        WITH RECURSIVE tree(card_id, id) AS (
            SELECT id, id FROM folder WHERE parent_id IS :parentId
            UNION ALL
            SELECT t.card_id, f.id FROM folder f JOIN tree t ON f.parent_id = t.id
        ),
        links AS (
            SELECT t.card_id AS card_id, sf.shot_id AS shot_id, MAX(sf.added_at) AS added_at
            FROM shot_folder sf JOIN tree t ON t.id = sf.folder_id
            GROUP BY t.card_id, sf.shot_id
        ),
        ranked AS (
            SELECT l.card_id AS card_id, l.shot_id AS shot_id,
                   ROW_NUMBER() OVER (
                       PARTITION BY l.card_id ORDER BY l.added_at DESC, l.shot_id DESC
                   ) AS rn
            FROM links l
        )
        SELECT r.card_id AS card_id, r.rn AS rn,
               s.id AS id, s.video_id AS video_id, s.at_sec AS at_sec, s.source AS source,
               s.frame_index AS frame_index, s.sb_level AS sb_level, s.event_date AS event_date,
               s.place AS place, s.description AS description
        FROM ranked r JOIN shot s ON s.id = r.shot_id
        WHERE r.rn <= 4
        ORDER BY r.card_id, r.rn
        """
    )
    suspend fun previewsIn(parentId: Long?): List<FolderPreviewProjection>
}

data class FolderCardProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "shot_count") val shotCount: Int,
    /** 一張圖都沒有時是 null；repo 會退回 `createdAt`。 */
    @ColumnInfo(name = "last_added_at") val lastAddedAt: Long?,
)

data class FolderPreviewProjection(
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "rn") val rank: Int,
    @Embedded val shot: ShotRowProjection,
)

data class FolderNodeProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "depth") val depth: Int,
)
