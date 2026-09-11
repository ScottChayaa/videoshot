# 階段 1 · Gradle 骨架與 storyboard 移植 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 標 👤 的步驟需要在 Android Studio 的 GUI 裡操作，代理執行時要請使用者操作並回報。

**Goal:** 建立 `android/` Gradle 專案（`:app` ＋ `:core`），並把 `src/lib/storyboard.ts` 的解析與定位邏輯連同測試移植成 Kotlin。

**Architecture:** `:app` 用 Android Studio 的 Compose 範本產生，本階段只確認能建置與安裝。`:core` 是純 Kotlin（JVM）模組，
不依賴 Android SDK，單元測試直接在 JVM 上跑。storyboard 邏輯放在 `com.xenyaa.videoshot.core.storyboard`。

**Tech Stack:** Kotlin、Gradle（Kotlin DSL、version catalog）、Jetpack Compose 範本、JUnit 4。

**Spec:** [`../specs/2026-09-10-videoshot-app-design.md`](../specs/2026-09-10-videoshot-app-design.md) 第二節第 1 點（storyboard 格式與定位規則）、第三節（專案結構、工具鏈、`:core`）。
**路線圖：** [`2026-09-11-videoshot-android-實作計畫.md`](2026-09-11-videoshot-android-實作計畫.md) 階段 1。
**移植來源：** `src/lib/storyboard.ts`、`src/lib/storyboard.test.ts`、`src/lib/types.ts`（**只讀，不得修改或刪除** —— `mockups/server.mjs:42` 執行期讀取它）。

## Global Constraints

- applicationId：`com.xenyaa.videoshot`；minSdk 26；`android:allowBackup="false"`
- `:core` 不得依賴 Android SDK
- 定位規則：取**最近的一格**（`round`），不是之前的一格；誤差 ±間隔/2（規格第二節第 1 點）
- commit message 用繁體中文，格式 `類型(範圍): 描述`，不加 AI 生成標記

## 與 TypeScript 版的差異（刻意的）

| TS 版 | Kotlin 版 | 理由 |
|---|---|---|
| `frameAt` 回傳負的 `offsetX`／`offsetY` | 回傳正的 `x`／`y`（該格在 sheet 內的像素座標） | 負值是給 CSS `background-position` 用的；Android 裁切用正座標 |
| 不回傳格號 | `FramePos.frameIndex` | `frame_index` 是去重與縮圖識別碼的一部分 |
| 只有 `frameAt(level, t)` | 拆成 `frameIndexAt`、`framePosition`、`frameAt` | 縮圖牆要逐格畫（由格號求位置），截圖要由秒數求格號 |
| — | `frameTimeSec(level, index)` | 縮圖牆每格的時間標籤 |
| `intervalMs = 0` 且 `t = 0` 時得到 NaN | 回傳第 0 格 | 修掉 TS 版的邊界問題 |

## 檔案結構

```
android/
├── settings.gradle.kts                  # 範本產生；T1.2 由精靈加入 include(":core")
├── gradle/libs.versions.toml            # 範本產生
├── app/
│   ├── build.gradle.kts                 # T1.2 加 implementation(project(":core"))
│   └── src/main/AndroidManifest.xml     # T1.1 改 allowBackup
└── core/
    ├── build.gradle.kts                 # 精靈產生；加 testImplementation(libs.junit)
    └── src/
        ├── main/kotlin/com/xenyaa/videoshot/core/storyboard/
        │   ├── StoryboardModels.kt      # StoryboardLevel、StoryboardSpec、FramePos
        │   └── Storyboard.kt            # parse、pickLevel、frameIndexAt、framePosition、frameAt、frameTimeSec、sheetUrl
        └── test/kotlin/com/xenyaa/videoshot/core/storyboard/
            ├── RealSpec.kt              # 實測 spec 字串
            ├── StoryboardParseTest.kt
            └── StoryboardFrameTest.kt
```

---

### Task 1.1: `android/` 專案骨架

