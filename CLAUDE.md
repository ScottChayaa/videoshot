# yt-space

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android ＋ iOS 原生 app，Flutter）。

**目前進度：尚未開始實作。** 2026-09-10 產品形態由 web（SvelteKit on Cloudflare）改為 Flutter 原生 app，
**app 版實作計畫待產出**。計畫的第一步是截圖功能的 POC（規格第十二節）。

---

## 動工前必讀（依這個順序）

| # | 文件 | 角色 | 什麼時候看 |
|---|---|---|---|
| 1 | 實作計畫（待產出，放在 `docs/superpowers/plans/`） | **主文件**。階段、任務、依賴、驗收條件 | 每次動工 |
| 2 | [`docs/superpowers/specs/2026-09-10-yt-space-app-design.md`](docs/superpowers/specs/2026-09-10-yt-space-app-design.md) | **規格**。技術事實（storyboard、watch page 欄位、trigram 限制）、架構與模組邊界、資料模型、備份／回填、POC | 動手寫某個模組前，讀對應章節 |
| 3 | [`mockups/uiux-v2/驗收操作手冊.md`](mockups/uiux-v2/驗收操作手冊.md) | **UI 驗收標準**。§一 登入等條目已過期（見手冊開頭），會在實作計畫中改寫 | 做完一個任務要驗收時 |

`mockups/uiux-v2/` 的 HTML 是 **UI 的視覺參考**（Flutter 重寫，程式碼不沿用），但它落後於驗收手冊（手冊是目標狀態）。
兩者不一致時**以手冊為準**；手冊與規格不一致時**以規格為準**。

---

## 開始實作時怎麼說

**不要說**「照計畫做」—— 任務不會一次做完。

**要說**做哪一段，例如：

```
執行實作計畫的階段 0
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
  它連同 `storyboard.test.ts` 也是 Dart 版 `storyboard` 模組的移植來源。

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

Flutter 專案（`app/`）的指令待實作計畫階段 1 建立後補上。iOS 以 Codemagic 雲端建置（開發環境沒有 Mac）。

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
- **重運算（裁切、dHash）放 isolate**，不卡 UI 執行緒。
