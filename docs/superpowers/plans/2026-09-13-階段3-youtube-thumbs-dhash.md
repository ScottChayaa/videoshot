# 階段 3 · `youtube`、`thumbs`、dHash 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 YouTube 的 storyboard 變成本機的 320×180 WebP 縮圖，並提供「這張 shot 的圖在哪」的單一讀取入口與相似畫面收斂。

**Architecture:** 純邏輯（JSON 括號配對、watch page 欄位對應、失敗分類、dHash、收斂）放 `:core`，JVM 單元測試直接跑、不需要裝置。
`:app` 只留會碰到網路與 Android API 的部分：`youtube`（OkHttp 抓 watch page 與 sheet）與 `thumbs`（裁切、WebP 編碼、檔案讀寫）。
畫面只呼叫 `thumbFor(shot)`，不知道圖來自 storyboard 檔案、`shot_image` 的 BLOB、封面圖還是預留圖。

**Tech Stack:** Kotlin、OkHttp 5.5.0、kotlinx-serialization-json 1.11.0、Android `BitmapFactory`／`Bitmap.compress(WEBP_LOSSY)`、JUnit。

**Spec:** [`../specs/2026-09-10-videoshot-app-design.md`](../specs/2026-09-10-videoshot-app-design.md)
第二節第 1～4 點（storyboard 格式、簽章效期、watch page 欄位、**失敗分類表**）、第三節（模組邊界）、
第五節第二步（縮圖牆載入與收斂）、**第七節（縮圖策略 —— 檔案佈局與降級行為）**。
**路線圖：** [`2026-09-11-videoshot-android-實作計畫.md`](2026-09-11-videoshot-android-實作計畫.md) 階段 3。

## Global Constraints

- **所有對 YouTube 非官方端點的存取只走 `youtube` 模組**（規格第三節模組邊界第 3 條）
- **縮圖讀寫只走 `thumbs`**，畫面只呼叫 `thumbFor(shot)`（模組邊界第 2 條）
- **所有 DB 存取只走 repo**（模組邊界第 1 條）
- **縮圖一律 320×180 WebP q75**；編碼用 `Bitmap.compress(WEBP_LOSSY, 75)`，**API 29 以下用已棄用的 `WEBP`**
- **縮圖識別碼是 `{videoId}/L{level}/{frameIndex}`，DB 裡不存任何檔案路徑**（規格第四節跨平台契約）
- **檔案放 `Context.filesDir`**，不可用 `cacheDir`
- **重運算（裁切、dHash）放 `Dispatchers.Default`**，網路與檔案 I/O 放 `Dispatchers.IO`
- **功能降級，絕不當機**（規格第三節設計原則第 6 條、第七節）——
  解不出 spec 時縮圖牆空白但流程繼續；sprite 403 時重抓一次 watch page，仍失敗退回封面圖
- **不依賴存下來的舊 spec**：任何需要 sheet 的時刻都重抓 watch page（規格第二節第 2 點，簽章效期不可知）
- **不做健康探測與告警**（規格第七節）
- **commit message 用繁體中文**，格式 `類型(範圍): 描述`，不加 AI 生成標記

---

## 與路線圖的對應

| 路線圖 | 本計畫 | 說明 |
|---|---|---|
| T3.1 watch page 抓取與解析 | **Task 3.3（純解析，`:core`）＋ Task 3.5（HTTP，`:app`）** | 解析是純邏輯，拆到 `:core` 才能用 JVM 測試 |
| T3.2 失敗分類 | **Task 3.4** | |
| ~~T3.3 InnerTube 用戶端~~ | **刪除** | 路線圖寫「僅在 POC P-3 為正面時」做。**POC 實測 P-3 不帶 storyboard spec**（規格第十二節），這個任務不成立 |
| T3.4 `thumbs` | **Task 3.6（讀取端）＋ Task 3.7（寫入端）** | 讀寫兩端可各自驗收 |
| T3.5 dHash | **Task 3.1** | 純邏輯、沒有依賴，先做 |
| T3.6 收斂演算法 | **Task 3.2** | |

## 本階段拍板的決策

| 項目 | 決定 | 理由 |
|---|---|---|
| **watch page 的解析放哪** | **`:core/youtube`**（純解析與分類）＋ **`:app/youtube`**（OkHttp 抓取） | 規格第三節把 `youtube` 整個放在 `:app`，但同一節也說「最需要測試的純邏輯放 `:core`，單元測試直接在 JVM 上跑」。失敗分類有五種分支，是這一階段最需要測試的東西。**模組邊界不變**：對 YouTube 的**網路存取**仍只在 `:app/youtube` 一處 |
| **JSON 解析器** | `kotlinx-serialization-json`（`JsonElement` 動態走訪，不定義完整 schema） | `org.json` 是 Android 專屬，JVM 單元測試會拿到丟例外的 stub。playerResponse 有上百個欄位而我們只要五個，不值得定義完整的 `@Serializable` 類別 |
| **測試用的 watch page** | 一份**真實錄製**的（驗括號配對）＋ 五份**手寫最小**的（驗五種分類） | 真實頁面 1.2 MB，五份就是 6 MB 進版控；分類邏輯只看幾個欄位，手寫最小頁面更穩定也更好讀 |
| **收斂的門檻比較** | `distance <= threshold` | 規格內文寫「小於門檻」、門檻表寫「高 ≤ 10」，兩者不一致。以**門檻表**為準（它是具體數值的來源）。此決定寫回規格 |
| **收斂的比較對象** | 與**最近一張被保留的**比（貪婪串接） | 規格「依時間順序掃描，每組保留最早的一張」的直接實作 |

## 檔案結構

```
android/
├── gradle/libs.versions.toml                     # Task 3.1／3.3／3.5：kotlinx-serialization、okhttp
├── core/
│   ├── build.gradle.kts                          # 套 kotlin-serialization 外掛
│   └── src/main/kotlin/com/xenyaa/videoshot/core/
│       ├── similarity/DHash.kt                   # Task 3.1：dHash、漢明距離
│       ├── similarity/Converge.kt                # Task 3.2：FilterStrength、converge()
│       └── youtube/
│           ├── WatchPageModels.kt                # Task 3.3：FetchResult、VideoMeta、WatchPage
│           ├── JsonExtract.kt                    # Task 3.3：括號配對擷取
│           └── WatchPageParser.kt                # Task 3.3／3.4：parseWatchPage()
└── app/src/main/java/com/xenyaa/videoshot/
    ├── youtube/
    │   ├── Youtube.kt                            # Task 3.5：介面 ＋ SheetForbidden
    │   └── OkHttpYoutube.kt                      # Task 3.5：實作
    └── thumbs/
        ├── ThumbKey.kt                           # Task 3.6：識別碼 → 路徑，只在這裡推導
        ├── Thumbs.kt                             # Task 3.6：介面、ThumbSource
        ├── FileThumbs.kt                         # Task 3.6：讀取端實作
        └── SheetHarvester.kt                     # Task 3.7：下載 → 裁切 → WebP → 寫檔
```

測試：

```
core/src/test/kotlin/com/xenyaa/videoshot/core/
├── similarity/DHashTest.kt          ├── similarity/ConvergeTest.kt
└── youtube/WatchPageParserTest.kt
core/src/test/resources/watchpages/  # Task 3.3／3.4 的測試頁面
app/src/androidTest/java/com/xenyaa/videoshot/thumbs/
├── ThumbKeyTest.kt   ├── FileThumbsTest.kt   └── SheetHarvesterTest.kt
```

## 每個任務都要跑的指令

```bash
cd android
export JAVA_HOME=/snap/android-studio/current/jbr
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :core:test                     # JVM 單元測試（Task 3.1～3.4 只需要這個）
./gradlew :app:connectedDebugAndroidTest # 儀器測試（需要 adb devices 看得到裝置）

# 只跑單一測試類別 —— AGP 9 的 connectedAndroidTest **不吃 `--tests`**，要用 runner 參數：
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.xenyaa.videoshot.youtube.OkHttpYoutubeTest
```

---

### Task 3.1: `:core` dHash

**Files:**
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/similarity/DHash.kt`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/similarity/DHashTest.kt`

**Interfaces:**
- Produces:
  - `const val DHASH_WIDTH = 9` / `const val DHASH_HEIGHT = 8`
  - `fun dHash(gray: IntArray): Long` —— 輸入長度必須是 72（9×8）的灰階值（0～255），列優先
  - `fun hammingDistance(a: Long, b: Long): Int`

