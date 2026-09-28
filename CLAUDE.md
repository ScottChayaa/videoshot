# videoshot

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin ＋ Jetpack Compose；iOS 暫不做）。

- **applicationId／Kotlin 套件：`com.xenyaa.videoshot`**（上線後不可改）。
- 舊名 **yt-space**（2026-09-11 改名）。舊名仍留在 `src/`、`static/`、`tests/`（web 版，清理階段整批刪除）
  與原型的 localStorage key `ytspace2_*`，這些刻意不改。

**目前進度：階段 0～3 完成，階段 4a（精靈外殼與第一步）完成（2026-09-14）、階段 4b（縮圖牆與收斂）、階段 4c（截圖與效能閘門）完成（2026-09-15）、階段 6（第三步、完成、草稿）、階段 7（App 外殼、首頁、Lightbox）完成（2026-09-16）、階段 8（分類資料夾）完成（2026-09-23）、階段 9（詳情頁）完成並實機驗收（2026-09-24）、階段 10（查詢）完成（2026-09-25，2026-09-26 完成全分支最終審查修正）、階段 11（帳號頁、設定、標籤管理）完成（2026-09-27）、階段 12（Google Drive 備份與還原）完成程式碼與全分支審查修正（2026-09-28，實機驗收待 T12.1 手動設定）。
三套測試：JVM **653 個**（`:core:test` 143 ＋ `:app:testDebugUnitTest` 510，2026-09-26）全綠；
**儀器測試 `OK (150 tests)`**（`am instrument`，2107113SG 實機，2026-09-24）——階段 9 Task 1 新增的 4 個 androidTest 方法已在實機上真的跑過並通過。
階段 10 新增了約 23 個 androidTest 方法（`SearchRepoTest.kt`／`OkHttpGeminiClientTest.kt`／
`AppSettingsTest.kt`，涵蓋 FTS5／CTE／row-value SQL、Gemini HTTP 往返、Keystore 加解密往返）。
**2026-09-27 補測**：這 23 個已在 2107113SG 實機上用 `am instrument` 真的跑過並全過——
`-e class com.xenyaa.videoshot.data.SearchRepoTest,com.xenyaa.videoshot.query.OkHttpGeminiClientTest,com.xenyaa.videoshot.data.AppSettingsTest`
→ `OK (35 tests)`（14＋5＋16，`AppSettingsTest` 的 16 個裡有 9 個是階段 10 以前就有的舊案例，
一起跑不影響結果）。跟階段 11 一起補測，兩階段累積的 androidTest 積欠（23＋10＝33 個新方法）
已全部清掉，不再是「只編譯驗證過」。
階段 10 新增查詢頁（標籤與地點多選、文字查詢的規則式／Gemini 解析、FTS5 相關度檢索）。
Gemini 解析路徑（有金鑰時）**尚未在實機驗過**——金鑰輸入畫面是階段 11 帳號頁的範圍，
本階段只在單元測試裡用假的 GeminiClient 驗證邏輯；沒有金鑰時的規則式解析路徑已可在實機驗。
**兩個已知的規格／手冊偏離**（實作了但先前沒有明確記錄成承認的缺口）：
(1) 標籤與地點模式與描述（文字）模式目前互斥，不能同時套用兩種條件（規格第六節提到「可以並用」，
本階段尚未實作，留待之後）；
(2) 手冊要求的「聽懂了：… [修改]」文字查詢摘要，本階段沒有做獨立的 [修改] 按鈕，改用左上角的
返回鍵回到條件畫面達到同樣效果。
階段 10 實作期間的程式碼審查另外發現一個**既有**（非本階段引入、只是接查詢頁時順帶注意到）的
落差，決定先記錄、不在本階段修：`AppRoot.kt` 的批次編輯【完成】流程完成後會重新整理首頁、分類、
資料夾、詳情頁，但沒有連帶重新整理查詢頁——如果使用者當下停在查詢分頁且已經有結果，這時候從
批次編輯改了圖資，查詢結果列表可能會繼續顯示圖資過期的那幾列，要等使用者自己重新查一次才會更新。
（「刪除整支收藏」原本也有同一種落差，已在 2026-09-26 的最終審查修正裡接上查詢頁同步，
見 `SearchViewModel.onVideoDeleted`／`AppRoot.kt` 的 `onDeleteVideo`。）

