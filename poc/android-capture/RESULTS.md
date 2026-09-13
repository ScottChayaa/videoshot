# 階段 0 POC 結果（暫存；T0.9 寫回規格後隨 poc/ 一併刪除）

- 裝置：Xiaomi 2107113SG（11T Pro，代號 vili）／Android 12（API 31，MIUI V13.0.6.0.SKDTWXM）／Android System WebView 151.0.7922.202
- 日期：2026-09-13

## 測試影片

| 類型 | videoId | 備註 |
|---|---|---|
| 一般公開 | aqz-KE-bpKQ | |
| 不公開 | KUdmrPVssFA | |
| 禁止嵌入 | | 待挑選 |
| 有廣告 | | 待挑選 |
| Shorts | | 待挑選 |

## watch page 與 InnerTube（P-3）

| 類型 | watch 傳輸 B | playability | publishDate | uploadDate | isUnlisted | 有 spec | InnerTube 傳輸 B | InnerTube 有 spec |
|---|---|---|---|---|---|---|---|---|
| 一般公開 `aqz-KE-bpKQ` | 307764（html 1290520 字元） | watch=`OK`／InnerTube=`UNPLAYABLE`（reason 無法播放影片） | 2014-11-10T06:05:55-08:00 | 2014-11-10T06:05:55-08:00 | false | ✅ | 4116（json 9436 字元） | ❌ 無 |

- watch page 的 `INNERTUBE_CLIENT_VERSION` = `2.20260911.01.00`，`ytInitialPlayerResponse` 以 `var ytInitialPlayerResponse = {` 出現，括號配對擷取可行。
- InnerTube（`WEB` client ＋ watch page 取得的 `clientVersion`／`INNERTUBE_API_KEY`）**metadata 欄位齊全**
  （title、author、lengthSeconds、publishDate、uploadDate 都與 watch page 一致），但 `playabilityStatus` 是 `UNPLAYABLE`
  且**沒有 `storyboards`**。流量只有 watch page 的 1/75。

## 截圖（P-1 JS canvas／P-2 PixelCopy）

| 類型 | 載入方式 | 方法 | 解析度 | 亮度平均／標準差 | 黑畫面 | 畫面上有 UI 疊加？ | ad | 同一暫停點截兩次 t 相同？ | 跳到 10s 後 t | 耗時 ms | webp B |
|---|---|---|---|---|---|---|---|---|---|---|---|

## embed 能否播放（R-5）

| 類型 | embed 能播？錯誤訊息 | m.youtube.com 能播？ |
|---|---|---|

## FTS5

- sqlite 版本：**3.50.1**（`androidx.sqlite:sqlite-bundled:2.7.0`）
- trigram 3 字命中／2 字命中：**1 / 0**（與預期相同：`MATCH '夜潛看'` 命中、`MATCH '大蝦'` 不命中）
- VACUUM INTO：**可用**，快照 28672B

→ 風險 R-2（自帶 SQLite 沒有 FTS5 trigram）**不成立**，階段 2 的 T2.1 沿用 `androidx.sqlite:sqlite-bundled`。

## 結論（T0.8 填寫）

- P-1：
- P-2：
- P-3：
- 黑畫面門檻：
- FTS5：**通過** —— trigram 可用、行為符合規格第二節第 7 點的假設；`VACUUM INTO` 可用，備份走這條路。
- 播放器載入方式：
