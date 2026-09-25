package com.xenyaa.videoshot.ui.shell

import androidx.compose.runtime.Immutable

/** 底部導覽的五格（手冊 §零：五個項目都是圖示＋文字）。 */
enum class Tab(val label: String) {
    HOME("首頁"),
    SEARCH("查詢"),
    CAPTURE("取圖"),
    FOLDERS("分類"),
    ACCOUNT("帳號"),
}

/**
 * 一格分頁裡的一層畫面。
 *
 * 階段 8 的資料夾頁、階段 9 的詳情頁往這裡加項目，`AppShell` 的 `when` 會因為窮盡檢查
 * 直接指出還沒處理的那一項 —— 這是不用 navigation-compose 換到的好處。
 */
sealed interface Dest {
    data object Root : Dest

    /** @param startIndex 開啟時停在清單的第幾張；之後左右滑動由 Lightbox 自己記 */
    data class Lightbox(val startIndex: Int) : Dest

    /** 分類分頁裡的一個資料夾。**只存 id**——名稱與內容每次進來重新查，改名之後標題才會對 */
    data class Folder(val folderId: Long) : Dest

    /**
     * 詳情頁（規格第六節）。**保留底部導覽**——跟 [Folder] 一起交給 `AppShell`。
     * @param focusShotId 進場時要跳去播的那一張（從 Lightbox【播放這一段】帶進來）
     */
    data class Detail(val videoId: String, val focusShotId: Long) : Dest

    /**
     * 批次編輯圖資（規格第六節：複用精靈第三步的同一個介面）。**沒有底部導覽**——
     * 跟 [Lightbox] 一樣是全螢幕、有終點的流程，理由同 `AppShell` 對精靈的 KDoc。
     * 只存 videoId：要編輯的 shot 清單每次進來重查，改動後才不會顯示過期的一批。
     */
    data class BatchEdit(val videoId: String) : Dest
}

/**
 * 導覽狀態。**每一格各自一個堆疊** —— 切分頁不該弄丟另一格走到哪裡。
 *
 * @param returnTo 從哪一格按了【取圖】。取圖是全螢幕、沒有底部導覽（手冊 §四），
 *        關掉它要回到原來那一格，不是一律回首頁
 */
@Immutable
data class NavState(
    val tab: Tab = Tab.HOME,
    val stacks: Map<Tab, List<Dest>> = Tab.entries.associateWith { listOf(Dest.Root) },
    val returnTo: Tab = Tab.HOME,
) {
    val current: Dest get() = stacks.getValue(tab).last()

    fun select(target: Tab): NavState =
        if (target == tab) this
        else copy(tab = target, returnTo = if (target == Tab.CAPTURE) tab else returnTo)

    /**
     * 切到某一格，**同時把它的堆疊重置成只剩 [Dest.Root]**。目前只給「首頁月份標籤點進查詢」
     * 這條路徑用——查詢分頁被剛帶入的新條件蓋過一次結果之後，那一格不能還停在使用者先前留在
     * 那裡的舊畫面（例如查詢結果 Lightbox【播放這一段】留下的詳情頁），不然使用者會落在一個
     * 過期的畫面上，看不到規格第六節要求的「直接顯示結果」。
     *
     * 跟 [select] 不同：[select] 只換作用中的分頁，不動堆疊本身——那是給「使用者自己點底部
     * 導覽切分頁」用的，那種情況本來就該留住那一格原來停在哪裡。這裡兩件事綁在一起做，
     * 因為呼叫端（`seedFromHome`）永遠是「把查詢分頁重新帶到最新結果」，不會有只想換分頁、
     * 卻不想清掉舊畫面的情境。
     *
     * 不複製 [select] 對 `returnTo` 的 [Tab.CAPTURE] 特別處理——這裡的 `target` 只會是
     * [Tab.SEARCH]，不會是 [Tab.CAPTURE]，`returnTo` 不需要跟著換。
     */
    fun selectAndReset(target: Tab): NavState =
        copy(tab = target, stacks = stacks + (target to listOf(Dest.Root)))

    fun push(dest: Dest): NavState =
        copy(stacks = stacks + (tab to stacks.getValue(tab) + dest))

    /**
     * 返回一層。**回傳 null 代表「已經在最外層，把返回鍵交還給系統」** ——
     * 用 null 而不是回傳自己，呼叫端才分得出「我處理掉了」與「我沒處理」。
     */
    fun pop(): NavState? {
        val stack = stacks.getValue(tab)
        if (stack.size > 1) return copy(stacks = stacks + (tab to stack.dropLast(1)))
        if (tab == Tab.CAPTURE) {
            // returnTo == CAPTURE 只有存檔的 Bundle 壞掉才會出現（正常流程 returnTo 永遠是
            // 按【取圖】前那一格，不會是 CAPTURE 自己）。這種狀態下 select(returnTo) 因為
            // target == tab 直接回傳 this（沒有變化的非 null），呼叫端會誤判成「已經處理」，
            // 返回鍵從此變成永久沒有反應的按鈕。回 null 讓呼叫端把返回鍵交還給系統。
            if (returnTo == Tab.CAPTURE) return null
            return select(returnTo)
        }
        if (tab != Tab.HOME) return copy(tab = Tab.HOME)
        return null
    }

    /**
     * 分類分頁目前打開的資料夾（Lightbox 疊在它上面時也問得出來）。
     * 取堆疊裡**最後一個** `Dest.Folder`：資料夾可以一層一層往下開。
     */
    fun openFolderId(): Long? =
        stacks.getValue(Tab.FOLDERS).filterIsInstance<Dest.Folder>().lastOrNull()?.folderId

    /**
     * 詳情頁、或疊在它上面的批次編輯，目前是哪一支影片。都不是就回 null。
     * [Dest.Detail] 與 [Dest.BatchEdit] 都自帶 videoId，不必像 [openFolderId] 那樣往下找父層。
     */
    fun currentDetailVideoId(): String? = when (val top = current) {
        is Dest.Detail -> top.videoId
        is Dest.BatchEdit -> top.videoId
        else -> null
    }
}

