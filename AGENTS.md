# videoshot

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin ＋ Jetpack Compose；iOS 暫不做）。

- **applicationId／Kotlin 套件：`com.xenyaa.videoshot`**（上線後不可改）。
- 舊名 **yt-space**（2026-09-11 改名）。web 版程式碼已於階段 14 刪除（2026-09-30，內容留在 git 歷史）；
  舊名只剩原型的 localStorage key `ytspace2_*`，刻意不改。

**目前進度：階段 0～3 完成，階段 4a（精靈外殼與第一步）完成（2026-09-14）、階段 4b（縮圖牆與收斂）、階段 4c（截圖與效能閘門）完成（2026-09-15）、階段 6（第三步、完成、草稿）、階段 7（App 外殼、首頁、Lightbox）完成（2026-09-16）、階段 8（分類資料夾）完成（2026-09-23）、階段 9（詳情頁）完成並實機驗收（2026-09-24）、階段 10（查詢）完成（2026-09-25，2026-09-26 完成全分支最終審查修正）、階段 11（帳號頁、設定、標籤管理）完成（2026-09-27）、階段 12（Google Drive 備份與還原）完成並實機驗收（2026-09-28，含 T12.1 手動設定與 T12.7 appDataFolder 研究）、階段 13（縮圖回填）完成並實機驗收（2026-09-29～30，全分支最終審查修正一個 Critical 與六個 Important；實機驗收另外發現並修掉「純行動網路時掃描永遠不會跑」的缺口）、階段 14（清理 web 程式碼）完成（2026-09-30）、階段 15A（UI 對齊原型：共用元件層與驗收工具）完成（2026-10-01，實機驗收發現兩個卡死現象，2026-10-02 已修）、階段 15B（取圖精靈對齊原型）完成（2026-10-03）、階段 15C（首頁、Lightbox、詳情、資料夾內容對齊原型）完成（2026-10-03）、階段 15D（查詢、分類、帳號對齊原型）完成（2026-10-03），**階段 15（UI 對齊原型）全部完成**，階段 16A（地點表與統計表，library.db v2）完成（2026-10-07）、階段 16B（畫面改讀統計表）完成（2026-10-07）。
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
(1) 標籤與地點模式與描述（文字）模式目前互斥，不能同時套用兩種條件——**2026-10-01 已結案**：
決定維持互斥，規格第六節已改；
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
（`themeId`）已經在階段 7 做好，只差畫面。（`NightMode` 已於 2026-10-06 隨深色模式一起移除。）
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
release 憑證的 SHA-1 要等正式簽名 keystore 建好後再補登記。
**2026-09-28 同一天，帳號連結／立即備份／從 Drive 還原／中斷連結、真的碰 Google Drive 的完整
手動路徑也在 2107113SG 實機上走過一次，全部成功**：連結 Google 帳號（Credential Manager 選擇
帳戶畫面第一次真的跳出來，選完帳號後直接進入已連結狀態，沒有另外跳出 Drive 授權同意畫面——
代表 `drive.appdata` 這個窄 scope 首次要求時 Play Services 沒有另外插一個確認步驟）、hero 頭像
字母（"Scott Lin" 顯示「S」）、【立即備份】（真的 `VACUUM INTO`→壓縮→上傳到 Drive appDataFolder，
「上次備份」正確顯示「剛剛」）、【從 Drive 還原】（清單正確列出剛上傳那份的日期／張數／裝置名稱／
大小，確認框文案正確，還原後 app 真的重啟、資料完整、連結狀態跨重啟保留）。
**過程中發現並修掉一個真正的中斷連結 bug**：第一次按【中斷連結】炸了兩次，都顯示「中斷連結失敗」
——`backupError` 有正確顯示在畫面上（驗證了前一輪修復波 I4 那個「錯誤要顯示在 BackupScreen」的
修正確實有效，不然這個失敗會完全看不到）。這個套件刻意不寫 log，臨時加一行 `Log.e` 重新編譯安裝
才抓到根因：`GisGoogleAuth.unlink()` 組 `RevokeAccessRequest` 時只呼叫了 `.setAccount(account)`
就直接 `.build()`，沒呼叫 `.setScopes(...)`——Play Services 內部對沒設定的 scopes 欄位做
`.toArray()` 直接 NullPointerException。程式碼裡的註解其實早就寫著「官方範例是
`.setAccount(account).setScopes(scopes)`」，卻忘了真的加那一行——這個缺漏在編譯期完全看不出來
（語法合法，只是執行期缺必要欄位），是 Task 7 做完當時**只編譯驗證過、從沒真的呼叫過 Play Services
API** 才會被完全略過的那種 bug，直到這次真機手動驗收才顯形。修好後移除臨時的 debug log，重測
通過，中斷連結正確回到「尚未設定備份」畫面。commit `52032c8`。
**教訓**：這是階段 12 第二次「只編譯驗證過的 androidTest／程式碼在真機上才炸」的案例（第一次是
`DriveBackupStoreTest` 的 OkHttp 連線重試問題）。往後幾個階段如果又有 Play Services／Drive／
其他外部 SDK 呼叫只做到編譯驗證，收尾前應該提醒盡快找機會上真機手動走一次，不能只憑「編譯過＋
單元測試綠燈」就當作完成。
**T12.7（同一個 Cloud 專案下不同 OAuth client 是否共用 appDataFolder）已在 2026-09-28 實測解決：
共用。** appDataFolder 是以「Google 帳號＋Cloud 專案」為界，跟 OAuth client 無關——用
OAuth 2.0 Playground 換一個完全不同的 OAuth client（`videoshot-signin-audience`，網頁應用程式
類型）授權 `drive.appdata`，一樣查得到 `videoshot-dev`（Android 類型 client）上傳的那份備份，
appProperties 完全對得上。結論已回寫規格第十節與第十六節（開放項目索引該列已解除）、計畫文件
Task 15。日後 iOS 版或 Chrome 擴充功能只要掛在同一個 Cloud 專案下即可看到同一份備份。
**這個過程也讓 Task 0 的實際登記方式跟原計畫預期的不一樣**：Google Cloud 重新設計過的
「Google Auth Platform」介面裡，Android 類型 OAuth client 的 SHA-1 欄位是單一欄位，沒有
「新增指紋」這種多值做法，所以改成建立兩個獨立的 Android client（`videoshot-dev`／
`videoshot-release`，套件名稱都是 `com.xenyaa.videoshot`）分別登記 debug／release 的 SHA-1，
功能上等效（Play Services 執行期用套件名稱＋當下簽章憑證比對，跟哪個 client 無關）。
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
首次開啟畫面新增的本機資料偵測沒有 timeout／loading 提示（Room 開啟異常慢時會白畫面；
階段 9 記過的那次 16 秒卡住後來查明不是 Room 慢，見階段 15A 段落）；還原換檔前刪 WAL／SHM 側車檔案的順序讓「原本的圖庫沒有變動」
這句失敗訊息稍微說得比程式碼實際保證的更肯定（純理論風險）；還原清尾兩段例外處理對
`CancellationException` 的處理不對稱（無害，只是不一致）；帳號頁頭像字母沒有做 TalkBack 語意
清除，會多唸一個英文字母。

