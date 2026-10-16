# 階段 12：Google Drive 備份與還原

[← 回索引](README.md)

- **日期**：2026-09-28
- **狀態**：完成並實機驗收（含 T12.1 手動設定、T12.7 appDataFolder 研究）
- **計畫**：[`2026-09-27-階段12-google-drive備份與還原.md`](../superpowers/plans/2026-09-27-階段12-google-drive備份與還原.md)
- **做法**：subagent-driven-development，14 個任務逐個實作與審查，再跑一次全分支最終審查（opus）

## 做了什麼

把帳號頁「備份」的佔位畫面換成真的備份與還原：

- `BackupStore`／`DriveBackupStore`：Drive REST v3 直打 OkHttp，appDataFolder，可續傳上傳
- `GoogleAuth`／`GisGoogleAuth`：Credential Manager 取顯示身分＋`AuthorizationClient` 取 `drive.appdata` 授權
- `BackupSnapshotter`（`VACUUM INTO`）、`BackupManager`（快照 → 上傳 → 保留 3 份 → 時間戳；WorkManager 每日排程與【立即備份】共用）
- `RestoreManager`：下載 → SHA-256 → `integrity_check` → schema 版本 → 原子換檔 → 清空 `cache.db` 與草稿
- 畫面：hero 連結狀態與頭像字母、備份子畫面（上次備份、立即備份、從 Drive 還原、中斷連結）、還原挑選與確認、全新安裝的首次開啟選擇（【從 Google Drive 還原】／【全新開始】）
- 純邏輯（gzip＋SHA-256、檔名、保留策略、續傳 chunk 計算）在 `:core`

## 決議

- **T12.1**（2026-09-28 完成）：Google Cloud 專案、OAuth 同意畫面發佈到 Production、Android 與網頁兩種 OAuth client、debug SHA-1 登記、`google_signin_web_client_id` 換成真的值。**release 憑證的 SHA-1 等正式簽名 keystore 建好再登記**（見[已知缺口](../已知缺口.md)）。
- **Android client 的 SHA-1 是單一欄位**（新版 Google Auth Platform 介面沒有「新增指紋」），所以建了兩個 client：`videoshot-dev`／`videoshot-release`，套件名稱都是 `com.xenyaa.videoshot`，功能等效。
- **T12.7：同一個 Cloud 專案下不同 OAuth client 共用 appDataFolder**。appDataFolder 以「Google 帳號＋Cloud 專案」為界，跟 client 無關（用 OAuth Playground 換網頁類型的 client 也查得到 Android client 上傳的備份）。已寫回規格第十節；日後 iOS 或 Chrome 擴充功能掛在同一個專案下就看得到同一份備份。

## 驗收

- JVM **766 個**（`:core:test` 167 ＋ `:app:testDebugUnitTest` 599，2026-09-28）全綠，用乾淨 `git worktree` 獨立重跑過。
- `DriveBackupStoreTest`／`BackupSnapshotterTest`／`RestoreManagerTest` 共 13 個 androidTest 在 2107113SG 跑過全過。
- **實機完整走過一次**（2026-09-28）：連結 Google 帳號（Credential Manager 選帳號後直接進入已連結，沒有另外跳 Drive 同意畫面）、頭像字母（「Scott Lin」→「S」）、【立即備份】（「上次備份」顯示「剛剛」）、【從 Drive 還原】（清單的日期／張數／裝置／大小正確，還原後 app 重啟、資料完整、連結狀態保留）、【中斷連結】。

## 教訓

- **全分支最終審查抓到五個整合性問題**（拆成 14 個任務後組裝起來才顯形），都已修：
  1. 全新安裝點【從 Google Drive 還原】是死路（沒有連結入口，還誤報「網路問題」）→ 先連結、需要同意畫面時跳出來
  2. 沒連結的裝置每天還是做一次完整快照再失敗 → `BackupManager.runIfDue()` 一開始先檢查連結
  3. 舊安裝升級上來被首次開啟畫面擋住 → 本機已有收藏就視同回答過
  4. 還原與備份的清尾步驟失敗會讓成功的主流程被回報成失敗，換檔失敗甚至卡在已關閉的 DB → 清尾盡力做、不影響主結果，換檔失敗照樣重啟
  5. 連結／中斷連結失敗時畫面看不到錯誤 → 改走備份子畫面的錯誤欄位
- **【中斷連結】第一次在實機就炸**：`GisGoogleAuth.unlink()` 組 `RevokeAccessRequest` 時少了 `.setScopes(...)`，Play Services 內部 NPE。編譯期看不出來，是只編譯驗證過、從沒真的呼叫過 Play Services 的那種 bug（commit `52032c8`）。
- **測試用的 OkHttp 要關 `retryOnConnectionFailure`**，不然真機上模擬斷線會被透明重試吃掉；正式環境保留預設。
- **incremental 編譯快取蓋住了壞掉的測試檔**，從此每個大階段收尾前用乾淨 `git worktree` 全新編譯重跑。
- 以上三條的細節見問答 [為什麼只編譯過、單元測試綠燈還不夠？](../問答/只編譯過為什麼不夠.md)。
- Task 13 曾因額度上限中斷、留下半成品；接手的 subagent 先跑測試找出真正卡住的原因（少一行 `Dispatchers.setMain`）才續做，省下重工。
- **擱置四項**（不影響正確性）見[已知缺口](../已知缺口.md)。