/**
 * 存檔用的字串編碼（`rememberSaveable`）。
 *
 * 自己寫而不是用 `@Parcelize`：`Dest` 之後會長出更多帶參數的項目，
 * 一個看得懂的文字格式在除錯時直接讀得出來，出錯也只是退回預設而不是當機。
 *
 * 格式：`目前分頁|回到哪一格|HOME=R,L3;FOLDERS=R;...`
 */
object NavCodec {

    fun encode(nav: NavState): String {
        val stacks = Tab.entries.joinToString(";") { tab ->
            val items = nav.stacks.getValue(tab).joinToString(",") { dest ->
                when (dest) {
                    is Dest.Root -> "R"
                    is Dest.Lightbox -> "L${dest.startIndex}"
                    is Dest.Folder -> "F${dest.folderId}"
                    is Dest.Detail -> "D${dest.videoId}:${dest.focusShotId}"
                    is Dest.BatchEdit -> "B${dest.videoId}"
                }
            }
            "${tab.name}=$items"
        }
        return "${nav.tab.name}|${nav.returnTo.name}|$stacks"
    }

    fun decode(raw: String): NavState {
        val parts = raw.split("|")
        if (parts.size != 3) return NavState()
        val tab = Tab.entries.firstOrNull { it.name == parts[0] } ?: return NavState()
        val returnTo = Tab.entries.firstOrNull { it.name == parts[1] } ?: return NavState()
        val stacks = mutableMapOf<Tab, List<Dest>>()
        for (chunk in parts[2].split(";")) {
            val (name, items) = chunk.split("=").takeIf { it.size == 2 } ?: return NavState()
            val owner = Tab.entries.firstOrNull { it.name == name } ?: return NavState()
            val stack = items.split(",").map { item ->
                when {
                    item == "R" -> Dest.Root
                    item.startsWith("L") -> Dest.Lightbox(item.drop(1).toIntOrNull() ?: return NavState())
                    item.startsWith("F") -> Dest.Folder(item.drop(1).toLongOrNull() ?: return NavState())
                    item.startsWith("D") -> {
                        val body = item.drop(1)
                        val sep = body.lastIndexOf(':')
                        if (sep < 0) return NavState()
                        val shotId = body.substring(sep + 1).toLongOrNull() ?: return NavState()
                        Dest.Detail(body.substring(0, sep), shotId)
                    }
                    item.startsWith("B") -> Dest.BatchEdit(item.drop(1))
                    else -> return NavState()
                }
            }
            if (stack.isEmpty()) return NavState()
            stacks[owner] = stack
        }
        if (stacks.keys != Tab.entries.toSet()) return NavState()
        return NavState(tab, stacks, returnTo)
    }
}
