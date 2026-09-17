package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.core.home.MonthGroup
import com.xenyaa.videoshot.core.home.groupByMonth
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow

/**
 * 首頁的全部狀態。
 *
 * @param items 目前**已載入**的清單（keyset 一次 50 筆）。Lightbox 左右滑動的範圍就是它
 * @param total 符合目前篩選的**總**張數，來自 SQL 的 COUNT —— Lightbox 的「共 M 張」
 * @param upToMonth `YYYY-MM`；null＝沒有時間篩選（手冊 §二第三條：預設看得到最新的資料）
 * @param months 月份選擇器的選項（全部月份與張數，不受篩選影響）
 * @param facets 每個月的標籤列，key 是 `YYYY-MM`；捲到才去查，查過就留著
 * @param error 上一次讀取失敗的訊息；null＝沒有錯誤。**不是例外物件** —— 這份狀態要能被
 *        純函式比較與測試，例外物件沒有結構相等
 */
data class HomeState(
    val items: List<ShotRow> = emptyList(),
    val total: Int = 0,
    val cursor: ShotCursor? = null,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val upToMonth: String? = null,
    val months: List<MonthCount> = emptyList(),
    val facets: Map<String, List<MonthFacet>> = emptyMap(),
    val error: String? = null,
)

/** 首頁的純狀態轉換。**沒有任何 suspend、沒有 Android 相依** —— 分頁邏輯要能單獨測。 */
object HomeStore {

    fun canLoadMore(state: HomeState): Boolean = !state.loading && !state.endReached

    fun startLoading(state: HomeState): HomeState = state.copy(loading = true)

    fun appendPage(state: HomeState, page: Page<ShotRow>, total: Int): HomeState = state.copy(
        items = state.items + page.items,
        cursor = page.next,
        endReached = page.next == null,
        loading = false,
        total = total,
        error = null,
    )

    /** 換篩選或重新整理：清單與游標一起作廢，否則新條件會接著舊游標往下撈。 */
    fun reset(state: HomeState, upToMonth: String?): HomeState = state.copy(
        items = emptyList(),
        cursor = null,
        endReached = false,
        loading = false,
        upToMonth = upToMonth,
        facets = emptyMap(),
        error = null,
    )

    fun removeShot(state: HomeState, id: Long): HomeState {
        if (state.items.none { it.id == id }) return state
        return state.copy(
            items = state.items.filterNot { it.id == id },
            // 總數一起減，否則 Lightbox 的「共 M 張」會停在舊數字
            total = (state.total - 1).coerceAtLeast(0),
        )
    }

    /**
     * 就地編輯之後換掉一張。**改了日期要重新排序** ——
     * 那張圖會換到另一個月份底下（原型的「已移到 2026年1月」）。
     */
    fun replace(state: HomeState, row: ShotRow): HomeState {
        if (state.items.none { it.id == row.id }) return state
        val updated = state.items.map { if (it.id == row.id) row else it }
            .sortedWith(compareByDescending<ShotRow> { it.eventDate }.thenByDescending { it.id })
        return state.copy(items = updated)
    }

    fun groups(state: HomeState): List<MonthGroup<ShotRow>> =
        groupByMonth(state.items) { monthOf(it.eventDate) }

    /**
     * 縮圖牆實際的排列順序（月份標題、標籤列、縮圖交錯在同一個格線裡）。
     *
     * 把排列算成一份資料而不是寫在 composable 的迴圈裡，是為了
     * **「捲到某個月份」有唯一的答案** —— 取圖完成要導回首頁並捲到新圖那個月（手冊 §四第三步最後一條），
     * 如果畫面一套排法、捲動另外算一次，兩邊遲早會對不上。
     */
    fun slots(state: HomeState): List<HomeSlot> {
        val out = mutableListOf<HomeSlot>()
        var index = 0
        for (group in groups(state)) {
            out += HomeSlot.Header(group.month, group.label)
            if (!state.facets[group.month].isNullOrEmpty()) out += HomeSlot.Facets(group.month)
            for (shot in group.items) {
                out += HomeSlot.Tile(index, shot)
                index++
            }
        }
        return out
    }

    /** 某個月的標題在格線裡的位置；該月不在已載入的範圍內就回 null。 */
    fun headerIndexOf(slots: List<HomeSlot>, month: String): Int? =
        slots.indexOfFirst { it is HomeSlot.Header && it.month == month }.takeIf { it >= 0 }
}

/** 縮圖牆格線裡的一格。 */
sealed interface HomeSlot {
    val key: String

    data class Header(val month: String, val label: String) : HomeSlot {
        override val key: String get() = "h-$month"
    }

    data class Facets(val month: String) : HomeSlot {
        override val key: String get() = "f-$month"
    }

    /** @param index 這張圖在 [HomeState.items] 裡的位置 —— Lightbox 從這個位置開始 */
    data class Tile(val index: Int, val shot: ShotRow) : HomeSlot {
        override val key: String get() = "t-${shot.id}"
    }
}