- [ ] **Step 1: 寫失敗的測試**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/similarity/DHashTest.kt`：

```kotlin
package com.xenyaa.videoshot.core.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DHashTest {

    /** 產生 9×8 的灰階陣列；f(x, y) 回傳該點亮度。 */
    private fun gray(f: (Int, Int) -> Int) =
        IntArray(DHASH_WIDTH * DHASH_HEIGHT) { i -> f(i % DHASH_WIDTH, i / DHASH_WIDTH) }

    @Test
    fun `同一張圖的指紋完全相同`() {
        val g = gray { x, y -> (x * 17 + y * 31) % 256 }
        assertEquals(dHash(g), dHash(g.copyOf()))
    }

    @Test
    fun `全白與全黑的指紋都是 0`() {
        // dHash 比的是「右邊有沒有比左邊亮」，純色圖每一位都是 0
        assertEquals(0L, dHash(gray { _, _ -> 255 }))
        assertEquals(0L, dHash(gray { _, _ -> 0 }))
    }

    @Test
    fun `由左至右漸亮時每一位都是 1`() {
        assertEquals(-1L, dHash(gray { x, _ -> x * 28 }))   // 64 位全 1 = -1L
    }

    @Test
    fun `整體加亮不改變指紋`() {
        // dHash 比的是相鄰像素的相對關係，所以對亮度平移免疫 —— 這正是我們要的性質
        val base = gray { x, y -> 40 + x * 10 + y }
        val brighter = gray { x, y -> 80 + x * 10 + y }
        assertEquals(dHash(base), dHash(brighter))
    }

    @Test
    fun `不同的圖給出不同的指紋`() {
        val a = gray { x, y -> (x * 29 + y * 7) % 256 }
        val b = gray { x, y -> (x * 7 + y * 29) % 256 }
        assertNotEquals(dHash(a), dHash(b))
    }

    @Test
    fun `改一個像素只動到少數幾位`() {
        val f = { x: Int, y: Int -> 50 + x * 20 + y * 3 }
        val a = gray(f)
        val b = gray(f).also { it[DHASH_WIDTH * 3 + 4] = 250 }
        val d = hammingDistance(dHash(a), dHash(b))
        assertTrue("距離應該很小，實際 $d", d in 1..2)
    }

    @Test
    fun `完全無關的雜訊圖距離很大`() {
        val r = Random(42)
        val a = gray { _, _ -> r.nextInt(256) }
        val b = gray { _, _ -> r.nextInt(256) }
        assertTrue(hammingDistance(dHash(a), dHash(b)) > 20)
    }

    @Test
    fun `漢明距離的基本性質`() {
        assertEquals(0, hammingDistance(0L, 0L))
        assertEquals(64, hammingDistance(0L, -1L))
        assertEquals(1, hammingDistance(0L, 1L))
        assertEquals(hammingDistance(123L, 456L), hammingDistance(456L, 123L))
    }

    @Test
    fun `長度不對就丟例外`() {
        val wrong = runCatching { dHash(IntArray(70)) }
        assertTrue(wrong.exceptionOrNull() is IllegalArgumentException)
    }
}
```

- [ ] **Step 2: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 編譯失敗，`Unresolved reference 'dHash'`、`'DHASH_WIDTH'`。

- [ ] **Step 3: 實作**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/similarity/DHash.kt`：

```kotlin
package com.xenyaa.videoshot.core.similarity

/** dHash 的取樣尺寸：寬要比高多 1，因為每一列比較的是「相鄰兩點」，9 個點產生 8 個位元。 */
const val DHASH_WIDTH = 9
const val DHASH_HEIGHT = 8

/**
 * 差分雜湊（difference hash）。把畫面縮成 9×8 灰階後，
 * 每一列由左至右比較相鄰兩點：右邊比較亮就記 1，否則記 0 —— 共 8×8 = 64 位元。
 *
 * 為什麼用 dHash 而不是平均雜湊：它比的是**相鄰像素的相對關係**，
 * 對整體亮度變化免疫（同一個場景忽明忽暗仍算同一張），而這正是 storyboard 相鄰格的典型差異。
 *
 * @param gray 長度必須是 [DHASH_WIDTH] × [DHASH_HEIGHT]，列優先，值域 0～255
 */
fun dHash(gray: IntArray): Long {
    require(gray.size == DHASH_WIDTH * DHASH_HEIGHT) {
        "dHash 需要 ${DHASH_WIDTH}×${DHASH_HEIGHT} = ${DHASH_WIDTH * DHASH_HEIGHT} 個灰階值，收到 ${gray.size} 個"
    }
    var hash = 0L
    var bit = 0
    for (y in 0 until DHASH_HEIGHT) {
        val row = y * DHASH_WIDTH
        for (x in 0 until DHASH_WIDTH - 1) {
            if (gray[row + x + 1] > gray[row + x]) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

/** 兩個指紋有幾個位元不同。0 代表一模一樣，64 代表完全相反。 */
fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
```

- [ ] **Step 4: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`DHashTest` 9 個測試通過，`:core` 共 36 個。

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): dHash 指紋與漢明距離"
```

---

### Task 3.2: `:core` 收斂演算法

**Files:**
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/similarity/Converge.kt`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/similarity/ConvergeTest.kt`

**Interfaces:**
- Consumes: Task 3.1 的 `hammingDistance`
- Produces:
  - `enum class FilterStrength(val threshold: Int) { HIGH(10), MEDIUM(6), LOW(3) }`
  - `data class Fingerprint(val frameIndex: Int, val hash: Long)`
  - `data class ConvergeResult(val kept: List<Int>, val hiddenCount: Int)`
  - `fun converge(fingerprints: List<Fingerprint>, strength: FilterStrength): ConvergeResult`

- [ ] **Step 1: 寫失敗的測試**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/similarity/ConvergeTest.kt`：

```kotlin
package com.xenyaa.videoshot.core.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvergeTest {

    /** 用位元數當距離：把前 n 位設成 1，與 0L 的漢明距離就是 n。 */
    private fun hashWithDistance(n: Int): Long = if (n == 0) 0L else (1L shl n) - 1

    private fun fp(vararg distances: Int) =
        distances.mapIndexed { i, d -> Fingerprint(i, hashWithDistance(d)) }

    @Test
    fun `空清單回傳空結果`() {
        val r = converge(emptyList(), FilterStrength.MEDIUM)
        assertEquals(emptyList<Int>(), r.kept)
        assertEquals(0, r.hiddenCount)
    }

    @Test
    fun `只有一張一定保留`() {
        val r = converge(listOf(Fingerprint(0, 123L)), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(0, r.hiddenCount)
    }

    @Test
    fun `第一張永遠保留`() {
        val r = converge(fp(0, 0, 0), FilterStrength.LOW)
        assertTrue(r.kept.contains(0))
    }

    @Test
    fun `完全相同的連續畫面只留最早的一張`() {
        val r = converge(fp(0, 0, 0, 0, 0), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(4, r.hiddenCount)
    }

    @Test
    fun `距離超過門檻就保留`() {
        // 中 = 6：距離 7 超過門檻，兩張都留
        val r = converge(fp(0, 7), FilterStrength.MEDIUM)
        assertEquals(listOf(0, 1), r.kept)
    }

    @Test
    fun `距離等於門檻視為相似`() {
        // 門檻表寫的是「中 ≤ 6」，所以 6 要被藏起來
        val r = converge(fp(0, 6), FilterStrength.MEDIUM)
        assertEquals(listOf(0), r.kept)
        assertEquals(1, r.hiddenCount)
    }

    @Test
    fun `比較的對象是最近一張被保留的而不是前一張`() {
        // 0 → 4 → 8：4 與 0 距離 4（中門檻 6 以內，藏起來）；
        // 8 與「最近保留的 0」距離 8（超過門檻，保留）。
        // 若錯誤地跟前一張（4）比，距離只有 4，就會被藏掉。
        val r = converge(fp(0, 4, 8), FilterStrength.MEDIUM)
        assertEquals(listOf(0, 2), r.kept)
        assertEquals(1, r.hiddenCount)
    }

    @Test
    fun `強度越高藏得越多`() {
        val input = fp(0, 4, 8, 12, 16, 20)
        val high = converge(input, FilterStrength.HIGH).kept.size
        val medium = converge(input, FilterStrength.MEDIUM).kept.size
        val low = converge(input, FilterStrength.LOW).kept.size
        assertTrue("高=$high 中=$medium 低=$low", high <= medium && medium <= low)
        assertTrue("高強度應該真的藏掉一些", high < low)
    }

    @Test
    fun `保留的順序與輸入的時間順序一致`() {
        val r = converge(fp(0, 20, 40, 60), FilterStrength.LOW)
        assertEquals(r.kept.sorted(), r.kept)
    }

    @Test
    fun `保留數加隱藏數等於總數`() {
        val input = fp(0, 2, 9, 3, 30, 1, 7)
        for (s in FilterStrength.entries) {
            val r = converge(input, s)
            assertEquals(s.name, input.size, r.kept.size + r.hiddenCount)
        }
    }

    @Test
    fun `回傳的是原始的 frameIndex 而不是清單位置`() {
        // 縮圖牆傳進來的可能不是從 0 開始的連續格號
        val input = listOf(Fingerprint(10, 0L), Fingerprint(11, 0L), Fingerprint(12, -1L))
        val r = converge(input, FilterStrength.MEDIUM)
        assertEquals(listOf(10, 12), r.kept)
    }

    @Test
    fun `三個強度的門檻值就是規格訂的`() {
        assertEquals(10, FilterStrength.HIGH.threshold)
        assertEquals(6, FilterStrength.MEDIUM.threshold)
        assertEquals(3, FilterStrength.LOW.threshold)
    }
}
```

- [ ] **Step 2: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 編譯失敗，`Unresolved reference 'converge'`、`'FilterStrength'`。

- [ ] **Step 3: 實作**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/similarity/Converge.kt`：

```kotlin
package com.xenyaa.videoshot.core.similarity