階段 11 完成帳號頁、設定與標籤管理，路線圖 T11.1～T11.4 全部四項都做完：漸層 hero ＋ 三格統計 ＋
六格選單、圖示語意齊全（`AccountScreen`／`AccountMenuRow`，`VsIcons.kt` 新增 9 個圖示）；設定頁的
過濾相似強度與 AI 分析區間存進 DataStore（`AppSettings.aiRangeBeforeSec`／`aiRangeAfterSec`／
`setAiRange`）；儲存用量顯示（`ThumbsUsageScreen`，掃 `thumbs/` 目錄與 `library.db` 檔案大小）；
Gemini 金鑰輸入／清除（`GeminiKeyScreen`，沿用階段 10 就做好的 Keystore 加密邏輯，這次只補 UI）；
標籤管理——改名即合併（撞名時跳確認框，且不覆蓋既有標籤的 kind／別名）、改 kind（人物／動物／主題／
其他四種）、編輯別名、刪除只解關聯（`TagManagementScreen`／`TagEditSheet`）。
三套測試：JVM **689 個**（`:core:test` 152 ＋ `:app:testDebugUnitTest` 537，2026-09-27）全綠——
比階段 10 完成時的 653 個多了 36 個（`:core` +9：Task 1 的 `TagKindTest` 5 個、Task 2 的 `BytesTest`
4 個；`:app` +27：`NavStateTest` 3 個、`AccountViewModelTest` 9 個、`AccountScreenTest` 4 個、
`CaptureSettingScreenTest` 1 個 ＋ `AiRangeScreenTest` 2 個、`GeminiKeyScreenTest` 3 個、
`TagManagementScreenTest` 5 個）。階段 11 另外新增 10 個 androidTest 方法（`AppSettingsTest.kt`
補 3 個 AI 分析區間案例、新檔 `AccountRepoTest.kt` 共 7 個——2 個帳號統計、5 個標籤管理含改名合併
與刪除）。**2026-09-27 補測，全部通過**：`AccountRepoTest.kt` 7 個方法單獨用
`am instrument -e class com.xenyaa.videoshot.data.AccountRepoTest` 跑，`OK (7 tests)`；
`AppSettingsTest.kt` 的 3 個 AI 分析區間案例跟階段 10 的補測一起跑（見上）。階段 11 新增的
10 個 androidTest 方法（7＋3）已全部在 2107113SG 實機上真的跑過並通過，不再只是編譯驗證。
**五項刻意留到之後的範圍**（跟 scott 確認過，不是本階段的疏漏）：
(1) 主題色系選擇器沒有做——規格第十五節提到「帳號頁，階段 11 的 UI」，但路線圖 T11.1～T11.4
與驗收手冊 §八都沒有列出，跟 scott 確認後這次刻意跳過，留到之後單獨排一個階段；DataStore 存取層
（`themeId`／`NightMode`）已經在階段 7 做好，只差畫面。
(2) 備份（雲）選單列只顯示靜態說明（`ComingSoonScreen`）——Google 帳號連結與備份是階段 12 的範圍，
這次沒有做任何 OAuth 或 Drive 相關的東西。
(3) 縮圖選單列只顯示儲存用量，沒有回填進度——回填是階段 13 的範圍，這次只做規格第九節版面表
「縮圖」那一列會出現的兩件事裡的其中一件（用量），回填進度（「320/1200」之類）刻意留白。
(4) 【中斷連結】按鈕沒有出現在畫面上——因為沒有「已連結」狀態可以中斷（Google 帳號連結是階段 12
才做），手冊 §八第四條字面上要求這顆按鈕，但邏輯上要等連結功能做出來才有意義。
(5) 手冊 §八「設定值真的存得住」「三格統計的數字正確」等條目**尚未在實機驗過**——跟階段 9、10
同樣的但書：這台開發機沒有連接的實機／模擬器（規格第十三節）。

