package com.xenyaa.videoshot.core.folders

import java.text.Collator
import java.util.Locale

/**
 * 排序看得到的那幾個欄位。清單頁與資料夾頁的子資料夾共用同一組排序，
 * 所以比較器只認這個介面，不認具體的卡片型別（`:core` 也不該認得 repo 的 model）。
 */
interface SortableFolder {
    val name: String
    val shotCount: Int

    /** 最近有圖被加進來的時間（秒）；沒有圖時是建立時間。 */
    val lastActivityAt: Long
}

/**
 * 清單頁的四種排序（規格第六節「名稱升／降、張數多→少、最近加入」）。
 *
 * @param id 存進 DataStore 的值，**不可更動**（改了使用者選過的排序會失效）
 * @param label 排序抽屜上顯示的文字；狀態列也用它，使用者才知道「現在是怎麼排的」
 */
enum class FolderSort(val id: String, val label: String) {
    NAME_ASC("name-asc", "名稱 A → Z"),
    NAME_DESC("name-desc", "名稱 Z → A"),
    COUNT_DESC("count-desc", "張數多 → 少"),
    RECENT("recent", "最近加入");

    companion object {
        fun byId(id: String?): FolderSort = entries.firstOrNull { it.id == id } ?: NAME_ASC
    }
}

/**
 * 中文照筆畫（`Collator`），名稱裡的數字照大小。
 *
 * 單純用 `Collator` 會把「第 10 集」排在「第 2 集」前面（一個字一個字比，'1' < '2'），
 * 所以先把名稱切成「非數字段落」與「數字段落」，數字段落比數值、其餘交給 `Collator`。
 * 原型用的是 `localeCompare('zh-Hant', { numeric: true })`，行為對齊它。
 */
private val collator: Collator = Collator.getInstance(Locale.TAIWAN)

public fun compareNaturally(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            val ai = a.indexOfFirstNonDigit(i)
            val bj = b.indexOfFirstNonDigit(j)
            // 去掉前導零再比長度：位數多的數字一定比較大
            val an = a.substring(i, ai).trimStart('0')
            val bn = b.substring(j, bj).trimStart('0')
            if (an.length != bn.length) return an.length - bn.length
            if (an != bn) return an.compareTo(bn)
            i = ai
            j = bj
        } else {
            val cmp = collator.compare(a[i].toString(), b[j].toString())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

private fun String.indexOfFirstNonDigit(from: Int): Int {
    var k = from
    while (k < length && this[k].isDigit()) k++
    return k
}

private val byName = Comparator<SortableFolder> { a, b -> compareNaturally(a.name, b.name) }

/** 排序是純函式：同樣的輸入永遠得到同樣的順序，畫面不必自己記。 */
fun <T : SortableFolder> FolderSort.sort(items: List<T>): List<T> = when (this) {
    FolderSort.NAME_ASC -> items.sortedWith(byName)
    FolderSort.NAME_DESC -> items.sortedWith(byName.reversed())
    // 同張數再照名稱，否則兩個都是 3 張的資料夾每次進來順序都可能不同
    FolderSort.COUNT_DESC -> items.sortedWith(compareByDescending<T> { it.shotCount }.then(byName))
    FolderSort.RECENT -> items.sortedWith(compareByDescending<T> { it.lastActivityAt }.then(byName))
}