/**
 * 過濾相似畫面的強度（規格第五節第二步，值取自帳號頁的「取圖 › 過濾相似強度」）。
 * threshold 是**漢明距離的上限**：距離 ≤ threshold 視為相似。
 * 門檻越大越容易判定相似 → 藏得越多，所以 HIGH 的數值最大。
 */
enum class FilterStrength(val threshold: Int) {
    HIGH(10),
    MEDIUM(6),
    LOW(3),
}

/** 一格的指紋。frameIndex 是 storyboard 的格號，不是清單位置。 */
data class Fingerprint(val frameIndex: Int, val hash: Long)

/** @param kept 保留下來的格號，維持時間順序 @param hiddenCount 被藏起來的張數 */
data class ConvergeResult(val kept: List<Int>, val hiddenCount: Int)

/**
 * 依時間順序掃描，把連續相似的畫面收斂成一張 —— 每組保留**最早**的那一張（規格第五節第二步）。
 *
 * 比較的對象是「最近一張**被保留的**」，不是「前一張」。
 * 若跟前一張比，畫面緩慢變化時每一張都跟前一張很像，會一路藏到底，
 * 最後只剩第一張 —— 那不是收斂，是把整支影片吃掉。
 *
 * 這是純計算，呼叫端要放在 `Dispatchers.Default`（規格第三節設計原則第 5 條）。
 */
fun converge(fingerprints: List<Fingerprint>, strength: FilterStrength): ConvergeResult {
    if (fingerprints.isEmpty()) return ConvergeResult(emptyList(), 0)

    val kept = mutableListOf<Int>()
    var lastKeptHash = 0L
    var first = true

    for (fp in fingerprints) {
        if (first || hammingDistance(lastKeptHash, fp.hash) > strength.threshold) {
            kept += fp.frameIndex
            lastKeptHash = fp.hash
            first = false
        }
    }
    return ConvergeResult(kept, fingerprints.size - kept.size)
}
```

- [ ] **Step 4: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`ConvergeTest` 12 個測試通過，`:core` 共 48 個。

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): 相似畫面收斂演算法"
```

---

### Task 3.3: `:core` watch page 解析

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/core/build.gradle.kts`
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageModels.kt`
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/JsonExtract.kt`
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParser.kt`
- Create: `android/core/src/test/resources/watchpages/ok-real.html`（錄製，見 Step 1）
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParserTest.kt`

**Interfaces:**
- Produces:
  - `enum class FetchResult { OK, VIDEO_UNAVAILABLE, NO_STORYBOARD, PARSE_FAILED, FETCH_FAILED }`
  - `data class VideoMeta(videoId, title, channelTitle, publishedAt, durationSec, privacy, playableInEmbed)`
  - `data class WatchPage(val result: FetchResult, val meta: VideoMeta?, val storyboardSpec: String?)`
  - `fun extractJsonObject(text: String, marker: String): String?`
  - `fun parseWatchPage(videoId: String, html: String): WatchPage`

- [ ] **Step 1: 錄製一份真實的 watch page**

  這一份是用來驗證括號配對能吃真的頁面（裡面有跳脫引號與深層巢狀）。
  只留 `ytInitialPlayerResponse` 那個物件並包成最小 HTML，完整頁面 1.2 MB 不必進版控。

```bash
cd android/core/src/test/resources && mkdir -p watchpages && python3 - <<'PY'
import subprocess, pathlib
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
html = subprocess.run(
    ["curl", "-s", "-A", UA, "https://www.youtube.com/watch?v=aqz-KE-bpKQ"],
    capture_output=True, text=True, errors="replace").stdout
i = html.index("var ytInitialPlayerResponse = ")
start = html.index("{", i)
depth = 0; instr = False; esc = False
for j in range(start, len(html)):
    c = html[j]
    if instr:
        if esc: esc = False
        elif c == "\\": esc = True
        elif c == '"': instr = False
    else:
        if c == '"': instr = True
        elif c == "{": depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                obj = html[start:j + 1]; break
out = pathlib.Path("watchpages/ok-real.html")
out.write_text(
    "<!doctype html><html><body><script>var ytInitialPlayerResponse = "
    + obj + ";</script></body></html>", encoding="utf-8")
print("錄製完成", out, len(obj), "bytes")
PY
```

  Expected: 印出 `錄製完成 watchpages/ok-real.html <數字> bytes`。
  若 YouTube 已改版而找不到 marker，腳本會丟 `ValueError` —— 那本身就是重要訊號，停下來回報，不要繼續。

- [ ] **Step 2: 加入 kotlinx-serialization**

  `android/gradle/libs.versions.toml`，`[versions]` 加：

```toml
kotlinxSerialization = "1.11.0"
```

  `[libraries]` 加：

```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

  `[plugins]` 加（版本必須與 `kotlin` 相同）：

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

  `android/core/build.gradle.kts` 的 `plugins { … }` 加一行：

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

  `dependencies { … }` 加一行：

```kotlin
    implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 3: 寫失敗的測試**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParserTest.kt`：

```kotlin
package com.xenyaa.videoshot.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPageParserTest {

    private fun page(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/watchpages/$name")) { "找不到測試頁面 $name" }
            .bufferedReader().use { it.readText() }

    // ---- 括號配對 ----

    @Test
    fun `擷取 marker 之後的第一個完整物件`() {
        val html = """<script>var x = {"a":1,"b":{"c":2}};</script>"""
        assertEquals("""{"a":1,"b":{"c":2}}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `字串裡的大括號不算層次`() {
        val html = """var x = {"a":"}{}{","b":1};"""
        assertEquals("""{"a":"}{}{","b":1}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `跳脫的引號不會提前結束字串`() {
        val html = """var x = {"a":"say \"hi\" }","b":2};"""
        assertEquals("""{"a":"say \"hi\" }","b":2}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `反斜線本身被跳脫時不影響後面的引號`() {
        val html = """var x = {"a":"c:\\","b":3};"""
        assertEquals("""{"a":"c:\\","b":3}""", extractJsonObject(html, "var x = "))
    }

    @Test
    fun `找不到 marker 或括號不完整時回傳 null`() {
        assertNull(extractJsonObject("沒有東西", "var x = "))
        assertNull(extractJsonObject("var x = 不是物件", "var x = "))
        assertNull(extractJsonObject("""var x = {"a":1""", "var x = "))
    }

    // ---- 真實頁面 ----

    @Test
    fun `吃得下真實錄製的 watch page`() {
        val r = parseWatchPage("aqz-KE-bpKQ", page("ok-real.html"))
        assertEquals(FetchResult.OK, r.result)
        val meta = r.meta!!
        assertEquals("aqz-KE-bpKQ", meta.videoId)
        assertEquals("Blender", meta.channelTitle)
        assertEquals(635, meta.durationSec)
        assertEquals("public", meta.privacy)
        assertTrue(meta.title.contains("Big Buck Bunny"))
        assertTrue("publishedAt=${meta.publishedAt}", meta.publishedAt.startsWith("2014-11-10"))
        assertTrue(meta.playableInEmbed)
        assertTrue(
            "spec=${r.storyboardSpec?.take(60)}",
            r.storyboardSpec!!.startsWith("https://i.ytimg.com/sb/aqz-KE-bpKQ/"),
        )
    }
}
```

- [ ] **Step 4: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 編譯失敗，`Unresolved reference 'extractJsonObject'`、`'parseWatchPage'`、`'FetchResult'`。

- [ ] **Step 5: 資料模型**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageModels.kt`：

```kotlin
package com.xenyaa.videoshot.core.youtube

/**
 * 抓一次 watch page 的結果分類（規格第二節第 4 點的失敗分類表）。
 * 判定順序很重要：先確認影片能不能播，再談有沒有 storyboard。
 */
enum class FetchResult {
    /** 抓得到 spec */
    OK,

    /** 影片不存在、私人、已刪除、地區限制，或需要登入（年齡限制） */
    VIDEO_UNAVAILABLE,

    /** 影片本身沒有 storyboard（過短、直播中、剛上傳） */
    NO_STORYBOARD,

    /** 連 videoDetails 與 playabilityStatus 都撈不到 —— **頁面結構變了，解析器失效** */
    PARSE_FAILED,

    /** HTTP 非 2xx 或網路錯誤。這一類由 :app 的 youtube 模組產生，解析器不會回傳它 */
    FETCH_FAILED,
}

/**
 * watch page 能提供的欄位（規格第二節第 4 點）。
 * **沒有拍攝日期** —— 那是 Data API 的 recordingDate，本版放棄。
 */
data class VideoMeta(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    /** ISO 8601 原字串，如 2014-11-10T06:05:55-08:00 */
    val publishedAt: String,
    val durationSec: Int,
    /** 'public' | 'unlisted' | 'unknown' */
    val privacy: String,
    /** 決定播放器載入 embed 還是 m.youtube.com（規格第二節第 5 點） */
    val playableInEmbed: Boolean,
)

/**
 * @param meta 只有 OK 與 NO_STORYBOARD 會有值 —— 影片播不了時那些欄位沒有意義
 * @param storyboardSpec 只有 OK 會有值
 */