階段 12 用 superpowers:subagent-driven-development 完成，14 個程式碼任務逐個經 subagent 實作與審查、
再跑一次全分支最終審查（opus），把「帳號頁『備份』選單列的 `ComingSoonScreen`」換成真的備份與還原：
`BackupStore`／`DriveBackupStore`（Drive REST v3 直打 OkHttp，appDataFolder，可續傳上傳）、
`GoogleAuth`／`GisGoogleAuth`（Credential Manager 取顯示身分 ＋ `AuthorizationClient` 取
`drive.appdata` 授權）、`BackupSnapshotter`（`VACUUM INTO`）、`BackupManager`（快照→上傳→保留
3 份→時間戳，WorkManager 每日排程 ＋ 帳號頁【立即備份】共用）、`RestoreManager`（下載→SHA-256→
`integrity_check`→schema 版本→原子換檔→清空 `cache.db` 與草稿）、帳號頁 hero 連結狀態與頭像字母、
備份子畫面（上次備份時間、立即備份、從 Drive 還原、中斷連結）、還原挑選與確認畫面、全新安裝的
首次開啟選擇畫面（【從 Google Drive 還原】／【全新開始】）。純邏輯（gzip＋SHA-256、備份檔名、
保留策略、續傳協定的 chunk 計算）放在 `:core`，不依賴 Android SDK。
三套測試：JVM **766 個**（`:core:test` 167 ＋ `:app:testDebugUnitTest` 599，2026-09-28）全綠——
比階段 11 完成時的 689 個多了 77 個，且已用**乾淨的 git worktree 獨立重跑**驗證過（不是只信
incremental 編譯的綠燈；下面「兩個開發期間發現的教訓」有說明為什麼這件事本階段特別在意）。
**2026-09-28 補測，T12.1 完成後**：`DriveBackupStoreTest.kt`／`BackupSnapshotterTest.kt`／
`RestoreManagerTest.kt` 共 13 個 androidTest 方法已在 2107113SG 實機上用 `am instrument` 真的
跑過並全過（`OK (13 tests)`），不再只是編譯驗證。過程中抓到一個只有真機才會顯形的問題並修掉：
`DriveBackupStoreTest` 用 `SocketEffect.CloseSocket()` 模擬上傳中途斷線的兩個測試，在真機的
真實 TCP 環境下，OkHttp 預設的 `retryOnConnectionFailure`（連線失敗透明重試）會把模擬斷線自己
吃掉、直接用新連線重送同一個請求拿到乾淨回應——導致測試真正要驗的
`DriveBackupStore` 自己的 `catch(IOException)`／`queryResumeOffset` 續傳邏輯根本沒被觸發到。
這不是續傳邏輯本身的 bug（實際上傳結果仍然成功，只是走的路徑跟測試預期的不一樣）；修法是**只在
測試用的 `OkHttpClient` 關掉 `retryOnConnectionFailure`**，正式環境（`AppContainer` 組出來的那個
client）保留這個預設值不變——這是合理的韌性行為，不該跟著關掉。
**實機驗收 T12.1（Google Cloud 專案、OAuth 同意畫面發佈到 Production、Android／網頁兩個 OAuth
client、debug SHA-1 登記、`google_signin_web_client_id` 換成真的值）已經在 2026-09-28 做完**；
release 憑證的 SHA-1 要等正式簽名 keystore 建好後再補登記。**帳號連結／備份／還原這條真的碰
Google Drive 的完整路徑（連結帳號→立即備份→從 Drive 還原→中斷連結、全新安裝的首次開啟畫面）
尚未在實機上手動走過一次**——T12.1 完成、androidTest 補測完成，但這段手動 UI 驗收還沒做，下次
接上實機時可以直接走。
T12.7（同一個 Cloud 專案下不同 OAuth client 是否共用 appDataFolder）維持留到之後的研究項目，
不算本階段疏漏。
**開發期間發現並修正的整合性問題**（都不是任何單一任務各自的實作偏離，是拆成 14 個任務後、
組裝起來才會顯形的問題，全分支最終審查才抓到）：
(1) 全新安裝點【從 Google Drive 還原】原本是條死路——沒有連結 Google 帳號的入口，直接去查
備份清單會因為沒有授權而失敗，且訊息還誤導成「網路問題」；已修成先連結、需要同意畫面時跳出來、
成功才進清單。
(2) 沒連結帳號的裝置，WorkManager 原本每天還是會做一次完整的 `VACUUM INTO`＋壓縮再失敗重試，
白耗電與 I/O；已修成 `BackupManager.runIfDue()` 一開始就檢查有沒有連結。
(3) 舊安裝（已經有圖庫資料）升級上來原本會被首次開啟畫面擋住；已修成偵測本機已有收藏就視同
回答過，不再跳出選擇畫面。
(4) 還原流程裡「已經關閉 DB 連線之後」的清尾步驟（換檔、清快取、清草稿）原本失敗會讓一次其實
已經成功的還原被回報成失敗，換檔失敗那條路徑甚至會讓 app 卡在一個已關閉、沒有重啟的 DB 連線上；
備份流程裡保留策略失敗同樣會讓成功的上傳被回報成失敗。兩邊都已修成：清尾步驟盡力做、失敗不影響
主結果，換檔失敗改成照樣觸發重啟。
(5) 帳號頁「連結／中斷連結」失敗時原本沒有畫面看得到（誤寫進一個沒人渲染的欄位，之後還會
莫名其妙冒到標籤管理頁上）；已修成走備份子畫面已經有渲染的錄誤訊息欄位。
**兩個開發期間發現的教訓**（記在這裡，之後幾個階段可以參考）：
第一，Task 13 有一次因為 session 額度上限被中斷，留下未 commit 的半成品；接手的 subagent 沒有
從頭重做，而是先跑測試找出真正卡住的原因（少了一行既有慣例的 `Dispatchers.setMain` 設定）才續做，
省下重工。第二，有一個既有測試檔案缺口曾經被 incremental 編譯快取蓋住、沒被抓到——某個任務改了
一個共用介面，另外三個既有測試檔案理論上該編譯失敗，但因為本機 Gradle 的 incremental Kotlin
編譯沒有重新檢查那幾個檔案，好幾輪「跑測試全綠」都沒發現，直到下一個任務用一次全新編譯才踩到。
從那之後每個大階段收尾前都會多開一個乾淨的 `git worktree` 重跑一次完整測試，不能只信
incremental 編譯的綠燈——階段 12 最後兩次驗證（fix wave 前、fix wave 後）都是這樣做的。
**四項刻意擱置、留待之後的次要項目**（全分支最終審查發現，裁定不影響正確性、不擋這次收尾）：
首次開啟畫面新增的本機資料偵測沒有 timeout／loading 提示（Room 開啟異常慢時會白畫面，跟階段 9
記過的那次 16 秒卡住是同一種風險）；還原換檔前刪 WAL／SHM 側車檔案的順序讓「原本的圖庫沒有變動」
這句失敗訊息稍微說得比程式碼實際保證的更肯定（純理論風險）；還原清尾兩段例外處理對
`CancellationException` 的處理不對稱（無害，只是不一致）；帳號頁頭像字母沒有做 TalkBack 語意
清除，會多唸一個英文字母。

