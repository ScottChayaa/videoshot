package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.MonthCount
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
}