data class WatchPage(
    val result: FetchResult,
    val meta: VideoMeta?,
    val storyboardSpec: String?,
)
```

- [ ] **Step 6: 括號配對**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/JsonExtract.kt`：

```kotlin
package com.xenyaa.videoshot.core.youtube

/**
 * 從一段文字裡找出 marker 之後的第一個完整 JSON 物件。
 *
 * 為什麼不用正規表示式：playerResponse 有上百層巢狀，而且字串欄位裡本來就帶著
 * `{`、`}`、跳脫引號。只有逐字元配對括號、並且正確略過字串內容，才不會提前收尾。
 */
fun extractJsonObject(text: String, marker: String): String? {
    val at = text.indexOf(marker)
    if (at < 0) return null
    val start = text.indexOf('{', at)
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
    }
    return null   // 括號沒有配對完就結束 —— 頁面被截斷了
}
```

- [ ] **Step 7: 解析器（先只做 OK 這條路）**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParser.kt`：

```kotlin
package com.xenyaa.videoshot.core.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 只比對變數名，不含 `=` 與兩側空白 —— extractJsonObject 會接著找下一個 `{`。
 * 真實頁面是 `var ytInitialPlayerResponse = {`，但只要 YouTube 改動一個空白字元
 * 就讓整個解析器失效，這個脆弱度不值得。
 */
private const val MARKER = "ytInitialPlayerResponse"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.obj(key: String): JsonObject? = this[key]?.let {
    runCatching { it.jsonObject }.getOrNull()
}

private fun JsonObject.str(key: String): String? = this[key]?.let {
    runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
}

private fun JsonObject.bool(key: String): Boolean? = this[key]?.let {
    runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull()
}

/**
 * 把 watch page 的 HTML 解析成結果分類 ＋ metadata ＋ storyboard spec。
 *
 * **純函式，不碰網路** —— 抓取是 :app 的 youtube 模組的事。
 * 這樣才能用錄製的頁面做 JVM 單元測試（規格第十三節）。
 */
fun parseWatchPage(videoId: String, html: String): WatchPage {
    val raw = extractJsonObject(html, MARKER)
        ?: return WatchPage(FetchResult.PARSE_FAILED, null, null)
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: return WatchPage(FetchResult.PARSE_FAILED, null, null)

    val playability = root.obj("playabilityStatus")
    val details = root.obj("videoDetails")
    if (playability == null && details == null) {
        return WatchPage(FetchResult.PARSE_FAILED, null, null)
    }

    val microformat = root.obj("microformat")?.obj("playerMicroformatRenderer")
    val meta = VideoMeta(
        videoId = videoId,
        title = details?.str("title").orEmpty(),
        channelTitle = details?.str("author").orEmpty(),
        publishedAt = microformat?.str("publishDate") ?: microformat?.str("uploadDate").orEmpty(),
        durationSec = details?.str("lengthSeconds")?.toIntOrNull() ?: 0,
        privacy = if (microformat?.bool("isUnlisted") == true) "unlisted" else "public",
        playableInEmbed = playability?.bool("playableInEmbed") ?: false,
    )

    val spec = root.obj("storyboards")?.obj("playerStoryboardSpecRenderer")?.str("spec")
    return WatchPage(FetchResult.OK, meta, spec)
}
```

- [ ] **Step 8: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`WatchPageParserTest` 6 個測試通過，`:core` 共 54 個。

  分類邏輯還沒做完（現在任何解得出 JSON 的頁面都會回 `OK`），Task 3.4 才補上 —— 這是刻意的：
  先確認**括號配對吃得下真實頁面**，再堆分類分支。

- [ ] **Step 9: Commit**

```bash
git add android/core android/gradle
git commit -m "feat(core): watch page 的括號配對擷取與欄位對應"
```

---

### Task 3.4: `:core` 失敗分類

**Files:**
- Modify: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParser.kt`
- Create: `android/core/src/test/resources/watchpages/unavailable.html`
- Create: `android/core/src/test/resources/watchpages/login-required.html`
- Create: `android/core/src/test/resources/watchpages/no-storyboard.html`
- Create: `android/core/src/test/resources/watchpages/unlisted.html`
- Create: `android/core/src/test/resources/watchpages/garbage.html`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/youtube/WatchPageParserTest.kt`（追加）

**Interfaces:**
- Consumes: Task 3.3 的 `parseWatchPage`、`WatchPage`、`FetchResult`
- Produces: `parseWatchPage` 的完整五分類行為（簽章不變）

- [ ] **Step 1: 手寫五份最小測試頁面**

  每一份只留分類邏輯會看的欄位。真實頁面 1.2 MB，五份進版控是 6 MB，而分類只看這幾個欄位。

  `android/core/src/test/resources/watchpages/unavailable.html`：

```html
<!doctype html><html><body><script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"ERROR","reason":"這部影片已無法使用。"}};</script></body></html>
```

  `android/core/src/test/resources/watchpages/login-required.html`：

```html
<!doctype html><html><body><script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"請登入以確認年齡。"},"videoDetails":{"videoId":"age1","title":"年齡限制影片","author":"某頻道","lengthSeconds":"300"},"storyboards":{"playerStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/age1/storyboard3_L$L/$N.jpg?sqp=x|48#27#100#10#10#0#default#rs$A"}}};</script></body></html>
```

  `android/core/src/test/resources/watchpages/no-storyboard.html`：

```html
<!doctype html><html><body><script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"OK","playableInEmbed":true},"videoDetails":{"videoId":"short1","title":"剛上傳的短片","author":"某頻道","lengthSeconds":"8"},"microformat":{"playerMicroformatRenderer":{"publishDate":"2026-09-13T00:00:00-07:00","isUnlisted":false}}};</script></body></html>
```

  `android/core/src/test/resources/watchpages/unlisted.html`：

```html
<!doctype html><html><body><script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"OK","playableInEmbed":false},"videoDetails":{"videoId":"KUdmrPVssFA","title":"20260726","author":"Scott Lin","lengthSeconds":"24"},"microformat":{"playerMicroformatRenderer":{"publishDate":"2026-07-30T01:58:30-07:00","isUnlisted":true}},"storyboards":{"playerStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L$L/$N.jpg?sqp=x|48#27#100#10#10#0#default#rs$A"}}};</script></body></html>
```

  `android/core/src/test/resources/watchpages/garbage.html`：

```html
<!doctype html><html><body><script>var somethingElse = {"nope":1};</script><p>YouTube 改版了，這裡已經沒有 ytInitialPlayerResponse</p></body></html>
```

- [ ] **Step 2: 寫失敗的測試**

  在 `WatchPageParserTest.kt` 的 `吃得下真實錄製的 watch page` 之後追加：

```kotlin
    @Test
    fun `影片不能播就是 video_unavailable`() {
        val r = parseWatchPage("gone", page("unavailable.html"))
        assertEquals(FetchResult.VIDEO_UNAVAILABLE, r.result)
        assertNull(r.meta)
        assertNull(r.storyboardSpec)
    }

    // 與參考實作（mockups/server.mjs）的一處刻意差異：規格第二節第 4 點。
    @Test
    fun `年齡限制即使有 videoDetails 與 spec 也算 video_unavailable`() {
        val r = parseWatchPage("age1", page("login-required.html"))
        assertEquals(FetchResult.VIDEO_UNAVAILABLE, r.result)
        // app 內的播放器一樣播不了，進第二步沒有意義 —— 所以連 spec 都不往下傳
        assertNull(r.storyboardSpec)
    }

    @Test
    fun `能播但沒有 storyboard 就是 no_storyboard`() {
        val r = parseWatchPage("short1", page("no-storyboard.html"))
        assertEquals(FetchResult.NO_STORYBOARD, r.result)
        // metadata 仍然要給 —— 第一步還是可以進第二步，只是縮圖牆空白（規格第七節）
        assertEquals("剛上傳的短片", r.meta!!.title)
        assertEquals(8, r.meta!!.durationSec)
        assertNull(r.storyboardSpec)
    }

    @Test
    fun `不公開影片的 privacy 是 unlisted`() {
        val r = parseWatchPage("KUdmrPVssFA", page("unlisted.html"))
        assertEquals(FetchResult.OK, r.result)
        assertEquals("unlisted", r.meta!!.privacy)
        assertEquals(false, r.meta!!.playableInEmbed)
        assertEquals("2026-07-30T01:58:30-07:00", r.meta!!.publishedAt)
    }

    @Test
    fun `頁面結構變了就是 parse_failed`() {
        val r = parseWatchPage("x", page("garbage.html"))
        assertEquals(FetchResult.PARSE_FAILED, r.result)
        assertNull(r.meta)
    }

    @Test
    fun `解析器不會自己回傳 fetch_failed`() {
        // FETCH_FAILED 是 :app 的 youtube 模組在 HTTP 層產生的，純解析器碰不到網路
        val all = listOf("ok-real.html", "unavailable.html", "login-required.html", "no-storyboard.html", "garbage.html")
        val results = all.map { parseWatchPage("x", page(it)).result }
        assertTrue(results.toString(), FetchResult.FETCH_FAILED !in results)
    }

    @Test
    fun `空字串不會當機`() {
        assertEquals(FetchResult.PARSE_FAILED, parseWatchPage("x", "").result)
    }