**app 啟動後落在首頁**（階段 2 的資料層冒煙畫面已刪除，內容在 git 歷史），底部導覽五格
（首頁／查詢／取圖／分類／帳號），取圖精靈在第三格。貼網址 → 挑畫面 → 填圖資 → 完成，
這一整段的接線已經做完，圖會真的寫進 `library.db`。

**實機（2107113SG）驗過的範圍**：首頁全部（手冊 §二，含年月分組、月份篩選、封面降級）、
Lightbox（手冊 §三，含刪除後自動停在下一張）、深色模式、縮圖抓不到時的降級預留圖 ——
這些是把種子資料直接寫進 `library.db` 再開 app 驗的。
**詳情頁與批次編輯（手冊 §七）也在 2026-09-24 驗完**：首頁→Lightbox→播放這一段→詳情頁、
點格子換聚焦、編輯圖資存檔、批次編輯圖資、刪除整支收藏，全程走過一次。過程中在三鍵導覽列
機種上抓到一個新 bug——批次編輯頁的【完成】／【套用到 N 張】按鈕整顆畫到系統導覽列底下，
點不到、鍵盤 Tab 也切不進去（`BatchEditScreen.kt` 少包一層 `navigationBarsPadding()`，精靈
第三步的同一顆按鈕靠外層 `Scaffold` 的預設 inset 沒事，這裡沒有 `Scaffold` 就露餡了）——
已修好並在實機上重新點過確認可以按。**手勢導覽的裝置沒有這個問題，只有三鍵／兩鍵導覽列會踩到。**
另外詳情頁第一次進入時偶爾（只發生過一次）卡在「正在載入…」約 16 秒，之後兩次乾淨重裝都沒再
重現、也沒有 logcat 錯誤，研判是那次驗收用外部組好的 SQLite 檔案直接塞進 `library.db`（不是透過
App 自己寫入）才踩到的邊界情況，非透過此方式塞資料則尚未再遇到，先記錄觀察。
**手冊 §零 也在 2026-09-18 驗完**：平板寬度加欄（`wm size 1280x800` ＋ `wm density 240` ＝ 853dp → 5 欄，
驗完 `wm size reset`／`wm density reset`）、TalkBack 要唸的名稱（`uiautomator dump` 每張縮圖都有
「片段縮圖 MM:SS」或它的描述）、實體鍵盤焦點框（首頁縮圖、導覽五格、Lightbox 的關閉與動作鈕都看得到框）。
**送 Tab 要用 `adb shell input keyboard keyevent 61`** —— 不加 `keyboard` 這個來源，
系統不會離開觸控模式，`clickable` 就不可聚焦（Compose 的 `focusableInNonTouchMode`），畫面上什麼都不會發生。

