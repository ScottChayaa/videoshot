package com.xenyaa.videoshot.ui.shell

import android.view.Choreographer

/**
 * 繞過 MIUI 13（Android 12，2107113SG 實機）「背景 app 停用動畫」的系統修改造成的 Compose 畫面凍結。
 *
 * MIUI 在 `Choreographer.postCallbackDelayedInternal` 裡插了一段：這個行程「還沒有可見的 surface」時
 * （`ChoreographerInjectorStubImpl.sSurfaceCount <= 0`，系統屬性 `persist.sys.disable_bganimate` 預設開），
 * 動畫類型的 frame callback 只排進佇列、**不要求下一幀**。Compose 所有的幀都走這一類。
 *
 * 第一幀的 traversal 裡，`ComposeView.onAttachedToWindow` 建立 Recomposer 時就會發第一個幀請求，
 * 時間點在 `relayoutWindow` 建出 surface 之前——被吞掉。`AndroidUiDispatcher` 以為已經排過了
 * （`scheduledFrameDispatch = true`）不會再排，之後只要沒有別的東西（觸控、版面變動）順手要一幀，
 * Compose 就永遠等不到下一幀：畫面停在第一次組合的樣子（例如首次開啟閘門讀 DataStore 時的空白）。
 * DataStore 已經在記憶體裡（同一個行程剛被背景工作或廣播用過）時最容易踩到，因為值會在那一刻立刻回來、
 * 需要重組。實機用 jdb 抓到的證據見 2026-10-02 的 commit 說明。
 *
 * 視窗取得焦點時 surface 一定已經在了，這時補要一幀：任何一幀都會把佇列裡到期的 callback
 * （包含被吞掉的那個）一起跑掉。回到前景時 surface 會重建，同樣的事可能再發生，所以每次取得焦點都補。
 * 代價是每次取得焦點多一個空幀。
 */
class FrameStallGuard(
    private val postFrame: (Choreographer.FrameCallback) -> Unit = { Choreographer.getInstance().postFrameCallback(it) },
) {
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) postFrame(NoOpFrame)
    }

    private object NoOpFrame : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) = Unit
    }
}