**階段 13（縮圖回填）完成（2026-09-29）**，用 superpowers:subagent-driven-development 執行，
12 個程式碼任務逐個經 subagent 實作與審查、再跑一次全分支最終審查（opus）＋一輪 fix wave：
新增 `BackfillManager`（掃描缺圖→依 `(videoId, sbLevel)` 分組抓 watch page→呼叫
`SheetHarvester` 下載裁切→依狀態機轉 `ok`／`missing` 退避／`lost`）、`BackfillWorker`
（WorkManager 一次性作業鏈，自我接續，`DelegatingWorkerFactory` 跟既有的每日備份 worker
共存）、`FetchResult.RATE_LIMITED`（HTTP 429 分類，回填層把它跟 `PARSE_FAILED` 一視同仁地
「整批暫停」）、`SheetHarvester.harvestRelocated`（storyboard 層級消失時用 `at_sec` 在新層級
重新定位，裁出來的圖仍寫回原識別碼）、`FileThumbs` 分辨「還在等回填」與「已確定 lost」
（lost 顯示中性預留圖）、帳號頁「縮圖」子畫面顯示回填進度、【稍後重試】【刪除這些收藏】
【用行動網路繼續】。
三套測試：JVM **797 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 627，2026-09-29）
全綠，已用乾淨 `git worktree` 重新編譯驗證過兩次（fix wave 前、fix wave 後，延續階段 12
立下的規矩）。新增的 androidTest（`CacheDbTest`／`LibraryRepoReadTest`／`SheetHarvesterTest`／
`ThumbsTest`／`OkHttpYoutubeTest` 各補了幾個方法）**只編譯驗證過，沒有一個真的在實機上跑
過**——這台開發機這次同樣沒有接裝置。
**全分支最終審查抓到 1 個 Critical＋6 個 Important**，全部屬於「拆成 12 個任務後、組裝起來
才會顯形」的整合性問題，不是任何單一任務各自的疏漏：
(1) `SheetHarvester` 對「目標檔案已經存在」的格子完全不回報（既不在 written 也不在
failed），`BackfillManager` 原本只處理這兩份清單，該格會永遠卡在 missing、每輪重抓一次
watch page、`BackfillWorker` 因為 `remaining` 恆真而無限自我重排——唯一煞車是意外撞上
YouTube 限流；已修成 `runBatch` 先用 `thumbs.exists()` 把到期格子裡檔案已經在的直接標
ok，`harvestAtLevel` 收尾再補一層「沒被回報的一律當失敗處理」的保險。
(2) 原本假設「同一支影片的 storyboard shot 一律共用同一個 sbLevel」，但這個假設會被同一支
影片分兩次取圖、中間 YouTube 換過 spec 的情境打破（既有的 `takenFrameIndexes(videoId,
level)` 帶層級參數正是同一個理由）；已修成依 `(videoId, sbLevel)` 分組處理，watch page
仍然每支影片只抓一次。
(3)【稍後重試】原本只重設 DB 狀態沒有排工作，要等下次開機才會真的處理；已修成同時呼叫
`scheduleBackfill()`。
(4) 手冊明文要求的【用行動網路繼續】按鈕，`AccountDeps`／`AccountViewModel`／
`AppContainer` 的接線都做好了，但 `ThumbsUsageScreen.kt` 實際上從頭到尾沒有加這顆按鈕；
已補上。
(5) 進度文字「縮圖回填中 N/N」的顯示條件用 `total > 0`，但 `total = ok + missing` 裡的
`ok` 只增不減，一般裝置上恆為真，進度文字會永久掛在畫面上；已改成 `total > done`（等同
`missing > 0`）。
(6)【刪除這些收藏】刪完不會通知首頁／分類／查詢／帳號頁重查，跟這個檔案其他刪除路徑的
既有慣例不一致；已補上（含查詢分頁的 `phase == RESULTS` 才重查的保護，避免使用者只勾了
條件還沒查詢就被硬拉進結果畫面）。
(7) 跨層級重新定位的座標換算（規格第十一節步驟 2b，本功能最細節的一段邏輯）在
`BackfillManager` 層完全沒有測試覆蓋（假物件把五個參數全部丟棄）；已補上會實際比對
`Storyboard.frameAt` 換算結果的回歸測試。
Fix wave 之後的 scoped re-review 又發現 3 個 Minor 並全部 park，判定不影響正確性，不擋
收尾：`harvestAtLevel` 裡一段已經不會被讀取的死程式碼式 merge（可讀性瑕疵，不影響行為）；
【刪除這些收藏】在文字查詢模式下會意外多觸發一次 Gemini 重新解析（有金鑰時，低頻操作，
不是資料正確性問題）；進度卡片沒有進一步區分「到期」與「還在退避中」的缺圖，理論上使用者
可能按了【用行動網路繼續】卻發現當下沒有東西可處理（`BackfillProgress` 模型本身的既有
限制，不是這次修壞的）。
**2026-09-30 實機驗收（2107113SG）**：全部 **210 個 androidTest 真的在裝置上跑過**
（`am instrument` → `OK (210 tests)`），這階段新增的方法（`OkHttpYoutubeTest`／
`CacheRepoTest`／`LibraryRepoReadTest`／`SheetHarvesterTest`／`ThumbsTest` 各補的案例）
不再只是編譯驗證。手動走了完整的 WorkManager 排程路徑：裝置純行動網路（Wi-Fi 關）時
`dumpsys jobscheduler` 確認回填工作真的排進去但 `Ready: false`（正確卡住等 Wi-Fi）；
切到 Wi-Fi 後工作真的執行，對假造的 `seed001` videoId 真的打了一次 YouTube watch page、
正確分類成 `VIDEO_UNAVAILABLE`、5 張全部轉 `lost`；按【稍後重試】時 job history 顯示
新工作立即執行、重新走一次流程；按【刪除這些收藏】後**同一個 session、不用重開 app**，
帳號頁統計與首頁列表立即同步更新（驗證了 Important 6 的修法）。
**過程中發現一個新的缺口並已修掉**：`scanForMissing()` 原本只在受 Wi-Fi 約束的
`BackfillWorker.doWork()` 裡執行——裝置純行動網路時實測 `cache.db` 的 `thumb_state`
永遠是空的，帳號頁完全不知道有缺圖，連【用行動網路繼續】按鈕的顯示條件都成立不了，
回填功能形同隱形。這是單元測試與全分支審查都測不到的情境（測試直接呼叫 `runBatch()`，
不會模擬 WorkManager 的網路約束）。修法：新增 `BackfillScanWorker`（沒有任何
constraint，純本機 DB／檔案掃描），開機時獨立排程，跟受 Wi-Fi 約束的 `BackfillWorker`
分開；已在實機上（純行動網路、Wi-Fi 關閉）重新驗證：`thumb_state` 正確填入
`missing` 列、帳號頁正確顯示「縮圖回填中 0/3」與【用行動網路繼續】按鈕。三套測試補到
JVM **799 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 629）。

