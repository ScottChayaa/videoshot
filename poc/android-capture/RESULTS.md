# 階段 0 POC 結果（暫存；T0.9 寫回規格後隨 poc/ 一併刪除）

- 裝置：Xiaomi 2107113SG（11T Pro，代號 vili）／Android 12（API 31，MIUI V13.0.6.0.SKDTWXM）／Android System WebView 151.0.7922.202
- 日期：2026-09-13

## 測試影片

| 類型 | videoId | 備註 |
|---|---|---|
| 一般公開 | aqz-KE-bpKQ | |
| 不公開 | KUdmrPVssFA | |
| 禁止嵌入 | 5QLiE08LO2M | `playableInEmbed:false`。14 秒、直式 360×640、storyboard 只有 L0（48×27） |
| 有廣告 | 5ZdN94BbK6Q | 3 小時完整電影。**播放時未出現廣告**，這一格仍未正向驗證 |
| Shorts（直式） | 5QLiE08LO2M | 同上 —— 它本身就是直式短片，一支同時覆蓋兩種情境 |
| 追加公開影片 1 | 70MRQLlBv8I | 29 分（1739s）潛水長片。實測可嵌入、未出現廣告 |
| 追加公開影片 2 | B-9lkAZXjto | 14 分（824s）潛水長片。實測可嵌入、未出現廣告 |
| 追加公開影片 3 | fpzeoSqzfOg | 39 分（2350s）旅遊長片。實測可嵌入、未出現廣告 |

> 使用者提供的這三支都是**可嵌入、未出現廣告的一般公開長片**，不涵蓋「禁止嵌入」「有廣告」「Shorts」三種情境，
> 這三格仍待補樣本。

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
| 追加 1 `70MRQLlBv8I` | Embed | JS canvas | 854×480 | 104.0／71.4 | false | 否 | false | — | 9.510 | 84 | 6304 |
| 追加 1 `70MRQLlBv8I` | Embed | PixelCopy | 1080×607 | 103.8／72.1 | false | 否 | false | — | 9.510 | 26 | 6624 |
| 追加 2 `B-9lkAZXjto` | Embed | JS canvas | 1280×720 | 97.3／53.2 | false | 否 | false | — | 9.596 | 86 | 20252 |
| 追加 2 `B-9lkAZXjto` | Embed | PixelCopy | 1080×607 | 95.8／51.2 | false | 否 | false | — | 9.596 | 16 | 19946 |
| 追加 3 `fpzeoSqzfOg` | Embed | JS canvas | 1280×720 | 121.2／62.8 | false | 否 | false | — | 9.509 | 40 | 13820 |
| 追加 3 `fpzeoSqzfOg` | Embed | PixelCopy | 1080×607 | 120.5／63.4 | false | 否 | false | — | 9.509 | 16 | 13692 |
| 禁止嵌入／直式 | Mobile | JS canvas | **360×640** | 101.2／55.3 | false | 否 | false | ✅ 兩次都 `t=6.130`、亮度相同 | — | 23 | 6876 |
| 禁止嵌入／直式 | Mobile | PixelCopy | **192×343** | 91.1／71.1 | false | 否 | false | — | — | 34 | 5584 |
| 禁止嵌入／直式（**影片播完**） | Mobile | JS canvas | 360×640 | **3.0／2.1** | **true** | 否 | false | — | — | 27 | 804 |
| 禁止嵌入／直式（**影片播完**） | Mobile | PixelCopy | 1080×132 | 31.7／54.8 | false | **是 —— 截到 YouTube 頁首黑條，不是影片** | false | — | — | 11 | 4022 |
| 長片深色開場 `5ZdN94BbK6Q` | Embed | JS canvas | 1280×702 | **2.2／13.7** | false | 否 | false | — | 11.365 | 78 | 2546 |

- **canvas 沒有被 taint** —— `drawImage` ＋ `toDataURL` 都沒有 `SecurityError`，兩種載入方式皆然。
- **暫停點精準**：`跳到10s` 之後兩種方法都讀到 `t=10.000`（要求是 ≤ 0.1 秒誤差）。同一暫停點重截，`t` 與亮度統計逐位元相同。
- **都沒有 UI 疊加**：embed 的 `controls=0` 畫面全乾淨；`m.youtube.com` 雖然頁面上有 YouTube 頂列與靜音鈕，
  但靜音鈕落在影片元素**左側的黑邊區**，`v.getBoundingClientRect()` 不含它，PixelCopy 的預覽一樣乾淨。
- **解析度會變動**：embed 多半播 720p，但追加影片 1 只拿到 854×480（自適應串流當下的選擇）；`m.youtube.com` 只播 360p。
  PixelCopy 固定是 1080×607（WebView 寬度決定），所以**串流降到 480p 時 PixelCopy 反而比 JS canvas 大**。JS canvas 拿到的是**影片原始解析度**，
  PixelCopy 拿到的是**螢幕上的像素**（受 WebView 寬度限制）。縮圖目標是 320×180，兩者都夠，但 embed ＋ JS 的餘裕最大。
- **PixelCopy 明顯較快**（8～34ms vs 23～111ms），因為不必做 JPEG 編碼與 base64 往返。
- **PixelCopy 會截錯區域** —— 在 `m.youtube.com` 上影片播完、播放器收合之後，它截到的是 **YouTube 的頁首黑條**
  （1080×132），而同一瞬間 JS canvas 正確拿到 360×640 的影片畫面。原因是它依賴
  `getBoundingClientRect()` ＋ `getLocationInWindow()` 的座標換算，版面一變就算錯，而且**算錯時不會報錯**，
  只會安靜地存下一張錯的圖。
