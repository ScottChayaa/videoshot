package com.xenyaa.videoshot.ui.shell

import androidx.compose.runtime.Immutable
import com.xenyaa.videoshot.core.timeline.ClipOrientation

/** 底部導覽的五格（手冊 §零：每個項目都是圖示＋文字；階段 17 拿掉了「查詢」，階段 18 加上「剪輯」）。 */
enum class Tab(val label: String) {
    HOME("照片"),
    CAPTURE("取圖"),
    CLIP("剪輯"),
    FOLDERS("相簿"),
    ACCOUNT("帳號"),
}

/**
 * 帳號頁選單的七個入口（規格第九節；[GEMINI] 階段 17 起選單上沒有那一列，畫面保留）。`id` 是 [NavCodec] 存檔用的短字串，**不可更動**。
 *
 * [BACKUP]／[THUMBS] 兩格本階段（階段 11）只顯示靜態說明——備份與 Google 帳號連結是階段 12，
 * 縮圖回填進度是階段 13；[THUMBS] 目前只掛「儲存用量」。
 */
enum class AccountSection(val id: String) {
    BACKUP("backup"),
    THUMBS("thumbs"),
    CAPTURE("capture"),
    GEMINI("gemini"),
    AI("ai"),
    PLACES("places"),
    TAGS("tags"),
}

/**
 * 一格分頁裡的一層畫面。
 *
 * 階段 8 的資料夾頁、原片播放往這裡加項目，`AppShell` 的 `when` 會因為窮盡檢查
 * 直接指出還沒處理的那一項 —— 這是不用 navigation-compose 換到的好處。
 */
sealed interface Dest {
    data object Root : Dest

    /** @param startIndex 開啟時停在清單的第幾張；之後左右滑動由 Lightbox 自己記 */
    data class Lightbox(val startIndex: Int) : Dest

    /** 分類分頁裡的一個資料夾。**只存 id**——名稱與內容每次進來重新查，改名之後標題才會對 */
    data class Folder(val folderId: Long) : Dest

    /**
     * 相簿分頁的封存相簿畫面（規格第六節）：在相簿畫面下拉過門檻放手進來。**保留底部導覽**——
     * 跟 [Folder] 一樣是分頁裡的一層，從這裡開的相簿再 push 一層 [Folder]。
     */
    data object ArchivedFolders : Dest

    /**
     * 原片播放（規格第六節）：全螢幕、**沒有底部導覽**，疊在 [Lightbox] 上面——返回回到 Lightbox 原本那一張。
     * @param shotId 從哪一張進來：從它的 `at_sec` 開始播，進場方向也照它的縮圖比例
     */
    data class Play(val videoId: String, val shotId: Long) : Dest

    /**
     * 帳號頁的一個設定子畫面（規格第九節）。**保留底部導覽**——跟 [Folder] 一樣，
     * 這不是有進有出的全螢幕流程，使用者應該隨時能切別的分頁。
     */
    data class AccountSetting(val section: AccountSection) : Dest

    /**
     * 還原挑選流程（規格第十節）。**沒有底部導覽**——跟 [Lightbox]／[Play] 一樣是
     * 全螢幕、有終點的流程；還原成功會整個重啟 app，不會走到「回上一層」這條路。
     */
    data object RestoreFlow : Dest

    /**
     * 剪輯分頁的選素材畫面（階段 18）：全螢幕、**沒有底部導覽**——有進有出的流程。
     * @param orientation 使用者在「影片方向」抽屜選的方向，往後的編輯與輸出都照它
     */
    data class ClipPicker(val orientation: ClipOrientation) : Dest

    /** 剪輯分頁的編輯畫面（階段 19）：選完素材後換掉 [ClipPicker]（`replaceTop`），返回直接回剪輯頁；同樣沒有底部導覽。 */
    data class ClipEditor(val orientation: ClipOrientation) : Dest
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

    fun push(dest: Dest): NavState =
        copy(stacks = stacks + (tab to stacks.getValue(tab) + dest))

    /** 把目前分頁堆疊最上面那層換成 [dest]（選素材換成編輯：返回時不會再經過選素材）。 */
    fun replaceTop(dest: Dest): NavState =
        copy(stacks = stacks + (tab to stacks.getValue(tab).dropLast(1) + dest))

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
                    is Dest.Play -> "P${dest.videoId}:${dest.shotId}"
                    is Dest.AccountSetting -> "A${dest.section.id}"
                    is Dest.RestoreFlow -> "V"
                    is Dest.ArchivedFolders -> "X"
                    is Dest.ClipPicker -> "C${orientationCode(dest.orientation)}"
                    is Dest.ClipEditor -> "E${orientationCode(dest.orientation)}"
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
                    item == "V" -> Dest.RestoreFlow
                    item == "X" -> Dest.ArchivedFolders
                    item.startsWith("L") -> Dest.Lightbox(item.drop(1).toIntOrNull() ?: return NavState())
                    item.startsWith("F") -> Dest.Folder(item.drop(1).toLongOrNull() ?: return NavState())
                    item.startsWith("P") -> {
                        val body = item.drop(1)
                        val sep = body.lastIndexOf(':')
                        if (sep < 0) return NavState()
                        val shotId = body.substring(sep + 1).toLongOrNull() ?: return NavState()
                        Dest.Play(body.substring(0, sep), shotId)
                    }
                    item.startsWith("C") -> Dest.ClipPicker(orientationOf(item.drop(1)) ?: return NavState())
                    item.startsWith("E") -> Dest.ClipEditor(orientationOf(item.drop(1)) ?: return NavState())
                    item.startsWith("A") -> {
                        val section = AccountSection.entries.firstOrNull { it.id == item.drop(1) }
                            ?: return NavState()
                        Dest.AccountSetting(section)
                    }
                    else -> return NavState()
                }
            }
            if (stack.isEmpty()) return NavState()
            stacks[owner] = stack
        }
        // 舊版存檔沒有後來新增的格子（例如剪輯）：缺的補根畫面；有不認識的格子名稱在上面就已重設
        for (t in Tab.entries) stacks.getOrPut(t) { listOf(Dest.Root) }
        return NavState(tab, stacks, returnTo)
    }

    /** 方向的存檔碼：`p`＝直式、`l`＝橫式；其他字元解碼失敗，整個回預設。 */
    private fun orientationCode(o: ClipOrientation): Char = when (o) {
        ClipOrientation.PORTRAIT -> 'p'
        ClipOrientation.LANDSCAPE -> 'l'
    }

    private fun orientationOf(code: String): ClipOrientation? = when (code) {
        "p" -> ClipOrientation.PORTRAIT
        "l" -> ClipOrientation.LANDSCAPE
        else -> null
    }
}