**Files:**
- Create: `android/`（Android Studio 產生）
- Modify: `android/app/src/main/AndroidManifest.xml`
- Delete: `android/app/src/main/res/xml/backup_rules.xml`、`android/app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `CLAUDE.md`（「指令」一節）

**Interfaces:**
- Produces: 可建置的 `android/` Gradle 專案，`:app` 模組、namespace `com.xenyaa.videoshot`

前置：階段 0 的 Task 0.1（開發環境）已完成。

- [ ] **Step 1: 👤 以範本建立專案**

  Android Studio → New Project → **Empty Activity**（Compose）：
  - Name：`videoshot`
  - Package name：`com.xenyaa.videoshot`
  - Save location：`<repo>\android`
  - Minimum SDK：API 26
  - Build configuration language：Kotlin DSL

- [ ] **Step 2: 關閉系統備份**

  在 `android/app/src/main/AndroidManifest.xml` 的 `<application>` 標籤上：
  - `android:allowBackup="true"` 改為 `android:allowBackup="false"`
  - 刪除 `android:dataExtractionRules="@xml/data_extraction_rules"` 與 `android:fullBackupContent="@xml/backup_rules"` 兩個屬性

  然後刪除 `android/app/src/main/res/xml/backup_rules.xml` 與 `android/app/src/main/res/xml/data_extraction_rules.xml`。
  （規格第四節：備份機制只有 Drive 一套）

- [ ] **Step 3: 建置**

  Run: `cd android && ./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 👤 安裝到實機**

  Run: `cd android && ./gradlew installDebug`
  Expected: 手機上出現 `videoshot`，開啟後顯示範本的「Hello Android!」。

- [ ] **Step 5: CLAUDE.md 補上建置指令**

  把 `CLAUDE.md`「指令」一節的這一行：

  ```
  Gradle 專案（`android/`）的指令待實作計畫階段 1 建立後補上。
  ```

  換成：

  ````markdown
  ```bash
  cd android
  ./gradlew :core:test        # :core 的 JVM 單元測試（PowerShell 用 .\gradlew.bat）
  ./gradlew assembleDebug     # 建置 debug APK
  ./gradlew installDebug      # 安裝到 USB 連接的手機
  ```
  ````

- [ ] **Step 6: Commit**

  先確認 `git status` 沒有 `android/local.properties`、`android/.gradle/`、`android/build/`、`android/app/build/`（範本的 `.gitignore` 應已排除）。

  ```bash
  git add android CLAUDE.md
  git commit -m "chore(android): 建立 Gradle 專案骨架，關閉系統備份"
  ```

---

### Task 1.2: `:core` 模組與 storyboard spec 解析

**Files:**
- Create: `android/core/`（Android Studio 精靈產生）
- Modify: `android/core/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardModels.kt`
- Create: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/storyboard/Storyboard.kt`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/RealSpec.kt`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardParseTest.kt`

**Interfaces:**
- Produces:
  - `data class StoryboardLevel(level: Int, width: Int, height: Int, frameCount: Int, cols: Int, rows: Int, intervalMs: Int, sigh: String)`
  - `data class StoryboardSpec(baseUrl: String, sqp: String, levels: List<StoryboardLevel>)`
  - `data class FramePos(frameIndex: Int, sheetIndex: Int, col: Int, row: Int, x: Int, y: Int, width: Int, height: Int, sheetWidth: Int, sheetHeight: Int)`
  - `object Storyboard { fun parse(spec: String): StoryboardSpec? }`
  - 測試常數 `REAL_SPEC: String`（`RealSpec.kt`，Task 1.3 沿用）

- [ ] **Step 1: 👤 新增模組**

  Android Studio → File → New → New Module → **Java or Kotlin Library**：
  - Library name：`core`
  - Package name：`com.xenyaa.videoshot.core`
  - Class name：`Placeholder`
  - Language：Kotlin

  精靈會在 `android/settings.gradle.kts` 加上 `include(":core")` 並建立 `android/core/build.gradle.kts`。

- [ ] **Step 2: 移除精靈產生的佔位類別**

  刪除精靈產生的 `Placeholder.kt`（位於 `android/core/src/main/java/com/xenyaa/videoshot/core/`），並刪除空掉的 `android/core/src/main/java/` 目錄。
  本模組的原始碼一律放 `src/main/kotlin/`，測試放 `src/test/kotlin/`（Kotlin JVM 外掛預設即包含這兩個目錄）。

- [ ] **Step 3: 加測試相依、讓 `:app` 依賴 `:core`**

  `android/core/build.gradle.kts` 檔尾加：

  ```kotlin
  dependencies {
      testImplementation(libs.junit)
  }
  ```

  （`libs.junit` 是範本 version catalog 已有的 JUnit 4；若精靈已產生 `dependencies` 區塊，把這行加進去即可，不要重複區塊。）

  `android/app/build.gradle.kts` 的 `dependencies { … }` 區塊加一行：

  ```kotlin
  implementation(project(":core"))
  ```

- [ ] **Step 4: 寫測試常數**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/RealSpec.kt`：

