package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.data.repo.model.FolderCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersStoreTest {

    private fun card(name: String, count: Int = 0, last: Long = 0) =
        FolderCard(id = name.hashCode().toLong(), name = name, shotCount = count, lastActivityAt = last, preview = emptyList())

    private val cards = listOf(card("旅行", 5, 300), card("貓", 2, 900), card("工作", 9, 100))

    @Test
    fun 沒有篩選時全部都在_照目前排序() {
        val state = FoldersState(cards = cards, sort = FolderSort.COUNT_DESC)
        assertEquals(listOf("工作", "旅行", "貓"), FoldersStore.visible(state).map { it.name })
    }

    @Test
    fun 篩選只比對名稱且不分大小寫() {
        val state = FoldersState(cards = cards + card("Cat photos"), query = "cat")
        assertEquals(listOf("Cat photos"), FoldersStore.visible(state).map { it.name })
    }

    /** 兩種空狀態要分得出來：一個是「還沒有分類」，一個是「篩掉光了」（手冊 §六第三條）。 */
    @Test
    fun 分得出還沒有分類與篩掉光了() {
        assertEquals(FoldersEmpty.NO_FOLDERS, FoldersStore.emptyKind(FoldersState(cards = emptyList())))
        assertEquals(FoldersEmpty.NO_MATCH, FoldersStore.emptyKind(FoldersState(cards = cards, query = "找不到的")))
        assertNull(FoldersStore.emptyKind(FoldersState(cards = cards)))
    }

    /** 讀取中還沒拿到資料時不能說「還沒有分類」—— 那會在每次進頁面時閃一下空狀態。 */
    @Test
    fun 讀取中不算空狀態() {
        assertNull(FoldersStore.emptyKind(FoldersState(cards = emptyList(), loading = true)))
    }

    @Test
    fun 新增與改名共用同一個對話框狀態() {
        val creating = FoldersStore.startCreate(FoldersState())
        assertEquals(FolderEditor(target = null, name = ""), creating.editor)

        val renaming = FoldersStore.startRename(FoldersState(cards = cards), cards[0])
        assertEquals(FolderEditor(target = cards[0].id, name = "旅行"), renaming.editor)
    }

    @Test
    fun 對話框把失敗訊息留在原地讓人改() {
        val state = FoldersStore.startCreate(FoldersState())
            .let { FoldersStore.editName(it, "旅行") }
            .let { FoldersStore.editorFailed(it, "同一層已經有「旅行」了") }

        assertEquals("旅行", state.editor?.name)
        assertEquals("同一層已經有「旅行」了", state.editor?.error)
    }

    /** 再打字就把錯誤訊息清掉，不然使用者改好了還看到舊的紅字。 */
    @Test
    fun 再打字就清掉錯誤訊息() {
        val state = FoldersStore.startCreate(FoldersState())
            .let { FoldersStore.editorFailed(it, "同一層已經有「旅行」了") }
            .let { FoldersStore.editName(it, "旅行 2") }

        assertNull(state.editor?.error)
    }

    @Test
    fun 關閉搜尋會一起清掉篩選字串() {
        val state = FoldersState(cards = cards, searching = true, query = "貓")
        assertEquals("", FoldersStore.setSearching(state, false).query)
        assertTrue(FoldersStore.visible(FoldersStore.setSearching(state, false)).size == 3)
    }
}
