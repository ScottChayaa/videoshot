# videoshot

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin ＋ Jetpack Compose；iOS 暫不做）。

- **applicationId／Kotlin 套件：`com.xenyaa.videoshot`**（上線後不可改）。
- 舊名 **yt-space**（2026-09-11 改名）。舊名仍留在 `src/`、`static/`、`tests/`（web 版，清理階段整批刪除）
  與原型的 localStorage key `ytspace2_*`，這些刻意不改。

**目前進度：階段 0～3 完成，階段 4a（精靈外殼與第一步）完成（2026-09-14）、階段 4b（縮圖牆與收斂）、階段 4c（截圖與效能閘門）完成（2026-09-15）、階段 6（第三步、完成、草稿）、階段 7（App 外殼、首頁、Lightbox）完成（2026-09-16）、階段 8（分類資料夾）完成（2026-09-23）、階段 9（詳情頁）完成並實機驗收（2026-09-24）、階段 10（查詢）完成（2026-09-25，2026-09-26 完成全分支最終審查修正）。
三套測試：JVM **653 個**（`:core:test` 143 ＋ `:app:testDebugUnitTest` 510，2026-09-26）全綠；
**儀器測試 `OK (150 tests)`**（`am instrument`，2107113SG 實機，2026-09-24）——階段 9 Task 1 新增的 4 個 androidTest 方法已在實機上真的跑過並通過。
階段 10 新增了約 23 個 androidTest 方法（`SearchRepoTest.kt`／`OkHttpGeminiClientTest.kt`／
`AppSettingsTest.kt`，涵蓋 FTS5／CTE／row-value SQL、Gemini HTTP 往返、Keystore 加解密往返）——
**這些只在這台開發機上編譯驗證過（`compileDebugAndroidTestSources`），尚未像上面那 150 個一樣
在實機上用 `am instrument` 真的跑過**，因為這台開發機沒有連接的實機／模擬器（見規格第十三節環境
限制）。跟上面已確認的 150 個放在一起講清楚，是為了不讓「✅ 完成」看起來像同一種驗證強度。
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
