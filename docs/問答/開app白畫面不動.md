# 為什麼 app 偶爾白畫面不動？

[← 回目錄](README.md)

- **已修**（`FrameStallGuard`，`MainActivity` 取得視窗焦點時補要一個空幀）。這題記的是原因，再遇到類似症狀時參考
- **症狀**：2107113SG（MIUI 13／Android 12）開 app 停在第一次組合的白畫面，所有執行緒閒置，跟資料庫無關
  - 只在 DataStore 已經在記憶體裡時發生：同一個行程剛被背景工作（備份、回填）或廣播（假資料匯入）喚醒過再開 app，**正式版使用者也可能遇到**
  - `force-stop` 後 DataStore 要重讀檔、值晚一點才到，所以「強制停止再開就正常」
- **根因**：MIUI「背景 app 停用動畫」（系統屬性 `persist.sys.disable_bganimate`，預設開）改了 `Choreographer`——行程還沒有可見 surface 時，動畫類型的 frame callback 只排進佇列、不要求下一幀
  - Compose 在第一幀的 traversal 裡（`ComposeView.onAttachedToWindow` 建立 Recomposer）就發出第一個幀請求，早於 `relayoutWindow` 建出 surface，被吞掉
  - `AndroidUiDispatcher` 以為已經排過就不再排，畫面停在第一次組合
- **怎麼查出來的**：用 jdb 在卡住當下讀 `Choreographer.mFrameScheduled`、動畫佇列、`AndroidUiDispatcher.scheduledFrameDispatch`，對 `disableAnimation` 下斷點抓呼叫堆疊，並從手機拉 `framework.jar`／`miui-framework.jar` 反組譯確認（細節見 commit `7e1eb2c` 的說明）
- **驗證**：同條件拿掉修正 6 次有 1 次白畫面，修正後 30 次全部正常
- 做這種實驗時注意手機上鎖也會看起來像卡住，見[怎麼用 adb 驅動手機做驗收？](用adb驅動手機驗收.md)