**階段 15A（UI 對齊原型：共用元件層與驗收工具）完成（2026-10-01）**，是階段 15 四份計畫（15A～15D）的第一份，
設計結論（階段 15 收尾時已併入規格第三節「UI 元件層」與第六節「UI 與原型的差異」，原設計文件已刪除、留在 git 歷史）。`ui/common/` 新增共用元件：
`VsTopBar`（含 `TopBarIconButton`／`TopBarNav`／`TopBarTitle`）、`VsBottomNav`（含 `avatarInitialOf`）、
`VsTagChip`（`ChipKind`／`ChipSize`）、`VsButton`（`ButtonVariant`）、`VsListRow`、`VsEmptyState`、
`VsActionDock`、`VsStepIndicator`、`VsUnderlineTabs`、`VsToolbarPill`（`PillStyle`）、`VsHintCard`；
主題補 `KindColors`、`Spacing.topBarHeight`，圖示補 `MapPin`、`Film`。
**現在看得到的 UI 改動**：首頁、查詢、分類、資料夾內容、詳情、批次編輯、帳號與其子畫面全部換成共用頂欄
（固定 60dp 高＋1dp 分隔線，標題 20 粗體）；底部導覽的「取圖」格是主色實心方塊、帳號格連結 Google 後
顯示頭像字母；帳號頁在 hero 上方多了一條「帳號」頂欄。**取圖精靈與 Lightbox 沒動**（留給 15B／15C）。
**開發測試版專用工具**（`app/src/debug/`，正式版不含，已用 release APK 的 dex 驗證不含任何 debug 類別）：
元件總覽頁、假資料匯入（`pnpm seed` 從原型轉出 JSON，`SeedReceiver` 一個 adb broadcast 就能取代
整個圖庫，13 張／11 支影片／5 個資料夾／20 個標籤，匯入後自動排縮圖回填）、
`pnpm compare` 原型與實機對照截圖；用法見下方「指令」。
**執行期間的裁定**：步驟列每段最小高 44dp（觸控目標），所以比原型鬆；假資料是 11 支影片（計畫原寫 12）；
假資料的加入時間用秒，與正式資料一致。
三套測試：JVM **846 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 676，2026-10-01）全綠——
比階段 13 完成時的 799 個多 47 個（`:core` 數量不變，`:app` +47）；已用乾淨 `git worktree` 全新編譯
重跑一次（`:core:test` 170／`:app:testDebugUnitTest` 676／`:app:assembleRelease`，84 個 task 全部重新執行），
數字一致。**儀器測試 `OK (210 tests)`**（`am instrument`，2107113SG 實機，2026-10-01；本階段沒改 DB／網路，
確認 `AppRoot` 的改動沒有波及）。這次跑完照規矩把假資料重新匯入裝置（`VsSeed done shots=13 folders=5`），
裝置現在是假資料狀態、首頁可直接看到。
**實機驗收發現的兩個卡住問題，2026-10-02 已查出根因並修正**（兩者**不同源**，也**都跟資料庫無關**——
卡住時所有執行緒閒置、DB 讀取 10 毫秒內就回來）：
(1) **詳情頁永遠停在「正在載入…」**（commit `4832d60`）：`DetailViewModel.loadPlayerInfo` 寫成
`_state.value = _state.value.copy(player = …watchPage…)`——Kotlin 先取當下的舊狀態（`loading` 還是 true）
才去等網路，watch page 約 1 秒後回來把那份舊快照整份寫回，蓋掉圖資讀取早就寫好的 `loading = false`。
同樣「先取快照、再等讀取、最後整份寫回」的寫法另有五處一併修掉（詳情頁標籤、首頁／查詢頁月份選項、
資料夾頁子資料夾、帳號頁回填進度；首頁那處會讓晚到的月份把已載入的列表蓋回載入中）。
**慣例：非同步讀取回來要先算結果、再 `_state.update { it.copy(…) }`，不要把 suspend 呼叫寫在
`.copy(…)` 的參數裡。** 階段 9 記的「偶爾卡 16 秒」就是這一個（見階段 9 段落）。
(2) **白畫面不動**（commit `7e1eb2c`）：MIUI 13（Android 12）的「背景 app 停用動畫」
（系統屬性 `persist.sys.disable_bganimate`，預設開）改了 `Choreographer`：行程還沒有可見 surface 時，
動畫類型的 frame callback 只排進佇列、**不要求下一幀**。Compose 在第一幀的 traversal 裡
（`ComposeView.onAttachedToWindow` 建立 Recomposer）就發出第一個幀請求，早於 `relayoutWindow` 建出
surface，被吞掉；`AndroidUiDispatcher` 以為已經排過就不再排，畫面停在第一次組合。
**DataStore 已在記憶體裡時才會踩到**（值在那一刻立刻回來、需要重組），所以不只假資料匯入——
**同一個行程剛被背景工作（備份、回填）喚醒過再開 App，正式版使用者也可能遇到**；`force-stop` 後
DataStore 要重讀檔，值晚一點才到，所以「強制停止再開就正常」。修法：`FrameStallGuard`，`MainActivity`
取得視窗焦點時補要一個空幀。實機同條件對照：拿掉這一行 6 次有 1 次白畫面，修正後 30 次全部正常。
證據是用 jdb 在卡住當下讀 `Choreographer`／`AndroidUiDispatcher` 內部欄位、對 `disableAnimation` 下斷點
抓到呼叫堆疊，並從手機拉 `framework.jar`／`miui-framework.jar` 反組譯確認的（細節見 commit 說明）。
三套測試：JVM **854 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 684，2026-10-02）全綠，
新增 7 個回歸測試修正前全部失敗。
**實機實驗的教訓**：這台手機 10 分鐘無操作就會熄螢幕上鎖，上鎖時 Activity 被暫停、vsync 停止，
看起來跟「畫面卡住」一模一樣——一度把上鎖期間的數據當成有效樣本。**每一輪實驗都要確認
`dumpsys window` 的 `isKeyguardShowing=false` 與 `dumpsys power` 的 `mWakefulness=Awake`**，
不符的樣本作廢；`KEYCODE_WAKEUP` 只會亮到鎖定畫面，有密碼鎖時要請 scott 解鎖。
**手機會自動上鎖，長時間的實機工作要保持喚醒**：10 分鐘無操作就上鎖（有密碼鎖），要跑超過
幾分鐘的實機流程（對照截圖、完整取圖流程、儀器測試）就另外開一個背景迴圈，每 3 分鐘送一次
`adb shell input keyevent KEYCODE_WAKEUP`（跟上面的 `isKeyguardShowing` 檢查搭配使用）；
遇到鬧鐘或通知橫幅蓋住畫面時，截圖要重拍。
**階段 15B（取圖精靈對齊原型）完成（2026-10-03）**，計畫見
`docs/superpowers/plans/2026-10-02-階段15B-取圖精靈對齊原型.md`，用 subagent-driven-development
逐任務實作與審查。**現在看得到的 UI 改動**：精靈外殼換成共用頂欄（影片載入後顯示影片標題）＋步驟條
（可跳回已到達過的步驟）；第一步取圖紀錄改成卡片列、【下一步】收進底部動作列、錯誤訊息用 warn 色；
第二步新格子樣式（已選＝主色框＋左上角打勾，已收藏＝整格變淡＋「已收藏」文字，手動格標「截圖 MM:SS」，
工具列膠囊，只有【只看已選】宣告選取語意）；第三步縮圖區（每列 4 張）、快速選取列、已套用改成右上角
打勾，dock 改原型樣式——抽屜高度設上限，鍵盤開著時聚焦的欄位與底部按鈕都看得見，標籤欄位按
鍵盤的完成鍵＝加入標籤（地點與描述欄位沒有這個行為），套用後底部短暫顯示「已套用到 N 張」。新增共用元件 `VsTextField`，`VsStepIndicator`
加 `furthest`，`VsToolbarPill` 加 `isToggle`。偏離原型的地方都記在規格第六節「UI 與原型的差異」。
`pnpm compare` 新增「取圖第二步」「取圖第三步」兩個畫面（原型端用 `capture.html?new=1` 避開草稿詢問，
實機端遇到「上次做到…要繼續嗎？」自動選【重新開始】；腳本新增 `tapTextPrefix` 與 `optional` 步驟選項）。
三套測試：JVM **900 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 730，2026-10-03）全綠，
（含全分支最終審查的修正與 6 個回歸測試）；比 2026-10-02 修完卡死問題時的 854 個多 46 個，全在 `:app`。
本階段沒動資料庫與網路程式碼，所以沒有重跑儀器測試。**實機（2107113SG）驗過**：`pnpm compare`
淺色＋深色的取圖第一～三步（深色每頁都讀得出來）；完整取圖流程——鍵盤「前往」送出網址、第二步勾 3 張、
【只看已選】來回切、第三步填地點、Enter 加標籤、套用顯示「已套用到 3 張」、【完成】回首頁 toast
「已新增 3 張」；假資料的影片上「已收藏」格子標示正確、點下去出現「這一格已經收藏過了」。跑完已重新匯入
假資料（`VsSeed done shots=13 folders=5`），裝置現在是假資料狀態。TalkBack 唸讀順序仍未實際開起來走過。
**階段 15C（首頁、Lightbox、詳情、資料夾內容對齊原型）完成（2026-10-03）**，計畫見
`docs/superpowers/plans/2026-10-03-階段15C-瀏覽動線對齊原型.md`，用 subagent-driven-development
逐任務實作與審查。**現在看得到的 UI 改動**：首頁與資料夾內容的縮圖改成**正方形**（格線 2dp，唯一的
間距例外）；首頁月份標籤小膠囊帶種類圖示與顏色（地點／人物／動物／主題／其他），觸控區撐到 44dp，
點了跳到查詢頁帶入條件；Lightbox 改原型樣式（關閉／⋯ 頂列、滑動提示、【播放這一段】＋加入分類／分享
兩顆圖示鈕），仍維持純看圖；詳情頁改成播放器 → 圖資卡（描述、地點與標籤小膠囊帶種類、日期、影片秒數、
【編輯這張的圖資】）→「這支影片的收藏（N）」網格（每格右上鉛筆鈕、聚焦那張有框）；資料夾內容頂欄下方多
一條麵包屑「分類 / 資料夾名稱」，子資料夾從橫向卡片改成直向清單列（保留右側 ⋯ 改名／刪除）。
縮圖抓不到時的預留圖換新樣式。新增 `OnDarkColors`（固定深底上的前景色）與 `ThumbTile`（正方形縮圖格）。
**深色模式圖示看不見的根因**：Lightbox 的底色在深淺模式都是固定近黑，但頂列與動作列的圖示原本用
`accentInk`，它在深色色盤裡也是近黑，結果深色模式下關閉／更多／加入分類／分享圖示幾乎看不見；詳情頁縮圖
右上角的編輯鈕同理。改成固定的 `OnDarkColors`（白色）後兩處都清楚可見。
**`pnpm compare` 順手抓到的真 bug（已修）**：首頁月份標籤的小膠囊原本全部是「其他」的菱形圖示——
`ShotDao.monthFacets` 回傳的 `kind` 只有 `place`／`tag` 兩種，畫面端拿它去查標籤種類，永遠落到「其他」。
修法是 SQL 另外帶出 `tag.kind`，`MonthFacet` 加 `tagKind` 欄位（`kind` 原值不動，查詢頁的 `facetKey`
還在用）；`facetsInRange`（查詢頁）同步帶出。單元測試看不出來（假資料直接組 `MonthFacet`），是實機對照截圖
才發現，也代表計畫寫的「不加新的 SQL」不成立。`LibraryRepoFeedTest` 補了斷言。
`pnpm compare` 新增「詳情」畫面（原型端從首頁開 Lightbox 再按【播放這一段】，跟實機同一條路）。
偏離原型的地方都記在規格第六節「UI 與原型的差異」。
**全分支最終審查的修正**：可點的 `VsTagChip` 分成動作型（預設，首頁月份標籤、第三步建議標籤，當按鈕、
不帶選取語意）與切換型（`isToggle = true`，核取方塊語意），原本所有可點的都被 TalkBack 唸成「未勾選，
核取方塊」；子資料夾 ⋯ 的名稱改回既有的「「名稱」的更多操作」；詳情頁縮圖格預留圖不再重複顯示秒數；
詳情頁讀標籤被取消的舊工作不再寫回過期標籤；月份標籤種類對應抽成 `chipKindOf` 並補單元測試。
三套測試：JVM **941 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 771，2026-10-03，最終審查修正後）全綠，
修正前 931 個，修正前已用乾淨 `git worktree` 全新重跑，數字一致。**實機（2107113SG）驗過**：`pnpm compare` 淺色＋深色的首頁／
Lightbox／詳情／資料夾內容（結構與原型一致，差異只在偏離清單）；首頁在最終審查修正後另外重跑一次
（淺色 `tmp/ui-compare/20261003-150130`、深色 `tmp/ui-compare/20261003-150143`），月份標籤的種類圖示
正確（主題 `#`、地點定位針、其他菱形；這批假資料沒有人物或動物標籤，那兩種沒有在截圖裡看到）；首頁捲動、月份標籤點了帶入查詢、
Lightbox（⋯ 選單、深色模式圖示清楚）→【播放這一段】→ 詳情頁（點另一格換聚焦、編輯鈕開編輯 sheet）→
分類；儀器測試只重跑了這次動到的 `LibraryRepoFeedTest`＋`SearchRepoTest`（`OK (25 tests)`），沒有重跑全部
210 個。裝置夜間模式已還原成原本的「no」，裝置現在是假資料狀態。TalkBack 唸讀順序仍未實際開起來走過。
**階段 15D（查詢、分類、帳號對齊原型）完成（2026-10-03），階段 15 至此全部完成**，計畫見
`docs/superpowers/plans/2026-10-03-階段15D-查詢分類帳號對齊原型.md`，用 subagent-driven-development
逐任務實作與審查。**現在看得到的 UI 改動**：查詢頁頂部是底線分頁（標籤與地點｜描述）、時間是滿版下拉欄位、
標籤小膠囊帶種類圖示與張數、動作列釘在導覽列正上方（`AppShell` 的內容區改成消耗 Scaffold 已給的 inset，
不再把系統導覽列高度墊兩次；鍵盤開著時動作列貼在鍵盤上方）、描述分頁按鍵盤搜尋鍵＝查詢；查詢結果縮圖改
正方形（格線 2dp），條件小膠囊純顯示且帶種類；分類卡片的張數改成圖片區右下角的膠囊、狀態列「N 個分類
＋排序」、排序選單改單選列；帳號頁 hero 加頭像、三格統計合成**一張**跨在 hero 下緣的卡（帶圖示與分隔線）、
六列選單放進分組清單；取圖、AI 分析、查詢金鑰、縮圖、標籤管理（含編輯 sheet 的種類切換小膠囊）、備份、
還原、首次開啟選擇全部改用新的**設定頁元件組**（`VsSettingGroup`／`VsSettingNote`／`VsRadioRow`／
`VsStepper`／`VsSelectField`，加上 `VsTextField` 的 `leadingIcon`）；【中斷連結】、【清除】金鑰、
【刪除這些收藏】、標籤編輯 sheet 的【刪除】用破壞性樣式。
**階段 15 收尾**：2026-10-01 的 UI 對齊原型設計文件有效的結論併入規格——第三節「UI 元件層」
（`ui/common/` 的角色）、第五節（套用後提示）、第六節各畫面與新增的「UI 與原型的差異」表、第十三節
「開發測試版專用工具」——然後 `git rm`；驗收手冊的「依據」改成「視覺權威是 app 的共用元件（元件總覽頁），
原型是歷史參考」，並補 15D 的條目。**原型 HTML 檔案都還在**（`pnpm compare` 與 `mockups/shared/storyboard.ts`
仍要用），只是不再是驗收對象。
三套測試：JVM **1019 個**（`:core:test` 170 ＋ `:app:testDebugUnitTest` 849，2026-10-03，最終審查修正後）全綠；
修正前（1016 個）與修正後各用乾淨 `git worktree` 全新編譯重跑一次（含 `:app:assembleRelease`），數字一致。**儀器測試 `OK (211 tests)`**
（`am instrument`，2107113SG 實機，2026-10-03；這是 15A 之後第一次全部重跑，比 15A 的 210 個多 1 個）。
**實機（2107113SG）驗過**：`pnpm compare` 12 個畫面淺色＋深色全部走過（淺色 `tmp/ui-compare/20261003-205938`、
深色 `tmp/ui-compare/20261003-210317`；新增「查詢結果」「取圖設定」兩個畫面），差異只在偏離表；
查詢（切分頁、選時間、勾地點與標籤、查詢、結果、改條件、描述分頁打字按鍵盤搜尋）、分類（搜尋、排序、卡片 ⋯）、
帳號（統計卡三格上緣點擊都有作用、六列選單逐一進出、取圖單選、AI 步進器、縮圖頁、標籤編輯 sheet）淺色與深色
各走一輪，深色每頁都讀得出來；裝置夜間模式已還原，裝置現在是假資料狀態。
**沒能在實機觀察到的**：(1) 備份頁並排的【立即備份】【從 Drive 還原】按鈕（要已連結 Google 帳號，實機驗收
不替使用者登入）；(2) 縮圖頁的回填進度條（假資料沒有缺圖就不顯示）；(3) 空的標籤清單外觀（要刪光標籤）。
這三項只有單元測試覆蓋。TalkBack 唸讀順序（包含步進器、下拉欄位、單選列）仍未實際開起來走過。
**已修（最終審查 fix wave）**：查詢結果頁按**系統返回鍵／手勢**原本會直接回首頁，現在跟頂欄左上角箭頭一樣回到條件頁
（`SearchScreen` 的 `BackHandler`），條件頁再返回才回首頁；從首頁月份標籤進來要按兩次（已在實機按過：結果頁→條件頁且勾選保留→首頁）。同一輪另外修了：
取圖、AI 分析、查詢金鑰、縮圖四個子畫面內容可捲動（大字級時按鈕不會被擠出畫面）、步進器數值改變時 TalkBack 會唸出、
縮圖頁刪除確認框改用共用按鈕、帳號頁 hero 已連結時的 Email 列觸控區補到 44dp（hero 會高約 16dp，已連結狀態實機還沒看過）。
分類頁搜尋列開著時按系統返回仍直接回首頁（同類問題，未處理）。另有兩個列為已知差異而非缺陷（已記在規格第六節差異表）：
查詢標籤小膠囊比原型大（共用元件 `VsTagChip` 的全圓角、帶張數、44dp 觸控區）；查詢結果列的條件小膠囊換到
「N 張」的下一行。**實機驅動的教訓**：連按兩次系統返回鍵會退出 app，之後用座標送出的點擊與輸入會落到桌面或別的 app。
這台是個人手機，**每次 `input tap／text` 前都要確認 `dumpsys window` 的 `mCurrentFocus` 是
`com.xenyaa.videoshot`**，不符就停下，返回鍵一次只按一下。
**階段 15 的未竟事項**：主題色系選擇器（決定 11，另外排一個階段）、查詢紀錄與沿用上次查詢（決定 10，新功能）。
**TalkBack 實際走一遍：2026-10-04 scott 決定跳過**（只確認過每個可點元素都有名稱；已知標籤編輯的種類選擇會被唸成核取方塊，未修）。