**取圖精靈也已經在實機上從頭走過一次**（2026-09-17）：貼 `youtu.be/aqz-KE-bpKQ` → watch page 抓到、
storyboard 裁出真圖、收斂回報「119 張候選、隱藏 9 張」→ 挑 3 張 → 第三步自動帶入上傳日期 2014-11-10 →
【完成】→「還有 3 張沒填資料」提醒但不阻擋 → 回首頁並捲到 2014年11月。全程正常。

**尚未在實機驗過的**：TalkBack 實際開起來走一遍（只確認過每個可點的東西都有名稱，沒有驗唸讀順序）。
焦點框在主色按鈕（Lightbox 的【播放這一段】）上是同色相疊同色相，看得出來但不明顯。
**階段 9 的驗收刻意跳過**：TalkBack、鍵盤操作、平板寬度、深色模式這幾項——詳情頁與批次編輯頁
沿用既有的共用元件（`AppTheme`、`focusRing` 等），風險低，跟 scott 討論後留到之後幾個階段一起補驗
（見 `docs/superpowers/plans/2026-09-24-階段9-詳情頁.md` 附近的驗收決議紀錄）。

**測試怎麼跑**（三套，環境限制見規格第十三節）：
`./gradlew :core:test`（JVM 純邏輯）、`./gradlew :app:testDebugUnitTest`（**Compose UI 走 Robolectric，跑在 JVM**）、
`./gradlew :app:connectedDebugAndroidTest`（資料庫與網路，實機）。
儀器測試有兩個陷阱，每次都會踩：