```

- [ ] **Step 3: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 失敗，`unavailable.html` 目前回 `OK` 而不是 `VIDEO_UNAVAILABLE`。

- [ ] **Step 4: 補上分類**

  `WatchPageParser.kt` 的 `parseWatchPage`，把 `val microformat = …` 之前的部分換成：

```kotlin
    val playability = root.obj("playabilityStatus")
    val details = root.obj("videoDetails")

    // 連兩個都撈不到 → 頁面結構變了，不是影片有問題
    if (playability == null && details == null) {
        return WatchPage(FetchResult.PARSE_FAILED, null, null)
    }

    // 判定順序：先確認能不能播，再談有沒有 storyboard（規格第二節第 4 點）
    val status = playability?.str("status")
    if (status != "OK") {
        // LOGIN_REQUIRED（年齡限制）即使帶著 videoDetails 與 spec 也歸這一類 ——
        // app 內的播放器一樣播不了，進第二步沒有意義。
        return WatchPage(FetchResult.VIDEO_UNAVAILABLE, null, null)
    }
    if (details == null) {
        return WatchPage(FetchResult.PARSE_FAILED, null, null)
    }
```

  並把函式最後兩行換成：

```kotlin
    val spec = root.obj("storyboards")?.obj("playerStoryboardSpecRenderer")?.str("spec")
    // 沒有 spec 不是錯誤：影片過短、直播中、剛上傳都會這樣。
    // metadata 仍然要給，第一步照樣能進第二步，只是縮圖牆空白（規格第七節）。
    return if (spec.isNullOrBlank()) {
        WatchPage(FetchResult.NO_STORYBOARD, meta, null)
    } else {
        WatchPage(FetchResult.OK, meta, spec)
    }
```

- [ ] **Step 5: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`WatchPageParserTest` 13 個測試通過，`:core` 共 61 個。

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "feat(core): watch page 的五種失敗分類"
```

---

### Task 3.5: `:app` youtube 模組（HTTP）

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/xenyaa/videoshot/youtube/Youtube.kt`
- Create: `android/app/src/main/java/com/xenyaa/videoshot/youtube/OkHttpYoutube.kt`
- Modify: `android/app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt`
- Test: `android/app/src/androidTest/java/com/xenyaa/videoshot/youtube/OkHttpYoutubeTest.kt`

**Interfaces:**
- Consumes: Task 3.3／3.4 的 `parseWatchPage`、`WatchPage`、`FetchResult`
- Produces:
  - `interface Youtube { suspend fun watchPage(videoId: String): WatchPage; suspend fun sheet(url: String): ByteArray }`
  - `class SheetForbidden(url: String) : Exception`
  - `class OkHttpYoutube(private val client: OkHttpClient, private val io: CoroutineDispatcher) : Youtube`
  - `AppContainer.youtube`

- [ ] **Step 1: 加入 OkHttp 與網路權限**

  `android/gradle/libs.versions.toml`，`[versions]` 加：

```toml
okhttp = "5.5.0"
```

  `[libraries]` 加：

```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver3", version.ref = "okhttp" }
```

  `android/app/build.gradle.kts` 的 `dependencies { … }` 加：

```kotlin
    implementation(libs.okhttp)
    androidTestImplementation(libs.mockwebserver)
```

  `android/app/src/main/AndroidManifest.xml` 的 `<manifest>` 之下、`<application>` 之前加：

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

  **MockWebServer 只能跑明文 HTTP，而 Android 9+ 預設禁止明文** ——
  不加下面兩個檔案，所有走 MockWebServer 的測試都會死在
  `UnknownServiceException: CLEARTEXT communication to localhost not permitted`。
  只放在 `debug` source set，release 仍然全面禁止明文。

  `android/app/src/debug/res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

  `android/app/src/debug/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
```

- [ ] **Step 2: 寫失敗的測試**

  用 `MockWebServer` 而不是打真的 YouTube：測試要離線、穩定、可重現（規格第十三節）。

  `android/app/src/androidTest/java/com/xenyaa/videoshot/youtube/OkHttpYoutubeTest.kt`：

```kotlin
package com.xenyaa.videoshot.youtube

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.core.youtube.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OkHttpYoutubeTest {

    private lateinit var server: MockWebServer
    private lateinit var youtube: OkHttpYoutube

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        youtube = OkHttpYoutube(
            // 明確給短逾時：連不上的案例要快速失敗，不要等網路層的預設 10 秒
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build(),
            io = Dispatchers.IO,
            watchUrl = { id -> server.url("/watch?v=$id").toString() },
        )
    }

    @After fun tearDown() { server.close() }

    private val okBody = """
        <!doctype html><html><body><script>var ytInitialPlayerResponse =
        {"playabilityStatus":{"status":"OK","playableInEmbed":true},
         "videoDetails":{"videoId":"v1","title":"測試片","author":"頻道","lengthSeconds":"60"},
         "microformat":{"playerMicroformatRenderer":{"publishDate":"2026-01-01T00:00:00Z","isUnlisted":false}},
         "storyboards":{"playerStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/v1/x|48#27#100#10#10#0#d#s"}}};
        </script></body></html>
    """.trimIndent()

    @Test
    fun 正常抓得到就回_OK() = runTest {
        server.enqueue(MockResponse(code = 200, body = okBody))
        val r = youtube.watchPage("v1")
        assertEquals(FetchResult.OK, r.result)
        assertEquals("測試片", r.meta!!.title)
    }

    @Test
    fun 帶了桌機版的_User_Agent() = runTest {
        server.enqueue(MockResponse(code = 200, body = okBody))
        youtube.watchPage("v1")
        // takeRequest() 沒有逾時參數時會**無限期阻塞** —— 請求沒到就整個測試掛住（實測卡了 10 分鐘）
        val ua = server.takeRequest(5, TimeUnit.SECONDS)!!.headers["User-Agent"]
        assertTrue("UA=$ua", ua!!.contains("Mozilla/5.0"))
    }

    @Test
    fun HTTP_錯誤回_fetch_failed_而不是丟例外() = runTest {
        server.enqueue(MockResponse(code = 500))
        assertEquals(FetchResult.FETCH_FAILED, youtube.watchPage("v1").result)
    }

    @Test
    fun 連不上也回_fetch_failed() = runTest {
        server.close()   // 伺服器先關掉，連線一定失敗
        assertEquals(FetchResult.FETCH_FAILED, youtube.watchPage("v1").result)
    }

    @Test
    fun sheet_下載得到位元組() = runTest {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        // 二進位 body 只能走 Builder —— MockResponse 的公開建構子第三個參數是 String
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).build())
        assertArrayEquals(bytes, youtube.sheet(server.url("/sb/x.jpg").toString()))
    }

    @Test
    fun sheet_回_403_時丟_SheetForbidden() = runTest {
        server.enqueue(MockResponse(code = 403))
        val e = runCatching { youtube.sheet(server.url("/sb/x.jpg").toString()) }.exceptionOrNull()
        assertTrue("實際是 $e", e is SheetForbidden)
    }

    @Test
    fun sheet_其他錯誤丟一般例外() = runTest {
        server.enqueue(MockResponse(code = 500))
        val e = runCatching { youtube.sheet(server.url("/sb/x.jpg").toString()) }.exceptionOrNull()
        assertTrue("實際是 $e", e != null && e !is SheetForbidden)
    }
}
```

- [ ] **Step 3: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xenyaa.videoshot.youtube.OkHttpYoutubeTest`
  Expected: 編譯失敗，`Unresolved reference 'OkHttpYoutube'`。

- [ ] **Step 4: 介面**

  `android/app/src/main/java/com/xenyaa/videoshot/youtube/Youtube.kt`：

```kotlin
package com.xenyaa.videoshot.youtube

import com.xenyaa.videoshot.core.youtube.WatchPage

/**
 * **所有對 YouTube 非官方端點的存取都只走這裡**（規格第三節模組邊界第 3 條）。
 * YouTube 改版時只有這個模組與 :core 的 WatchPageParser 需要改。
 */
interface Youtube {
    /** 抓 watch page 並解析。網路或 HTTP 失敗時回 FETCH_FAILED，**不丟例外**（功能降級，絕不當機）。 */
    suspend fun watchPage(videoId: String): WatchPage

    /** 下載一張 storyboard sheet。403 時丟 [SheetForbidden]，呼叫端才知道該重抓 spec。 */
    suspend fun sheet(url: String): ByteArray
}

/**
 * sprite 的 sigh 簽章失效了（規格第二節第 2 點：效期不可知）。
 * 呼叫端的對策是重抓一次 watch page 拿新 spec，仍失敗才退回封面圖（規格第七節）。
 */
class SheetForbidden(url: String) : Exception("sprite 回 403，簽章可能已失效：$url")
```

- [ ] **Step 5: 實作**

  `android/app/src/main/java/com/xenyaa/videoshot/youtube/OkHttpYoutube.kt`：

```kotlin
package com.xenyaa.videoshot.youtube

import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.core.youtube.parseWatchPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 桌機版 Chrome 的 UA。行動版 UA 會拿到結構不同的頁面，解析器吃不了。 */
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"

