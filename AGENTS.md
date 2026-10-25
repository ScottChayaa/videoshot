# videoshot

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin ＋ Jetpack Compose；iOS 暫不做）。

- **applicationId／Kotlin 套件：`com.xenyaa.videoshot`**（上線後不可改）。
- 舊名 **yt-space**（2026-09-11 改名）。web 版程式碼已於階段 14 刪除（2026-09-30，內容留在 git 歷史）；
  舊名只剩原型的 localStorage key `ytspace2_*`，刻意不改。

## 目前狀態

- 路線圖的階段 0～17 都已完成（2026-09-11～10-08），之後是一連串 UI 優化（10 月），逐項見[開發紀錄](docs/開發紀錄/README.md)。
- 底部導覽四格：**照片**（首頁，可篩選地點與標籤）／**取圖**（精靈三步：貼網址 → 挑畫面 → 填圖資）／**相簿**（可封存）／**帳號**（設定、標籤與地點管理、Google Drive 備份、縮圖回填）。
- **查詢頁與描述查詢已下架**（階段 17），程式碼保留、沒有入口；**沒有深色模式**（2026-10-06 移除）。
- 目標規模 100 萬張（`library.db` 有地點表與統計表）。
- 沒有排定中的階段。還沒處理的事看[已知缺口](docs/已知缺口.md)。

---

## 文件索引

| 文件 | 角色 | 什麼時候看 |
|---|---|---|
| [`docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md`](docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md) | **路線圖**。15 個階段、依賴、驗收條件、風險；各階段的細節計畫從這裡連出去 | 排新階段時 |
| [`docs/superpowers/specs/2026-09-10-videoshot-app-design.md`](docs/superpowers/specs/2026-09-10-videoshot-app-design.md) | **規格（現況）**。技術事實（storyboard、watch page 欄位、trigram 限制）、架構與模組邊界、資料模型、備份／回填、各畫面行為 | 動手寫某個模組前，讀對應章節 |
| [`mockups/uiux-v2/驗收操作手冊.md`](mockups/uiux-v2/驗收操作手冊.md) | **UI 驗收標準**。計畫的驗收條件引用它的原文；標〔app〕的條目是只有 app 才有的行為 | 做完一個任務要驗收時 |
| 開發測試版的**元件總覽頁**（指令見下方「指令」） | **視覺參考**。`ui/common/` 每個共用元件的各種狀態；視覺的權威是它，不是原型 | 做 UI、問「這個元件該長怎樣」時 |
| [`docs/開發紀錄/`](docs/開發紀錄/README.md) | **歷史**。每個階段或功能做了什麼、決議、驗收、教訓 | 想知道某個東西為什麼長這樣時 |
| [`docs/已知缺口.md`](docs/已知缺口.md) | **待辦**。沒在實機驗的、刻意擱置的、未達標但接受的、還沒做的功能 | 排下一件事、或收尾前檢查時 |
| [`docs/問答/`](docs/問答/README.md) | **常查的做法**。測試怎麼跑、實機怎麼驅動、踩過的坑 | 遇到問題時先查 |

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

細節計畫只為即將動工的階段撰寫；沒有細節計畫的階段，先用 superpowers:writing-plans 產出再動工。

---

## 文件的維護規則

**規格永遠是現況，git 是歷史，計畫是排程。** 三者不重疊。

- 規格有異動就**改本文**，不在頂部累積變更紀錄 —— 讀者不該先讀到過期的內容、
  再回頭套用一張修訂表。
- 舊的規格與計畫直接刪除，內容留在 git 歷史；其中仍然有效的結論要**先併入現行規格再刪**
  （2026-09-01 與 2026-09-10 都是這樣處理的，成果見規格的附錄 A）。
- **尚未定案的地方就地標 ⏳**，寫在該條款旁邊。規格第十六節另有一張索引表，新增或解決 ⏳ 時兩處一起改。
- **完成一個階段或功能時**：
  1. 在 `docs/開發紀錄/` 寫一篇，並在它的 README 索引最上面加一列（寫法見該 README）。
  2. 沒做完、沒驗到、刻意擱置的寫進 `docs/已知缺口.md`；處理掉的從那裡刪掉。
  3. 跨階段都用得到的做法或踩過的坑，寫成 `docs/問答/` 的一題。
  4. `AGENTS.md` 只在「目前狀態」有變化時才改，不在這裡記流水帳。
- **問答**（`docs/問答/`）：格式規則見 [`docs/問答/AGENTS.md`](docs/問答/AGENTS.md)。

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
| 首頁（照片） | 首頁 | 導覽列第一格。畫面上叫「照片」，規格與程式碼仍叫首頁／`home`（2026-10-10 改名） |
| 相簿 | 「分類」、資料夾 | 導覽列第三格。畫面上叫相簿，程式碼與資料表仍叫 `folder`（2026-10-10 改名） |
| 帳號 | 「設定」 | 導覽列第四格。「帳號」指**備份用的 Google 帳號**，app 本身不需要登入 |
| 【截圖】 | 【手動補圖】 | 在播放器上一鍵截圖；失敗時退回相簿選圖 |

---

## 指令

這台開發機**系統 PATH 上沒有 java**，直接跑 `./gradlew` 會失敗。用 Android Studio 內建的 JDK：

```bash
export JAVA_HOME=/snap/android-studio/current/jbr
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools   # adb
```

