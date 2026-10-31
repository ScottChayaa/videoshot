# 階段 23（前半）：送審前補齊 YouTube API 開發者政策

[← 回索引](README.md)

- **日期**：2026-10-31
- **狀態**：app 端完成並實機走查；錄示範影片、在 Cloud Console 送審由 scott 做（[計畫](../superpowers/plans/2026-10-31-階段23-送Google審核.md) Task 4、5）
- **計畫**：[`2026-10-31-階段23-送Google審核.md`](../superpowers/plans/2026-10-31-階段23-送Google審核.md)

## 做了什麼

- **導出時選公開狀態**：導出抽屜在標題下面加「公開狀態」三列單選：非公開（預設）／公開／私人。選私人多一行小字「私人影片無法在 app 裡取圖」。原本「以「非公開」上傳到你的 YouTube」那行拿掉。
- **私人影片不接回取圖**：上傳完直接算已完成、不等縮圖；已完成畫面寫「已上傳到 YouTube（私人）」＋「私人影片無法在 app 裡取圖」，按鈕是【刪除原檔】【保留】（素材都不在時只有【完成】），按了移除任務、回剪輯頁，不問取圖草稿。
- **已完成畫面的標題照公開狀態**：「已上傳到 YouTube（非公開／公開／私人）」；剪輯頁空狀態的副標拿掉「（非公開）」。
- **app 裡的連結**：導出抽屜加「上傳即表示同意 YouTube 服務條款」（點了用瀏覽器開條款）；帳號頁最下面加「隱私權政策」一列（開網站的隱私權政策）。手機上沒有能開網頁的 app 時什麼都不做。
- **資料**：`cache.db` 5 → 6，`edit_task` 加 `privacy`（`'unlisted'`／`'public'`／`'private'`，舊任務是 `'unlisted'`）。
- **網站**：隱私權政策改成「照你選的公開狀態（預設非公開）上傳」，日期改 2026-10-31（**還沒部署**，要 scott 在 `site/` 執行 `wrangler deploy`）。
- **範圍說明定稿**寫回規格第十九節「Google 授權與 YouTube 權限」。

## 決議

- scott（2026-10-31）：
  1. 加公開狀態選項，預設非公開（YouTube API 開發者政策要求讓使用者選公開、私人或非公開）。
  2. 導出抽屜與帳號頁兩處都加連結（政策要求 app 裡顯示 YouTube 服務條款、隱私權政策隨時看得到）。
- Claude 定的（計畫裡標了 ⏳，scott 可推翻）：私人影片不等縮圖、不接回取圖，已完成畫面只問刪原檔。

## 驗收

- JVM 測試 **2006 個**全過（`:core:test` ＋ `:app:testDebugUnitTest`；階段 22 後是 1993 個）。
- 儀器測試（實機 2107113SG，`am instrument`，`CacheMigrationTest`＋`EditTaskRepoTest`）：`OK (28 tests)`，含新的 v5 → v6 遷移。
- 實機走查（2026-10-31）：
  - 裝新版後 `cache.db` 升到 v6，舊的已完成任務照樣顯示「已上傳到 YouTube（非公開）」與階段 22 的刪除問句。
  - 導出抽屜：公開狀態預設非公開；點私人出現小字；點「YouTube 服務條款」用 Chrome 開到 `youtube.com/t/terms`。測完放棄這次的剪輯草稿（scott 原本沒有剪輯草稿），沒按【開始】、沒上傳。
  - 帳號頁最下面有「隱私權政策」，點了用 Chrome 開到 `videoshot.scottchayaa.com/privacy`（還是 10/26 那一版，網站還沒部署）。
- **沒在實機驗的**：真的以公開、私人上傳（要上傳到 scott 的頻道，沒做）、私人影片的已完成畫面與【刪除原檔】【保留】。
- 最終審查：作者自審（沒有另外派審查代理）。

## 教訓

- **抽屜（`ModalBottomSheet`）是另一個視窗，會自己提供 `LocalUriHandler`**：在外面用 `CompositionLocalProvider` 換掉的測試替身傳不進去。抽屜裡的連結改用 Robolectric 看實際送出的 `Intent`（`shadowOf(application).nextStartedActivity`）。
