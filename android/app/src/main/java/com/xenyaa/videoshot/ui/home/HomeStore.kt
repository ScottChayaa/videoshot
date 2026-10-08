package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.core.home.MonthGroup
import com.xenyaa.videoshot.core.home.groupByMonth
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow

/**
 * 首頁的全部狀態。
 *
 * @param items 目前**已載入**的清單（keyset 一次 50 筆）。Lightbox 左右滑動的範圍就是它
 * @param upToMonth `YYYY-MM`；null＝沒有時間篩選（手冊 §二第三條：預設看得到最新的資料）
 * @param filter 已套用的地點／標籤篩選（任一符合）；空＝沒有篩選，分頁走 `homeFeed`
 * @param months 月份選擇器的選項：有圖的月份；篩選中只列選取項目有圖的月份；不帶張數
 * @param filterOptions 篩選抽屜的候選（開抽屜時才讀）
 * @param draft 抽屜開著時的草稿；null＝抽屜關著
 * @param facets 每個月的標籤列，key 是 `YYYY-MM`；捲到才去查，查過就留著
 * @param error 上一次讀取失敗的訊息；null＝沒有錯誤。**不是例外物件** —— 這份狀態要能被
 *        純函式比較與測試，例外物件沒有結構相等
 */
data class HomeState(
    val items: List<ShotRow> = emptyList(),
    val cursor: ShotCursor? = null,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val upToMonth: String? = null,
    val filter: HomeFilter = HomeFilter(),
    val months: List<String> = emptyList(),
    val filterOptions: List<FilterOption> = emptyList(),
    val draft: FilterDraft? = null,
    val facets: Map<String, List<MonthFacet>> = emptyMap(),
    val error: String? = null,
)

/** 首頁的純狀態轉換。**沒有任何 suspend、沒有 Android 相依** —— 分頁邏輯要能單獨測。 */
object HomeStore {

    fun canLoadMore(state: HomeState): Boolean = !state.loading && !state.endReached

    fun startLoading(state: HomeState): HomeState = state.copy(loading = true)

    fun appendPage(state: HomeState, page: Page<ShotRow>): HomeState = state.copy(
        items = state.items + page.items,
        cursor = page.next,
        endReached = page.next == null,
        loading = false,
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

    /** 換篩選條件：跟 [reset] 一樣清單、游標、月份標籤列一起作廢，時間範圍保留。 */
    fun withFilter(state: HomeState, filter: HomeFilter): HomeState = reset(state, state.upToMonth).copy(filter = filter)

    fun removeShot(state: HomeState, id: Long): HomeState {
        if (state.items.none { it.id == id }) return state
        return state.copy(
            items = state.items.filterNot { it.id == id },
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
     * 該月的**地點**畫在標題那一列的右邊、跟著標題吸頂；**其他標籤**另起一列（[HomeSlot.Facets]），
     * 不吸頂，該月沒有地點以外的標籤就不出現。
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
            if (state.filter.isEmpty && state.facets[group.month].orEmpty().any { !it.isPlace }) out += HomeSlot.Facets(group.month)
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

/** 地點（[MonthFacet.kind] 是 `"place"`）畫在月份標題列；其他標籤畫在第二列。 */
val MonthFacet.isPlace: Boolean get() = kind == "place"

/** 縮圖牆格線裡的一格。 */
sealed interface HomeSlot {
    val key: String

    /** 這一格屬於哪個月（`YYYY-MM`）—— 用來判斷目前吸頂的是哪個月的標題 */
    val month: String

    data class Header(override val month: String, val label: String) : HomeSlot {
        override val key: String get() = "h-$month"
    }

    /** 該月地點以外的標籤（地點在 [Header] 那一列） */
    data class Facets(override val month: String) : HomeSlot {
        override val key: String get() = "f-$month"
    }

    /** @param index 這張圖在 [HomeState.items] 裡的位置 —— Lightbox 從這個位置開始 */
    data class Tile(val index: Int, val shot: ShotRow) : HomeSlot {
        override val key: String get() = "t-${shot.id}"
        override val month: String get() = monthOf(shot.eventDate)
    }
}
