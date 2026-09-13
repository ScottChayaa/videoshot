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
| 一般公開 | Embed | JS canvas | **1280×720** | 170.6／42.5 | false | 否 | false | ✅ 兩次都 `t=25.661`，亮度統計完全相同 | **10.000** | 111（第二次 47） | 5842 |
| 一般公開 | Embed | PixelCopy | 1080×607 | 198.0／61.2 | false | 否 | false | —（與 JS 同一暫停點，亮度 198.0 vs 198.2） | **10.000** | 28 | 8518 |
| 一般公開 | Mobile | JS canvas | **640×360** | 88.0／43.0 | false | 否 | false | — | — | 30 | 14522 |
| 一般公開 | Mobile | PixelCopy | 610×343 | 86.9／42.7 | false | 否 | false | — | — | 8 | 14140 |

- **canvas 沒有被 taint** —— `drawImage` ＋ `toDataURL` 都沒有 `SecurityError`，兩種載入方式皆然。
- **暫停點精準**：`跳到10s` 之後兩種方法都讀到 `t=10.000`（要求是 ≤ 0.1 秒誤差）。同一暫停點重截，`t` 與亮度統計逐位元相同。
- **都沒有 UI 疊加**：embed 的 `controls=0` 畫面全乾淨；`m.youtube.com` 雖然頁面上有 YouTube 頂列與靜音鈕，
  但靜音鈕落在影片元素**左側的黑邊區**，`v.getBoundingClientRect()` 不含它，PixelCopy 的預覽一樣乾淨。
- **解析度差很多**：embed 播 720p、`m.youtube.com` 只播 360p。JS canvas 拿到的是**影片原始解析度**，
  PixelCopy 拿到的是**螢幕上的像素**（受 WebView 寬度限制）。縮圖目標是 320×180，兩者都夠，但 embed ＋ JS 的餘裕最大。
- **PixelCopy 明顯較快**（8～28ms vs 30～111ms），因為不必做 JPEG 編碼與 base64 往返。
- `v.play()` **不足以啟動 embed 播放器** —— 必須點畫面上的播放鍵。`m.youtube.com` 則會自動播放（靜音）。
  這一點影響階段 4 的 T4.4（`Player` 介面的 `play` 實作）。

### POC 程式碼的缺陷（已修）

`captureJs` 把 `BitmapFactory.decodeByteArray` 的結果直接傳給 `report(bmp: Bitmap)`，解不出 bitmap 時 NPE 當掉整個 app。
觸發情境：影片還沒載入（`videoWidth=0`）時 canvas 是 0×0，`toDataURL` 回傳 `"data:,"`，不走 `catch` 分支。
已改為照實記錄（含影片尺寸、dataURL 長度與前綴）再返回。**實作階段的 `capture` 必須把「截得到但解不出圖」當成一種失敗型態**，
不能假設 `toDataURL` 成功就一定拿得到 bitmap。

## embed 能否播放（R-5）

| 類型 | embed 能播？錯誤訊息 | m.youtube.com 能播？ |
|---|---|---|
| 一般公開 `aqz-KE-bpKQ` | ✅ 能播，無錯誤訊息；`controls=0` 畫面無控制列。需點畫面上的播放鍵才會開始 | ✅ 能播，且自動播放（靜音） |

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
