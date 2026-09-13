package com.xenyaa.videoshot.data.library.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification

@Dao
interface SearchDao {
    /**
     * ≥ 3 個字元的關鍵字走 FTS5（規格第八節）。
     * shot_fts 是 callback 用原生 SQL 建的虛擬表，Room 的編譯期驗證看不到它，只能跳過這一個方法的驗證。
     */
    @SkipQueryVerification
    @Query("SELECT rowid FROM shot_fts WHERE shot_fts MATCH :query ORDER BY rowid")
    suspend fun matchIds(query: String): List<Long>

    /** < 3 個字元的關鍵字 trigram 一律落空，退回全表掃描；本機一萬筆為毫秒級。 */
    @Query(
        """
        SELECT id FROM shot
        WHERE description LIKE '%' || :keyword || '%' OR place LIKE '%' || :keyword || '%'
        ORDER BY id
        """
    )
    suspend fun likeIds(keyword: String): List<Long>
}
