package com.xenyaa.videoshot.core.folders

import org.junit.Assert.assertEquals
import org.junit.Test

private data class F(
    override val name: String,
    override val shotCount: Int = 0,
    override val lastActivityAt: Long = 0,
) : SortableFolder

class FolderSortTest {

    private fun names(sort: FolderSort, vararg items: F) = sort.sort(items.toList()).map { it.name }

    @Test
    fun 名稱升冪照中文筆畫() {
        assertEquals(
            listOf("一月", "九月", "十月"),
            names(FolderSort.NAME_ASC, F("十月"), F("一月"), F("九月")),
        )
    }

    /** 「第 2 集」要排在「第 10 集」前面 —— 純字串比較會把 "10" 排在 "2" 前面。 */
    @Test
    fun 名稱裡的數字照大小排不照字典序() {
        assertEquals(
            listOf("第 2 集", "第 10 集"),
            names(FolderSort.NAME_ASC, F("第 10 集"), F("第 2 集")),
        )
    }

    @Test
    fun 名稱降冪是升冪的相反() {
        assertEquals(
            listOf("第 10 集", "第 2 集"),
            names(FolderSort.NAME_DESC, F("第 2 集"), F("第 10 集")),
        )
    }

    @Test
    fun 張數多到少_同張數再照名稱() {
        assertEquals(
            listOf("多的", "一月", "九月"),
            names(
                FolderSort.COUNT_DESC,
                F("九月", shotCount = 1), F("多的", shotCount = 9), F("一月", shotCount = 1),
            ),
        )
    }

    @Test
    fun 最近加入排最前_沒有活動的排最後() {
        assertEquals(
            listOf("昨天加過", "上個月加過", "從來沒加過"),
            names(
                FolderSort.RECENT,
                F("上個月加過", lastActivityAt = 100),
                F("從來沒加過", lastActivityAt = 0),
                F("昨天加過", lastActivityAt = 999),
            ),
        )
    }

    @Test
    fun 認不得的偏好值退回名稱升冪() {
        assertEquals(FolderSort.NAME_ASC, FolderSort.byId(null))
        assertEquals(FolderSort.NAME_ASC, FolderSort.byId("這個排序已經不存在了"))
        assertEquals(FolderSort.RECENT, FolderSort.byId("recent"))
    }
}