1. **Gradle 跑完會把 app 解除安裝**（AGP 的 `uninstall-after-tests=true`，日誌可見 `Uninstalling com.xenyaa.videoshot`），
   **裝置上的 `library.db` 會一起消失**。要在實機上用種子資料驗收，順序是「先跑儀器測試、再塞資料」，
   不然資料會被下一次測試吃掉。
2. **安裝被擋時 Gradle 仍回報 BUILD SUCCESSFUL、exit code 0，但一個測試都沒跑**
   （MIUI 的「透過 USB 安裝」會自己關掉；新版 AGP 只產 `.pb` 不產 XML，用 XML 數測試數會誤判成 0）。
   不要相信 BUILD SUCCESSFUL，自己跑才看得到數字：

   ```bash
   adb install -r -t app/build/outputs/apk/debug/app-debug.apk
   adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb shell am instrument -w -e package com.xenyaa.videoshot \
     com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
   ```

   最後一行印出 `OK (N tests)` 才算數（2026-09-23 那一次是 `OK (143 tests)`）。

**舊的進度描述（階段 0～3）：**
`android/` 有可建置的 `:app` 與 `:core`，共 **133 個測試**。
資料層（`library.db`／`cache.db`／FTS5／`LibraryRepo`）、`youtube`（watch page 解析與五種失敗分類、OkHttp 抓取）、
`thumbs`（`thumbFor` 單一讀取入口、sheet 下載裁切成 WebP）、dHash 與收斂演算法都已就緒。
下一步是**階段 9**（詳情頁）；**階段 14**（清理 web 程式碼）也隨時可做。**原本的階段 5 已併入 4c**。
細節計畫只為即將動工的階段撰寫；沒有細節計畫的階段，先用 superpowers:writing-plans 產出再動工。

階段 0 的結論已寫回規格（第二節第 5、7 點、第五節、第十一節、第十二節），POC 程式碼已刪除、內容留在 git 歷史。
三個對實作有直接影響的結論：**截圖用 JS canvas**（不是 PixelCopy）、
**播放器依 `playabilityStatus.playableInEmbed` 決定載入方式且 embed 必須模擬點擊才會播**、
**InnerTube 不帶 storyboard spec，回填只能走 watch page**。

---

## 動工前必讀（依這個順序）

| # | 文件 | 角色 | 什麼時候看 |
|---|---|---|---|
| 1 | [`docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md`](docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md) | **主文件**（路線圖）。15 個階段、依賴、驗收條件、風險；各階段的細節計畫從這裡連出去 | 每次動工 |
| 2 | [`docs/superpowers/specs/2026-09-10-videoshot-app-design.md`](docs/superpowers/specs/2026-09-10-videoshot-app-design.md) | **規格**。技術事實（storyboard、watch page 欄位、trigram 限制）、架構與模組邊界、資料模型、備份／回填、POC | 動手寫某個模組前，讀對應章節 |
| 3 | [`mockups/uiux-v2/驗收操作手冊.md`](mockups/uiux-v2/驗收操作手冊.md) | **UI 驗收標準**。計畫的驗收條件引用它的原文；標〔app〕的條目只在 app 上驗 | 做完一個任務要驗收時 |

`mockups/uiux-v2/` 的 HTML 是 **UI 的視覺參考**（以 Compose 重寫，程式碼不沿用），但它落後於驗收手冊（手冊是目標狀態）。
兩者不一致時**以手冊為準**；手冊與規格不一致時**以規格為準**。

---

## 開始實作時怎麼說

**不要說**「照計畫做」—— 任務不會一次做完。

**要說**做哪一段，例如：

```
執行實作計畫的階段 2
```

---

## 文件的維護規則

**規格永遠是現況，git 是歷史，計畫是排程。** 三者不重疊。

- 規格有異動就**改本文**，不在頂部累積變更紀錄 —— 讀者不該先讀到過期的內容、
  再回頭套用一張修訂表。
