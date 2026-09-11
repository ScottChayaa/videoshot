# yt-space

從任何 YouTube 影片挑出畫面，成為可依時間瀏覽、依標籤與語意檢索的個人圖庫（Android 原生 app，Kotlin）。

一趟流程收完一整支影片：貼網址 → 從整支影片的縮圖裡批次挑 → 統一給標籤與地點 → 完成。
資料只存在自己的手機，換機時從自己的 Google Drive 還原；沒有任何伺服器。

## 目前進度

**尚未開始實作。** 2026-09-10 產品形態由 web（SvelteKit on Cloudflare）改為 Android 原生 app（Kotlin ＋ Jetpack Compose；iOS 暫不做），
app 版實作計畫待產出；計畫的第一步是截圖功能的可行性 POC。

`src/` 是 2026-08-07 clip 版的 web 實作，不再是實作的輸入（只有 storyboard 解析演算法要移植成 Kotlin），
會在計畫的清理階段一次刪除。

`mockups/uiux-v2/` 是已驗收的 UI 原型（假資料），仍是 UI 的目標狀態。

## 文件

動工前的閱讀順序見 [`CLAUDE.md`](CLAUDE.md)。

| 文件 | 角色 |
|---|---|
| [設計規格](docs/superpowers/specs/2026-09-10-yt-space-app-design.md) | 唯一的現行規格：技術事實、架構、資料模型、備份與回填、POC |
| 實作計畫 | 待產出 |
| [驗收操作手冊](mockups/uiux-v2/驗收操作手冊.md) | UI 驗收標準（登入章節已過期，見手冊開頭說明） |

較舊的規格與計畫已從 `docs/` 移除，內容留在 git 歷史；仍有效的結論已併入現行規格。

## UI 原型

```bash
pnpm install
pnpm mock         # http://localhost:8231/uiux-v2/login.html
```

`pnpm mock` 會把 `src/lib/storyboard.ts` 即時轉譯成原型用的 `/shared/storyboard.js`，
所以刪除 `src/` 之前必須先把這個檔案搬進 `mockups/`。

## 架構

Android app（Kotlin），沒有自有後端：app 直接抓 YouTube watch page 與 storyboard sprite（原生 app 不受 CORS 限制）。
資料存本機 SQLite（Room ＋ 自帶 SQLite 的 FTS5 trigram），分成要備份的 `library.db` 與裝置本地的 `cache.db`；
縮圖存本機檔案，換機後從 YouTube 回填。細節見規格第三、四節。