class OkHttpYoutube(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher,
    /** 測試用：把網址指到 MockWebServer。正式環境用預設值。 */
    private val watchUrl: (String) -> String = { "https://www.youtube.com/watch?v=$it" },
) : Youtube {

    override suspend fun watchPage(videoId: String): WatchPage = withContext(io) {
        val request = Request.Builder()
            .url(watchUrl(videoId))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext WatchPage(FetchResult.FETCH_FAILED, null, null)
                }
                val html = response.body.string()
                parseWatchPage(videoId, html)
            }
        } catch (e: IOException) {
            // 沒網路、DNS 失敗、逾時 —— 都是環境問題，不是解析器壞了
            WatchPage(FetchResult.FETCH_FAILED, null, null)
        }
    }

    override suspend fun sheet(url: String): ByteArray = withContext(io) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw SheetForbidden(url)
            if (!response.isSuccessful) throw IOException("sheet HTTP ${response.code}：$url")
            response.body.bytes()
        }
    }
}
```

- [ ] **Step 6: 掛進 `AppContainer`**

  `AppContainer.kt` 的 class 內追加（import `com.xenyaa.videoshot.youtube.OkHttpYoutube`、
  `com.xenyaa.videoshot.youtube.Youtube`、`okhttp3.OkHttpClient`）：

```kotlin
    /** 整個 app 共用一個 OkHttpClient —— 它自帶連線池與執行緒池，每次 new 一個會把資源用光。 */
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val youtube: Youtube by lazy { OkHttpYoutube(httpClient, Dispatchers.IO) }
```

- [ ] **Step 7: 執行測試，確認通過**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xenyaa.videoshot.youtube.OkHttpYoutubeTest`
  Expected: `BUILD SUCCESSFUL`，7 個測試通過。

- [ ] **Step 8: Commit**

```bash
git add android
git commit -m "feat(youtube): OkHttp 抓取 watch page 與 sheet"
```

---

### Task 3.6: `thumbs` 讀取端

**Files:**
- Create: `android/app/src/main/java/com/xenyaa/videoshot/thumbs/ThumbKey.kt`
- Create: `android/app/src/main/java/com/xenyaa/videoshot/thumbs/Thumbs.kt`
- Create: `android/app/src/main/java/com/xenyaa/videoshot/thumbs/FileThumbs.kt`
- Modify: `android/app/src/main/java/com/xenyaa/videoshot/data/repo/LibraryRepo.kt`
- Modify: `android/app/src/main/java/com/xenyaa/videoshot/data/repo/RoomLibraryRepo.kt`
- Modify: `android/app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt`
- Test: `android/app/src/androidTest/java/com/xenyaa/videoshot/thumbs/ThumbsTest.kt`

**Interfaces:**
- Consumes: `ShotRow`（階段 2）、`LibraryRepo`
- Produces:
  - `data class ThumbKey(val videoId: String, val level: Int, val frameIndex: Int)`
    與 `fun ThumbKey.relativePath(): String`（`"{videoId}/L{level}/{frameIndex}.webp"`）
  - `sealed interface ThumbSource`：`LocalFile(file)`、`Bytes(webp)`、`Cover(videoId)`、`Placeholder`
  - `interface Thumbs { suspend fun thumbFor(shot: ShotRow): ThumbSource; fun fileOf(key: ThumbKey): File; fun exists(key: ThumbKey): Boolean }`
  - `LibraryRepo.shotImage(shotId: Long): ByteArray?`
  - `AppContainer.thumbs`

- [ ] **Step 1: 寫失敗的測試**

  `android/app/src/androidTest/java/com/xenyaa/videoshot/thumbs/ThumbsTest.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThumbsTest {

    private lateinit var root: File
    private lateinit var thumbs: Thumbs
    private val images = mutableMapOf<Long, ByteArray>()

    @Before fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "thumbs-test-${System.nanoTime()}",
        )
        thumbs = FileThumbs(root, Dispatchers.IO) { id -> images[id] }
    }

    private fun shot(
        id: Long = 1,
        source: String = "storyboard",
        frameIndex: Int? = 7,
        sbLevel: Int? = 3,
        videoId: String = "v1",
    ) = ShotRow(id, videoId, 0.0, source, frameIndex, sbLevel, "2026-01-01", null, null)

    @Test
    fun 識別碼推導出的相對路徑() {
        assertEquals("v1/L3/7.webp", ThumbKey("v1", 3, 7).relativePath())
        assertEquals("abc-DEF_12/L2/0.webp", ThumbKey("abc-DEF_12", 2, 0).relativePath())
    }

    @Test
    fun 檔案在的時候回傳檔案() = runTest {
        val key = ThumbKey("v1", 3, 7)
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val src = thumbs.thumbFor(shot())
        assertTrue("實際是 $src", src is ThumbSource.LocalFile)
        assertEquals(thumbs.fileOf(key), (src as ThumbSource.LocalFile).file)
    }

    @Test
    fun 檔案不在的時候退回封面圖() = runTest {
        val src = thumbs.thumbFor(shot())
        assertTrue("實際是 $src", src is ThumbSource.Cover)
        assertEquals("v1", (src as ThumbSource.Cover).videoId)
    }

    @Test
    fun 手動補圖讀_shot_image_的_BLOB() = runTest {
        images[42L] = byteArrayOf(9, 9, 9)
        val src = thumbs.thumbFor(shot(id = 42, source = "manual", frameIndex = null, sbLevel = null))
        assertTrue("實際是 $src", src is ThumbSource.Bytes)
        assertTrue(byteArrayOf(9, 9, 9).contentEquals((src as ThumbSource.Bytes).webp))
    }

    @Test
    fun 手動補圖的_BLOB_不見了就給預留圖() = runTest {
        // 手動圖無法從 YouTube 重建，退回封面圖會誤導 —— 直接給預留圖
        val src = thumbs.thumbFor(shot(id = 99, source = "manual", frameIndex = null, sbLevel = null))
        assertEquals(ThumbSource.Placeholder, src)
    }

    @Test
    fun 欄位不全的_storyboard_shot_給預留圖而不是當機() = runTest {
        assertEquals(ThumbSource.Placeholder, thumbs.thumbFor(shot(frameIndex = null)))
        assertEquals(ThumbSource.Placeholder, thumbs.thumbFor(shot(sbLevel = null)))
    }

    @Test
    fun exists_反映檔案在不在() {
        val key = ThumbKey("v1", 3, 1)
        assertEquals(false, thumbs.exists(key))
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertEquals(true, thumbs.exists(key))
    }

    @Test
    fun 不同層級各自分開放() {
        assertTrue(thumbs.fileOf(ThumbKey("v1", 3, 0)) != thumbs.fileOf(ThumbKey("v1", 2, 0)))
    }
}
```

- [ ] **Step 2: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xenyaa.videoshot.thumbs.ThumbsTest`
  Expected: 編譯失敗，`Unresolved reference 'ThumbKey'`、`'FileThumbs'`。

- [ ] **Step 3: 識別碼**

  `android/app/src/main/java/com/xenyaa/videoshot/thumbs/ThumbKey.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

/**
 * 縮圖的**邏輯識別碼**（規格第四節「跨平台的資料契約」第 1 條）。
 *
 * DB 裡不存任何檔案路徑 —— 這個識別碼由 shot 的 (video_id, sb_level, frame_index) 推導，
 * 而「識別碼對應到哪種儲存」只在 thumbs 模組內決定。
 * 換到別的平台（瀏覽器的 OPFS、iOS 的檔案）時，只有下面這個 relativePath 要重寫。
 */
data class ThumbKey(val videoId: String, val level: Int, val frameIndex: Int)

fun ThumbKey.relativePath(): String = "$videoId/L$level/$frameIndex.webp"
```

- [ ] **Step 4: 介面**

  `android/app/src/main/java/com/xenyaa/videoshot/thumbs/Thumbs.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

import com.xenyaa.videoshot.data.repo.model.ShotRow
import java.io.File

/**
 * 一張圖從哪裡來。畫面只管畫，不必知道背後是檔案、BLOB 還是降級的替代圖
 * （規格第三節模組邊界第 2 條）。
 */
sealed interface ThumbSource {
    /** storyboard 裁出來的單格，存在 thumbs/ 底下 */
    data class LocalFile(val file: File) : ThumbSource

    /** 手動補圖，存在 library.db 的 shot_image */
    data class Bytes(val webp: ByteArray) : ThumbSource {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && webp.contentEquals(other.webp))

        override fun hashCode(): Int = webp.contentHashCode()
    }

    /** 縮圖還沒回填：先顯示影片封面（無簽章、不會過期） */
    data class Cover(val videoId: String) : ThumbSource

    /** 無法取回：顯示預留圖（規格第七節、第十一節的 lost 狀態） */
    data object Placeholder : ThumbSource
}

/** **縮圖的讀寫只走這裡**（規格第三節模組邊界第 2 條）。 */
interface Thumbs {
    suspend fun thumbFor(shot: ShotRow): ThumbSource
    fun fileOf(key: ThumbKey): File
    fun exists(key: ThumbKey): Boolean
}