- 舊的規格與計畫直接刪除，內容留在 git 歷史；其中仍然有效的結論要**先併入現行規格再刪**
  （2026-09-01 與 2026-09-10 都是這樣處理的，成果見規格的附錄 A）。
- **尚未定案的地方就地標 ⏳**，寫在該條款旁邊。規格第十六節另有一張索引表，新增或解決 ⏳ 時兩處一起改。

---

## 不能刪的東西

**`src/` 已不是實作的輸入**（web 版程式碼），會在計畫的清理階段**一次刪除**。在那之前不要零散刪除，特別是：

- **`src/lib/storyboard.ts`** —— `mockups/server.mjs:42` **在執行期讀取它**並轉譯成
  `/shared/storyboard.js`。刪掉它 `pnpm mock` 就開不起來，UI 原型每一頁都會壞
  （原型是驗收基準）。清理階段要**先把它搬進 `mockups/`** 再刪 `src/`。
  它連同 `storyboard.test.ts` 也是 Kotlin 版 `storyboard`（`:core` 模組）的移植來源。

`test-results/`、`tmp/`、`.svelte-kit/`、`.wrangler/` 都在 `.gitignore` 裡，隨時可刪。

---

## 詞彙

規格與計畫一律用左欄；mockup 的程式碼還是右欄，讀原型時要換算。

| 正式 | mockup | 說明 |
|---|---|---|
| `shot` | `clip` | 同一個東西 |
| `at_sec` | `start` | 單一時間點。`end` 已移除，不存結束時間 |
| `description` | `summary` ＋ `note` | 已合併為一欄 |
| `place` | `tag.kind='place'` | 已獨立成欄位，`tag.kind` 不再有 `place` |
| 帳號 | 「設定」 | 導覽列第五格。「帳號」指**備份用的 Google 帳號**，app 本身不需要登入 |
| 【截圖】 | 【手動補圖】 | 在播放器上一鍵截圖；失敗時退回相簿選圖 |

---

## 指令

```bash
pnpm mock         # UI 原型（需要 src/lib/storyboard.ts 存在）
```

```bash
cd android
./gradlew :core:test        # :core 的 JVM 單元測試
./gradlew assembleDebug     # 建置 debug APK
./gradlew installDebug      # 安裝到 USB 連接的手機
```

這台開發機**系統 PATH 上沒有 java**，直接跑 `./gradlew` 會失敗。用 Android Studio 內建的 JDK：

```bash
export JAVA_HOME=/snap/android-studio/current/jbr
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools   # adb
```

**pnpm 只管 JS／TS**（`mockups/`、未來的 `extension/`）；Kotlin 用 Gradle、未來的 Swift 用 Xcode／SwiftPM。
各目錄各自建置，根目錄不設統一的建置協調器（規格第三節「工具鏈」）。

---

## 慣例

- **commit message 用繁體中文**，格式 `類型(範圍): 描述`，不加 AI 生成標記。
- **模組邊界**（規格第三節）：
  - 所有 DB 存取只走 **repo**。
  - 縮圖讀寫只走 **`thumbs`**，畫面只呼叫 `thumbFor(shot)`。
  - 所有對 YouTube 非官方端點的存取只走 **`youtube`**。
  - 截圖實作藏在 **`capture`** 介面後面。
  - Google Drive 只在 **`backup`** 裡用。
- **無法重建的在 `library.db`（要備份），DB 外面的都能重建**（`cache.db`、`thumbs/`、草稿都不備份）。
- **DB 裡不存檔案路徑**：縮圖以邏輯識別碼 `{videoId}/L{level}/{frameIndex}` 定位，`library.db` 的 schema 是跨平台資料格式（規格第四節「跨平台的資料契約」）。
- **重運算（裁切、dHash）放背景執行緒**（`Dispatchers.Default`），不卡 UI 執行緒。純邏輯放 `:core`（不依賴 Android SDK）。