```kotlin
package com.xenyaa.videoshot.core.storyboard

// 實測擷取自 YouTube watch page（24 秒的 unlisted 影片）；移植自 src/lib/storyboard.test.ts。
// 注意 Kotlin 字串裡的 $ 必須跳脫成 \$。
const val REAL_SPEC: String =
    "https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L\$L/\$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg==" +
        "|48#27#100#10#10#0#default#rs\$AOn4CLDCQG-jwLOoOGPBLaFWxpqItJgENA" +
        "|80#45#25#10#10#1000#M\$M#rs\$AOn4CLAdQajGjXcFllukj8IozdMskyx6Zw" +
        "|160#90#25#5#5#1000#M\$M#rs\$AOn4CLDDTrcJY1ywfKuJLuu2E4bctSN8og" +
        "|320#180#25#3#3#1000#M\$M#rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"
```

- [ ] **Step 5: 寫失敗的解析測試**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardParseTest.kt`：

```kotlin
package com.xenyaa.videoshot.core.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardParseTest {

    @Test
    fun `解析出 base URL、sqp 與四個 level`() {
        val spec = Storyboard.parse(REAL_SPEC)
        assertNotNull(spec)
        assertEquals(4, spec!!.levels.size)
        assertEquals("-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg==", spec.sqp)
        assertEquals("https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L\$L/\$N.jpg", spec.baseUrl)
    }

    @Test
    fun `L3 的參數正確`() {
        val l3 = Storyboard.parse(REAL_SPEC)!!.levels[3]
        assertEquals(
            StoryboardLevel(
                level = 3,
                width = 320,
                height = 180,
                frameCount = 25,
                cols = 3,
                rows = 3,
                intervalMs = 1000,
                sigh = "rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"
            ),
            l3
        )
    }

    @Test
    fun `level 編號等於它在 spec 裡的順序`() {
        val levels = Storyboard.parse(REAL_SPEC)!!.levels
        assertEquals(listOf(0, 1, 2, 3), levels.map { it.level })
    }

    @Test
    fun `格式不符回傳 null`() {
        assertNull(Storyboard.parse(""))
        assertNull(Storyboard.parse("https://example.com/no-levels"))
        assertNull(Storyboard.parse("not-a-url|1#2"))
    }

    @Test
    fun `欄位不足或不是數字的 level 會被略過`() {
        val spec = Storyboard.parse("https://x/\$L/\$N.jpg?sqp=a|1#2#3|320#180#x#3#3#1000#M\$M#s|320#180#25#3#3#1000#M\$M#ok")
        assertEquals(1, spec!!.levels.size)
        assertEquals("ok", spec.levels[0].sigh)
        assertEquals(2, spec.levels[0].level)
    }
}
```

  最後一個測試的 level 編號是 2（spec 裡第三段）—— 被略過的前兩段仍佔順位，與 TS 版行為一致（TS 版以 `levelParts` 的索引為 `level`）。

- [ ] **Step 6: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 編譯失敗，`Unresolved reference: Storyboard` 與 `Unresolved reference: StoryboardLevel`。

- [ ] **Step 7: 實作資料類別**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardModels.kt`：

```kotlin
package com.xenyaa.videoshot.core.storyboard

/** YouTube storyboard 的一個畫質層級（L0～L3）。level 是它在 spec 字串裡的順位。 */
data class StoryboardLevel(
    val level: Int,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val cols: Int,
    val rows: Int,
    val intervalMs: Int,
    val sigh: String,
)

data class StoryboardSpec(
    val baseUrl: String,
    val sqp: String,
    val levels: List<StoryboardLevel>,
)

/** 某一格在 sheet 裡的位置。x／y 是該格左上角在 sheet 內的像素座標。 */
data class FramePos(
    val frameIndex: Int,
    val sheetIndex: Int,
    val col: Int,
    val row: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val sheetWidth: Int,
    val sheetHeight: Int,
)
```