**階段 16A（地點表與統計表，`library.db` v1→v2）完成（2026-10-07）**，計畫見
`docs/superpowers/plans/2026-10-07-階段16A-地點表與統計表.md`。目標規模改成 100 萬張，v1 在 100 萬張時查詢頁候選清單要 9.2 秒。
**做了什麼**：地點從 `shot.place`（文字）改成 `place` 表（名稱唯一、帶別名）＋`shot.place_id`；新增 `shot_stat`／`shot_stat_total`
兩張統計表，由 9 個資料庫觸發器維護（跟圖資同一個交易）；全文索引只索引描述，地點改成另外比對名稱；`MIGRATION_1_2` 與還原 v1 備份的升級都有測試。
**使用者看不到任何變化**（畫面改讀統計表是 16B，地點管理與合併是 16C）。
三套測試：JVM **1015 個**（`:core:test` 172 ＋ `:app:testDebugUnitTest` 843）全綠；**儀器測試 `OK (232 tests)`**（2107113SG，全部）。
**100 萬張實機量測（v2）**：統計表讀取 0.004～0.01 秒（v1 候選清單 9.2 秒）；寫入成本取圖 100 張 0.17 秒、編輯一張 0.003～0.004 秒、
合併地點 10 萬張 2.2～2.3 秒；首頁第一頁 0.011 秒不變；v1→v2 升級 84.1 秒（開檔一次性）。
**未達標，待 scott 決定**：(1) 合併 22 萬筆關聯的大標籤要 3.9～4.3 秒，超過目標 3 秒（v1 無觸發器時 1.9 秒）；(2) 合併地點 10 萬張要 2.2～2.3 秒，超過目標 2 秒（最終審查發現原先量到的是只有約 340 張的地點，已重量更正）；設計都未改。
另外候選清單帶「某月以前」條件仍要 0.51～0.57 秒，16B 再設計；現行畫面查詢在 v2 多了 `LEFT JOIN place`，略慢（候選清單 11.2 秒、標籤管理 4.1～4.3 秒），16B 整個換掉。
**實機升級**：裝置上的舊版圖庫（v1，181 張）推回手機開 app，升到 v2，完整性 ok、外鍵檢查 0、張數 181＝統計總數、每張圖的地點與升級前一致；
首頁月份標題、地點小膠囊、標籤正常，點「廣西」小膠囊查到 169 張（＝升級前張數）。取圖第三步的「用過的地點」沒有在實機點開看（單元測試覆蓋）。
**觀察**：描述查詢 2 字關鍵字命中太多仍會失敗（SQLite 參數上限，16A 前就有，暫緩）；手機橫向時查詢頁時間欄位與動作列之間幾乎沒有空間、候選小膠囊看不到（版面問題，與 16A 無關）。
裝置現在是升級後的 v2 圖庫（181 張），另有 `files/bench-library.db`（100 萬張，約 500 MB）。

