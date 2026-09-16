# videoshot

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin ＋ Jetpack Compose；iOS 暫不做）。

- **applicationId／Kotlin 套件：`com.xenyaa.videoshot`**（上線後不可改）。
- 舊名 **yt-space**（2026-09-11 改名）。舊名仍留在 `src/`、`static/`、`tests/`（web 版，清理階段整批刪除）
  與原型的 localStorage key `ytspace2_*`，這些刻意不改。

**目前進度：階段 0～3 完成，階段 4a（精靈外殼與第一步）完成（2026-09-14）、階段 4b（縮圖牆與收斂）、階段 4c（截圖與效能閘門）完成（2026-09-15）、階段 6（第三步、完成、草稿）、階段 7（App 外殼、首頁、Lightbox）完成（2026-09-16）。
三套測試全綠（2026-09-16）：JVM 440 個（`:core:test` 119 ＋ `:app:testDebugUnitTest` 321），
**儀器測試 117 個**（`:app:connectedDebugAndroidTest`，實機 2107113SG）。
本階段新增的 `LibraryRepoFeedTest`（10）與 `ShotDeleterTest`（6）都已在實機上跑過。

**app 啟動後落在首頁**（階段 2 的資料層冒煙畫面已刪除，內容在 git 歷史），底部導覽五格
（首頁／查詢／取圖／分類／帳號），取圖精靈在第三格。貼網址 → 挑畫面 → 填圖資 → 完成，
這一整段的接線已經做完，圖會真的寫進 `library.db`。

階段 7 在實機（2107113SG）上驗過的是**首頁全部**（手冊 §二，含年月分組、月份篩選、封面降級）、
**Lightbox**（手冊 §三，含刪除後自動停在下一張）、深色模式、縮圖抓不到時的降級預留圖——
做法是把種子資料直接寫進 `library.db` 再開 app，**不是**實際跑一次取圖精靈。
**取圖精靈從貼網址到完成這一整條路徑，目前仍然只在 JVM／Robolectric 測試下驗證過，還沒有在實機上走過一次**，
這件事還沒解決，下次有人要動精靈相關程式碼時要留意。

**測試怎麼跑**（三套，環境限制見規格第十三節）：
`./gradlew :core:test`（JVM 純邏輯）、`./gradlew :app:testDebugUnitTest`（**Compose UI 走 Robolectric，跑在 JVM**）、
`./gradlew :app:connectedDebugAndroidTest`（資料庫與網路，實機）。
儀器測試要注意 MIUI 的「USB 安裝」開關會自己關掉 —— 安裝失敗時 Gradle 仍回報 BUILD SUCCESSFUL 但**測試數是 0**，
每次都要確認數字不是 0。

**舊的進度描述（階段 0～3）：**
`android/` 有可建置的 `:app` 與 `:core`，共 **133 個測試**。
資料層（`library.db`／`cache.db`／FTS5／`LibraryRepo`）、`youtube`（watch page 解析與五種失敗分類、OkHttp 抓取）、
`thumbs`（`thumbFor` 單一讀取入口、sheet 下載裁切成 WebP）、dHash 與收斂演算法都已就緒。
下一步是**階段 8**（分類資料夾）；**階段 14**（清理 web 程式碼）也隨時可做。**原本的階段 5 已併入 4c**。
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
