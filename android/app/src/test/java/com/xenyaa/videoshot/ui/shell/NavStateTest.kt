package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavStateTest {

    @Test
    fun 預設站在首頁的根層() {
        val nav = NavState()
        assertEquals(Tab.HOME, nav.tab)
        assertEquals(Dest.Root, nav.current)
    }

    @Test
    fun push_之後_current_是新的那一層() {
        val nav = NavState().push(Dest.Lightbox(7))
        assertEquals(Dest.Lightbox(7), nav.current)
    }

    @Test
    fun pop_退一層_退到根層再退是_null_交還給系統() {
        val opened = NavState().push(Dest.Lightbox(7))
        val back = opened.pop()!!
        assertEquals(Dest.Root, back.current)
        assertNull(back.pop())
    }

    /** 各格的堆疊各自留著：從分類切去首頁再切回來，分類還停在原來那一層。 */
    @Test
    fun 切換分頁不會清掉另一格的堆疊() {
        val nav = NavState()
            .select(Tab.FOLDERS).push(Dest.Lightbox(3))
            .select(Tab.HOME)
        assertEquals(Dest.Root, nav.current)
        assertEquals(Dest.Lightbox(3), nav.select(Tab.FOLDERS).current)
    }

    /** 非首頁的根層按返回 → 回首頁分頁，不是直接離開 app。 */
    @Test
    fun 其他分頁的根層按返回會回到首頁分頁() {
        val nav = NavState().select(Tab.ACCOUNT).pop()!!
        assertEquals(Tab.HOME, nav.tab)
    }

    /** 取圖是全螢幕的，關掉之後要回到按下【取圖】之前那一格。 */
    @Test
    fun 取圖記得從哪一格來的() {
        val nav = NavState().select(Tab.FOLDERS).select(Tab.CAPTURE)
        assertEquals(Tab.FOLDERS, nav.returnTo)
        assertEquals(Tab.FOLDERS, nav.pop()!!.tab)
    }

    @Test
    fun 編碼與解碼是往返一致的() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.Lightbox(12)).select(Tab.HOME)
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test
    fun 解不開的存檔退回預設而不是當機() {
        assertEquals(NavState(), NavCodec.decode("亂七八糟"))
        assertEquals(NavState(), NavCodec.decode(""))
    }

    /**
     * 見階段 7 全盤覆查第 8 點第 3 項：這個組合只有存檔的 Bundle 壞掉才會出現
     * （正常流程 returnTo 永遠是按【取圖】前那一格，不會是 CAPTURE 自己）。
     * 沒修之前 pop() 會走到 select(returnTo)，target == tab 讓它直接回傳 this ——
     * 呼叫端看到非 null 就當作「已經處理」，返回鍵從此變成永久沒有反應的按鈕。
     */
    @Test
    fun returnTo也是CAPTURE時退一層交還給系統而不是卡住() {
        val corrupted = NavCodec.decode(NavCodec.encode(NavState(tab = Tab.CAPTURE, returnTo = Tab.CAPTURE)))
        assertEquals(Tab.CAPTURE, corrupted.tab)
        assertEquals(Tab.CAPTURE, corrupted.returnTo)
        assertNull(corrupted.pop())
    }

    @Test
    fun 資料夾頁存得住也讀得回來() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.Folder(42)).push(Dest.Lightbox(3))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test
    fun 壞掉的資料夾編碼退回預設() {
        assertEquals(NavState(), NavCodec.decode("HOME|HOME|HOME=R;CAPTURE=R;FOLDERS=R,F不是數字;ACCOUNT=R"))
    }

    /** Lightbox 疊在資料夾頁上面時，要問得出「現在開的是哪個資料夾」。 */
    @Test
    fun 問得出目前打開的資料夾() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.Folder(42)).push(Dest.Lightbox(0))
        assertEquals(42L, nav.openFolderId())
        assertNull(NavState().openFolderId())
    }

    @Test
    fun 原片播放存得住也讀得回來() {
        val nav = NavState().push(Dest.Lightbox(3)).push(Dest.Play("abc12345678", 99L))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    /** 舊版存下的詳情頁（D）／批次編輯（B）已經沒有對應的畫面：退回預設，不當機。 */
    @Test
    fun 舊版的詳情頁存檔退回預設() {
        val raw = "HOME|HOME|HOME=R,Dabc:1;CAPTURE=R;FOLDERS=R;ACCOUNT=R"
        assertEquals(NavState(), NavCodec.decode(raw))
    }

    /** 原片播放疊在 Lightbox 上：返回一層回到 Lightbox 原本那一張。 */
    @Test
    fun 原片播放返回回到lightbox() {
        val nav = NavState().push(Dest.Lightbox(3)).push(Dest.Play("v1", 1L))
        assertEquals(Dest.Lightbox(3), nav.pop()?.current)
    }

    @Test
    fun account區段的push與pop() {
        val nav = NavState().select(Tab.ACCOUNT).push(Dest.AccountSetting(AccountSection.TAGS))
        assertEquals(Dest.AccountSetting(AccountSection.TAGS), nav.current)
        val popped = nav.pop()
        assertEquals(Dest.Root, popped?.current)
    }

    @Test
    fun account區段的編碼與解碼往返() {
        val nav = NavState().select(Tab.ACCOUNT).push(Dest.AccountSetting(AccountSection.GEMINI))
        val decoded = NavCodec.decode(NavCodec.encode(nav))
        assertEquals(nav, decoded)
    }

    @Test
    fun account區段解不出來時退回預設NavState() {
        assertEquals(NavState(), NavCodec.decode("ACCOUNT|HOME|HOME=R;CAPTURE=R;FOLDERS=R;ACCOUNT=A不存在的區段"))
    }

    /** 階段 17 拿掉查詢分頁：舊版存檔（目前分頁是 SEARCH、或堆疊裡有 SEARCH 那一格）要安全退回預設。 */
    @Test
    fun 舊版存檔的目前分頁是SEARCH時退回預設NavState() {
        assertEquals(
            NavState(),
            NavCodec.decode("SEARCH|HOME|HOME=R;SEARCH=R;CAPTURE=R;FOLDERS=R;ACCOUNT=R"),
        )
    }

    @Test
    fun 舊版存檔的堆疊含SEARCH那一格時退回預設NavState() {
        assertEquals(
            NavState(),
            NavCodec.decode("HOME|HOME|HOME=R;SEARCH=R,L1;CAPTURE=R;FOLDERS=R;ACCOUNT=R"),
        )
        assertEquals(
            NavState(),
            NavCodec.decode("HOME|SEARCH|HOME=R;CAPTURE=R;FOLDERS=R;ACCOUNT=R"),
        )
    }

    @Test
    fun 分頁有五格() {
        assertEquals(listOf("照片", "取圖", "剪輯", "相簿", "帳號"), Tab.entries.map { it.label })
    }

    @Test fun 剪輯的兩層畫面可以編碼再解回來() {
        val nav = NavState().select(Tab.CLIP).push(Dest.ClipPicker(ClipOrientation.PORTRAIT)).push(Dest.ClipEditor(ClipOrientation.PORTRAIT))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test fun 橫式的剪輯畫面也解得回來() {
        val nav = NavState().select(Tab.CLIP).push(Dest.ClipPicker(ClipOrientation.LANDSCAPE)).push(Dest.ClipEditor(ClipOrientation.LANDSCAPE))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test fun 舊版沒有剪輯格的字串只補上剪輯的根畫面() {
        val old = "FOLDERS|HOME|HOME=R;CAPTURE=R;FOLDERS=R,F7;ACCOUNT=R"
        val nav = NavCodec.decode(old)
        assertEquals(Tab.FOLDERS, nav.tab)
        assertEquals(listOf(Dest.Root, Dest.Folder(7)), nav.stacks.getValue(Tab.FOLDERS))
        assertEquals(listOf(Dest.Root), nav.stacks.getValue(Tab.CLIP))
    }

    @Test fun 剪輯格按返回回到照片() {
        assertEquals(Tab.HOME, NavState().select(Tab.CLIP).pop()?.tab)
    }

    @Test
    fun RestoreFlow編解碼往返() {
        val nav = NavState().push(Dest.RestoreFlow)
        val decoded = NavCodec.decode(NavCodec.encode(nav))
        assertEquals(Dest.RestoreFlow, decoded.current)
    }

    @Test
    fun 封存相簿存得住也讀得回來() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.ArchivedFolders).push(Dest.Folder(7))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test
    fun 從封存相簿裡開的相簿也問得出來() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.ArchivedFolders).push(Dest.Folder(7))
        assertEquals(7L, nav.openFolderId())
    }
}