**階段 16B（畫面改讀統計表）完成（2026-10-07）**，計畫見
`docs/superpowers/plans/2026-10-07-階段16B-畫面改讀統計表.md`。**使用者看得到的改變**：(1) Lightbox 頂列只剩關閉與 ⋯，不再顯示「第 N / 共 M 張」；
(2) 查詢結果超過 1000 張顯示「1000+ 張」；(3) 查詢頁候選清單依**全部時間**的張數排序，選時間範圍只拿掉範圍內沒有圖的項目。
內部：首頁月份標籤、月份選單、查詢候選清單、帳號頁統計、標籤管理改讀統計表；查詢結果依選取項目的總張數（`shot_stat_total`）
選查法——小於 `FACET_UNION_THRESHOLD`（5,000）用分段合併，否則沿時間軸掃描；**第一頁與結果張數都用同一個門檻**（一開始只有第一頁選查法，
張數仍一律掃描，冷門標籤量到 0.73 秒才補上）。
三套測試：JVM **1018 個**（`:core:test` 172 ＋ `:app:testDebugUnitTest` 846）全綠；**儀器測試 `OK (244 tests)`**（2107113SG，全部），
張數修正後 `SearchRepoTest` 單獨重跑 `OK (19 tests)`。
**實測（手機、100 萬張，暖機後中位數）**：月份選單 0.67 → 0.014 秒；首頁某月標籤 0.95 → 0.06～0.08 秒；查詢候選清單（全部時間）11.2 → 0.007 秒、
（2020-01 以前）0.056 秒、顯示更多 500 筆 0.036 秒；標籤管理 4.1～4.3 → 0.059 秒；帳號頁統計 0.2 → 0.009 秒；結果第一頁常見地點 0.030 秒、
大標籤 0.023 秒、冷門標籤 0.059 秒；結果 N 張常見地點 0.3 → 0.022 秒、冷門標籤 0.73 → 0.033 秒；首頁第一頁 0.005 秒、往下滑 0.016 秒（不變）。
**未達標但接受**：首頁某月標籤 0.06～0.08 秒，目標 0.05 秒——量測庫每月有 2,010 個不同的地點與標籤，真實圖庫每月只有幾十個，統計表讀取本身只要 0.012 秒。
**門檻量測**（第一頁，分段／掃描，秒）：1,266 張 0.039／0.145；1,997 張 0.064／0.132；5,014 張 0.080／0.053；10,082 張 0.113／0.040；19,924 張 0.165／0.029；
45,341 張 0.255／0.028。交叉點約 4～5 千張，門檻維持 5,000（交叉點兩側都略高於 0.05 秒，找不到兩側都 ≤ 0.05 秒的點）。
寫入成本（16A 觸發器，本階段未改）：取圖 100 張 0.20 秒、編輯一張 0.0075～0.009 秒、合併大標籤 4.4～4.5 秒、合併地點 10 萬張 2.3～2.4 秒。
**決議（scott，2026-10-07）**：合併大標籤與合併地點的超時接受現況，16C 合併時顯示「合併中…」；候選清單排序選依全部時間張數。
**實機畫面（直向）**：首頁月份標題、地點小膠囊、標籤列正常；Lightbox 頂列沒有第幾張；帳號頁 181／本月 0／來源影片 11 與資料庫一致，標籤管理看得到的列張數與資料庫一致。
**沒在實機驗的**：Lightbox 滑超過 50 張的接續載入（單元測試覆蓋）。描述查詢未改（暫緩）：2 字詞仍會 too many SQL variables。

