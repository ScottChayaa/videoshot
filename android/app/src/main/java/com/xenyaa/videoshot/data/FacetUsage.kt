package com.xenyaa.videoshot.data

import com.xenyaa.videoshot.data.library.StatKind
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
import com.xenyaa.videoshot.data.repo.model.FilterOption
import kotlinx.coroutines.CancellationException

/**
 * 首頁篩選抽屜候選的「最近使用」排序（規格第六節「首頁」、第四節 `facet_recent`）。
 *
 * 跟 [ShotDeleter] 一樣是跨兩個 DB 的協調者：候選與名稱轉編號在 library.db、使用時間在 cache.db，
 * 兩邊無法共用交易。使用時間只影響順序，所以**寫入一律盡力而為**：失敗吞掉，不讓篩選、取圖或合併跟著失敗。
 */
class FacetUsage(
    private val library: LibraryRepo,
    private val cache: CacheRepo,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /**
     * 抽屜候選。讀候選失敗照樣往外丟（抽屜有【重試】）；讀不到使用時間就維持張數排序——
     * 順序壞掉不該讓整個抽屜讀不出來。
     */
    suspend fun filterOptions(upToMonth: String?): List<FilterOption> {
        val options = library.filterOptions(upToMonth)
        val recent = bestEffort(emptyMap()) { cache.facetRecent() }
        return orderByRecent(options, recent)
    }

    /** 這次用到的地點與標籤記成現在。名稱要完全相同（[LibraryRepo.facetRefs]），查不到的略過。 */
    suspend fun markUsed(places: Set<String>, tagNames: Set<String>) {
        if (places.isEmpty() && tagNames.isEmpty()) return
        bestEffort(Unit) {
            val refs = library.facetRefs(places, tagNames)
            if (refs.isNotEmpty()) cache.touchFacets(refs, nowSec())
        }
    }

    /** 合併地點或標籤之後呼叫（規格第九節）：目標取兩者中較新的時間。 */
    suspend fun merged(kind: Int, fromId: Long, toId: Long) = bestEffort(Unit) { cache.mergeFacetRecent(kind, fromId, toId) }

    /** 刪除地點或標籤之後呼叫。 */
    suspend fun deleted(kind: Int, id: Long) = bestEffort(Unit) { cache.forgetFacetRecent(kind, id) }

    private suspend fun <T> bestEffort(fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }

    companion object {
        /**
         * 排序（純函式）：用過的在前、依使用時間新到舊；沒用過的接在後面，**維持 [options] 原本的順序**
         * （repo 已依全部時間張數多到少、同張數依名稱排好）。排序是穩定的，同一個時間的也維持原順序。
         * 地點與標籤混在同一份清單裡無妨：抽屜依 isPlace 分兩區，各區內的相對順序就是這裡的順序。
         * `id == 0` 的補位項目（`FilterOption` 的 KDoc）查不到紀錄，當成沒用過。
         */
        fun orderByRecent(options: List<FilterOption>, recent: Map<FacetRef, Long>): List<FilterOption> {
            if (recent.isEmpty()) return options
            fun usedAt(o: FilterOption): Long? =
                if (o.id == 0L) null else recent[FacetRef(if (o.isPlace) StatKind.PLACE else StatKind.TAG, o.id)]
            val (used, unused) = options.partition { usedAt(it) != null }
            return used.sortedByDescending { usedAt(it) } + unused
        }
    }
}