- [ ] **Step 8: 實作解析**

  `android/core/src/main/kotlin/com/xenyaa/videoshot/core/storyboard/Storyboard.kt`：

```kotlin
package com.xenyaa.videoshot.core.storyboard

/**
 * storyboard spec 的解析與定位。移植自 src/lib/storyboard.ts。
 * spec 格式：{baseURL}|{L0}|{L1}|{L2}|{L3}，每個 level 為 width#height#frameCount#cols#rows#intervalMs#nameReplacement#sigh。
 */
object Storyboard {

    private val sqpPattern = Regex("[?&]sqp=([^&]*)")

    fun parse(spec: String): StoryboardSpec? {
        if (spec.isEmpty()) return null
        val parts = spec.split('|')
        if (parts.size < 2) return null

        val urlPart = parts[0]
        if (!urlPart.startsWith("http")) return null
        val sqp = sqpPattern.find(urlPart)?.groupValues?.get(1) ?: ""
        val baseUrl = urlPart.substringBefore('?')

        val levels = parts.drop(1).mapIndexedNotNull { index, levelPart ->
            val fields = levelPart.split('#')
            if (fields.size < 8) return@mapIndexedNotNull null
            val numbers = fields.take(6).map { it.toIntOrNull() ?: return@mapIndexedNotNull null }
            StoryboardLevel(
                level = index,
                width = numbers[0],
                height = numbers[1],
                frameCount = numbers[2],
                cols = numbers[3],
                rows = numbers[4],
                intervalMs = numbers[5],
                sigh = fields[7],
            )
        }
        if (levels.isEmpty()) return null
        return StoryboardSpec(baseUrl, sqp, levels)
    }
}
```

- [ ] **Step 9: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`StoryboardParseTest` 5 個測試全數通過（報告在 `android/core/build/reports/tests/test/index.html`）。