**2026-10-06 移除深色模式**：app 打開就是設定好的色系，不跟手機的淺色／深色設定走（`NightMode`、
各色系的深色那一套、`pnpm compare --dark` 都已刪除；`themes.xml` 關掉強制反色、系統列圖示固定深色）。
下面各階段紀錄裡「深色模式驗過」的文字是當時的歷史，不再是驗收項目。

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
另外詳情頁第一次進入時偶爾（只發生過一次）卡在「正在載入…」約 16 秒，當時研判是那次驗收用外部
組好的 SQLite 檔案直接塞進 `library.db` 才踩到的邊界情況——**這個研判是錯的**：2026-10-02 查出是
`DetailViewModel` 把晚到的播放器資訊連同舊快照一起寫回、蓋掉已載入的狀態（見階段 15A 段落，已修）；
當時為什麼 16 秒後自己恢復沒有查證（推測是之後另一次重查又寫回了狀態）。
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
**階段 14**（清理 web 程式碼）已於 2026-09-30 完成。**原本的階段 5 已併入 4c**。
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
| 3 | [`mockups/uiux-v2/驗收操作手冊.md`](mockups/uiux-v2/驗收操作手冊.md) | **UI 驗收標準**。計畫的驗收條件引用它的原文；標〔app〕的條目是只有 app 才有的行為 | 做完一個任務要驗收時 |
| 4 | 開發測試版的**元件總覽頁**（`adb shell am start -n com.xenyaa.videoshot/.debug.ComponentCatalogActivity`，指令見下方「指令」） | **視覺參考**。`ui/common/` 每個共用元件的各種狀態；視覺的權威是它，不是原型 | 做 UI、問「這個元件該長怎樣」時 |