/** 封面圖的網址。無簽章、不會過期，所以缺圖時拿它頂著是安全的（規格第五節）。 */
fun coverUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
```

- [ ] **Step 5: repo 補上讀 BLOB 的方法**

  `data/repo/LibraryRepo.kt` 的 interface 內追加：

```kotlin
    /** 手動補圖的 WebP 位元組；不是手動圖或圖不見了就回 null。 */
    suspend fun shotImage(shotId: Long): ByteArray?
```

  `data/repo/RoomLibraryRepo.kt` 的 class 內追加：

```kotlin
    override suspend fun shotImage(shotId: Long): ByteArray? = withContext(io) {
        db.shotDao().imageOf(shotId)?.webp
    }
```

- [ ] **Step 6: 讀取端實作**

  `android/app/src/main/java/com/xenyaa/videoshot/thumbs/FileThumbs.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @param root 縮圖根目錄，正式環境是 `filesDir/thumbs`（**不可用 cacheDir**，規格第四節）
 * @param loadManualImage 讀 shot_image 的 BLOB。傳函式而不是整個 repo，
 *        是為了讓 thumbs 只依賴它真正需要的那一件事，測試也不必準備資料庫。
 */
class FileThumbs(
    private val root: File,
    private val io: CoroutineDispatcher,
    private val loadManualImage: suspend (Long) -> ByteArray?,
) : Thumbs {

    override fun fileOf(key: ThumbKey): File = File(root, key.relativePath())

    override fun exists(key: ThumbKey): Boolean = fileOf(key).exists()

    override suspend fun thumbFor(shot: ShotRow): ThumbSource = withContext(io) {
        if (shot.source == "manual") {
            // 手動圖無法從 YouTube 重建，不見了就是不見了 —— 退回封面圖會誤導使用者
            return@withContext loadManualImage(shot.id)
                ?.let { ThumbSource.Bytes(it) }
                ?: ThumbSource.Placeholder
        }
        val level = shot.sbLevel
        val frameIndex = shot.frameIndex
        if (level == null || frameIndex == null) {
            // storyboard 來源卻缺了推導路徑必要的欄位 —— 資料不一致，但不該當機
            return@withContext ThumbSource.Placeholder
        }
        val file = fileOf(ThumbKey(shot.videoId, level, frameIndex))
        if (file.exists()) ThumbSource.LocalFile(file) else ThumbSource.Cover(shot.videoId)
    }
}
```

- [ ] **Step 7: 掛進 `AppContainer`**

  `AppContainer.kt` 的 class 內追加（import `com.xenyaa.videoshot.thumbs.FileThumbs`、
  `com.xenyaa.videoshot.thumbs.Thumbs`）：

```kotlin
    val thumbs: Thumbs by lazy {
        FileThumbs(File(appContext.filesDir, "thumbs"), Dispatchers.IO) { libraryRepo.shotImage(it) }
    }
```

- [ ] **Step 8: 執行測試，確認通過**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL`；`ThumbsTest` 8 個測試通過，先前的測試全部仍通過。

- [ ] **Step 9: Commit**

```bash
git add android
git commit -m "feat(thumbs): 縮圖識別碼與 thumbFor 單一讀取入口"
```

---

### Task 3.7: `thumbs` 寫入端（下載、裁切、WebP）

**Files:**
- Create: `android/app/src/main/java/com/xenyaa/videoshot/thumbs/SheetHarvester.kt`
- Test: `android/app/src/androidTest/java/com/xenyaa/videoshot/thumbs/SheetHarvesterTest.kt`

**Interfaces:**
- Consumes: Task 3.5 的 `Youtube`／`SheetForbidden`、Task 3.6 的 `Thumbs`／`ThumbKey`、
  `:core` 的 `Storyboard.parse`／`pickLevel`／`framePosition`／`sheetUrl`、`dHash`
- Produces:
  - `class SheetHarvester(youtube, thumbs, default: CoroutineDispatcher)`
  - `suspend fun harvest(videoId: String, spec: StoryboardSpec, level: StoryboardLevel, frameIndexes: List<Int>, refreshSpec: suspend () -> StoryboardSpec?): HarvestResult`
  - `data class HarvestResult(val written: List<ThumbKey>, val failed: List<Int>, val degradedToCover: Boolean)`
  - `fun grayscale9x8(sheet: Bitmap, pos: FramePos): IntArray`

- [ ] **Step 1: 寫失敗的測試**

  `android/app/src/androidTest/java/com/xenyaa/videoshot/thumbs/SheetHarvesterTest.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class SheetHarvesterTest {

    /** 3×3 格、每格 320×180 的假 sheet；每一格填不同顏色，才驗得出裁對了沒有。 */
    private fun fakeSheet(): ByteArray {
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.WHITE, Color.GRAY, Color.BLACK,
        )
        for (i in 0 until 9) {
            paint.color = colors[i]
            val x = (i % 3) * 320f
            val y = (i / 3) * 180f
            canvas.drawRect(x, y, x + 320f, y + 180f, paint)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private class FakeYoutube(
        private val sheets: MutableList<Result<ByteArray>>,
    ) : Youtube {
        val requestedUrls = mutableListOf<String>()
        override suspend fun watchPage(videoId: String): WatchPage = error("這個測試不會用到")
        override suspend fun sheet(url: String): ByteArray {
            requestedUrls += url
            return sheets.removeAt(0).getOrThrow()
        }
    }

    private val spec = Storyboard.parse(
        "https://i.ytimg.com/sb/v1/storyboard3_L\$L/\$N.jpg?sqp=x" +
            "|48#27#100#10#10#0#default#rs\$A" +
            "|320#180#9#3#3#1000#M\$M#rs\$B"
    )!!
    private val level = Storyboard.pickLevel(spec, preferred = 1)!!

    private lateinit var root: File
    private lateinit var thumbs: Thumbs

    @Before fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "harvest-test-${System.nanoTime()}",
        )
        thumbs = FileThumbs(root, Dispatchers.IO) { null }
    }

    @Test
    fun 裁出指定的格子並存成_320x180_的_WebP() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0, 4, 8)) { null }

        assertEquals(3, r.written.size)
        assertTrue(r.failed.isEmpty())
        for (key in r.written) {
            val f = thumbs.fileOf(key)
            assertTrue("${key.relativePath()} 不存在", f.exists())
            val decoded = BitmapFactory.decodeFile(f.path)
            assertEquals(320, decoded.width)
            assertEquals(180, decoded.height)
        }
    }

    @Test
    fun 裁到的是正確的那一格() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        harvester.harvest("v1", spec, level, listOf(4)) { null }

        // 第 4 格在中間，測試圖填的是 CYAN
        val decoded = BitmapFactory.decodeFile(thumbs.fileOf(ThumbKey("v1", 1, 4)).path)
        val px = decoded.getPixel(160, 90)
        assertEquals("實際 ${Integer.toHexString(px)}", Color.CYAN, px)
    }

    @Test
    fun 同一張_sheet_只下載一次() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        harvester.harvest("v1", spec, level, listOf(0, 1, 2, 3)) { null }
        assertEquals(1, yt.requestedUrls.size)
    }

    @Test
    fun 已經有檔案的格子直接跳過不重寫() = runTest {
        val key = ThumbKey("v1", 1, 0)
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val yt = FakeYoutube(mutableListOf())   // 一張 sheet 都不給 —— 真的去下載就會炸
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertTrue(r.written.isEmpty())
        assertTrue(r.failed.isEmpty())
        assertEquals(0, yt.requestedUrls.size)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(thumbs.fileOf(key).readBytes()))
    }

    @Test
    fun sheet_回_403_時重抓一次_spec_再試() = runTest {
        val yt = FakeYoutube(
            mutableListOf(Result.failure(SheetForbidden("x")), Result.success(fakeSheet()))
        )
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        var refreshed = 0

        val r = harvester.harvest("v1", spec, level, listOf(0)) { refreshed++; spec }

        assertEquals(1, refreshed)
        assertEquals(1, r.written.size)
        assertEquals(false, r.degradedToCover)
    }

    @Test
    fun 重抓之後仍然_403_就退回封面圖() = runTest {
        val yt = FakeYoutube(
            mutableListOf(Result.failure(SheetForbidden("x")), Result.failure(SheetForbidden("x")))
        )
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { spec }

        assertTrue(r.written.isEmpty())
        assertEquals(listOf(0), r.failed)
        assertEquals(true, r.degradedToCover)   // 規格第七節：先重抓 spec，仍失敗才退回封面
    }

    @Test
    fun 重抓_spec_也拿不到時不會無限重試() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.failure(SheetForbidden("x"))))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertEquals(listOf(0), r.failed)
        assertEquals(true, r.degradedToCover)
        assertEquals(1, yt.requestedUrls.size)
    }

    @Test
    fun sheet_解不出圖片時記為失敗而不是當機() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(byteArrayOf(1, 2, 3))))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertEquals(listOf(0), r.failed)
    }

    @Test
    fun 灰階取樣是_9x8_共_72_個值() = runTest {
        val sheet = BitmapFactory.decodeByteArray(fakeSheet(), 0, fakeSheet().size)
        val pos = Storyboard.framePosition(level, 0)
        val gray = grayscale9x8(sheet, pos)
        assertEquals(72, gray.size)
        assertTrue(gray.all { it in 0..255 })
    }
}
```