- [ ] **Step 10: 確認 `:app` 仍可建置**

  Run: `cd android && ./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

  ```bash
  git add android
  git commit -m "feat(core): 新增 :core 模組與 storyboard spec 解析"
  ```

---

### Task 1.3: 層級挑選、定位與 sheet 網址

**Files:**
- Modify: `android/core/src/main/kotlin/com/xenyaa/videoshot/core/storyboard/Storyboard.kt`
- Test: `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardFrameTest.kt`

**Interfaces:**
- Consumes: Task 1.2 的 `StoryboardLevel`、`StoryboardSpec`、`FramePos`、`Storyboard.parse`、`REAL_SPEC`
- Produces（`object Storyboard` 的新成員，階段 3 的 `thumbs` 與階段 4 的縮圖牆會用到）：
  - `fun pickLevel(spec: StoryboardSpec, preferred: Int = 3): StoryboardLevel?`
  - `fun frameIndexAt(level: StoryboardLevel, t: Double): Int`
  - `fun framePosition(level: StoryboardLevel, frameIndex: Int): FramePos`
  - `fun frameAt(level: StoryboardLevel, t: Double): FramePos`
  - `fun frameTimeSec(level: StoryboardLevel, frameIndex: Int): Double`
  - `fun sheetUrl(spec: StoryboardSpec, level: StoryboardLevel, sheetIndex: Int): String`

- [ ] **Step 1: 寫失敗的測試**

  `android/core/src/test/kotlin/com/xenyaa/videoshot/core/storyboard/StoryboardFrameTest.kt`：

```kotlin
package com.xenyaa.videoshot.core.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardFrameTest {

    private val spec = Storyboard.parse(REAL_SPEC)!!
    private val l3 = spec.levels[3] // 320x180、3x3、每格 1 秒、共 25 格

    private fun FramePos.grid() = listOf(frameIndex, sheetIndex, col, row)

    // ---- pickLevel ----

    @Test
    fun `預設取 level 3`() {
        assertEquals(3, Storyboard.pickLevel(spec)!!.level)
    }

    @Test
    fun `指定的 level 不存在時退回最高可用的`() {
        assertEquals(3, Storyboard.pickLevel(spec, preferred = 9)!!.level)
    }

    @Test
    fun `沒有任何 level 時回傳 null`() {
        assertNull(Storyboard.pickLevel(StoryboardSpec("x", "", emptyList())))
    }

    @Test
    fun `frameCount 為 0 的 level 會被過濾掉`() {
        val zero = StoryboardLevel(0, 100, 100, 0, 3, 3, 1000, "test")
        assertNull(Storyboard.pickLevel(StoryboardSpec("x", "", listOf(zero))))
    }

    @Test
    fun `intervalMs 為 0 的 L0 不會被選到`() {
        val onlyL0 = StoryboardSpec("x", "", listOf(spec.levels[0]))
        assertNull(Storyboard.pickLevel(onlyL0))
    }

    // ---- frameAt ----

    @Test
    fun `t 為 0 是第 0 張的左上角`() {
        val p = Storyboard.frameAt(l3, 0.0)
        assertEquals(listOf(0, 0, 0, 0), p.grid())
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun `t 為 4 是第 0 張的第 1 列第 1 欄`() {
        val p = Storyboard.frameAt(l3, 4.0)
        assertEquals(listOf(4, 0, 1, 1), p.grid())
        assertEquals(320, p.x)
        assertEquals(180, p.y)
    }

    @Test
    fun `t 為 9 跨到第 1 張的左上角`() {
        assertEquals(listOf(9, 1, 0, 0), Storyboard.frameAt(l3, 9.0).grid())
    }

    // 取最近的一格，不是之前的一格。對照 YouTube 播放器 hover 預覽驗證過的規則。
    @Test
    fun `t 為 4 點 6 取最近的第 5 格而不是無條件捨去的第 4 格`() {
        assertEquals(listOf(5, 0, 2, 1), Storyboard.frameAt(l3, 4.6).grid())
    }

    @Test
    fun `t 為 4 點 4 仍落在第 4 格`() {
        assertEquals(listOf(4, 0, 1, 1), Storyboard.frameAt(l3, 4.4).grid())
    }

    @Test
    fun `接近下一張 sheet 時提前跨過去`() {
        assertEquals(listOf(9, 1, 0, 0), Storyboard.frameAt(l3, 8.7).grid())
    }

    @Test
    fun `超過 frameCount 時夾到最後一格`() {
        assertEquals(listOf(24, 2, 0, 2), Storyboard.frameAt(l3, 9999.0).grid())
    }

    @Test
    fun `負的秒數視為 0`() {
        assertEquals(listOf(0, 0, 0, 0), Storyboard.frameAt(l3, -3.0).grid())
    }

    @Test
    fun `帶出整張 sheet 的尺寸`() {
        val p = Storyboard.frameAt(l3, 0.0)
        assertEquals(960, p.sheetWidth)
        assertEquals(540, p.sheetHeight)
        assertEquals(320, p.width)
        assertEquals(180, p.height)
    }

    @Test
    fun `frameCount 為 0 時回傳第 0 格而不是負數`() {
        val zero = StoryboardLevel(0, 320, 180, 0, 3, 3, 1000, "test")
        val p = Storyboard.frameAt(zero, 5.0)
        assertEquals(listOf(0, 0, 0, 0), p.grid())
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun `intervalMs 為 0 時回傳第 0 格`() {
        val noInterval = l3.copy(intervalMs = 0)
        assertEquals(0, Storyboard.frameIndexAt(noInterval, 0.0))
        assertEquals(0, Storyboard.frameIndexAt(noInterval, 12.0))
    }

    // ---- framePosition 與 frameTimeSec ----

    @Test
    fun `由格號求位置與由秒數求位置一致`() {
        for (i in 0 until l3.frameCount) {
            assertEquals(Storyboard.framePosition(l3, i), Storyboard.frameAt(l3, Storyboard.frameTimeSec(l3, i)))
        }
    }

    @Test
    fun `frameTimeSec 由格號推回影片秒數`() {
        assertEquals(12.0, Storyboard.frameTimeSec(l3, 12), 0.0)
        assertEquals(12.5, Storyboard.frameTimeSec(l3.copy(intervalMs = 2500), 5), 0.0)
    }

    // ---- sheetUrl ----

    @Test
    fun `sheetUrl 換掉 level 與 sheet 編號並補上 sqp 與 sigh`() {
        val url = Storyboard.sheetUrl(spec, l3, 1)
        assertTrue(url, url.contains("/storyboard3_L3/M1.jpg"))
        assertTrue(url, url.contains("sigh=rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"))
        assertTrue(url, url.contains("sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg=="))
    }
}
```

- [ ] **Step 2: 執行測試，確認失敗**

  Run: `cd android && ./gradlew :core:test`
  Expected: 編譯失敗，`Unresolved reference: pickLevel`（以及 `frameAt`、`frameIndexAt`、`framePosition`、`frameTimeSec`、`sheetUrl`）。

- [ ] **Step 3: 實作**

  在 `Storyboard.kt` 的檔頭 `package` 之後加 import：

```kotlin
import kotlin.math.max
import kotlin.math.roundToInt
```

  並在 `object Storyboard { … }` 內、`parse` 之後加入：

```kotlin
    /** 取指定層級；不存在時退回最高可用的層級。intervalMs、cols、rows、frameCount 任一為 0 的層級不可用。 */
    fun pickLevel(spec: StoryboardSpec, preferred: Int = 3): StoryboardLevel? {
        val usable = spec.levels.filter { it.intervalMs > 0 && it.cols > 0 && it.rows > 0 && it.frameCount > 0 }
        if (usable.isEmpty()) return null
        return usable.find { it.level == preferred } ?: usable.last()
    }

    /**
     * 第 k 格代表影片的 t = k × interval，因此取「最近的一格」而非「之前的一格」。
     * 對照 YouTube 播放器 hover 預覽驗證：間隔 5s 的影片在 t=102.5（100 與 105 的中點）才從第 20 格換到第 21 格。
     */
    fun frameIndexAt(level: StoryboardLevel, t: Double): Int {
        if (level.intervalMs <= 0 || level.frameCount <= 0) return 0
        val raw = (max(0.0, t) / (level.intervalMs / 1000.0)).roundToInt()
        return raw.coerceIn(0, level.frameCount - 1)
    }

    fun framePosition(level: StoryboardLevel, frameIndex: Int): FramePos {
        val perSheet = level.cols * level.rows
        val sheetIndex = frameIndex / perSheet
        val posInSheet = frameIndex % perSheet
        val col = posInSheet % level.cols
        val row = posInSheet / level.cols
        return FramePos(
            frameIndex = frameIndex,
            sheetIndex = sheetIndex,
            col = col,
            row = row,
            x = col * level.width,
            y = row * level.height,
            width = level.width,
            height = level.height,
            sheetWidth = level.cols * level.width,
            sheetHeight = level.rows * level.height,
        )
    }

    fun frameAt(level: StoryboardLevel, t: Double): FramePos = framePosition(level, frameIndexAt(level, t))

    fun frameTimeSec(level: StoryboardLevel, frameIndex: Int): Double = frameIndex * level.intervalMs / 1000.0

    fun sheetUrl(spec: StoryboardSpec, level: StoryboardLevel, sheetIndex: Int): String {
        val path = spec.baseUrl.replace("\$L", level.level.toString()).replace("\$N", "M$sheetIndex")
        return "$path?sqp=${spec.sqp}&sigh=${level.sigh}"
    }
```

- [ ] **Step 4: 執行測試，確認通過**

  Run: `cd android && ./gradlew :core:test`
  Expected: `BUILD SUCCESSFUL`；`StoryboardParseTest`（5）與 `StoryboardFrameTest`（19）全數通過。

- [ ] **Step 5: Commit**

  ```bash
  git add android/core
  git commit -m "feat(core): storyboard 層級挑選、定位與 sheet 網址"
  ```

- [ ] **Step 6: 路線圖標記階段 1 完成**

  在 `docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md`「一、階段總覽」表的階段 1 那列，細節計畫欄改為 `✅ 完成`。

  ```bash
  git add docs/superpowers/plans/2026-09-11-videoshot-android-實作計畫.md
  git commit -m "docs(plan): 階段 1 完成"
  ```