```bash
cd android
./gradlew :core:test                 # :core 的 JVM 單元測試（純邏輯）
./gradlew :app:testDebugUnitTest     # :app 的 JVM 單元測試（Compose UI 走 Robolectric）
./gradlew assembleDebug              # 建置 debug APK
./gradlew installDebug               # 安裝到 USB 連接的手機
./gradlew assembleBenchmark          # 效能量測版：跟 release 一樣不可除錯，但用 debug 金鑰簽，`adb install -r` 直接覆蓋開發測試版、圖庫不會清掉
```

**儀器測試（資料庫與網路，實機）不要信 Gradle 的 `BUILD SUCCESSFUL`**，自己裝、自己跑，印出 `OK (N tests)` 才算數；
`connectedDebugAndroidTest` 還會解除安裝 app、清掉手機上的圖庫。原因見問答 [儀器測試怎麼跑才算數？](docs/問答/儀器測試怎麼跑才算數.md)：

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e package com.xenyaa.videoshot \
  com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
```

**量畫面耗時要用效能量測版**（開發測試版可除錯，Compose 慢一倍以上）：裝完先 `adb shell cmd package compile -m speed -f com.xenyaa.videoshot`
（模擬商店安裝後的完整編譯），再用 `adb shell atrace -a com.xenyaa.videoshot gfx view input` 看每一幀（`Choreographer#doFrame`）。
效能量測版不含開發測試版專用工具（元件總覽頁、假資料匯入），要用時重裝 `app-debug.apk`。

開發測試版專用的實機工具（正式版不含）：

```bash
# 元件總覽頁（所有共用元件）；手機桌面也有「元件總覽」圖示可以直接點
adb shell am start -n com.xenyaa.videoshot/.debug.ComponentCatalogActivity
# 匯入假資料：會「取代」裝置上的整個圖庫（13 張／11 支影片／5 個資料夾／20 個標籤），匯入後自動排縮圖回填
adb shell am broadcast -a com.xenyaa.videoshot.debug.SEED -n com.xenyaa.videoshot/.debug.seed.SeedReceiver
```

匯入完成的訊號是 logcat 出現 `VsSeed done`；之後直接開 app 即可。

```bash
pnpm mock                   # UI 原型（需要 mockups/shared/storyboard.ts 存在）
pnpm seed                   # 從原型重新產生 android/app/src/debug/assets/seed/mock-seed.json（假資料）
pnpm compare [--seed] [--only 首頁,查詢]   # 原型與實機對照截圖（需先開 pnpm mock、本機要有 Chrome——位置不是 /usr/bin/google-chrome 就設 CHROME=路徑；輸出 tmp/ui-compare/<時間>/；--seed 約 2.5 分鐘）
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
- **非同步讀取回來先算結果，再 `_state.update { it.copy(…) }`**，不要把 suspend 呼叫寫在 `.copy(…)` 的參數裡（原因見問答 [非同步讀取寫回畫面狀態要注意什麼？](docs/問答/非同步讀取寫回狀態.md)）。
- **抽屜或頁面打開時一次要畫很多東西（覺得「頓一下」），照首頁篩選抽屜的做法優化**（2026-10-09，scott 實際操作後認可，範本是 `FilterSheet.kt`）：
  1. **先量再改**：用效能量測版＋完整編譯＋`atrace`（見「指令」）看每一幀，先確認慢的是畫面還是資料（篩選抽屜的資料庫查詢只要 5～9ms，加快取沒用）。
  2. **第一幀只畫外框＋骨架屏**（`VsChipSkeleton` 這類，形狀跟真內容一樣），讓抽屜／頁面立刻開始動。
  3. **之後一幀多畫一小塊**（一列、一區）：`LaunchedEffect` 裡 `withFrameNanos {}` 一次加一列，區塊高度一開始就是最終高度、版面不跳；
     一區畫完再換下一區。不要等動畫停穩才畫（太晚，scott 嫌慢），也不要在動畫途中一次畫完（會卡）。目標：動畫途中每幀 ≲ 30ms。
  4. **同一件事不做兩次**：例如分頁時量過的文字排版直接交給元件畫，不再排版一次。

### 收尾

- **大階段收尾前用乾淨的 `git worktree` 全新編譯重跑測試**（含 `:app:assembleRelease`），不能只信 incremental 編譯的綠燈。
- **androidTest 只編譯沒在實機跑、或外部 SDK（Play Services、Drive）呼叫只編譯驗證過**，收尾時要主動講出來、提醒上實機走一次。
  兩條的原因見問答 [為什麼只編譯過、單元測試綠燈還不夠？](docs/問答/只編譯過為什麼不夠.md)。

### 實機操作守則

實機（2107113SG）是 scott 的個人手機。細節與指令見問答 [怎麼用 adb 驅動手機做驗收？](docs/問答/用adb驅動手機驗收.md)。

1. **每次點擊或輸入前確認焦點在 app 上**（`dumpsys window` 的 `mCurrentFocus`；選單、抽屜開著時看 `mFocusedApp`），不符就停下。
2. **手機 10 分鐘無操作會上鎖**：超過幾分鐘的流程先在背景每 3 分鐘送一次 `KEYCODE_WAKEUP`。
3. **返回鍵一次只按一下**（連按兩次會退出 app）。
4. **取圖精靈右上角的【✕】絕不按**（會清掉草稿；按【取圖】會直接續做草稿，不再詢問）。
5. **scott 的圖庫只看不改**；非改不可時先備份、驗完還原，並在回報裡說明動了什麼。
6. **`connectedDebugAndroidTest` 會解除安裝 app、清掉圖庫**，用 `am instrument` 跑（見「指令」）。
