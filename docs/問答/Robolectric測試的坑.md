# Compose 單元測試（Robolectric）有哪些坑？

[← 回目錄](README.md)

`:app:testDebugUnitTest` 的 Compose UI 測試跑在 JVM 上的 Robolectric，跟實機有幾個差別。

## 時鐘

- **`waitUntil` 不推進測試時鐘**，重組與 `LaunchedEffect` 不會自己跑；狀態改了、點了之後要 `compose.waitForIdle()`
- 測「3 秒自動收起」這類計時時要 `compose.mainClock.autoAdvance = false`，自己 `advanceTimeBy(…)`；不然一次 `waitForIdle` 就跑完了
- `autoAdvance = false` 時，手勢在第一次推進時才處理，結果要再下一幀才畫出來：先 `waitForIdle()` 再 `advanceTimeBy(…)`
- 點一下的動作要等過點兩下的判定時間（約 300 毫秒）才觸發，測試裡 `advanceTimeBy(400)`

## 找節點

- `onNodeWithText` 預設是完整比對；用 `substring = true` 時小心撞到別的字（例如找「已選」會找到工具列的「只看已選」）
- 同一個字出現兩次（例如抽屜底部和對話框都有【取消】）會丟例外，用 `onAllNodesWithText("取消").onLast()` 取最上層那個
- `clearAndSetSemantics {}` 清掉的文字找不到，改用外層的 `contentDescription` 找
- Material 3 月曆的日期格，文字是**英文完整日期**（測試環境 locale 是英文，例如「Saturday, August 15, 2026」），用：

  ```kotlin
  compose.onNode(hasText(" 15, ", substring = true) and hasClickAction())
  ```

## 手勢

- 播放器控制層開著時，正中央是播放／暫停鈕，點中央會切換播放；要收起控制層就點偏離中央的位置（例如 `Offset(width * 0.2f, height * 0.3f)`）
- 手指按住不放的拖曳測試，暫停的時鐘不會把狀態變化套進畫面，改讓時鐘自己走（`autoAdvance = true`）

## 環境

- Robolectric 預設視窗是 320×470dp，比任何支援的手機都小；測版面時加 `@Config(qualifiers = "w411dp-h891dp")`
- WebView 在 Robolectric 不會觸發 `onPageFinished`，播放器的控制層測試要直接測 `PlayerControls`，不要透過 `PlayerSurface`
- 要印語意樹除錯用 `printToString`（`printToLog` 預設不會出現在測試輸出裡）；有對話框時有多個根節點，用 `compose.onAllNodes(isRoot())[1].printToString(maxDepth = 40)` 印對話框那一個

相關：[儀器測試怎麼跑才算數？](儀器測試怎麼跑才算數.md)