- **直式影片 PixelCopy 只有 192×343**（螢幕上的小框），低於 320×180 的縮圖目標寬度；JS canvas 拿到完整的 360×640。
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
| 追加 1 `70MRQLlBv8I` | ✅ 能播，無錯誤訊息 | 未測 |
| 追加 2 `B-9lkAZXjto` | ✅ 能播，無錯誤訊息 | 未測 |
| 追加 3 `fpzeoSqzfOg` | ✅ 能播，無錯誤訊息 | 未測 |

| **禁止嵌入 `5QLiE08LO2M`** | ❌ **被擋** —— 畫面顯示「無法播放影片／影片擁有者已禁止在其他網站上播放這部影片」＋【在 YouTube 上觀看】 | ✅ **能播**（自動播放、靜音） |

**R-5 的負面路徑已驗證，退路成立**：embed 被擋的影片，`m.youtube.com` 照樣播得出來。

**可以事先判定，不必等 embed 失敗**：watch page 的 **`playabilityStatus.playableInEmbed`**
就是這個旗標（注意是掛在 `playabilityStatus` 下，不是 `microformat`）。上面四支實測都是 `true`，與 embed 全部可播一致。
→ 階段 4 的 T4.4 可以**讀這個欄位決定載入方式**（`true` 走 embed、`false` 直接走 `m.youtube.com`），
不需要靠「載入失敗再退回」的猜測式流程。這一項要寫回規格。

## FTS5

- sqlite 版本：**3.50.1**（`androidx.sqlite:sqlite-bundled:2.7.0`）
- trigram 3 字命中／2 字命中：**1 / 0**（與預期相同：`MATCH '夜潛看'` 命中、`MATCH '大蝦'` 不命中）
- VACUUM INTO：**可用**，快照 28672B

→ 風險 R-2（自帶 SQLite 沒有 FTS5 trigram）**不成立**，階段 2 的 T2.1 沿用 `androidx.sqlite:sqlite-bundled`。

## 黑畫面門檻校準（T0.7）

| 組 | 樣本 | 亮度平均 | 標準差 | 暫定門檻（平均 < 16 且標準差 < 8）判定 |
|---|---|---|---|---|
| **真黑畫面** | 直式影片播完後的 JS 截圖 | **3.0** | **2.1** | `true` ✅ 正確 |
| **很暗但有效** | 長片的深色開場（黑底白字甘地語錄） | **2.2** | **13.7** | `false` ✅ 正確 |
| 一般畫面（最暗的） | `m.youtube.com` 的 BBB | 86.9 | 42.7 | `false` ✅ 正確 |
| 一般畫面（最亮的） | embed 的 BBB t=10 | 198.2 | 61.7 | `false` ✅ 正確 |

**結論：沿用暫定門檻（平均 < 16 且標準差 < 8）。**

關鍵在於**標準差這一項不能拿掉** —— 真黑畫面的平均是 3.0，深色開場的平均是 2.2，**比黑畫面還暗**。
只看平均會把有效的深色畫面誤判成黑畫面；是標準差（2.1 vs 13.7）把兩組分開的。這一點要寫回規格。

## 結論（T0.8 填寫）

- **P-1（JS canvas）：通過，且選它當正式實作。**
  canvas 沒有被 taint；拿到的是**影片原始解析度**（360×640～1280×720），不受版面、捲動、播放器收合影響；
  暫停點精準（`t=10.000`）；同一暫停點重截逐位元相同。耗時 23～111ms，可接受。
- **P-2（PixelCopy）：可行但不採用。**
  速度快 3～4 倍（8～34ms），畫質在串流降到 480p 時反而較好，但有兩個致命問題：
  （1）**會安靜地截錯區域**——實測截到 YouTube 頁首而非影片，且不會報錯；
  （2）解析度受螢幕限制，直式影片只有 192×343。
  → 留作備案；若日後 JS canvas 被 YouTube 擋（canvas taint），再切換過去，並且**必須加上截後驗證**。
- **P-3（InnerTube）：不通過。**
  `WEB` client ＋ watch page 的 `clientVersion`／`INNERTUBE_API_KEY` 可以取得完整 metadata、流量只要 1/75（約 4KB），
  但 `playabilityStatus=UNPLAYABLE` 且**回應裡沒有 `storyboards`**。五支影片一致。
  → 回填只能走 watch page。**R-4 的「改走流量較小的 JSON 端點」這條退路不成立**，必須靠節流與退避。
  （只測了 `WEB` client；`ANDROID`／`TVHTML5` 等變體未測，若日後回填被限流嚴重可再評估。）
- **黑畫面門檻：沿用暫定值（平均 < 16 且標準差 < 8）**，見上一節。標準差不可省略。
- FTS5：**通過** —— trigram 可用、行為符合規格第二節第 7 點的假設；`VACUUM INTO` 可用，備份走這條路。
- **播放器載入方式：預設 embed，依 `playabilityStatus.playableInEmbed` 決定。**
  `true` → `https://www.youtube.com/embed/{id}?playsinline=1&controls=0&cc_load_policy=0&rel=0`（畫面全乾淨、720p）；
  `false` → `https://m.youtube.com/watch?v={id}`（實測可播被擋的影片，但只有 360p 且頁面有介面）。
  **注意：embed 不會自動播放，且 `v.play()` 無效，必須模擬點擊播放鍵**；`m.youtube.com` 則會自動靜音播放。

### 仍未驗證

- **廣告偵測（`.ad-showing`）從未正向觸發** —— 八支影片、十七次截圖全部 `ad=false`，包含 3 小時的完整電影。
  無法斷定 `.ad-showing` 這個選擇器在 app 的 WebView 裡有沒有效。階段 5 的 T5.2 要把它當成**未驗證的假設**，
  並且準備一個不依賴它的退路（例如只靠黑畫面判定 ＋ 使用者回報「這格截不到」）。