`mockups/uiux-v2/` 的 HTML 是 **UI 的歷史參考**（最初的視覺來源，已經以 Compose 重寫、程式碼不沿用；階段 15 完成後視覺以 app 的共用元件為準），
它落後於驗收手冊（手冊是目標狀態）。與原型刻意不同的地方集中記在規格第六節「UI 與原型的差異」。
原型與手冊不一致時**以手冊為準**；手冊與規格不一致時**以規格為準**。

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
- **問答**（`docs/問答/`，開發時常查的問題）：格式規則見 [`docs/問答/AGENTS.md`](docs/問答/AGENTS.md)。

---

## 不能刪的東西

- **`mockups/shared/storyboard.ts`** —— `mockups/server.mjs` **在執行期讀取它**並轉譯成
  `/shared/storyboard.js`。刪掉它 `pnpm mock` 就開不起來，UI 原型每一頁都會壞
  （原型是歷史參考，`pnpm compare` 的對照截圖也靠它）。它也是 Kotlin 版 `storyboard`（`:core` 模組）的移植來源
  （2026-09-30 階段 14 由 `src/lib/` 搬入；web 版其餘程式碼已刪除，測試在 git 歷史）。

`test-results/`、`tmp/` 都在 `.gitignore` 裡，隨時可刪。

