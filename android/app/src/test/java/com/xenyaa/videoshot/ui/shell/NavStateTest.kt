package com.xenyaa.videoshot.ui.shell

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

    /**
     * 首頁月份標籤點進查詢分頁：查詢分頁上次留下的舊畫面（例如詳情頁）要被清掉，
     * 不能讓使用者切過去卻看到過期的那一層（見 Task 12 覆查 Important 2）。
     */
    @Test
    fun selectAndReset會換分頁並把該格堆疊清回根層() {
        val nav = NavState()
            .select(Tab.SEARCH).push(Dest.Lightbox(1)).push(Dest.Detail("v1", 9L))
            .select(Tab.HOME)
        val reseeded = nav.selectAndReset(Tab.SEARCH)
        assertEquals(Tab.SEARCH, reseeded.tab)
        assertEquals(Dest.Root, reseeded.current)
        // 其他格的堆疊不受影響
        assertEquals(Dest.Root, reseeded.select(Tab.HOME).current)
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
        assertEquals(NavState(), NavCodec.decode("HOME|HOME|HOME=R;SEARCH=R;CAPTURE=R;FOLDERS=R,F不是數字;ACCOUNT=R"))
    }

    /** Lightbox 疊在資料夾頁上面時，要問得出「現在開的是哪個資料夾」。 */
    @Test
    fun 問得出目前打開的資料夾() {
        val nav = NavState().select(Tab.FOLDERS).push(Dest.Folder(42)).push(Dest.Lightbox(0))
        assertEquals(42L, nav.openFolderId())
        assertNull(NavState().openFolderId())
    }

    @Test
    fun 詳情頁存得住也讀得回來() {
        val nav = NavState().push(Dest.Detail("abc12345678", 99L))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    @Test
    fun 批次編輯存得住也讀得回來() {
        val nav = NavState().push(Dest.Detail("abc12345678", 99L)).push(Dest.BatchEdit("abc12345678"))
        assertEquals(nav, NavCodec.decode(NavCodec.encode(nav)))
    }

    /** 詳情頁或疊在它上面的批次編輯都要問得出「現在是哪一支影片」。 */
    @Test
    fun 問得出詳情頁目前是哪一支影片() {
        assertNull(NavState().currentDetailVideoId())
        val onDetail = NavState().push(Dest.Detail("v1", 1L))
        assertEquals("v1", onDetail.currentDetailVideoId())
        assertEquals("v1", onDetail.push(Dest.BatchEdit("v1")).currentDetailVideoId())
        // Lightbox 疊在詳情頁上面不算——目前沒有這個路徑，但守住這條語意
        assertNull(NavState().push(Dest.Lightbox(0)).currentDetailVideoId())
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
        assertEquals(NavState(), NavCodec.decode("ACCOUNT|HOME|HOME=R;SEARCH=R;CAPTURE=R;FOLDERS=R;ACCOUNT=A不存在的區段"))
    }

    @Test
    fun RestoreFlow編解碼往返() {
        val nav = NavState().push(Dest.RestoreFlow)
        val decoded = NavCodec.decode(NavCodec.encode(nav))
        assertEquals(Dest.RestoreFlow, decoded.current)
    }
}