- [ ] **Step 2: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xenyaa.videoshot.thumbs.SheetHarvesterTest`
  Expected: 編譯失敗，`Unresolved reference 'SheetHarvester'`、`'grayscale9x8'`。

- [ ] **Step 3: 實作**

  `android/app/src/main/java/com/xenyaa/videoshot/thumbs/SheetHarvester.kt`：

```kotlin
package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.xenyaa.videoshot.core.similarity.DHASH_HEIGHT
import com.xenyaa.videoshot.core.similarity.DHASH_WIDTH
import com.xenyaa.videoshot.core.storyboard.FramePos
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * @param written 這次真的寫進 thumbs/ 的格子（已存在而跳過的不算）
 * @param failed 拿不到的格號
 * @param degradedToCover 重抓 spec 之後仍然 403 —— 呼叫端該顯示封面圖（規格第七節）
 */
data class HarvestResult(
    val written: List<ThumbKey>,
    val failed: List<Int>,
    val degradedToCover: Boolean,
)

/**
 * 下載 storyboard sheet、裁出指定的格子、編成 320×180 WebP 存進 thumbs/。
 *
 * **存單格不存 sheet**（規格第七節）：一張 sheet 54 KB / 9 格，單格 5.9 KB，
 * 要收藏到 9.2 格才划算 —— 但一張只有 9 格，所以存 sheet 永遠不會比較省。
 */
class SheetHarvester(
    private val youtube: Youtube,
    private val thumbs: Thumbs,
    /** 裁切與編碼是重運算，規格第三節設計原則第 5 條要求放 Default。 */
    private val default: CoroutineDispatcher,
) {

    /**
     * @param frameIndexes 要取的格號
     * @param refreshSpec sprite 回 403 時重抓 watch page 拿新 spec；拿不到回 null
     */
    suspend fun harvest(
        videoId: String,
        spec: StoryboardSpec,
        level: StoryboardLevel,
        frameIndexes: List<Int>,
        refreshSpec: suspend () -> StoryboardSpec?,
    ): HarvestResult {
        val written = mutableListOf<ThumbKey>()
        val failed = mutableListOf<Int>()
        var degraded = false
        var currentSpec = spec
        var refreshed = false

        // 同一張 sheet 上的格子一起處理，一張只下載一次
        val todo = frameIndexes.filterNot { thumbs.exists(ThumbKey(videoId, level.level, it)) }
        val bySheet = todo.groupBy { Storyboard.framePosition(level, it).sheetIndex }

        for ((sheetIndex, frames) in bySheet) {
            val bytes = try {
                youtube.sheet(Storyboard.sheetUrl(currentSpec, level, sheetIndex))
            } catch (e: SheetForbidden) {
                // 簽章效期不可知（規格第二節第 2 點）：重抓一次 watch page 拿新 spec 再試
                if (refreshed) { failed += frames; degraded = true; continue }
                refreshed = true
                val fresh = refreshSpec()
                if (fresh == null) { failed += frames; degraded = true; continue }
                currentSpec = fresh
                try {
                    youtube.sheet(Storyboard.sheetUrl(currentSpec, level, sheetIndex))
                } catch (e2: Exception) {
                    failed += frames; degraded = true; continue
                }
            } catch (e: Exception) {
                failed += frames; continue
            }

            val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (sheet == null) {
                // 下載成功但解不出圖 —— 截圖 POC 學到的教訓：這是一種失敗型態，不是例外
                failed += frames
                continue
            }

            for (frameIndex in frames) {
                val key = ThumbKey(videoId, level.level, frameIndex)
                val ok = withContext(default) {
                    runCatching { writeFrame(sheet, Storyboard.framePosition(level, frameIndex), key) }
                        .getOrDefault(false)
                }
                if (ok) written += key else failed += frameIndex
            }
            sheet.recycle()
        }
        return HarvestResult(written, failed, degraded)
    }

    private fun writeFrame(sheet: Bitmap, pos: FramePos, key: ThumbKey): Boolean {
        if (pos.x + pos.width > sheet.width || pos.y + pos.height > sheet.height) return false
        val frame = Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height)
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format =
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        val encoded = frame.compress(format, 75, out)
        frame.recycle()
        if (!encoded) return false
        val file = thumbs.fileOf(key)
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
        return true
    }
}

/**
 * 把 sheet 上的某一格取樣成 9×8 灰階，給 dHash 用（規格第五節第二步）。
 * 直接從 sheet 取樣，不先裁再縮 —— 少一次 Bitmap 配置，148 格會差很多。
 */
fun grayscale9x8(sheet: Bitmap, pos: FramePos): IntArray {
    val out = IntArray(DHASH_WIDTH * DHASH_HEIGHT)
    for (y in 0 until DHASH_HEIGHT) {
        for (x in 0 until DHASH_WIDTH) {
            val sx = (pos.x + pos.width * x / DHASH_WIDTH).coerceIn(0, sheet.width - 1)
            val sy = (pos.y + pos.height * y / DHASH_HEIGHT).coerceIn(0, sheet.height - 1)
            val p = sheet.getPixel(sx, sy)
            out[y * DHASH_WIDTH + x] =
                (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
        }
    }
    return out
}
```

- [ ] **Step 4: 執行測試，確認通過**

  Run: `cd android && ./gradlew :app:connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL`；`SheetHarvesterTest` 9 個測試通過，先前的全部仍通過。

- [ ] **Step 5: 掛進 `AppContainer`**

  `AppContainer.kt` 的 class 內追加（import `com.xenyaa.videoshot.thumbs.SheetHarvester`）：

```kotlin
    val sheetHarvester: SheetHarvester by lazy { SheetHarvester(youtube, thumbs, Dispatchers.Default) }
```

- [ ] **Step 6: 把本階段的決策寫回規格**

  規格 `docs/superpowers/specs/2026-09-10-videoshot-app-design.md`：

  1. 第三節「專案結構」的模組樹，`youtube/` 那一行加註解：

     ```
     │   ├─ youtube/     OkHttp 抓 watch page 與 sheet；**解析與失敗分類在 :core/youtube**（純邏輯，JVM 測試）
     ```

     並在 `:core` 的清單加一行：

     ```
     ├─ youtube      watch page 解析、欄位對應、五種失敗分類（吃錄製的頁面）
     ```

  2. 第五節收斂那一段，把這一行：

     ```
     - 演算法：每格縮到 9×8 灰階，算 **dHash**（64-bit 指紋），漢明距離小於門檻視為相似。依時間順序掃描，每組保留最早的一張。
       ⏳ 暫定門檻：高 ≤ 10、中 ≤ 6、低 ≤ 3，待實測調校。
     ```

     換成：

     ```
     - 演算法：每格縮到 9×8 灰階，算 **dHash**（64-bit 指紋），漢明距離 **≤ 門檻**視為相似。
       依時間順序掃描，每組保留最早的一張 —— 比較的對象是「最近一張**被保留的**」，不是「前一張」
       （跟前一張比的話，畫面緩慢變化時會一路藏到底，只剩第一張）。
       ⏳ 暫定門檻：高 ≤ 10、中 ≤ 6、低 ≤ 3，待以真實影片調校。
     ```

  3. 第十六節「開放項目」表的「dHash 三檔門檻值」那一列，位置欄改為 `第五節、:core 的 FilterStrength`。

- [ ] **Step 7: 路線圖標記階段 3 完成**

  `docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md`：
  「一、階段總覽」表階段 3 那列的細節計畫欄改為 `✅ 完成`；
  階段 3 的任務表加一行註記：**T3.3（InnerTube 用戶端）依 POC P-3 的否定結果刪除**，
  T3.1 拆成 `:core` 解析與 `:app` HTTP，T3.4 拆成讀取端與寫入端。

- [ ] **Step 8: Commit**

```bash
git add android docs
git commit -m "feat(thumbs): sheet 下載、裁切與 WebP 編碼"
```

---

## 刻意留給階段 4 的部分

規格第五節第二步寫「sheet 由 app 直接下載（**同時 4 張**）…縮圖牆收到一張就畫一張」。
本階段的 `SheetHarvester.harvest()` 是**循序**下載、一次回傳結果 —— 併發與串流是縮圖牆的需求，
屬於階段 4 的 T4.5（「收到一張畫一張」）。屆時在 harvester 外面用
`coroutineScope` ＋ `Semaphore(4)` 包一層、改成回傳 `Flow<ThumbKey>` 即可，
不必動裡面的下載／裁切／編碼邏輯。**這一段不是遺漏，是分工。**

## 完成後的狀態

- `:core` 多了 `similarity`（dHash、收斂）與 `youtube`（解析、五種失敗分類），全部是 JVM 單元測試
- `:app` 多了 `youtube`（OkHttp，用 MockWebServer 測）與 `thumbs`（讀取入口 ＋ 下載裁切）
- `:core` 約 61 個測試、`:app` 儀器測試約 72 個
- 規格的「收斂門檻比較方式」與「youtube 解析放 :core」都已寫回

**接下來**：階段 4（取圖精靈第一、二步）的前置全部備齊 —— 它要的 `youtube.watchPage`、
`Storyboard.pickLevel`、`SheetHarvester`、`converge` 都在這裡做完了。
階段 13（縮圖回填）也只差 WorkManager 的作業鏈。