---

## 詞彙

規格與計畫一律用左欄；mockup 的程式碼還是右欄，讀原型時要換算。

| 正式 | mockup | 說明 |
|---|---|---|
| `shot` | `clip` | 同一個東西 |
| `at_sec` | `start` | 單一時間點。`end` 已移除，不存結束時間 |
| `description` | `summary` ＋ `note` | 已合併為一欄 |
| `place` 表＋`shot.place_id` | `tag.kind='place'` | 2026-10-07 起是獨立的表（有別名），不是 shot 上的文字 |
| 帳號 | 「設定」 | 導覽列第五格。「帳號」指**備份用的 Google 帳號**，app 本身不需要登入 |
| 【截圖】 | 【手動補圖】 | 在播放器上一鍵截圖；失敗時退回相簿選圖 |

---

## 指令

```bash
pnpm mock         # UI 原型（需要 mockups/shared/storyboard.ts 存在）
```

```bash
pnpm seed                   # 從原型重新產生 android/app/src/debug/assets/seed/mock-seed.json（假資料）
pnpm compare [--seed] [--only 首頁,查詢]   # 原型與實機對照截圖（需先開 pnpm mock、本機要有 Chrome——位置不是 /usr/bin/google-chrome 就設 CHROME=路徑；輸出 tmp/ui-compare/<時間>/；--seed 約 2.5 分鐘）
```

開發測試版專用的實機工具（正式版不含）：

```bash
# 元件總覽頁（所有共用元件）
adb shell am start -n com.xenyaa.videoshot/.debug.ComponentCatalogActivity
# 匯入假資料：會「取代」裝置上的整個圖庫（13 張／11 支影片／5 個資料夾／20 個標籤），匯入後自動排縮圖回填
adb shell am broadcast -a com.xenyaa.videoshot.debug.SEED -n com.xenyaa.videoshot/.debug.seed.SeedReceiver
```

匯入完成的訊號是 logcat 出現 `VsSeed done`；之後直接開 app 即可（2026-10-02 修掉白畫面問題前要先 `force-stop`，見階段 15A 段落）。

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
