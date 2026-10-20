# 縮圖牆的格數為什麼比 storyboard spec 的 frameCount 少一格？

[← 回目錄](README.md)

- YouTube 的 spec 一律給 **ceil(片長 ÷ 間隔) ＋ 1** 格，第 k 格代表 k × 間隔，所以**最後一格的時間點總是超過片長**
  - 例：178.8 秒、每 2 秒一格 → spec 寫 91 格，最後一格標 180 秒（畫面上是 03:00）
  - 那一格的圖是片尾附近的畫面，但時間標錯了；入庫的話 `at_sec` 會超過片長
- 縮圖牆只放時間點 ≤ 片長的格子：`Storyboard.frameCountWithin(level, 片長秒數)`，`framePlanOf` 與 `StoryboardFrameSource` 都用它算出的格數
- **片長要用精確的毫秒數**：`streamingData` 各格式的 `approxDurationMs` 取最大值（`VideoMeta.durationMs`）
  - `lengthSeconds` 是四捨五入過的整數：281.5 秒記成 282，282 秒那一格就判不掉
  - 讀不到毫秒數（沒有 `streamingData`、舊版草稿）才退回 `lengthSeconds`
- 自己查一支影片的數字：

  ```bash
  curl -s -A "Mozilla/5.0" "https://www.youtube.com/watch?v=<videoId>" -o w.html
  grep -o '"approxDurationMs":"[0-9]*"' w.html | head -1
  grep -o '"spec":"[^"]*"' w.html | head -1 | tr '|' '\n' | tail -1 | cut -d'#' -f3,6   # 格數#間隔毫秒
  ```

- 詳情頁拖曳預覽沒有截掉這一格：它用 `frameIndexAt` 取「最近的一格」，拖到片尾半個間隔內時會顯示多出來那一格的圖 —— 那張本來就是片尾附近的畫面，只是預覽，不入庫

相關：[怎麼用 adb 驅動手機做驗收？](用adb驅動手機驗收.md)
