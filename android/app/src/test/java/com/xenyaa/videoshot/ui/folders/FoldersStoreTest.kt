package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.data.repo.model.FolderCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * N2 的回歸測試：`folderCards(null)` 讀取失敗時 `cards` 是空的、`loading` 已經是 false，
     * 沒接住 `error` 的話會被誤判成 `NO_FOLDERS`，畫面對使用者主動說錯話（「還沒有任何分類」）。
     * 這是資料夾頁已經修過的同一個 bug（`FolderScreen.kt` 的 `FolderErrorRow`），清單頁沒跟上。
     */
    @Test
    fun 讀取失敗不算任何一種空狀態() {
        assertNull(FoldersStore.emptyKind(FoldersState(cards = emptyList(), error = "載入失敗，請再試一次")))
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

    private fun archivedCard(id: Long, name: String, at: Long) =
        FolderCard(id, name, 0, lastActivityAt = 0, preview = emptyList(), archivedAt = at)

    @Test
    fun 相簿畫面只算沒封存的() {
        val state = FoldersState(cards = listOf(
            FolderCard(1, "旅行", 0, 0, emptyList()),
            archivedCard(2, "舊的", 100),
        ))
        assertEquals(listOf("旅行"), FoldersStore.visible(state).map { it.name })
        assertEquals(listOf("旅行"), FoldersStore.active(state).map { it.name })
    }

    @Test
    fun 只有封存的相簿時相簿畫面是沒有相簿的空狀態() {
        val state = FoldersState(cards = listOf(archivedCard(2, "舊的", 100)))
        assertEquals(FoldersEmpty.NO_FOLDERS, FoldersStore.emptyKind(state))
    }

    @Test
    fun 搜尋不會找到封存的相簿() {
        val state = FoldersState(
            cards = listOf(FolderCard(1, "旅行", 0, 0, emptyList()), archivedCard(2, "旅行舊照", 100)),
            query = "旅行",
        )
        assertEquals(listOf("旅行"), FoldersStore.visible(state).map { it.name })
    }

    @Test
    fun 封存的依封存時間新到舊() {
        val state = FoldersState(cards = listOf(
            archivedCard(1, "甲", 100),
            archivedCard(2, "乙", 300),
            archivedCard(3, "丙", 200),
            FolderCard(4, "沒封存", 0, 0, emptyList()),
        ))
        assertEquals(listOf("乙", "丙", "甲"), FoldersStore.archived(state).map { it.name })
    }

    @Test
    fun 封存相簿的空狀態只在讀完且沒錯誤時成立() {
        assertTrue(FoldersStore.archivedEmpty(FoldersState()))
        assertFalse(FoldersStore.archivedEmpty(FoldersState(loading = true)))
        assertFalse(FoldersStore.archivedEmpty(FoldersState(error = "載入失敗，請再試一次")))
        assertFalse(FoldersStore.archivedEmpty(FoldersState(cards = listOf(archivedCard(1, "甲", 1)))))
    }

    // ---- 長按多選 ----

    @Test
    fun 切換選取會加入與移除() {
        val once = FoldersStore.toggleSelect(FoldersState(cards = cards), cards[0].id)
        assertEquals(setOf(cards[0].id), once.selection)
        val twice = FoldersStore.toggleSelect(once, cards[1].id)
        assertEquals(setOf(cards[0].id, cards[1].id), twice.selection)
        assertEquals(setOf(cards[1].id), FoldersStore.toggleSelect(twice, cards[0].id).selection)
    }

    @Test
    fun 清除選取() {
        val state = FoldersState(cards = cards, selection = setOf(cards[0].id, cards[2].id))
        assertEquals(emptySet<Long>(), FoldersStore.clearSelection(state).selection)
    }

    /** 依畫面上的順序（相簿畫面照目前排序，封存相簿照封存時間），不是 cards 原本的順序。 */
    @Test
    fun 已選的卡片依畫面順序() {
        val state = FoldersState(
            cards = cards + archivedCard(7, "舊甲", 100) + archivedCard(8, "舊乙", 200),
            sort = FolderSort.COUNT_DESC,
            selection = setOf(cards[1].id, cards[2].id, cards[0].id),
        )
        assertEquals(listOf("工作", "旅行", "貓"), FoldersStore.selectedCards(state).map { it.name })

        val archived = state.copy(selection = setOf(7L, 8L))
        assertEquals(listOf("舊乙", "舊甲"), FoldersStore.selectedCards(archived).map { it.name })
    }

    @Test
    fun 已選的卡片只有選到的() {
        val state = FoldersState(cards = cards, selection = setOf(cards[1].id))
        assertEquals(listOf("貓"), FoldersStore.selectedCards(state).map { it.name })
        assertEquals(emptyList<FolderCard>(), FoldersStore.selectedCards(FoldersState(cards = cards)))
    }

    /** 重新讀取後已經不在的相簿（例如在別處刪掉了）要從選取拿掉，不然「已選 N 本」會算錯。 */
    @Test
    fun 重新讀取後消失的相簿從選取拿掉() {
        val state = FoldersState(cards = cards, selection = setOf(cards[0].id, cards[1].id))
        val reloaded = FoldersStore.loaded(state, listOf(cards[1], cards[2]))
        assertEquals(listOf(cards[1], cards[2]), reloaded.cards)
        assertEquals(setOf(cards[1].id), reloaded.selection)
        assertEquals(false, reloaded.loading)
        assertNull(reloaded.error)
    }
}
