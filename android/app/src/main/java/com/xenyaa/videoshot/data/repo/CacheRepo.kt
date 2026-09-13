package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity

/**
 * cache.db 的唯一對外入口（規格第三節模組邊界第 1 條）。
 * 這裡面的東西**都能重建**，所以不進備份；還原備份時整個清掉（`clearAll`）。
 */
interface CacheRepo {
    suspend fun putThumbStates(states: List<ThumbStateEntity>)
    suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity?
    /** 回填作業要處理的：還缺、而且已經到重試時間的。 */
    suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity>
    suspend fun forgetVideoThumbs(videoId: String)

    suspend fun saveDraft(draft: DraftEntity)
    suspend fun currentDraft(): DraftEntity?
    suspend fun clearDraft()

    /** 還原備份之後呼叫：裝置本地狀態全部作廢，回填作業會重新掃一遍。 */
    suspend fun clearAll()
}
