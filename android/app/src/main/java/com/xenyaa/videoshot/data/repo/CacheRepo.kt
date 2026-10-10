package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.FacetRef

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
    /** 刪掉一格的回填狀態。不清的話，回填作業會把已經刪掉的那一格又抓回來。 */
    suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int)

    /** 回填進度／「無法取回 N 張」用的計數。@param state 'ok' | 'missing' | 'lost' */
    suspend fun countByState(state: String): Int

    /** 全部 lost 的格子——帳號頁【刪除這些收藏】要靠這份清單反查是哪幾張 shot。 */
    suspend fun lostThumbs(): List<ThumbStateEntity>

    /** 【稍後重試】：全部 lost 的格子重設成 missing，立刻可以重試。 */
    suspend fun resetLostToMissing(now: Long)

    suspend fun saveDraft(draft: DraftEntity)
    suspend fun currentDraft(): DraftEntity?
    suspend fun clearDraft()

    /** 把這幾個地點或標籤的最近使用時間記成 [usedAt]（Unix 秒）；已有的覆蓋（規格第六節「首頁」）。 */
    suspend fun touchFacets(refs: Collection<FacetRef>, usedAt: Long)

    /** 全部的最近使用時間。 */
    suspend fun facetRecent(): Map<FacetRef, Long>

    /** 合併地點或標籤：目標取兩者中較新的時間，來源那一列刪掉（規格第九節）。來源沒有紀錄就什麼都不動。 */
    suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long)

    /** 刪除地點或標籤時連同使用時間刪掉。 */
    suspend fun forgetFacetRecent(kind: Int, id: Long)

    /** 還原備份之後呼叫：裝置本地狀態全部作廢（回填作業會重新掃一遍缺圖；還原後地點與標籤的編號對不上，最近使用時間也不能沿用）。 */
    suspend fun clearAll()
}
