# 階段 12 —— Google Drive 備份與還原 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 videoshot 能把 `library.db` 備份到使用者自己的 Google Drive（appDataFolder），並能在新裝置或全新安裝時還原回來——同步模型是換機／災難復原，整包覆蓋、不做合併。

**Architecture:** 新增 `com.xenyaa.videoshot.backup` 套件（`:app`），對外只透過 `BackupStore`（Drive REST v3，OkHttp 直打，不用 Drive SDK）與 `GoogleAuth`（Google Identity Services：Credential Manager 取身分、`AuthorizationClient` 取 `drive.appdata` 授權）兩個介面，兩者都有 Fake 版本供測試。純邏輯（gzip/SHA-256 編碼、備份檔名、保留策略、續傳協定的位元組範圍計算、相對時間顯示）留在 `:core`，不依賴 Android SDK，JVM 測試就能跑、不必 Robolectric。`BackupManager` 協調「快照 → 上傳 → 保留 3 份 → 更新時間戳」，手動按鈕與 WorkManager 每日排程都呼叫它。`RestoreManager` 負責「下載 → 驗證 → 換檔 → 清 `cache.db` 與草稿 → 重啟 app」，還原後讓 app 用全新的 `AppContainer`／`Room` 實例開啟新檔，不嘗試在執行中置換既有的 `by lazy` 資料庫實例。

**Tech Stack:** Kotlin, Room 2.8（`BundledSQLiteDriver`）, OkHttp 5.5, kotlinx.serialization.json, WorkManager, Google Identity Services（`com.google.android.gms:play-services-auth` 的 `AuthorizationClient` ＋ `androidx.credentials` 的 Credential Manager), Robolectric（Compose UI 測試）, MockWebServer（Drive REST 測試）, JUnit4。

**Spec:** [docs/superpowers/specs/2026-09-10-videoshot-app-design.md](../specs/2026-09-10-videoshot-app-design.md) 第三節（模組邊界）、第四節（跨平台資料契約／設定值不進備份）、第九節（帳號頁版面）、第十節（備份與還原，本計畫的主要依據）、第十一節（縮圖回填，還原後觸發但不在本階段實作）、第十八節（appDataFolder 跨 client 的 ⏳ 開放項目）。

## Global Constraints

- `applicationId`／Kotlin 套件固定 `com.xenyaa.videoshot`，上線後不可改（CLAUDE.md）。
- `minSdk = 26`、`compileSdk`/`targetSdk = 37`（`app/build.gradle.kts`）——`java.time` 可直接用，不需要 core library desugaring。
- **模組邊界**：所有 DB 存取只走 repo；**Google Drive 只在 `backup` 裡用**，其他地方不得直接碰 Drive REST 或 Google Identity API（規格第三節）。
- **純邏輯放 `:core`，不依賴 Android SDK**；重運算（gzip、雜湊）放 `Dispatchers.IO`／`Dispatchers.Default`，不卡 UI 執行緒。
- **DB 裡不存檔案路徑**；縮圖以邏輯識別碼定位——本階段不動縮圖，但還原後留給階段 13 的回填掃描，本階段只需正確清空 `cache.db`。
- **無法重建的在 `library.db`（要備份），DB 外面的都能重建**：備份範圍只有 `library.db` 一個檔案；`cache.db`、`thumbs/`、草稿、設定值、Gemini 金鑰都不進備份（規格第四節、第十節）。
- Drive 權限只要求 **`drive.appdata`**（app 專用隱藏資料夾），不要求任何更廣的 scope。
- 保留 Drive 上**最近 3 份**備份，上傳成功後才刪舊的。
- 還原驗證順序固定：**SHA-256 → 解壓 → `PRAGMA integrity_check` → schema 版本**（`checkBackupSchema()` 已存在於 `app/src/main/java/com/xenyaa/videoshot/data/library/BackupSchemaCheck.kt`，本計畫直接沿用、不重寫）。
- commit message 用繁體中文，格式 `類型(範圍): 描述`，不加 AI 生成標記（CLAUDE.md）。
- 每個 Task 結束都要跑對應測試並確認通過才進下一個 Task；JVM 測試（`:core:test`、`:app:testDebugUnitTest`）本機就能跑，androidTest 只在有實機連接時才能真的執行（沒有裝置時只能靠編譯與 IDE 靜態檢查，要在任務清單裡老實標註「尚未上機」，比照 CLAUDE.md 既有的做法）。

---

## 前置條件（Task 0——使用者操作，Claude 無法代為執行）

**這是 T12.1。** 在 Task 7（GoogleAuth 真正實作）與任何需要真的連上 Google 帳號的驗收之前，scott 需要先在 Google Cloud Console 完成以下設定。Task 1～6、Task 8～15 的程式碼與測試（用 Fake）**不需要等這一步**，可以先做。

1. **建立或選用一個 Google Cloud 專案**，且**長期沿用**——`appDataFolder` 以這個專案為界（規格第十節、第十八節），日後 iOS 版或其他用戶端都要掛在同一個專案下才看得到同一份備份（見 Task 15 / T12.7）。
2. **啟用 Google Drive API**（Cloud Console → API 和服務 → 程式庫 → 搜尋 "Google Drive API" → 啟用）。
3. **設定 OAuth 同意畫面**：新增 scope `.../auth/drive.appdata`，**發佈到 Production**（不要停在 Testing——Testing 狀態的 refresh token 7 天就失效，自動備份會悄悄停掉，規格第十節已明確點出這個坑）。
4. **建立憑證 → OAuth 用戶端 ID → 應用程式類型「Android」**：
   - 套件名稱填 `com.xenyaa.videoshot`。
   - 簽章憑證指紋（SHA-1）：debug 與 release **都要登記**（R-6 風險）。取 debug keystore 的 SHA-1：
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     release 憑證要等正式簽名 keystore 建好後再補登記；同一個 Android OAuth 用戶端可以登記多個 SHA-1 指紋，不必為 debug/release 分別建立兩個用戶端。
5. **再建立第二個 OAuth 用戶端 ID，應用程式類型「網頁應用程式」**（例如命名 `videoshot-signin-audience`，不需要填「已授權的重新導向 URI」）。這個 client ID **只用來當 Credential Manager「使用 Google 登入」的 ID token 受眾**，本身不是密鑰，可以直接寫進程式碼／resource（Task 7 會用到）。
6. 把步驟 5 拿到的網頁用戶端 ID 交給實作者，寫入 `app/src/main/res/values/strings.xml` 的新字串資源 `google_signin_web_client_id`（Task 7 的 Step 1 會做這件事；沒有這個值前可以先填一個明顯的佔位字串，讓專案能編譯，但**帳號連結功能在真機上不會成功**，直到換成真的值）。

**驗收方式**：這一步沒有自動化驗收——確認方式是 Task 7 完成後，在裝了 debug APK 的實機上按【連結 Google 帳號】，真的能跳出 Google 帳號選擇畫面並成功取得 Drive 授權。

---

## File Structure

```
android/
  core/src/main/kotlin/com/xenyaa/videoshot/core/backup/
    BackupCodec.kt          # gzip + SHA-256 編碼／驗證解碼（純 JVM）
    BackupNaming.kt         # 備份檔名、保留策略、「上次備份：…」相對時間顯示
    ResumableUpload.kt      # 續傳協定用的純函式：切 chunk、解析 Range 標頭
  core/src/test/kotlin/com/xenyaa/videoshot/core/backup/
    BackupCodecTest.kt
    BackupNamingTest.kt
    ResumableUploadTest.kt

  app/src/main/java/com/xenyaa/videoshot/backup/
    BackupStore.kt          # 介面 + RemoteBackup/NewBackup
    FakeBackupStore.kt
    DriveBackupStore.kt      # Drive REST v3 真正實作
    GoogleAuth.kt            # 介面 + LinkedGoogleAccount
    FakeGoogleAuth.kt
    GisGoogleAuth.kt          # Credential Manager + AuthorizationClient 真正實作
    BackupSnapshotter.kt      # VACUUM INTO → BackupCodec
    BackupManager.kt          # 快照→上傳→保留→時間戳 的協調者
    RestoreManager.kt         # 下載→驗證→換檔→清空→重啟
    RestartApp.kt              # 還原成功後重啟整個 process 的小工具
    BackupWorker.kt            # WorkManager 每日排程

  app/src/test/java/com/xenyaa/videoshot/backup/
    FakeBackupStoreTest.kt
    FakeGoogleAuthTest.kt
    BackupManagerTest.kt
    BackupWorkerTest.kt

  app/src/androidTest/java/com/xenyaa/videoshot/backup/
    DriveBackupStoreTest.kt
    BackupSnapshotterTest.kt
    RestoreManagerTest.kt

  app/src/main/java/com/xenyaa/videoshot/data/settings/AppSettings.kt   # 修改：linkedAccount、restoreDecisionMade
  app/src/main/java/com/xenyaa/videoshot/ui/account/
    AccountState.kt          # 修改：連結狀態、上次備份
    AccountDeps.kt            # 修改：備份/連結相關方法
    AccountViewModel.kt        # 修改
    AccountScreen.kt           # 修改：hero 顯示連結帳號
    BackupScreen.kt            # 新增：帳號頁「備份」子畫面
    RestoreScreen.kt            # 新增：還原挑選清單 + 確認框
    RestoreViewModel.kt          # 新增
  app/src/main/java/com/xenyaa/videoshot/ui/onboarding/
    FirstRunChooserScreen.kt    # 新增：全新安裝的第一個畫面
  app/src/main/java/com/xenyaa/videoshot/ui/shell/
    NavState.kt                # 修改：Dest.RestoreFlow
    AppRoot.kt                  # 修改：串接首次開啟閘門、備份/還原畫面
    AppRootDeps.kt               # 修改：暴露 backupManager/restoreManager/googleAuth
  app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt   # 修改：組裝以上所有東西
  app/src/main/java/com/xenyaa/videoshot/VideoshotApp.kt        # 修改：掛 WorkManager 排程

  gradle/libs.versions.toml    # 修改：新增 WorkManager、play-services-auth、credentials 相關依賴
  app/build.gradle.kts          # 修改：加上述依賴
  app/src/main/res/values/strings.xml   # 新增：google_signin_web_client_id
```

---

## Task 1: `:core` 備份編碼——gzip ＋ SHA-256

**Files:**
- Create: `core/src/main/kotlin/com/xenyaa/videoshot/core/backup/BackupCodec.kt`
- Test: `core/src/test/kotlin/com/xenyaa/videoshot/core/backup/BackupCodecTest.kt`

**Interfaces:**
- Produces: `BackupCodec.gzipWithSha256(source: File, dest: File): String`（回傳 SHA-256 十六進位小寫字串）、`BackupCodec.verifySha256(gzFile: File, expectedHex: String): Boolean`、`BackupCodec.gunzip(source: File, dest: File)`。Task 6（快照）、Task 9（`BackupManager`）、Task 11（`RestoreManager`）都會呼叫這三個函式。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.core.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCodecTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun 壓縮再解壓拿回原始內容() {
        val source = tmp.newFile("library.db").apply { writeBytes("hello-videoshot".toByteArray()) }
        val gz = tmp.newFile("library.db.gz")
        BackupCodec.gzipWithSha256(source, gz)

        val restored = tmp.newFile("restored.db")
        BackupCodec.gunzip(gz, restored)

        assertEquals("hello-videoshot", restored.readText())
    }

    @Test
    fun 雜湊算在壓縮後的位元組上_同輸入永遠同雜湊() {
        val source = tmp.newFile("a.db").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val gz1 = tmp.newFile("a1.db.gz")
        val gz2 = tmp.newFile("a2.db.gz")
        val hash1 = BackupCodec.gzipWithSha256(source, gz1)
        val hash2 = BackupCodec.gzipWithSha256(source, gz2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 十六進位字串長度
    }

    @Test
    fun 雜湊比對通過() {
        val source = tmp.newFile("b.db").apply { writeBytes("abc".toByteArray()) }
        val gz = tmp.newFile("b.db.gz")
        val hash = BackupCodec.gzipWithSha256(source, gz)

        assertTrue(BackupCodec.verifySha256(gz, hash))
        assertTrue(BackupCodec.verifySha256(gz, hash.uppercase())) // 大小寫不敏感
    }

    @Test
    fun 雜湊不符會被抓出來() {
        val source = tmp.newFile("c.db").apply { writeBytes("original".toByteArray()) }
        val gz = tmp.newFile("c.db.gz")
        BackupCodec.gzipWithSha256(source, gz)

        assertFalse(BackupCodec.verifySha256(gz, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.BackupCodecTest"`
Expected: FAIL（`BackupCodec` 還不存在，編譯錯誤）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.core.backup

import java.io.DigestOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 備份檔案的編碼格式：`library.db` 的 `VACUUM INTO` 快照先 gzip 壓縮，
 * SHA-256 算在**壓縮後**的位元組上（規格第十節「VACUUM INTO 產生快照 → gzip → 算 SHA-256」；
 * 還原流程圖也是先驗 SHA-256 再解壓——雜湊要對應到真正上傳／下載的那份位元組）。
 */
object BackupCodec {

    /** 壓縮 [source] 寫到 [dest]，回傳壓縮後檔案的 SHA-256（小寫十六進位）。 */
    fun gzipWithSha256(source: File, dest: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        dest.outputStream().use { fileOut ->
            DigestOutputStream(fileOut, digest).use { digestOut ->
                GZIPOutputStream(digestOut).use { gz ->
                    source.inputStream().use { input -> input.copyTo(gz) }
                }
            }
        }
        return digest.digest().toHexString()
    }

    /** [gzFile] 的 SHA-256 是否等於 [expectedHex]（不分大小寫）。 */
    fun verifySha256(gzFile: File, expectedHex: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        gzFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHexString().equals(expectedHex, ignoreCase = true)
    }

    /** 解壓 [source] 寫到 [dest]。呼叫端要自己先用 [verifySha256] 驗過。 */
    fun gunzip(source: File, dest: File) {
        GZIPInputStream(source.inputStream()).use { gz ->
            dest.outputStream().use { out -> gz.copyTo(out) }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.BackupCodecTest"`
Expected: PASS（4 個測試）

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/xenyaa/videoshot/core/backup/BackupCodec.kt core/src/test/kotlin/com/xenyaa/videoshot/core/backup/BackupCodecTest.kt
git commit -m "feat(備份): 快照的 gzip 壓縮與 SHA-256 編碼／驗證"
```

---

## Task 2: `:core` 備份檔名、保留策略、相對時間顯示

**Files:**
- Create: `core/src/main/kotlin/com/xenyaa/videoshot/core/backup/BackupNaming.kt`
- Test: `core/src/test/kotlin/com/xenyaa/videoshot/core/backup/BackupNamingTest.kt`

**Interfaces:**
- Produces: `backupFileName(epochSec: Long, zone: ZoneId = ZoneId.systemDefault()): String`、`data class RemoteBackupSummary(val id: String, val createdAtEpochSec: Long)`、`backupsToDelete(all: List<RemoteBackupSummary>, keep: Int = 3): List<RemoteBackupSummary>`、`lastBackupLabel(lastBackupAtEpochSec: Long, nowEpochSec: Long): String`。
- Consumes: 無（純函式，不依賴其他 Task）。
- 下游：Task 9 的 `BackupManager`（檔名、保留策略）、Task 12 的 `BackupScreen`（相對時間顯示）、Task 4 的 `RemoteBackup` 會轉成 `RemoteBackupSummary` 餵給 `backupsToDelete`。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.core.backup

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupNamingTest {

    @Test
    fun 檔名格式符合規格() {
        // 2026-09-27 10:05:00 UTC
        val epochSec = 1790855100L
        assertEquals("library-20260930-1745.db.gz", backupFileName(epochSec, ZoneOffset.ofHours(8)))
    }

    @Test
    fun 保留最新_3_份_其餘要刪除() {
        val all = listOf(
            RemoteBackupSummary("a", 500),
            RemoteBackupSummary("b", 100),
            RemoteBackupSummary("c", 400),
            RemoteBackupSummary("d", 300),
            RemoteBackupSummary("e", 200),
        )
        val toDelete = backupsToDelete(all, keep = 3)
        assertEquals(listOf("d", "e"), toDelete.map { it.id }) // 保留 a(500) c(400) b... 等等見下方修正
    }

    @Test
    fun 不到保留門檻時全部留著() {
        val all = listOf(RemoteBackupSummary("a", 100), RemoteBackupSummary("b", 200))
        assertEquals(emptyList<RemoteBackupSummary>(), backupsToDelete(all, keep = 3))
    }

    @Test
    fun 尚未備份過顯示尚未備份() {
        assertEquals("尚未備份", lastBackupLabel(0L, 1_000_000L))
    }

    @Test
    fun 一分鐘內顯示剛剛() {
        assertEquals("剛剛", lastBackupLabel(1_000_000L, 1_000_030L))
    }

    @Test
    fun 超過一小時顯示小時前() {
        assertEquals("3 小時前", lastBackupLabel(1_000_000L, 1_000_000L + 3 * 3600 + 10))
    }
}
```

注意 `保留最新_3_份_其餘要刪除` 這個案例：`a=500,c=400,d=300,e=200,b=100` 由新到舊排序後留前 3 個（`a,c,d`），要刪的是 `e,b`——上面草稿的期望值寫錯了，寫測試時要修正成：

```kotlin
    @Test
    fun 保留最新_3_份_其餘要刪除() {
        val all = listOf(
            RemoteBackupSummary("a", 500),
            RemoteBackupSummary("b", 100),
            RemoteBackupSummary("c", 400),
            RemoteBackupSummary("d", 300),
            RemoteBackupSummary("e", 200),
        )
        val toDelete = backupsToDelete(all, keep = 3)
        assertEquals(listOf("e", "b"), toDelete.map { it.id })
    }
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.BackupNamingTest"`
Expected: FAIL（`BackupNaming.kt` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.core.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/**
 * 上傳到 Drive 的檔名（規格第十節 `library-{yyyyMMdd-HHmm}.db.gz`）。
 * 用裝置所在時區——單人單機，不必存 UTC 再換算。
 */
fun backupFileName(epochSec: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val stamp = Instant.ofEpochSecond(epochSec).atZone(zone).format(FILE_NAME_FORMAT)
    return "library-$stamp.db.gz"
}

/** Drive 上的一份備份，只留下決定保留策略要用的欄位。 */
data class RemoteBackupSummary(val id: String, val createdAtEpochSec: Long)

/**
 * 保留最近 [keep] 份，其餘要刪除（規格第十節「上傳成功後，才刪除超過 3 份的舊備份」）。
 * 純函式：呼叫端負責「先確認新檔上傳成功才呼叫這個」與「真的送出刪除」。
 */
fun backupsToDelete(all: List<RemoteBackupSummary>, keep: Int = 3): List<RemoteBackupSummary> =
    all.sortedByDescending { it.createdAtEpochSec }.drop(keep)

/** 帳號頁「上次備份：…」的顯示文字（手冊 §一：完成後顯示「上次備份：剛剛」）。 */
fun lastBackupLabel(lastBackupAtEpochSec: Long, nowEpochSec: Long): String {
    if (lastBackupAtEpochSec <= 0L) return "尚未備份"
    val deltaSec = (nowEpochSec - lastBackupAtEpochSec).coerceAtLeast(0)
    return when {
        deltaSec < 60 -> "剛剛"
        deltaSec < 3600 -> "${deltaSec / 60} 分鐘前"
        deltaSec < 86400 -> "${deltaSec / 3600} 小時前"
        else -> "${deltaSec / 86400} 天前"
    }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.BackupNamingTest"`
Expected: PASS（6 個測試）

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/xenyaa/videoshot/core/backup/BackupNaming.kt core/src/test/kotlin/com/xenyaa/videoshot/core/backup/BackupNamingTest.kt
git commit -m "feat(備份): 備份檔名、保留 3 份策略、上次備份相對時間顯示"
```

---

## Task 3: `:core` 續傳上傳協定的純函式

**Files:**
- Create: `core/src/main/kotlin/com/xenyaa/videoshot/core/backup/ResumableUpload.kt`
- Test: `core/src/test/kotlin/com/xenyaa/videoshot/core/backup/ResumableUploadTest.kt`

**Interfaces:**
- Produces: `data class UploadChunk(val start: Long, val endInclusive: Long, val total: Long)`（含 `length`、`contentRangeHeader()`）、`nextChunk(fromByte: Long, total: Long, chunkSize: Long): UploadChunk`、`parseReceivedBytes(rangeHeader: String?): Long`。
- 下游：Task 5 的 `DriveBackupStore` 用這兩個函式把整份檔案切成塊、並在斷線重試時知道該從哪個位元組續傳。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumableUploadTest {

    @Test
    fun 切出第一塊() {
        val chunk = nextChunk(fromByte = 0, total = 1000, chunkSize = 256)
        assertEquals(0L, chunk.start)
        assertEquals(255L, chunk.endInclusive)
        assertEquals(256L, chunk.length)
        assertEquals("bytes 0-255/1000", chunk.contentRangeHeader())
    }

    @Test
    fun 最後一塊不超過總長度() {
        val chunk = nextChunk(fromByte = 900, total = 1000, chunkSize = 256)
        assertEquals(900L, chunk.start)
        assertEquals(999L, chunk.endInclusive)
        assertEquals(100L, chunk.length)
    }

    @Test
    fun 檔案小於一個chunk時一次就送完() {
        val chunk = nextChunk(fromByte = 0, total = 100, chunkSize = 256)
        assertEquals(0L, chunk.start)
        assertEquals(99L, chunk.endInclusive)
    }

    @Test
    fun 沒有Range標頭代表完全沒收到() {
        assertEquals(0L, parseReceivedBytes(null))
    }

    @Test
    fun 解析已收到的位元組數() {
        assertEquals(256L, parseReceivedBytes("bytes=0-255"))
        assertEquals(1000L, parseReceivedBytes("bytes=0-999"))
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.ResumableUploadTest"`
Expected: FAIL（`ResumableUpload.kt` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.core.backup

/** 這次要送出的區塊：位元組範圍 `[start, endInclusive]`，`total` 是整份檔案大小。 */
data class UploadChunk(val start: Long, val endInclusive: Long, val total: Long) {
    val length: Long get() = endInclusive - start + 1

    /** Drive 可續傳上傳一個 chunk 要帶的 `Content-Range` 標頭值。 */
    fun contentRangeHeader(): String = "bytes $start-$endInclusive/$total"
}

/** 切出下一塊：從 [fromByte] 開始，最多 [chunkSize] 個位元組，不超過 [total]。 */
fun nextChunk(fromByte: Long, total: Long, chunkSize: Long): UploadChunk {
    require(fromByte < total) { "fromByte ($fromByte) 必須小於 total ($total)" }
    val endInclusive = minOf(fromByte + chunkSize - 1, total - 1)
    return UploadChunk(fromByte, endInclusive, total)
}

/**
 * 從 Drive 回應的 308（續傳查詢或部分上傳成功）解析已收到的位元組數
 * （規格第十節「可續傳上傳」；Google Drive 文件：`Range` 標頭格式固定是 `bytes=0-<lastByteReceived>`）。
 * Drive 完全沒收到任何位元組時**不會回 `Range` 標頭**——這種情況回 0（從頭開始）。
 */
fun parseReceivedBytes(rangeHeader: String?): Long {
    if (rangeHeader == null) return 0L
    val match = Regex("""bytes=0-(\d+)""").find(rangeHeader) ?: return 0L
    return match.groupValues[1].toLong() + 1
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd android && ./gradlew :core:test --tests "com.xenyaa.videoshot.core.backup.ResumableUploadTest"`
Expected: PASS（5 個測試）

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/xenyaa/videoshot/core/backup/ResumableUpload.kt core/src/test/kotlin/com/xenyaa/videoshot/core/backup/ResumableUploadTest.kt
git commit -m "feat(備份): 續傳上傳協定的 chunk 切割與 Range 標頭解析"
```

---

## Task 4: `BackupStore` 介面與 `FakeBackupStore`

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/BackupStore.kt`
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/FakeBackupStore.kt`
- Test: `app/src/test/java/com/xenyaa/videoshot/backup/FakeBackupStoreTest.kt`

**Interfaces:**
- Consumes: 無 Android 相依，`java.io.File` 即可。
- Produces: `interface BackupStore { list(); upload(); download(); delete() }`、`data class RemoteBackup(id, name, createdAtEpochSec, sizeBytes, schemaVersion, shotCount, deviceName, sha256)`、`data class NewBackup(file, sha256, schemaVersion, shotCount, deviceName, createdAtEpochSec)`、`class FakeBackupStore : BackupStore`。Task 5（`DriveBackupStore`）、Task 9（`BackupManager`）、Task 11（`RestoreManager`）都吃這個介面；`FakeBackupStore` 供它們的單元測試與尚未連上真帳號前的手動測試用。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.backup

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeBackupStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun 上傳後可以列出來_按時間新到舊() = runTest {
        val store = FakeBackupStore()
        val older = newBackup(tmp, "a.db.gz", createdAtEpochSec = 100)
        val newer = newBackup(tmp, "b.db.gz", createdAtEpochSec = 200)

        store.upload(older)
        store.upload(newer)

        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(200L, list[0].createdAtEpochSec)
        assertEquals(100L, list[1].createdAtEpochSec)
    }

    @Test
    fun 下載拿回上傳的原始內容() = runTest {
        val store = FakeBackupStore()
        val source = tmp.newFile("src.db.gz").apply { writeBytes("payload".toByteArray()) }
        val remote = store.upload(NewBackup(source, "sha", 1, 10, "device", 100))

        val dest = tmp.newFile("dest.db.gz")
        store.download(remote.id, dest)

        assertEquals("payload", dest.readText())
    }

    @Test
    fun 刪除後list看不到它() = runTest {
        val store = FakeBackupStore()
        val remote = store.upload(newBackup(tmp, "c.db.gz", 100))

        store.delete(remote.id)

        assertTrue(store.list().isEmpty())
    }

    @Test
    fun failNextUpload會讓下一次上傳丟例外_之後恢復正常() = runTest {
        val store = FakeBackupStore()
        store.failNextUpload = IllegalStateException("模擬網路錯誤")

        val threw = runCatching { store.upload(newBackup(tmp, "d.db.gz", 100)) }.isFailure
        assertTrue(threw)

        // 恢復正常——第二次呼叫不該再丟
        store.upload(newBackup(tmp, "e.db.gz", 200))
        assertEquals(1, store.list().size)
    }

    private fun newBackup(tmp: TemporaryFolder, name: String, createdAtEpochSec: Long): NewBackup {
        val file = tmp.newFile(name).apply { writeBytes("x".toByteArray()) }
        return NewBackup(file, "sha-$name", 1, 0, "device", createdAtEpochSec)
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.FakeBackupStoreTest"`
Expected: FAIL（`BackupStore`／`FakeBackupStore` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import java.io.File

/** Drive appDataFolder 裡的一份備份（規格第十節）。`appProperties` 在 Drive REST 裡本來就都是字串。 */
data class RemoteBackup(
    val id: String,
    val name: String,
    val createdAtEpochSec: Long,
    val sizeBytes: Long,
    val schemaVersion: Int,
    val shotCount: Int,
    val deviceName: String,
    val sha256: String,
)

/** 要上傳的一份備份：本機已經壓縮好、算好雜湊的檔案，加上要寫進 `appProperties` 的中繼資料。 */
data class NewBackup(
    val file: File,
    val sha256: String,
    val schemaVersion: Int,
    val shotCount: Int,
    val deviceName: String,
    val createdAtEpochSec: Long,
)

/**
 * app 專用隱藏資料夾（appDataFolder）的存取介面（規格第三節模組邊界：
 * **Google Drive 只在 `backup` 裡用**，其他地方不得直接碰 Drive REST）。
 * 真正的 Drive 實作見 [DriveBackupStore]；測試與尚未連上真帳號時用 [FakeBackupStore]。
 */
interface BackupStore {
    /** 依建立時間新到舊排序。 */
    suspend fun list(): List<RemoteBackup>

    suspend fun upload(
        backup: NewBackup,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): RemoteBackup

    suspend fun download(
        id: String,
        dest: File,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    )

    suspend fun delete(id: String)
}
```

```kotlin
package com.xenyaa.videoshot.backup

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** [BackupStore] 的記憶體版本（比照 `FakePlayer`／`FakeCapture` 的角色）。 */
class FakeBackupStore : BackupStore {
    private val nextId = AtomicLong(1)
    private val files = mutableMapOf<String, ByteArray>()
    private val backups = mutableListOf<RemoteBackup>()

    var failNextUpload: Exception? = null
    var failNextDownload: Exception? = null

    override suspend fun list(): List<RemoteBackup> = backups.sortedByDescending { it.createdAtEpochSec }

    override suspend fun upload(backup: NewBackup, onProgress: (Long, Long) -> Unit): RemoteBackup {
        failNextUpload?.let { failNextUpload = null; throw it }
        val bytes = backup.file.readBytes()
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        val id = "fake-${nextId.getAndIncrement()}"
        files[id] = bytes
        val remote = RemoteBackup(
            id = id,
            name = backup.file.name,
            createdAtEpochSec = backup.createdAtEpochSec,
            sizeBytes = bytes.size.toLong(),
            schemaVersion = backup.schemaVersion,
            shotCount = backup.shotCount,
            deviceName = backup.deviceName,
            sha256 = backup.sha256,
        )
        backups += remote
        return remote
    }

    override suspend fun download(id: String, dest: File, onProgress: (Long, Long) -> Unit) {
        failNextDownload?.let { failNextDownload = null; throw it }
        val bytes = files[id] ?: error("no such backup: $id")
        dest.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }

    override suspend fun delete(id: String) {
        files.remove(id)
        backups.removeAll { it.id == id }
    }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.FakeBackupStoreTest"`
Expected: PASS（4 個測試）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/BackupStore.kt app/src/main/java/com/xenyaa/videoshot/backup/FakeBackupStore.kt app/src/test/java/com/xenyaa/videoshot/backup/FakeBackupStoreTest.kt
git commit -m "feat(備份): BackupStore 介面與記憶體版 FakeBackupStore"
```

---

## Task 5: `DriveBackupStore`——Drive REST v3 真正實作

不需要新依賴：OkHttp 與 `kotlinx-serialization-json` 都已經是 `:app` 的既有依賴。

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/DriveBackupStore.kt`
- Test: `app/src/androidTest/java/com/xenyaa/videoshot/backup/DriveBackupStoreTest.kt`（比照 `OkHttpYoutubeTest.kt`／`OkHttpGeminiClientTest.kt` 的慣例：真正打 HTTP 的 client 放 androidTest，用 `mockwebserver3`）

**Interfaces:**
- Consumes: `BackupStore`（Task 4）、`nextChunk`／`parseReceivedBytes`（Task 3）。
- Produces: `class DriveBackupStore(http: OkHttpClient, accessToken: suspend () -> String, io: CoroutineDispatcher, baseUrl: String = "https://www.googleapis.com", chunkSize: Long = 8L * 1024 * 1024) : BackupStore`。Task 9 的 `BackupManager`、`AppContainer` 會用這個建構子。`accessToken` 是每次呼叫前取得目前有效權杖的函式——來自 Task 7 的 `GoogleAuth.accessToken()`，這裡故意不快取，權杖過期與重新取得的責任交給呼叫端。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class DriveBackupStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: DriveBackupStore

    private fun newStore(chunkSize: Long = 8L * 1024 * 1024) = DriveBackupStore(
        http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build(),
        accessToken = { "fake-token" },
        io = Dispatchers.IO,
        baseUrl = server.url("/").toString().trimEnd('/'),
        chunkSize = chunkSize,
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        store = newStore()
    }

    @After fun tearDown() { server.close() }

    @Test
    fun list解析files陣列() = runTest {
        server.enqueue(MockResponse(code = 200, body = """
            {"files":[
                {"id":"f1","name":"library-20260101-0000.db.gz","createdTime":"2026-01-01T00:00:00.000Z","size":"1024",
                 "appProperties":{"schemaVersion":"1","shotCount":"42","deviceName":"Pixel","sha256":"abc"}}
            ]}
        """.trimIndent()))

        val result = store.list()

        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals(1024L, result[0].sizeBytes)
        assertEquals(42, result[0].shotCount)
        assertEquals("abc", result[0].sha256)
    }

    @Test
    fun list帶上appDataFolder與授權標頭() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"files":[]}"""))
        store.list()
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.target?.contains("spaces=appDataFolder") == true)
        assertEquals("Bearer fake-token", recorded.headers["Authorization"])
    }

    @Test
    fun 上傳一次PUT就完成的小檔案() = runTest {
        val source = tmp.newFile("a.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = headersOf("Location", server.url("/upload-session-1").toString())))
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-1"}"""))

        val result = store.upload(NewBackup(source, "sha-a", 1, 5, "Pixel", 100))

        assertEquals("drive-1", result.id)
        assertEquals(source.name, result.name)
        assertEquals(100L, result.sizeBytes)
    }

    @Test
    fun 上傳分成兩個chunk_第一個回308第二個完成() = runTest {
        store = newStore(chunkSize = 50)
        val source = tmp.newFile("b.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = headersOf("Location", server.url("/upload-session-2").toString())))
        server.enqueue(MockResponse(code = 308, headers = headersOf("Range", "bytes=0-49")))
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-2"}"""))

        val result = store.upload(NewBackup(source, "sha-b", 1, 5, "Pixel", 100))

        assertEquals("drive-2", result.id)
        // 三個請求：初始化 + 兩個 chunk
        server.takeRequest(5, TimeUnit.SECONDS)
        val firstChunk = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 0-49/100", firstChunk.headers["Content-Range"])
        val secondChunk = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 50-99/100", secondChunk.headers["Content-Range"])
    }

    @Test
    fun 下載寫入目的檔案() = runTest {
        server.enqueue(MockResponse(code = 200, body = "gz-bytes"))
        val dest = tmp.newFile("dest.db.gz")

        store.download("f1", dest)

        assertEquals("gz-bytes", dest.readText())
    }

    @Test
    fun 刪除送出DELETE() = runTest {
        server.enqueue(MockResponse(code = 204))
        store.delete("f1")
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("DELETE", recorded.method)
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `am instrument` 需要實機／模擬器；沒有裝置時先確認能編譯：`cd android && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: 編譯失敗（`DriveBackupStore` 還不存在）——真正跑測試要等實機連上，比照 CLAUDE.md 既有的 androidTest 慣例先老實記下這個限制

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import com.xenyaa.videoshot.core.backup.nextChunk
import com.xenyaa.videoshot.core.backup.parseReceivedBytes
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024
private const val MAX_RETRIES = 5

/**
 * Drive REST v3 的真正實作，只碰 appDataFolder（規格第十節「只要求 `drive.appdata`」）。
 * 不用 Drive SDK——跟 `OkHttpYoutube`／`OkHttpGeminiClient` 一樣直接打 REST，符合技術選型表
 * 「HTTP: OkHttp」，也少一層 SDK 相依。
 *
 * @param accessToken 每次呼叫前取得目前有效的存取權杖；這裡不快取，過期與重新取得交給呼叫端
 *        （[GoogleAuth.accessToken]）。
 * @param baseUrl 測試時指向 MockWebServer；正式環境用預設值。
 * @param chunkSize 續傳上傳每一塊的大小；測試用小值逼出多 chunk 的路徑。
 */
class DriveBackupStore(
    private val http: OkHttpClient,
    private val accessToken: suspend () -> String,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://www.googleapis.com",
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : BackupStore {

    override suspend fun list(): List<RemoteBackup> = withContext(io) {
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id,name,createdTime,size,appProperties)")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "10")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${accessToken()}").build()
        execute(request).use { response ->
            check(response.isSuccessful) { "列出備份失敗: ${response.code}" }
            val body = Json.parseToJsonElement(response.body!!.string()).jsonObject
            body["files"]?.jsonArray.orEmpty().map { it.jsonObject.toRemoteBackup() }
        }
    }

    override suspend fun upload(
        backup: NewBackup,
        onProgress: (Long, Long) -> Unit,
    ): RemoteBackup = withContext(io) {
        val total = backup.file.length()
        val metadata = buildJsonObject {
            put("name", backup.file.name)
            putJsonArray("parents") { add(kotlinx.serialization.json.JsonPrimitive("appDataFolder")) }
            putJsonObject("appProperties") {
                put("schemaVersion", backup.schemaVersion.toString())
                put("shotCount", backup.shotCount.toString())
                put("deviceName", backup.deviceName)
                put("sha256", backup.sha256)
            }
        }
        val initRequest = Request.Builder()
            .url("$baseUrl/upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer ${accessToken()}")
            .header("X-Upload-Content-Type", "application/gzip")
            .header("X-Upload-Content-Length", total.toString())
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val sessionUri = execute(initRequest).use { response ->
            check(response.isSuccessful) { "初始化續傳上傳失敗: ${response.code}" }
            response.header("Location") ?: error("Drive 沒有回傳續傳網址")
        }

        var offset = 0L
        var attempt = 0
        var resultJson: JsonObject? = null
        while (resultJson == null) {
            try {
                val chunk = nextChunk(offset, total, chunkSize)
                val bytes = backup.file.readRange(chunk.start, chunk.length)
                val putRequest = Request.Builder()
                    .url(sessionUri)
                    .header("Content-Range", chunk.contentRangeHeader())
                    .put(bytes.toRequestBody("application/gzip".toMediaType()))
                    .build()
                execute(putRequest).use { response ->
                    when {
                        response.code == 308 -> {
                            offset = parseReceivedBytes(response.header("Range"))
                            onProgress(offset, total)
                        }
                        response.isSuccessful -> {
                            resultJson = Json.parseToJsonElement(response.body!!.string()).jsonObject
                            onProgress(total, total)
                        }
                        else -> error("上傳失敗: ${response.code}")
                    }
                }
            } catch (e: java.io.IOException) {
                attempt++
                if (attempt > MAX_RETRIES) throw e
                offset = queryResumeOffset(sessionUri, total)
            }
        }
        checkNotNull(resultJson).toRemoteBackupFromUploadResult(backup)
    }

    private suspend fun queryResumeOffset(sessionUri: String, total: Long): Long {
        val request = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", "bytes */$total")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        return execute(request).use { response ->
            if (response.isSuccessful) total else parseReceivedBytes(response.header("Range"))
        }
    }

    override suspend fun download(
        id: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(io) {
        val request = Request.Builder()
            .url("$baseUrl/drive/v3/files/$id?alt=media")
            .header("Authorization", "Bearer ${accessToken()}")
            .build()
        execute(request).use { response ->
            check(response.isSuccessful) { "下載失敗: ${response.code}" }
            val body = checkNotNull(response.body)
            val total = body.contentLength()
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var received = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(io) {
        val request = Request.Builder()
            .url("$baseUrl/drive/v3/files/$id")
            .header("Authorization", "Bearer ${accessToken()}")
            .delete()
            .build()
        execute(request).use { response ->
            check(response.isSuccessful || response.code == 404) { "刪除失敗: ${response.code}" }
        }
    }

    private fun execute(request: Request): Response = http.newCall(request).execute()

    private fun JsonObject.toRemoteBackup(): RemoteBackup {
        val props = this["appProperties"]?.jsonObject
        return RemoteBackup(
            id = this["id"]!!.jsonPrimitive.content,
            name = this["name"]!!.jsonPrimitive.content,
            createdAtEpochSec = Instant.parse(this["createdTime"]!!.jsonPrimitive.content).epochSecond,
            sizeBytes = this["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            schemaVersion = props?.get("schemaVersion")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            shotCount = props?.get("shotCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            deviceName = props?.get("deviceName")?.jsonPrimitive?.content ?: "",
            sha256 = props?.get("sha256")?.jsonPrimitive?.content ?: "",
        )
    }

    /** 上傳完成後的 Drive 回應通常只含少數欄位（除非額外要求 `fields`）——直接用本機已知的中繼資料組回傳值，只取回應裡的 `id`。 */
    private fun JsonObject.toRemoteBackupFromUploadResult(source: NewBackup): RemoteBackup = RemoteBackup(
        id = this["id"]!!.jsonPrimitive.content,
        name = source.file.name,
        createdAtEpochSec = source.createdAtEpochSec,
        sizeBytes = source.file.length(),
        schemaVersion = source.schemaVersion,
        shotCount = source.shotCount,
        deviceName = source.deviceName,
        sha256 = source.sha256,
    )
}

private fun File.readRange(start: Long, length: Long): ByteArray =
    RandomAccessFile(this, "r").use { raf ->
        raf.seek(start)
        val buffer = ByteArray(length.toInt())
        raf.readFully(buffer)
        buffer
    }
```

- [ ] **Step 4: 執行確認通過（需要實機）**

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.xenyaa.videoshot.backup.DriveBackupStoreTest \
  com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (7 tests)`。**沒有裝置連接時**：先跑 `./gradlew :app:assembleDebugAndroidTest` 確認編譯通過，並在計畫進度裡老實記下「只編譯驗證過，未上機」（CLAUDE.md 既有慣例）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/DriveBackupStore.kt app/src/androidTest/java/com/xenyaa/videoshot/backup/DriveBackupStoreTest.kt
git commit -m "feat(備份): DriveBackupStore——appDataFolder 的可續傳上傳、下載、刪除"
```

---

## Task 6: `BackupSnapshotter`——`VACUUM INTO` 產生一致快照

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/BackupSnapshotter.kt`
- Test: `app/src/androidTest/java/com/xenyaa/videoshot/backup/BackupSnapshotterTest.kt`（需要真的 SQLite 檔案，比照 `MigrationTest.kt`／`TestDb.kt` 放 androidTest）

**Interfaces:**
- Consumes: `LibraryDatabase`（既有，`app/src/main/java/com/xenyaa/videoshot/data/library/LibraryDatabase.kt`）。
- Produces: `class BackupSnapshotter(libraryDb: LibraryDatabase, io: CoroutineDispatcher) { suspend fun snapshotTo(dest: File) }`。Task 9 的 `BackupManager` 用它產生要壓縮上傳的原始 `.db` 檔。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.inMemoryLibraryDb
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSnapshotterTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: LibraryDatabase

    @Before fun setUp() { db = inMemoryLibraryDb() }
    @After fun tearDown() { db.close() }

    private fun pick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-09-27", place = null, description = null, webp = null,
    )

    @Test
    fun 快照檔可以被獨立開啟_內容跟原DB一致() = runTest {
        val repo = RoomLibraryRepo(db, Dispatchers.IO)
        repo.commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0), pick(1), pick(2)),
        )

        val dest = File(tmp.root, "snapshot.db")
        BackupSnapshotter(db, Dispatchers.IO).snapshotTo(dest)

        assertTrue(dest.exists())
        val driver = BundledSQLiteDriver()
        val connection = driver.open(dest.absolutePath)
        try {
            val integrityOk = connection.prepare("PRAGMA integrity_check").use { stmt ->
                stmt.step(); stmt.getText(0)
            }
            assertEquals("ok", integrityOk)
            val shotCount = connection.prepare("SELECT COUNT(*) FROM shot").use { stmt ->
                stmt.step(); stmt.getLong(0)
            }
            assertEquals(3L, shotCount)
        } finally {
            connection.close()
        }
    }

    @Test
    fun 目的檔已存在時會被覆蓋() = runTest {
        val dest = File(tmp.root, "snapshot.db").apply { writeText("stale") }
        BackupSnapshotter(db, Dispatchers.IO).snapshotTo(dest)
        assertTrue(dest.length() > 5L) // 真的被换成一個 SQLite 檔案，不是留著舊的 5 個位元組
    }
}
```

- [ ] **Step 2: 執行確認失敗（編譯，等實機時真跑）**

Run: `cd android && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: 編譯失敗（`BackupSnapshotter` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import androidx.room.useWriterConnection
import androidx.sqlite.execSQL
import com.xenyaa.videoshot.data.library.LibraryDatabase
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 對 `library.db` 做一次一致的快照（規格第十節：「`VACUUM INTO` 產生一致的快照——
 * WAL 模式下不可直接複製 DB 檔」）。快照本身就是一個完整、獨立、可直接開啟的 SQLite 檔，
 * 連 `user_version`（schema 版本）都會一起複製過去，還原流程要靠它判斷相容性。
 */
class BackupSnapshotter(
    private val libraryDb: LibraryDatabase,
    private val io: CoroutineDispatcher,
) {
    /** [dest] 事先不能存在——`VACUUM INTO` 遇到已存在的目的檔會直接失敗，所以先刪一次。 */
    suspend fun snapshotTo(dest: File) = withContext(io) {
        dest.delete()
        val escapedPath = dest.absolutePath.replace("'", "''")
        libraryDb.useWriterConnection { connection ->
            connection.execSQL("VACUUM INTO '$escapedPath'")
        }
    }
}
```

- [ ] **Step 4: 執行確認通過（需要實機）**

```bash
adb shell am instrument -w -e class com.xenyaa.videoshot.backup.BackupSnapshotterTest \
  com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (2 tests)`；沒裝置時記下「只編譯驗證過」。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/BackupSnapshotter.kt app/src/androidTest/java/com/xenyaa/videoshot/backup/BackupSnapshotterTest.kt
git commit -m "feat(備份): BackupSnapshotter——VACUUM INTO 產生一致快照"
```

---

## Task 7: `GoogleAuth`——Google 帳號連結與 `drive.appdata` 授權

這是本階段唯一需要新增第三方依賴、也唯一在真機上需要 Task 0 的真實憑證才能完整走通的一塊。
介面與 Fake 不需要等 Task 0；真正實作（`GisGoogleAuth`）能編譯，但**連結會失敗**直到 Task 0 的
Android／網頁 OAuth client 都設好為止——這件事要在 PR 說明與 CLAUDE.md 進度段落誠實記下來。

**背景**：`AuthorizationClient.authorize()` 若使用者是第一次同意（或先前同意已失效），回應會帶
`hasResolution()==true`，要由呼叫端跳出 `PendingIntent`；這在 Compose 裡必須靠
`rememberLauncherForActivityResult` 完成，不能整個包在一個 suspend 函式裡回傳最終結果——
所以 [GoogleAuth] 把連結拆成 `beginLink`／`finishLink` 兩步，Task 12 會示範怎麼在畫面上接這兩步。

**Files:**
- Modify: `gradle/libs.versions.toml`（新增 `play-services-auth`／`androidx-credentials`／`googleid`）
- Modify: `app/build.gradle.kts`（掛上面三個依賴）
- Modify: `app/src/main/res/values/strings.xml`（新增 `google_signin_web_client_id`，見 Task 0 步驟 6）
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/GoogleAuth.kt`
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/FakeGoogleAuth.kt`
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/GisGoogleAuth.kt`
- Test: `app/src/test/java/com/xenyaa/videoshot/backup/FakeGoogleAuthTest.kt`

**Interfaces:**
- Produces: `data class LinkedGoogleAccount(displayName, email)`、`sealed interface LinkOutcome { Linked(account); NeedsConsent(intentSender) }`、
  `interface GoogleAuth { beginLink(activity): LinkOutcome; finishLink(data: Intent): LinkedGoogleAccount; accessToken(): String; unlink() }`、
  `class FakeGoogleAuth`、`class GisGoogleAuth(context, webClientId, io)`。
- Task 9（`BackupManager`）、Task 11（`RestoreManager`）只用 `accessToken()`；Task 12（帳號頁 hero／BackupScreen）用全部四個方法。

- [ ] **Step 1: 加依賴（`gradle/libs.versions.toml`）**

在 `[versions]` 區塊加：

```toml
playServicesAuth = "21.3.0"
androidxCredentials = "1.5.0"
googleid = "1.1.1"
```

在 `[libraries]` 區塊加：

```toml
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "androidxCredentials" }
androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "androidxCredentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

`app/build.gradle.kts` 的 `dependencies {}` 加：

```kotlin
implementation(libs.play.services.auth)
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.googleid)
```

- [ ] **Step 2: 執行確認能同步（版本若解析不到要調整）**

Run: `cd android && ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i "play-services-auth\|credentials\|googleid"`
Expected: 三個函式庫都被解析到；若某個版本號在 Maven Central／google() 找不到（4xx），改成該群組目前可查到的最新穩定版重試，這是唯一允許偏離上面版本號的情況。

- [ ] **Step 3: 加 string resource（`app/src/main/res/values/strings.xml`）**

```xml
<string name="google_signin_web_client_id">REPLACE_WITH_WEB_CLIENT_ID_FROM_TASK_0</string>
```

這個值等 Task 0 步驟 5、6 完成、scott 把「網頁應用程式」OAuth client id 交出來後才換成真的值——
在那之前專案仍然能編譯，只是帳號連結在真機上會失敗（顯示的錯誤來自 Credential Manager 認不得這個 client id）。

- [ ] **Step 4: 寫失敗的測試（先驗證 Fake 的行為，不牽動真正的 Google API）**

```kotlin
package com.xenyaa.videoshot.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakeGoogleAuthTest {

    @Test
    fun 預設直接連結成功() = runTest {
        val auth = FakeGoogleAuth()
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        val outcome = auth.beginLink(activity)

        assertTrue(outcome is LinkOutcome.Linked)
        assertEquals("test@example.com", (outcome as LinkOutcome.Linked).account.email)
    }

    @Test
    fun 需要同意時回NeedsConsent_finishLink後才算連結完成() = runTest {
        val auth = FakeGoogleAuth()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE,
        )
        auth.nextLinkOutcome = LinkOutcome.NeedsConsent(pendingIntent.intentSender)

        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val outcome = auth.beginLink(activity)
        assertTrue(outcome is LinkOutcome.NeedsConsent)
        assertNull(auth.linked) // 還沒 finishLink，不算連結完成

        val account = auth.finishLink(Intent())
        assertEquals("test@example.com", account.email)
    }

    @Test
    fun accessToken未連結時丟例外() = runTest {
        val auth = FakeGoogleAuth()
        val threw = runCatching { auth.accessToken() }.isFailure
        assertTrue(threw)
    }

    @Test
    fun unlink後accessToken再丟例外() = runTest {
        val auth = FakeGoogleAuth(initiallyLinked = LinkedGoogleAccount("阿明", "ming@example.com"))
        auth.accessToken() // 連結中，不丟

        auth.unlink()

        assertTrue(runCatching { auth.accessToken() }.isFailure)
    }
}
```

（`Robolectric.buildActivity` 需要 `import org.robolectric.Robolectric`；上面示範省略了這行 import，實作時記得補上。）

- [ ] **Step 5: 執行確認失敗**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.FakeGoogleAuthTest"`
Expected: FAIL（`GoogleAuth`／`FakeGoogleAuth` 還不存在）

- [ ] **Step 6: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import android.app.Activity
import android.content.Intent
import android.content.IntentSender

/** 連結完成後要顯示的東西（規格第九節 hero：「名稱、Email、頭像字母」，這裡只需要前兩者）。 */
data class LinkedGoogleAccount(val displayName: String, val email: String)

/**
 * `beginLink` 的結果：[Linked] 代表授權早就同意過，靜默完成；[NeedsConsent] 代表要由呼叫端
 * 跳出系統畫面（`ActivityResultContracts.StartIntentSenderForResult`），使用者同意後把拿到的
 * `Intent` 餵回 [GoogleAuth.finishLink]。
 */
sealed interface LinkOutcome {
    data class Linked(val account: LinkedGoogleAccount) : LinkOutcome
    data class NeedsConsent(val intentSender: IntentSender) : LinkOutcome
}

/**
 * Google 帳號連結：身分（顯示名稱／Email）＋ `drive.appdata` 授權（規格第十節）。
 * 真正實作見 [GisGoogleAuth]；測試與 Task 0 完成前的開發用 [FakeGoogleAuth]。
 * **模組邊界**：這是唯一允許碰 Google Identity／Drive 授權 API 的地方（規格第三節）。
 */
interface GoogleAuth {
    /** 前景才能呼叫——會跳出系統的帳號選擇／同意畫面。 */
    suspend fun beginLink(activity: Activity): LinkOutcome

    /** 使用者從 [LinkOutcome.NeedsConsent] 的系統畫面回來後呼叫，`data` 是 activity result 帶回的 Intent。 */
    suspend fun finishLink(data: Intent): LinkedGoogleAccount

    /** 背景可呼叫：拿目前授權下可用的存取權杖。未連結、或授權已失效需要重新同意時丟例外。 */
    suspend fun accessToken(): String

    /** 撤銷授權（規格「中斷連結」，破壞性樣式按鈕）。 */
    suspend fun unlink()
}
```

```kotlin
package com.xenyaa.videoshot.backup

import android.app.Activity
import android.content.Intent

/** [GoogleAuth] 的假版本——測試，以及 Task 0（Google Cloud 設定）完成前先讓其他功能能開發。 */
class FakeGoogleAuth(initiallyLinked: LinkedGoogleAccount? = null) : GoogleAuth {

    var linked: LinkedGoogleAccount? = initiallyLinked

    /** null＝下一次 `beginLink` 直接回傳一個預設帳號的 [LinkOutcome.Linked]。 */
    var nextLinkOutcome: LinkOutcome? = null
    var failNextBeginLink: Exception? = null
    var failNextAccessToken: Exception? = null

    override suspend fun beginLink(activity: Activity): LinkOutcome {
        failNextBeginLink?.let { failNextBeginLink = null; throw it }
        val outcome = nextLinkOutcome ?: LinkOutcome.Linked(LinkedGoogleAccount("測試用戶", "test@example.com"))
        nextLinkOutcome = null
        if (outcome is LinkOutcome.Linked) linked = outcome.account
        return outcome
    }

    override suspend fun finishLink(data: Intent): LinkedGoogleAccount =
        linked ?: LinkedGoogleAccount("測試用戶", "test@example.com").also { linked = it }

    override suspend fun accessToken(): String {
        failNextAccessToken?.let { failNextAccessToken = null; throw it }
        return linked?.let { "fake-access-token" } ?: error("尚未連結 Google 帳號")
    }

    override suspend fun unlink() { linked = null }
}
```

- [ ] **Step 7: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.FakeGoogleAuthTest"`
Expected: PASS（4 個測試）

- [ ] **Step 8: 寫 `GisGoogleAuth`（真正實作，靠 Task 0 的憑證才能真的連上）**

```kotlin
package com.xenyaa.videoshot.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/**
 * 真正的 Google 帳號連結：Credential Manager 拿身分（只為了畫面顯示，**不驗證 idToken 簽章**——
 * 這裡不是安全判斷，安全判斷完全交給 Google 的授權流程本身），`AuthorizationClient` 拿
 * `drive.appdata` 授權（規格第十節）。
 *
 * @param webClientId Task 0 步驟 5 在 Google Cloud Console 另外建立的「網頁應用程式」OAuth
 *        client id——Credential Manager 的登入流程需要它當 ID token 的受眾，不是密鑰。
 *
 * **已知限制（Task 0 完成前）**：`webClientId` 若還是 strings.xml 裡的佔位字串，
 * `beginLink` 會在 Credential Manager 那一步丟例外，不會走到 `AuthorizationClient`。
 */
class GisGoogleAuth(
    private val context: Context,
    private val webClientId: String,
    private val io: CoroutineDispatcher,
) : GoogleAuth {

    /** `beginLink` 到 `finishLink` 之間的身分結果——授權若需要額外同意，要先記住身分，等 `finishLink` 再一起回傳。 */
    private var pendingAccount: LinkedGoogleAccount? = null

    override suspend fun beginLink(activity: Activity): LinkOutcome = withContext(io) {
        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val response = credentialManager.getCredential(activity, request)
        val idTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
        val email = decodeEmailFromIdToken(idTokenCredential.idToken) ?: idTokenCredential.id
        val account = LinkedGoogleAccount(displayName = idTokenCredential.displayName ?: email, email = email)
        pendingAccount = account

        val authClient = Identity.getAuthorizationClient(activity)
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Tasks.await(authClient.authorize(authRequest))
        val pendingIntent = result.pendingIntent
        if (result.hasResolution() && pendingIntent != null) {
            LinkOutcome.NeedsConsent(pendingIntent.intentSender)
        } else {
            LinkOutcome.Linked(account)
        }
    }

    override suspend fun finishLink(data: Intent): LinkedGoogleAccount = withContext(io) {
        val authClient = Identity.getAuthorizationClient(context)
        authClient.getAuthorizationResultFromIntent(data) // 授權失敗會丟 ApiException，讓它往上傳
        checkNotNull(pendingAccount) { "finishLink 呼叫順序錯了——要先呼叫過 beginLink" }
    }

    override suspend fun accessToken(): String = withContext(io) {
        val authClient = Identity.getAuthorizationClient(context)
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Tasks.await(authClient.authorize(authRequest))
        if (result.hasResolution()) error("Drive 授權已失效，需要使用者重新連結")
        result.accessToken ?: error("Drive 授權沒有回傳存取權杖")
    }

    override suspend fun unlink(): Unit = withContext(io) {
        val authClient = Identity.getAuthorizationClient(context)
        Tasks.await(authClient.revokeAccess(RevokeAccessRequest.builder().build()))
        pendingAccount = null
    }

    private fun decodeEmailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        JSONObject(json).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()
}
```

**這個 Task 沒有 androidTest**——`GisGoogleAuth` 整支都在跟 Play Services／系統畫面互動，
真正的驗證是 Task 12 接上帳號頁之後，在裝了 debug APK、且 Task 0 已完成的實機上手動按
【連結 Google 帳號】走一次（進 Task 12 的驗收清單）。

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/strings.xml \
  app/src/main/java/com/xenyaa/videoshot/backup/GoogleAuth.kt \
  app/src/main/java/com/xenyaa/videoshot/backup/FakeGoogleAuth.kt \
  app/src/main/java/com/xenyaa/videoshot/backup/GisGoogleAuth.kt \
  app/src/test/java/com/xenyaa/videoshot/backup/FakeGoogleAuthTest.kt
git commit -m "feat(備份): GoogleAuth——Credential Manager 身分＋AuthorizationClient 的 drive.appdata 授權"
```

---

## Task 8: `AppSettings` 補欄位——連結帳號顯示用資訊、首次開啟的還原決定

**Files:**
- Modify: `app/src/main/java/com/xenyaa/videoshot/data/settings/AppSettings.kt`
- Modify: `app/src/androidTest/java/com/xenyaa/videoshot/data/AppSettingsTest.kt`（既有檔案，在既有的 `@Before reset()` 之後新增測試方法，比照現有 `過濾強度存得住` 的寫法）

**Interfaces:**
- Consumes: `LinkedGoogleAccount`（Task 7）。
- Produces: `AppSettings.linkedAccount: Flow<LinkedGoogleAccount?>`、`setLinkedAccount(account)`、`clearLinkedAccount()`、`AppSettings.restoreDecisionMade: Flow<Boolean>`、`markRestoreDecisionMade()`。Task 12（帳號頁 hero／中斷連結）、Task 14（首次開啟閘門）都靠這些欄位。

**這兩組欄位都是裝置本地、不進備份**——理由跟 `geminiKey`／`themeId` 一樣（規格第四節「設定值不進備份」）：換裝置本來就要重新連結 Google 帳號，`restoreDecisionMade` 也是「這台裝置有沒有問過使用者」的本地旗標。

- [ ] **Step 1: 在既有的 `AppSettingsTest.kt` 加測試方法**

在 `過濾強度存得住()` 之後插入：

```kotlin
    @Test
    fun 尚未連結時linkedAccount是null() = runBlocking {
        assertNull(settings.linkedAccount.first())
    }

    @Test
    fun 連結帳號存得住() = runBlocking {
        settings.setLinkedAccount(LinkedGoogleAccount("阿明", "ming@example.com"))
        val account = settings.linkedAccount.first()
        assertEquals("阿明", account?.displayName)
        assertEquals("ming@example.com", account?.email)
    }

    @Test
    fun 中斷連結後linkedAccount回到null() = runBlocking {
        settings.setLinkedAccount(LinkedGoogleAccount("阿明", "ming@example.com"))
        settings.clearLinkedAccount()
        assertNull(settings.linkedAccount.first())
    }

    @Test
    fun 首次開啟的還原決定預設是還沒決定() = runBlocking {
        assertFalse(settings.restoreDecisionMade.first())
    }

    @Test
    fun 標記過還原決定之後不會再是還沒決定() = runBlocking {
        settings.markRestoreDecisionMade()
        assertTrue(settings.restoreDecisionMade.first())
    }
```

同時在檔案頂部加一行 import：

```kotlin
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
```

- [ ] **Step 2: 執行確認失敗（編譯，等實機時真跑）**

Run: `cd android && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: 編譯失敗（`AppSettings` 還沒有 `linkedAccount`／`restoreDecisionMade`）

- [ ] **Step 3: 修改 `AppSettings.kt`**

在 `setFolderSort` 方法之後（`writeRawFolderSortForTest` 之前或之後皆可，維持既有分組風格）插入：

```kotlin
    /**
     * 已連結的 Google 帳號（規格第九節 hero：「已連結 Google：名稱、Email、頭像字母」）。
     * 只存顯示用的兩個欄位——真正的授權狀態交給 `GoogleAuth`／Play Services 自己管，
     * 這裡不快取存取權杖。換裝置、清除 app 資料都會回到未連結，使用者要重新連結。
     */
    val linkedAccount: Flow<com.xenyaa.videoshot.backup.LinkedGoogleAccount?> = store.data.map { prefs ->
        val name = prefs[LINKED_ACCOUNT_NAME] ?: return@map null
        val email = prefs[LINKED_ACCOUNT_EMAIL] ?: return@map null
        com.xenyaa.videoshot.backup.LinkedGoogleAccount(name, email)
    }

    suspend fun setLinkedAccount(account: com.xenyaa.videoshot.backup.LinkedGoogleAccount) {
        store.edit {
            it[LINKED_ACCOUNT_NAME] = account.displayName
            it[LINKED_ACCOUNT_EMAIL] = account.email
        }
    }

    suspend fun clearLinkedAccount() {
        store.edit {
            it.remove(LINKED_ACCOUNT_NAME)
            it.remove(LINKED_ACCOUNT_EMAIL)
        }
    }

    /**
     * 全新安裝的第一個畫面（【從 Google Drive 還原】／【全新開始】）有沒有被回答過
     * （規格第十節「還原」入口）。回答過就不再問——即使之後圖庫又變空，也不會重新跳出來。
     */
    val restoreDecisionMade: Flow<Boolean> = store.data.map { it[RESTORE_DECISION_MADE] ?: false }

    suspend fun markRestoreDecisionMade() {
        store.edit { it[RESTORE_DECISION_MADE] = true }
    }
```

（正式寫檔時把 `com.xenyaa.videoshot.backup.LinkedGoogleAccount` 改成頂部 `import` 一次、型別處直接寫 `LinkedGoogleAccount`——這裡用全名只是避免計畫裡的程式碼片段互相搶 import 位置。）

在 `private companion object` 裡的鍵值清單加三個：

```kotlin
        val LINKED_ACCOUNT_NAME = stringPreferencesKey("linked_account_name")
        val LINKED_ACCOUNT_EMAIL = stringPreferencesKey("linked_account_email")
        val RESTORE_DECISION_MADE = booleanPreferencesKey("restore_decision_made")
```

- [ ] **Step 4: 執行確認通過（需要實機）**

```bash
adb shell am instrument -w -e class com.xenyaa.videoshot.data.AppSettingsTest \
  com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: 既有案例＋新增 5 個都通過；沒裝置時記下「只編譯驗證過」。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/data/settings/AppSettings.kt app/src/androidTest/java/com/xenyaa/videoshot/data/AppSettingsTest.kt
git commit -m "feat(備份): AppSettings 補連結帳號顯示資訊與首次開啟的還原決定旗標"
```

---

## Task 9: `BackupManager`——快照→上傳→保留策略→時間戳的協調者

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/BackupManager.kt`
- Test: `app/src/test/java/com/xenyaa/videoshot/backup/BackupManagerTest.kt`

**Interfaces:**
- Consumes: `BackupStore`（Task 4/5）、`BackupCodec`／`backupFileName`／`backupsToDelete`／`RemoteBackupSummary`（Task 1/2）。**故意不直接依賴 `BackupSnapshotter`**——`snapshotTo` 收一個 `suspend (File) -> Unit` 函式，讓這個類別的單元測試不必牽動真的 Room／SQLite（比照 `RoomLibraryRepo` 建構子收 `onChanged: suspend () -> Unit` 的做法）。
- Produces: `class BackupManager(...) { suspend fun runIfDue(force: Boolean): Boolean }`。Task 10（`BackupWorker`）呼叫 `runIfDue(force = false)`；Task 12（帳號頁【立即備份】）呼叫 `runIfDue(force = true)`。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.backup

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class BackupManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var workDir: File
    private lateinit var store: FakeBackupStore
    private var lastChangedAt = 0L
    private var lastBackupAt = 0L
    private var now = 0L
    private var markedBackedUpAt: Long? = null

    private fun manager() = BackupManager(
        snapshotTo = { dest -> dest.writeBytes(ByteArray(16) { it.toByte() }) },
        store = store,
        workDir = workDir,
        io = Dispatchers.IO,
        deviceName = { "測試裝置" },
        shotCount = { 7 },
        lastChangedAtSec = { lastChangedAt },
        lastBackupAtSec = { lastBackupAt },
        nowSec = { now },
        markBackedUp = { at -> markedBackedUpAt = at },
    )

    @Before fun setUp() {
        workDir = tmp.newFolder("work")
        store = FakeBackupStore()
    }

    @Test
    fun 沒有變更時不強制執行就不備份() = runTest {
        lastChangedAt = 100; lastBackupAt = 100; now = 100 + 25 * 3600
        val ran = manager().runIfDue(force = false)
        assertFalse(ran)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun 有變更但不到24小時就不備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 100; now = 100 + 3600 // 只過 1 小時
        val ran = manager().runIfDue(force = false)
        assertFalse(ran)
    }

    @Test
    fun 有變更且超過24小時才備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 100; now = 100 + 25 * 3600
        val ran = manager().runIfDue(force = false)
        assertTrue(ran)
        assertEquals(1, store.list().size)
        assertEquals(now, markedBackedUpAt)
    }

    @Test
    fun force為true時忽略時間限制() = runTest {
        lastChangedAt = 100; lastBackupAt = 100; now = 100 // 剛備份完，沒有變更
        val ran = manager().runIfDue(force = true)
        assertTrue(ran)
    }

    @Test
    fun 上傳的appProperties帶正確中繼資料() = runTest {
        now = 1_000
        manager().runIfDue(force = true)
        val uploaded = store.list().single()
        assertEquals(7, uploaded.shotCount)
        assertEquals("測試裝置", uploaded.deviceName)
    }

    @Test
    fun 保留策略只留最新3份() = runTest {
        val mgr = manager()
        repeat(4) { i -> now = 1000L + i * 100; mgr.runIfDue(force = true) }
        assertEquals(3, store.list().size)
        assertEquals(listOf(1300L, 1200L, 1100L), store.list().map { it.createdAtEpochSec })
    }

    @Test
    fun 結束後不留暫存檔() = runTest {
        now = 5_000
        manager().runIfDue(force = true)
        assertTrue(workDir.listFiles()?.isEmpty() != false)
    }

    @After fun tearDown() {}
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.BackupManagerTest"`
Expected: FAIL（`BackupManager` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.core.backup.RemoteBackupSummary
import com.xenyaa.videoshot.core.backup.backupFileName
import com.xenyaa.videoshot.core.backup.backupsToDelete
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 快照 → 上傳 → 保留 3 份 → 更新時間戳 的協調者（規格第十節）。手動【立即備份】與
 * WorkManager 的每日自動備份都呼叫這裡——差別只在有沒有繞過「距上次備份 > 24 小時」的檢查。
 */
class BackupManager(
    private val snapshotTo: suspend (dest: File) -> Unit,
    private val store: BackupStore,
    private val workDir: File,
    private val io: CoroutineDispatcher,
    private val deviceName: () -> String,
    private val shotCount: suspend () -> Int,
    private val lastChangedAtSec: suspend () -> Long,
    private val lastBackupAtSec: suspend () -> Long,
    private val nowSec: () -> Long,
    private val markBackedUp: suspend (nowSec: Long) -> Unit,
) {
    /**
     * @param force true＝帳號頁【立即備份】，跳過「有變更且距上次 > 24 小時」的檢查
     *        （規格第十節「觸發」表的「手動」列）；false＝WorkManager 每日排程，照「自動」列的條件判斷。
     * @return true 代表真的執行了一次備份（上傳成功）；false 代表這次判斷不需要備份。
     */
    suspend fun runIfDue(force: Boolean): Boolean = withContext(io) {
        if (!force) {
            val changed = lastChangedAtSec() > lastBackupAtSec()
            val overADay = nowSec() - lastBackupAtSec() > 24 * 3600
            if (!changed || !overADay) return@withContext false
        }

        workDir.mkdirs()
        val now = nowSec()
        val rawDb = File(workDir, "snapshot-$now.db")
        val gz = File(workDir, backupFileName(now))
        try {
            snapshotTo(rawDb)
            val sha256 = BackupCodec.gzipWithSha256(rawDb, gz)
            val newBackup = NewBackup(
                file = gz,
                sha256 = sha256,
                schemaVersion = LIBRARY_SCHEMA_VERSION,
                shotCount = shotCount(),
                deviceName = deviceName(),
                createdAtEpochSec = now,
            )
            store.upload(newBackup)
            enforceRetention()
            markBackedUp(now)
            true
        } finally {
            rawDb.delete()
            gz.delete()
        }
    }

    private suspend fun enforceRetention() {
        val summaries = store.list().map { RemoteBackupSummary(it.id, it.createdAtEpochSec) }
        backupsToDelete(summaries, keep = 3).forEach { store.delete(it.id) }
    }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.BackupManagerTest"`
Expected: PASS（8 個測試）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/BackupManager.kt app/src/test/java/com/xenyaa/videoshot/backup/BackupManagerTest.kt
git commit -m "feat(備份): BackupManager——快照、上傳、保留 3 份、時間戳一次協調好"
```

---

## Task 10: WorkManager 每日自動備份

**Files:**
- Modify: `gradle/libs.versions.toml`（新增 `work-runtime-ktx`／`work-testing`）
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/BackupWorker.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/VideoshotApp.kt`（實作 `Configuration.Provider`，用自訂 `WorkerFactory` 把 `BackupManager` 注入 `BackupWorker`；WorkManager 2.6+ 的「on-demand initialization」——`Application` 實作這個介面，App Startup 的預設初始化器就會自動改用它，不需要動 `AndroidManifest.xml`）
- Test: `app/src/test/java/com/xenyaa/videoshot/backup/BackupWorkerTest.kt`

**Interfaces:**
- Consumes: `BackupManager.runIfDue(force: Boolean)`（Task 9）。
- Produces: `class BackupWorker(context, params, backupManager) : CoroutineWorker`、`class BackupWorkerFactory(backupManager) : WorkerFactory`、`fun scheduleDailyBackup(context: Context)`。

- [ ] **Step 1: 加依賴**

`gradle/libs.versions.toml` 的 `[versions]`：

```toml
work = "2.10.0"
```

`[libraries]`：

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

`app/build.gradle.kts` 的 `dependencies {}`：

```kotlin
implementation(libs.androidx.work.runtime.ktx)
testImplementation(libs.androidx.work.testing)
```

- [ ] **Step 2: 寫失敗的測試**

**設計筆記**：`WorkerParameters` 沒有公開建構子，`TestListenableWorkerBuilder<W>` 預設只知道怎麼
反射呼叫 `(Context, WorkerParameters)` 兩參數建構子。`BackupWorker` 的建構子多帶了第三個參數
（下面的 `runIfDue`），所以不能靠預設建構方式——不管production 還是測試都要透過自訂
`WorkerFactory` 來建立它；`TestListenableWorkerBuilder.setWorkerFactory(factory)` 就是為這種
情境設計的：

```kotlin
package com.xenyaa.videoshot.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupWorkerTest {

    @Test
    fun 有需要備份時回success_呼叫時force是false() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var calledForce: Boolean? = null
        val factory = BackupWorkerFactory(runIfDue = { force -> calledForce = force; true })

        val worker = TestListenableWorkerBuilder<BackupWorker>(context)
            .setWorkerFactory(factory)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(false, calledForce)
    }

    @Test
    fun BackupManager丟例外時回retry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackupWorkerFactory(runIfDue = { throw IllegalStateException("網路壞了") })

        val worker = TestListenableWorkerBuilder<BackupWorker>(context)
            .setWorkerFactory(factory)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
```

（測試直接把 `BackupWorkerFactory` 的建構子參數簡化成一個 `runIfDue: suspend (Boolean) -> Boolean` 函式，不需要真的組一個 `BackupManager`——見下面 Step 3 的 `BackupWorkerFactory` 定義。）

- [ ] **Step 3: 執行確認失敗**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.BackupWorkerTest"`
Expected: FAIL（`BackupWorker`／`BackupWorkerFactory` 還不存在）

- [ ] **Step 4: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private const val DAILY_BACKUP_WORK_NAME = "daily_backup"

/**
 * 每日自動備份（規格第十節「觸發」表的「自動」列）。約束（不計費網路、電量不低）在
 * [scheduleDailyBackup] 設定，這裡只管執行一次 `runIfDue(force = false)`。
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
    private val runIfDue: suspend (force: Boolean) -> Boolean,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        runIfDue(false)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

/** 把 [BackupManager] 接進 `BackupWorker` 的建構子——WorkManager 自己不知道怎麼生出 `BackupManager`。 */
class BackupWorkerFactory(private val runIfDue: suspend (force: Boolean) -> Boolean) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        BackupWorker::class.java.name -> BackupWorker(appContext, workerParameters, runIfDue)
        else -> null
    }
}

/** app 啟動時呼叫一次；`enqueueUniquePeriodicWork` 是 idempotent 的，重覆呼叫不會排出第二份工作。 */
fun scheduleDailyBackup(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()
    val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(DAILY_BACKUP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
```

`app/src/main/java/com/xenyaa/videoshot/VideoshotApp.kt` 改成：

```kotlin
package com.xenyaa.videoshot

import android.app.Application
import androidx.work.Configuration
import com.xenyaa.videoshot.backup.BackupWorkerFactory
import com.xenyaa.videoshot.backup.scheduleDailyBackup
import com.xenyaa.videoshot.di.AppContainer

class VideoshotApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleDailyBackup(this)
    }

    /**
     * WorkManager 2.6+ 的「on-demand initialization」：`Application` 實作這個介面，
     * App Startup 的預設 `WorkManagerInitializer` 就會自動改用這份設定，不需要在
     * `AndroidManifest.xml` 裡關掉預設初始化器。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(BackupWorkerFactory(runIfDue = { force -> container.backupManager.runIfDue(force) }))
            .build()
}
```

（`container.backupManager` 是 Task 12 才會加進 `AppContainer` 的欄位——這一步先照這樣寫，等 Task 12 補上那個欄位就會編譯通過；在那之前這個檔案會編譯失敗是預期中的暫時狀態，兩個 Task 要連著做完才算收尾。）

- [ ] **Step 5: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.backup.BackupWorkerTest"`
Expected: PASS（2 個測試）——`VideoshotApp.kt` 對 `container.backupManager` 的參照要等 Task 12 才會補上，如果編譯因此卡住，可以先把 `VideoshotApp.kt` 的改動留到 Task 12 一起做，這裡只先把 `BackupWorker`／`BackupWorkerFactory`／`scheduleDailyBackup` 三個獨立單元做完、測試跑過。

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/xenyaa/videoshot/backup/BackupWorker.kt app/src/test/java/com/xenyaa/videoshot/backup/BackupWorkerTest.kt
git commit -m "feat(備份): BackupWorker——WorkManager 每日排程的自動備份"
```

---

## Task 11: `RestoreManager`——下載、驗證、換檔、清空、交給呼叫端重啟

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/RestoreManager.kt`
- Test: `app/src/androidTest/java/com/xenyaa/videoshot/backup/RestoreManagerTest.kt`（需要真的檔案型 SQLite db，比照 `MigrationTest.kt` 放 androidTest）

**背景**：還原流程圖（規格第十節）是 `SHA-256 → 解壓 → integrity_check → schema 版本 → 換檔 → 跑
migrations → 清空 cache.db 與草稿`。「跑 migrations」這一步**不需要 `RestoreManager`自己動手**——
換檔之後，下一次用 `Room.databaseBuilder(...).addMigrations(*LIBRARY_MIGRATIONS)` 開啟這個檔案時，
Room 自己會比對 `user_version` 跑該跑的遷移。所以還原完成後**必須整個重啟 app**，讓
`AppContainer` 的 `libraryDb` 用一個全新的 Room 實例重新打開新檔——不嘗試在執行中置換
`AppContainer` 裡 `by lazy` 的既有實例（那個實例已經被其他 repo／ViewModel 持有參照，硬換會有
一半元件看到新檔、一半還握著舊連線的分裂狀態）。`RestoreManager` 只負責換檔到「隨時可以重啟」
為止，真正呼叫重啟由 Task 13 的 UI 層做。

**Files 換檔的原子性**：候選檔案（下載解壓後）先寫在 `workDir`（跟 `library.db` 同一個檔案系統，
都在 `filesDir` 底下），驗證通過後用 `File.renameTo()` 蓋掉舊檔——同檔案系統上的 rename 是原子的
（底層是 `rename(2)` 系統呼叫），中途斷電或程序被殺掉只會停在「舊檔還在」或「新檔已經就位」
兩種狀態之一，不會有寫一半的檔案（規格第十節「換檔以『寫到暫存檔、驗證完再改名』完成」）。

**Interfaces:**
- Consumes: `BackupStore`（Task 4/5）、`BackupCodec`（Task 1）、`checkBackupSchema`／`BackupCompat`（既有，`data/library/BackupSchemaCheck.kt`）、`CacheRepo.clearAll()`（既有）。
- Produces: `sealed interface RestoreResult { Success; Failure(reason: String) }`、`class RestoreManager(...) { suspend fun listBackups(): List<RemoteBackup>; suspend fun restore(backup: RemoteBackup): RestoreResult }`。Task 13（還原挑選畫面）呼叫這兩個方法。

- [ ] **Step 1: 寫失敗的測試**

```kotlin
package com.xenyaa.videoshot.backup

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var libraryDbFile: File
    private lateinit var libraryDb: LibraryDatabase
    private lateinit var cacheDbFile: File
    private lateinit var draftsDir: File
    private lateinit var workDir: File
    private lateinit var store: FakeBackupStore

    private fun openLibraryDb(): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, libraryDbFile.path)
            .setDriver(BundledSQLiteDriver())
            .addCallback(LibrarySchemaCallback)
            .addMigrations(*LIBRARY_MIGRATIONS)
            .build()

    private fun pick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-09-27", place = null, description = null, webp = null,
    )

    @Before fun setUp() {
        libraryDbFile = File(tmp.root, "library.db")
        cacheDbFile = File(tmp.root, "cache.db")
        draftsDir = File(tmp.root, "drafts").apply { mkdirs(); File(this, "v1/sheets").mkdirs() }
        workDir = File(tmp.root, "restore_work")
        libraryDb = openLibraryDb()
        store = FakeBackupStore()
    }

    @After fun tearDown() { if (::libraryDb.isInitialized) runCatching { libraryDb.close() } }

    private fun manager(db: LibraryDatabase = libraryDb) = RestoreManager(
        store = store,
        libraryDb = db,
        libraryDbFile = libraryDbFile,
        cacheRepo = RoomCacheRepo(
            Room.databaseBuilder(context, CacheDatabase::class.java, cacheDbFile.path)
                .setDriver(BundledSQLiteDriver()).build(),
            Dispatchers.IO,
        ),
        draftsDir = draftsDir,
        workDir = workDir,
        io = Dispatchers.IO,
    )

    /** 上傳一份「別的裝置」的備份到 [store]：內容跟現在本機的 library.db 不一樣，換檔後才驗得出來真的換過。 */
    private suspend fun uploadBackupWithShots(shotCount: Int): RemoteBackup {
        val otherDbFile = File(tmp.root, "other-${shotCount}.db")
        val otherDb = Room.databaseBuilder(context, LibraryDatabase::class.java, otherDbFile.path)
            .setDriver(BundledSQLiteDriver()).addCallback(LibrarySchemaCallback).addMigrations(*LIBRARY_MIGRATIONS).build()
        RoomLibraryRepo(otherDb, Dispatchers.IO).commitPicks(
            VideoEntity("other", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            (0 until shotCount).map { pick(it) },
        )
        val snapshotFile = File(tmp.root, "snapshot-${shotCount}.db")
        BackupSnapshotter(otherDb, Dispatchers.IO).snapshotTo(snapshotFile)
        otherDb.close()
        val gz = File(tmp.root, "snapshot-${shotCount}.db.gz")
        val sha256 = BackupCodec.gzipWithSha256(snapshotFile, gz)
        return store.upload(NewBackup(gz, sha256, 1, shotCount, "別的裝置", 100))
    }

    @Test
    fun 成功還原後本機資料變成備份的內容_舊快取與草稿被清空() = runTest {
        // 本機先有 2 張
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0), pick(1)),
        )
        val remote = uploadBackupWithShots(shotCount = 5)

        val result = manager().restore(remote)

        assertTrue(result is RestoreResult.Success)
        val reopened = openLibraryDb()
        val shotCount = reopened.readSingleLong("SELECT COUNT(*) FROM shot")
        assertEquals(5L, shotCount)
        reopened.close()
        assertFalse(File(draftsDir, "v1/sheets").exists())
    }

    @Test
    fun 備份比app新時拒絕_原本資料不動() = runTest {
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0)),
        )
        val remote = uploadBackupWithShots(shotCount = 3)
        // 手動把剛上傳那份備份的檔案內容的 user_version 改成比現在的 app 新——
        // 用另一個 driver 連線直接改，模擬「這份備份是用比較新的 app 版本做的」
        val bumpedGz = File(tmp.root, "bumped.db.gz")
        val bumpedRaw = File(tmp.root, "bumped.db")
        store.download(remote.id, bumpedGz)
        BackupCodec.gunzip(bumpedGz, bumpedRaw)
        val driver = androidx.sqlite.driver.bundled.BundledSQLiteDriver()
        driver.open(bumpedRaw.absolutePath).use { it.execSQLDirect("PRAGMA user_version = 9999") }
        val newSha = BackupCodec.gzipWithSha256(bumpedRaw, bumpedGz)
        store.delete(remote.id)
        val tooNewRemote = store.upload(NewBackup(bumpedGz, newSha, 9999, 3, "未來裝置", 200))

        val result = manager().restore(tooNewRemote)

        assertTrue(result is RestoreResult.Failure)
        val stillLocal = openLibraryDb()
        assertEquals(1L, stillLocal.readSingleLong("SELECT COUNT(*) FROM shot"))
        stillLocal.close()
    }

    @Test
    fun 雜湊不符時拒絕_原本資料不動() = runTest {
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0)),
        )
        val remote = uploadBackupWithShots(shotCount = 3)
        val corrupted = remote.copy(sha256 = "0".repeat(64))

        val result = manager().restore(corrupted)

        assertTrue(result is RestoreResult.Failure)
        val stillLocal = openLibraryDb()
        assertEquals(1L, stillLocal.readSingleLong("SELECT COUNT(*) FROM shot"))
        stillLocal.close()
    }
}
```

上面用到兩個小工具，要一起加：`LibraryDatabase.readSingleLong` 已經存在於
`app/src/androidTest/java/com/xenyaa/videoshot/data/TestDb.kt`（Task 6 引用過的同一個檔案），
但那個檔案在 `com.xenyaa.videoshot.data` 套件，跟這份測試的 `com.xenyaa.videoshot.backup` 套件
不同——在測試檔案開頭加 `import com.xenyaa.videoshot.data.readSingleLong`。另外
`execSQLDirect` 不是真的存在的方法，上面「備份比 app 新」這個案例改成直接用
`prepare("PRAGMA user_version = 9999").use { it.step() }`（`PRAGMA` 賦值語句本身不回資料列，
但 SQLite 的 driver 仍要求呼叫一次 `step()` 才會真的執行），把測試裡那一行換成：

```kotlin
        driver.open(bumpedRaw.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version = 9999").use { it.step() }
        }
```

- [ ] **Step 2: 執行確認失敗（編譯，等實機時真跑）**

Run: `cd android && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: 編譯失敗（`RestoreManager` 還不存在）

- [ ] **Step 3: 寫最小實作**

```kotlin
package com.xenyaa.videoshot.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.data.library.BackupCompat
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.checkBackupSchema
import com.xenyaa.videoshot.data.repo.CacheRepo
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    data object Success : RestoreResult
    data class Failure(val reason: String) : RestoreResult
}

/**
 * 下載 → 驗證 → 換檔 → 清空 `cache.db` 與草稿（規格第十節的還原流程圖）。
 * **不負責重啟 app**——換檔完成後交給呼叫端（Task 13）決定何時重啟，讓
 * `AppContainer` 用全新的 Room 實例打開新檔（見本 Task 開頭的說明）。
 */
class RestoreManager(
    private val store: BackupStore,
    private val libraryDb: LibraryDatabase,
    private val libraryDbFile: File,
    private val cacheRepo: CacheRepo,
    private val draftsDir: File,
    private val workDir: File,
    private val io: CoroutineDispatcher,
) {
    suspend fun listBackups(): List<RemoteBackup> = store.list()

    suspend fun restore(backup: RemoteBackup): RestoreResult = withContext(io) {
        workDir.mkdirs()
        val gz = File(workDir, "restore-${backup.id}.db.gz")
        val candidate = File(workDir, "restore-${backup.id}.db")
        try {
            store.download(backup.id, gz)
            if (!BackupCodec.verifySha256(gz, backup.sha256)) {
                return@withContext RestoreResult.Failure("下載的檔案跟雲端記錄的雜湊不符，原本的圖庫沒有變動")
            }
            BackupCodec.gunzip(gz, candidate)

            val info = inspectCandidate(candidate.absolutePath)
            if (!info.integrityOk) {
                return@withContext RestoreResult.Failure("備份檔案損毀，原本的圖庫沒有變動")
            }
            when (checkBackupSchema(info.userVersion)) {
                BackupCompat.TOO_NEW ->
                    return@withContext RestoreResult.Failure("這份備份是用比較新的 app 版本做的，請先更新 app")
                BackupCompat.OK, BackupCompat.NEEDS_MIGRATION -> Unit
            }

            libraryDb.close()
            deleteWalSidecarsQuietly()
            check(candidate.renameTo(libraryDbFile)) { "換檔失敗：${candidate.path} -> ${libraryDbFile.path}" }
            cacheRepo.clearAll()
            draftsDir.deleteRecursively()
            RestoreResult.Success
        } finally {
            gz.delete()
            candidate.delete()
        }
    }

    /** WAL 模式下正常關閉會 checkpoint，但保守起見還是自己清掉——換檔後這兩個檔案對應舊內容，留著會誤導下一次開啟。 */
    private fun deleteWalSidecarsQuietly() {
        File(libraryDbFile.path + "-wal").delete()
        File(libraryDbFile.path + "-shm").delete()
    }

    private fun inspectCandidate(path: String): CandidateInfo {
        val connection = BundledSQLiteDriver().open(path)
        try {
            val integrityOk = connection.prepare("PRAGMA integrity_check").use { stmt ->
                stmt.step(); stmt.getText(0) == "ok"
            }
            val userVersion = connection.prepare("PRAGMA user_version").use { stmt ->
                stmt.step(); stmt.getLong(0).toInt()
            }
            return CandidateInfo(integrityOk, userVersion)
        } finally {
            connection.close()
        }
    }

    private data class CandidateInfo(val integrityOk: Boolean, val userVersion: Int)
}
```

- [ ] **Step 4: 執行確認通過（需要實機）**

```bash
adb shell am instrument -w -e class com.xenyaa.videoshot.backup.RestoreManagerTest \
  com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (3 tests)`；沒裝置時記下「只編譯驗證過」。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/RestoreManager.kt app/src/androidTest/java/com/xenyaa/videoshot/backup/RestoreManagerTest.kt
git commit -m "feat(備份): RestoreManager——下載、驗證、原子換檔、清空 cache 與草稿"
```

---

## Task 12: 帳號頁串接——連結／中斷連結、備份子畫面

**Files:**
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/account/AccountState.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/account/AccountDeps.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/account/AccountScreen.kt`（hero 顯示連結帳號）
- Create: `app/src/main/java/com/xenyaa/videoshot/ui/account/BackupScreen.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt`（組出 `googleAuth`／`backupStore`／`backupManager`，補上 Task 10 留下的那個參照）
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRoot.kt`（`AccountSection.BACKUP` 換成真的 `BackupScreen`，接 `rememberLauncherForActivityResult` 處理 `LinkOutcome.NeedsConsent`）
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/account/AccountViewModelTest.kt`（既有檔案，新增案例）
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/account/BackupScreenTest.kt`（新增）

**Interfaces:**
- Consumes: `GoogleAuth`（Task 7）、`BackupManager`（Task 9）、`AppSettings.linkedAccount`／`lastBackupAt`（Task 8、既有）。
- Produces: `AccountDeps`新增 `linkedAccount: Flow<LinkedGoogleAccount?>`、`beginLink(activity): LinkOutcome`、`finishLink(data): LinkedGoogleAccount`、`unlink()`、`lastBackupAtEpochSec: Flow<Long>`、`backupNow(): Boolean`。`AppContainer` 新增 `googleAuth`、`backupStore`、`backupManager` 三個欄位（`backupManager` 就是 Task 10 `VideoshotApp.kt` 裡 `container.backupManager` 那個參照，這個 Task 補上它）。

- [ ] **Step 1: 修改 `AccountState.kt`**

在 `data class AccountState` 裡加四個欄位（跟既有欄位同一個 data class，不用另外拆）：

```kotlin
    val linkedAccount: LinkedGoogleAccount? = null,
    val lastBackupAtEpochSec: Long = 0L,
    val backingUp: Boolean = false,
    val backupError: String? = null,
```

檔案頂部加：

```kotlin
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
```

- [ ] **Step 2: 修改 `AccountDeps.kt`**

在 `geminiKeySet`／`clearGeminiKey` 那一組之後加：

```kotlin
    val linkedAccount: Flow<LinkedGoogleAccount?>
    /** 前景才能呼叫（會跳出系統畫面）。回傳值只是通知呼叫端「需不需要再跳一個 IntentSender」——
     * 真的連結完成是靠 [linkedAccount] 這個 Flow 自己更新畫面。 */
    suspend fun beginLink(activity: Activity): LinkOutcome
    suspend fun finishLink(data: Intent): LinkedGoogleAccount
    suspend fun unlink()

    val lastBackupAtEpochSec: Flow<Long>
    /** @return true 代表真的執行了一次上傳；false 代表（理論上不會發生，因為帳號頁的呼叫永遠是 force=true）什麼都沒做。 */
    suspend fun backupNow(): Boolean
```

檔案頂部加：

```kotlin
import android.app.Activity
import android.content.Intent
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
```

- [ ] **Step 3: 修改 `AccountViewModel.kt`**

在 `init {}` 裡補兩行收集：

```kotlin
        viewModelScope.launch { deps.linkedAccount.collect { v -> _state.value = _state.value.copy(linkedAccount = v) } }
        viewModelScope.launch { deps.lastBackupAtEpochSec.collect { v -> _state.value = _state.value.copy(lastBackupAtEpochSec = v) } }
```

在 `clearGeminiKey()` 之後加：

```kotlin
    /**
     * @param onNeedsConsent 需要使用者到系統畫面同意時呼叫——`AppRoot` 接住這個 callback，
     *        用 `rememberLauncherForActivityResult` 跳出畫面，回來後呼叫 [finishLink]。
     *        連結**成功**（不需要額外同意）時不會呼叫這個 callback，`linkedAccount` flow 自己會更新畫面。
     */
    fun beginLink(activity: Activity, onNeedsConsent: (android.content.IntentSender) -> Unit) = launchGuarded {
        when (val outcome = deps.beginLink(activity)) {
            is com.xenyaa.videoshot.backup.LinkOutcome.Linked -> Unit
            is com.xenyaa.videoshot.backup.LinkOutcome.NeedsConsent -> onNeedsConsent(outcome.intentSender)
        }
    }

    fun finishLink(data: android.content.Intent) = launchGuarded { deps.finishLink(data) }

    fun unlink() = launchGuarded { deps.unlink() }

    fun backupNow() {
        _state.value = _state.value.copy(backingUp = true, backupError = null)
        viewModelScope.launch {
            try {
                deps.backupNow()
                _state.value = _state.value.copy(backingUp = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(backingUp = false, backupError = "備份失敗，請確認網路後再試一次")
            }
        }
    }
```

（正式寫檔時把 `com.xenyaa.videoshot.backup.LinkOutcome`／`android.content.IntentSender`／`android.content.Intent` 改成頂部 import，型別處直接用短名——這裡用全名只是避免計畫片段互搶 import 位置，跟 Task 8 的說明一樣。）

- [ ] **Step 4: 寫 `AccountViewModelTest.kt` 的新案例**

在既有測試檔案裡新增（沿用檔案既有的 `FakeAccountDeps`／類似的假物件——找到現有測試怎麼造
`AccountDeps` 的假實作，在那個假物件類別裡把新加的四個屬性／方法也一起補上，`linkedAccount`
用 `MutableStateFlow<LinkedGoogleAccount?>(null)`、`lastBackupAtEpochSec` 用
`MutableStateFlow(0L)`，`beginLink`／`finishLink`／`unlink`／`backupNow` 記錄呼叫次數與參數）：

```kotlin
    @Test
    fun backupNow成功後backingUp回到false() = runTest {
        val vm = AccountViewModel(deps)
        vm.backupNow()
        advanceUntilIdle()
        assertFalse(vm.state.value.backingUp)
        assertNull(vm.state.value.backupError)
    }

    @Test
    fun backupNow失敗時顯示backupError() = runTest {
        deps.failNextBackup = true
        val vm = AccountViewModel(deps)
        vm.backupNow()
        advanceUntilIdle()
        assertFalse(vm.state.value.backingUp)
        assertEquals("備份失敗，請確認網路後再試一次", vm.state.value.backupError)
    }

    @Test
    fun unlink會呼叫deps的unlink() = runTest {
        val vm = AccountViewModel(deps)
        vm.unlink()
        advanceUntilIdle()
        assertTrue(deps.unlinkCalled)
    }
```

（`deps.failNextBackup`／`deps.unlinkCalled` 是要在假物件裡新增的欄位；`advanceUntilIdle` 需要
`import kotlinx.coroutines.test.advanceUntilIdle`，跟檔案裡其他非同步測試案例的寫法一致。）

- [ ] **Step 5: 執行確認失敗**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.AccountViewModelTest"`
Expected: FAIL（編譯錯誤：`AccountDeps` 還沒有新方法）

- [ ] **Step 6: 補上 `AppContainer.kt` 的接線**

在 `accountDeps` 那個 `object : AccountDeps { ... }` 裡（`clearGeminiKey` 之後）加：

```kotlin
                override val linkedAccount = settings.linkedAccount
                override suspend fun beginLink(activity: android.app.Activity): com.xenyaa.videoshot.backup.LinkOutcome {
                    val outcome = googleAuth.beginLink(activity)
                    if (outcome is com.xenyaa.videoshot.backup.LinkOutcome.Linked) {
                        settings.setLinkedAccount(outcome.account)
                    }
                    return outcome
                }
                override suspend fun finishLink(data: android.content.Intent): com.xenyaa.videoshot.backup.LinkedGoogleAccount {
                    val account = googleAuth.finishLink(data)
                    settings.setLinkedAccount(account)
                    return account
                }
                override suspend fun unlink() {
                    googleAuth.unlink()
                    settings.clearLinkedAccount()
                }

                override val lastBackupAtEpochSec = settings.lastBackupAt
                override suspend fun backupNow() = backupManager.runIfDue(force = true)
```

在 `AppContainer` 類別裡（`accountDeps` 這個 `by lazy` 之前，跟 `youtube`／`thumbs` 那一組同一個
分組風格）加四個新欄位：

```kotlin
    val googleAuth: com.xenyaa.videoshot.backup.GoogleAuth by lazy {
        com.xenyaa.videoshot.backup.GisGoogleAuth(
            context = appContext,
            webClientId = appContext.getString(R.string.google_signin_web_client_id),
            io = Dispatchers.IO,
        )
    }

    val backupStore: com.xenyaa.videoshot.backup.BackupStore by lazy {
        com.xenyaa.videoshot.backup.DriveBackupStore(
            http = httpClient,
            accessToken = { googleAuth.accessToken() },
            io = Dispatchers.IO,
        )
    }

    val backupManager: com.xenyaa.videoshot.backup.BackupManager by lazy {
        com.xenyaa.videoshot.backup.BackupManager(
            snapshotTo = { dest -> com.xenyaa.videoshot.backup.BackupSnapshotter(libraryDb, Dispatchers.IO).snapshotTo(dest) },
            store = backupStore,
            workDir = File(appContext.filesDir, "backup_work"),
            io = Dispatchers.IO,
            deviceName = { android.os.Build.MODEL ?: "Android" },
            shotCount = { libraryRepo.accountStats(com.xenyaa.videoshot.core.home.monthOf(java.time.LocalDate.now().toString())).totalShots },
            lastChangedAtSec = { settings.lastChangedAt.first() },
            lastBackupAtSec = { settings.lastBackupAt.first() },
            nowSec = { System.currentTimeMillis() / 1000 },
            markBackedUp = { at -> settings.markBackedUp(at) },
        )
    }
```

（需要 `import com.xenyaa.videoshot.R` 才能用 `appContext.getString(R.string.google_signin_web_client_id)`；
`appContext.filesDir` 已經是既有寫法，跟 `libraryDb`／`thumbs` 用的是同一個目錄樹。）

- [ ] **Step 7: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.AccountViewModelTest"`
Expected: PASS（既有案例＋新增 3 個）

- [ ] **Step 8: 寫 `BackupScreen.kt` 與它的測試**

```kotlin
package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.backup.lastBackupLabel
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「備份」子畫面（規格第九節）。【中斷連結】放在這個子畫面最底，不是整個帳號頁最底——
 * 規格第九節的版面表把它畫在「備份」那一列裡，跟這個子畫面對得上。
 */
@Composable
fun BackupScreen(
    linkedAccount: LinkedGoogleAccount?,
    lastBackupAtEpochSec: Long,
    backingUp: Boolean,
    backupError: String?,
    onBack: () -> Unit,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    onBackupNowClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochSec: Long = System.currentTimeMillis() / 1000,
) {
    var confirmingUnlink by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s4)) {
        AccountSettingHeader("備份", onBack)
        Column(
            Modifier.padding(horizontal = AppTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            if (linkedAccount == null) {
                Text("尚未設定備份", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text)
                Button(onClick = onLinkClick) { Text("連結 Google 帳號以啟用備份") }
            } else {
                Text(
                    "已連結：${linkedAccount.displayName}（${linkedAccount.email}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.text,
                )
                Text(
                    "上次備份：${lastBackupLabel(lastBackupAtEpochSec, nowEpochSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    Button(onClick = onBackupNowClick, enabled = !backingUp, modifier = Modifier.weight(1f)) {
                        Text(if (backingUp) "備份中…" else "立即備份")
                    }
                    TextButton(onClick = onRestoreClick, modifier = Modifier.weight(1f)) { Text("從 Drive 還原") }
                }
                if (backupError != null) {
                    Text(backupError, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
                }
            }
        }
        if (linkedAccount != null) {
            Spacer(Modifier.weight(1f, fill = false))
            TextButton(
                onClick = { confirmingUnlink = true },
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s4),
            ) { Text("中斷連結", color = AppTheme.colors.danger) }
        }
    }

    if (confirmingUnlink) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text("中斷連結？") },
            text = { Text("中斷後不會刪除 Drive 上已經備份的檔案，但這台裝置不會再自動備份。") },
            confirmButton = {
                TextButton(onClick = { confirmingUnlink = false; onUnlinkClick() }) {
                    Text("中斷連結", color = AppTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingUnlink = false }) { Text("取消") } },
        )
    }
}
```

```kotlin
package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BackupScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 未連結時顯示連結按鈕_沒有備份按鈕() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = null, lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("連結 Google 帳號以啟用備份").assertIsDisplayed()
    }

    @Test
    fun 已連結時顯示帳號資訊與上次備份時間() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 1000, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                    nowEpochSec = 1030,
                )
            }
        }
        compose.onNodeWithText("已連結：阿明（ming@example.com）").assertIsDisplayed()
        compose.onNodeWithText("上次備份：剛剛").assertIsDisplayed()
    }

    @Test
    fun 點立即備份會呼叫onBackupNowClick() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = { called = true }, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("立即備份").performClick()
        assert(called)
    }

    @Test
    fun 點中斷連結先跳確認框_按確認才真的呼叫onUnlinkClick() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = { called = true }, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("中斷連結").performClick()
        assert(!called) // 只是跳出確認框，還沒真的呼叫
        compose.onNodeWithText("中斷連結").onLast().performClick() // 確認框裡的那顆
    }
}
```

最後一個測試案例的 `onLast()` 用法需要 `onAllNodesWithText("中斷連結").onLast()`（畫面上「中斷連結」
文字這時候有兩個：底部按鈕與確認框裡的確認鈕）——把最後一步改成：

```kotlin
        compose.onAllNodesWithText("中斷連結").onLast().performClick()
        assert(called)
```

（需要 `import androidx.compose.ui.test.onAllNodesWithText` 與 `import androidx.compose.ui.test.onLast`。）

- [ ] **Step 9: 接上 `AppRoot.kt`**

把 `AccountSection.BACKUP -> ComingSoonScreen(...)` 換成：

```kotlin
                            AccountSection.BACKUP -> BackupScreen(
                                linkedAccount = accountState.linkedAccount,
                                lastBackupAtEpochSec = accountState.lastBackupAtEpochSec,
                                backingUp = accountState.backingUp,
                                backupError = accountState.backupError,
                                onBack = { nav = nav.pop() ?: nav },
                                onLinkClick = {
                                    accountVm.beginLink(context as android.app.Activity) { intentSender ->
                                        consentLauncher.launch(
                                            androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    }
                                },
                                onUnlinkClick = accountVm::unlink,
                                onBackupNowClick = accountVm::backupNow,
                                onRestoreClick = { /* Task 13 接上 Dest.RestoreFlow */ },
                            )
```

在 `AppRoot` 函式最前面（`val context = LocalContext.current` 那一行附近，第 204 行）加一個
`rememberLauncherForActivityResult`：

```kotlin
    val consentLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            accountVm.finishLink(data)
        }
    }
```

（`accountVm` 要在這一行之前就已經建立——檢查 `AppRoot.kt` 裡 `accountVm`／`accountState` 目前
是在哪裡宣告的，這個新的 `consentLauncher` 放在那之後、`Tab.ACCOUNT` 的 `when` 分支之前即可；
`rememberLauncherForActivityResult` 需要 `import androidx.compose.material3...`已有的 Compose
import 之外，額外加 `import androidx.activity.compose.rememberLauncherForActivityResult`。）

- [ ] **Step 10: 修改 `AccountScreen.kt` 的 hero**

把 hero 區塊裡固定顯示「尚未設定備份」／「連結 Google 帳號以啟用備份」的那段，改成依
`state.linkedAccount` 分支：

```kotlin
                    Column(Modifier.padding(start = AppTheme.spacing.s3)) {
                        if (state.linkedAccount == null) {
                            Text("尚未設定備份", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.accentInk)
                            Text(
                                "連結 Google 帳號以啟用備份",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.accentInk,
                                modifier = Modifier.focusRing().clickable { onOpenSection(AccountSection.BACKUP) }
                                    .padding(top = AppTheme.spacing.s1),
                            )
                        } else {
                            Text(state.linkedAccount.displayName, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.accentInk)
                            Text(
                                state.linkedAccount.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.accentInk,
                                modifier = Modifier.clickable { onOpenSection(AccountSection.BACKUP) }.padding(top = AppTheme.spacing.s1),
                            )
                        }
                    }
```

以及「備份」選單列的說明文字，從固定的 `"尚未設定備份"` 改成：

```kotlin
        item {
            AccountMenuRow(
                VsIcons.Cloud, "備份",
                state.linkedAccount?.let { "已連結：${it.displayName}" } ?: "尚未設定備份",
                onClick = { onOpenSection(AccountSection.BACKUP) },
            )
        }
```

既有的 `沒連結時hero顯示尚未設定備份()` 測試案例不用改（`AccountState()` 預設
`linkedAccount = null`，行為不變）；新增一個案例驗證已連結時的顯示：

```kotlin
    @Test
    fun 已連結時hero顯示帳號名稱() {
        setContent(state.copy(linkedAccount = com.xenyaa.videoshot.backup.LinkedGoogleAccount("阿明", "ming@example.com")))
        compose.onNodeWithText("阿明").assertIsDisplayed()
    }
```

- [ ] **Step 11: 執行確認通過**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.*"
```
Expected: 全部通過（`AccountScreenTest`／`AccountViewModelTest`／`BackupScreenTest`）

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/ui/account/ app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRoot.kt app/src/test/java/com/xenyaa/videoshot/ui/account/
git commit -m "feat(帳號): 接上備份——hero 連結狀態、備份子畫面、立即備份與中斷連結"
```

**實機驗收（Task 0 完成後才能真的走通）**：裝 debug APK，帳號頁按【連結 Google 帳號以啟用備份】→
應該跳出 Google 帳號選擇畫面 → 選帳號後（可能還會跳一個「允許 videoshot 存取…」的同意畫面）→
回到帳號頁，hero 顯示帳號名稱與 Email → 按【立即備份】→ 稍等後「上次備份」變成「剛剛」→ 到
Google Drive 網頁版的「我的雲端硬碟」瀏覽，**應該看不到這個檔案**（appDataFolder 對使用者是隱藏
資料夾，這正是預期行為，不是 bug——規格第十節的取捨）。

---

## Task 13: 還原挑選畫面與確認框

**Files:**
- Create: `app/src/main/java/com/xenyaa/videoshot/backup/RestartApp.kt`（重啟 app 的小工具函式）
- Create: `app/src/main/java/com/xenyaa/videoshot/ui/account/RestoreViewModel.kt`
- Create: `app/src/main/java/com/xenyaa/videoshot/ui/account/RestoreScreen.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/shell/NavState.kt`（新增 `Dest.RestoreFlow`）
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRootDeps.kt`（新增 `restoreManager`）
- Modify: `app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt`（組出 `restoreManager`，實作新介面）
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRoot.kt`（接上 `Dest.RestoreFlow`、`BackupScreen.onRestoreClick`）
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/account/RestoreViewModelTest.kt`
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/account/RestoreScreenTest.kt`
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/shell/NavStateTest.kt`（既有檔案，新增 `Dest.RestoreFlow` 的編解碼案例）

**Interfaces:**
- Consumes: `RestoreManager`（Task 11）。
- Produces: `sealed interface RestoreStep { Loading; Picking(backups); Confirming(backup); Restoring; Failed(reason) }`、`class RestoreViewModel(restoreManager, localShotCount, onRestartApp)`、`fun restartApp(context: Context)`、`Dest.RestoreFlow`。Task 14（首次開啟畫面）直接重用 `RestoreViewModel`／`RestoreScreen`，不重寫一份。

- [ ] **Step 1: 寫 `RestartApp.kt`**

```kotlin
package com.xenyaa.videoshot.backup

import android.content.Context
import android.content.Intent
import com.xenyaa.videoshot.MainActivity

/**
 * 還原成功後重啟整個 app（見 `RestoreManager` 的 KDoc：換檔後要讓 `AppContainer` 用全新的
 * Room 實例打開新檔，不嘗試在執行中置換既有實例）。`Runtime.getRuntime().exit(0)` 保證
 * 舊的 process 徹底結束，不會有殘留的協程／ViewModel 還握著已經關閉的舊 `LibraryDatabase`。
 */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
```

- [ ] **Step 2: 寫 `RestoreViewModel`（設計筆記＋失敗的測試）**

**設計筆記**：`RestoreManager` 的建構子需要真的 `LibraryDatabase`／`CacheRepo`（Task 11），
如果 `RestoreViewModel` 直接依賴具體的 `RestoreManager` 型別，它的單元測試就要整套組出一個真的
資料庫殼才能跑，而這個 ViewModel 真正要測的只是「Loading／Picking／Confirming／Restoring／
Failed 之間怎麼轉換」這個狀態機邏輯。所以 `RestoreViewModel` 改成收兩個函式
（`listBackups`／`restore`）而不是整個 `RestoreManager`——**跟 `BackupManager` 故意不依賴具體
`BackupSnapshotter`、只收一個 `snapshotTo` 函式是同一個理由**（Task 9）。

```kotlin
package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.backup.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed interface RestoreStep {
    data object Loading : RestoreStep
    data class Picking(val backups: List<RemoteBackup>) : RestoreStep
    data class Confirming(val backup: RemoteBackup) : RestoreStep
    data object Restoring : RestoreStep
    data class Failed(val reason: String) : RestoreStep
}

/**
 * 還原挑選畫面的狀態機（規格第十節的還原流程圖）。首次開啟（Task 14）與帳號頁【從 Drive 還原】
 * （Task 12 的 `BackupScreen.onRestoreClick`）共用這一個 ViewModel／畫面，不重寫兩份。
 *
 * @param localShotCount 本機目前有幾張收藏——大於 0 才要跳「會被取代」的確認框（流程圖的
 *        `本機已有資料？` 分支）；首次開啟時這裡永遠是 0，天然跳過確認框。
 * @param onRestartApp 還原成功後呼叫——通常是 [com.xenyaa.videoshot.backup.restartApp]。
 */
class RestoreViewModel(
    private val listBackups: suspend () -> List<RemoteBackup>,
    private val restore: suspend (RemoteBackup) -> RestoreResult,
    private val localShotCount: suspend () -> Int,
    private val onRestartApp: () -> Unit,
) : ViewModel() {

    private val _step = MutableStateFlow<RestoreStep>(RestoreStep.Loading)
    val step: StateFlow<RestoreStep> = _step.asStateFlow()

    init { load() }

    fun load() {
        _step.value = RestoreStep.Loading
        viewModelScope.launch {
            try {
                _step.value = RestoreStep.Picking(listBackups())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _step.value = RestoreStep.Failed("讀取備份清單失敗，請確認網路後再試一次")
            }
        }
    }

    fun pick(backup: RemoteBackup) = viewModelScope.launch {
        if (localShotCount() > 0) {
            _step.value = RestoreStep.Confirming(backup)
        } else {
            doRestore(backup)
        }
    }

    fun confirmRestore() {
        val current = _step.value as? RestoreStep.Confirming ?: return
        viewModelScope.launch { doRestore(current.backup) }
    }

    fun dismissConfirm() { if (_step.value is RestoreStep.Confirming) load() }

    private suspend fun doRestore(backup: RemoteBackup) {
        _step.value = RestoreStep.Restoring
        when (val result = restore(backup)) {
            is RestoreResult.Success -> onRestartApp()
            is RestoreResult.Failure -> _step.value = RestoreStep.Failed(result.reason)
        }
    }
}
```

現在可以寫真正的測試：

```kotlin
package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.backup.RestoreResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreViewModelTest {

    private fun backup(id: String) = RemoteBackup(id, "library-$id.db.gz", 100, 1024, 1, 5, "裝置", "sha")

    private fun vm(
        backups: List<RemoteBackup> = listOf(backup("a")),
        localShotCount: Int = 0,
        restoreResult: RestoreResult = RestoreResult.Success,
        onRestartApp: () -> Unit = {},
    ) = RestoreViewModel(
        listBackups = { backups },
        restore = { restoreResult },
        localShotCount = { localShotCount },
        onRestartApp = onRestartApp,
    )

    @Test
    fun 一開始是Loading_讀完清單後變成Picking() = runTest {
        val model = vm(backups = listOf(backup("a"), backup("b")))
        advanceUntilIdle()
        val step = model.step.value
        assertTrue(step is RestoreStep.Picking)
        assertEquals(2, (step as RestoreStep.Picking).backups.size)
    }

    @Test
    fun 本機沒有資料時選了就直接還原_不跳確認框() = runTest {
        var restarted = false
        val model = vm(localShotCount = 0, restoreResult = RestoreResult.Success, onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertTrue(restarted)
    }

    @Test
    fun 本機有資料時選了先進Confirming() = runTest {
        val model = vm(localShotCount = 3)
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertTrue(model.step.value is RestoreStep.Confirming)
    }

    @Test
    fun 確認後才真的還原() = runTest {
        var restarted = false
        val model = vm(localShotCount = 3, onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        model.confirmRestore()
        advanceUntilIdle()
        assertTrue(restarted)
    }

    @Test
    fun 取消確認框回到Picking() = runTest {
        val model = vm(localShotCount = 3)
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        model.dismissConfirm()
        advanceUntilIdle()
        assertTrue(model.step.value is RestoreStep.Picking)
    }

    @Test
    fun 還原失敗顯示原因_不呼叫重啟() = runTest {
        var restarted = false
        val model = vm(localShotCount = 0, restoreResult = RestoreResult.Failure("雜湊不符"), onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertEquals(RestoreStep.Failed("雜湊不符"), model.step.value)
        assertTrue(!restarted)
    }
}
```

- [ ] **Step 3: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.RestoreViewModelTest"`
Expected: PASS（6 個測試）

- [ ] **Step 4: 寫 `RestoreScreen.kt`**

```kotlin
package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 還原挑選畫面（規格第十節流程圖）。首次開啟（Task 14）與帳號頁【從 Drive 還原】共用。 */
@Composable
fun RestoreScreen(
    step: RestoreStep,
    onBack: () -> Unit,
    onPick: (RemoteBackup) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AccountSettingHeader("從 Google Drive 還原", onBack)
        when (step) {
            is RestoreStep.Loading, is RestoreStep.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    CircularProgressIndicator()
                    Text(if (step is RestoreStep.Restoring) "正在還原…" else "正在讀取備份清單…", color = AppTheme.colors.textDim)
                }
            }
            is RestoreStep.Picking -> if (step.backups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(AppTheme.spacing.s5), contentAlignment = Alignment.Center) {
                    Text("Drive 上還沒有任何備份", color = AppTheme.colors.textDim)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().padding(AppTheme.spacing.s4)) {
                    items(step.backups, key = { it.id }) { backup -> BackupRow(backup, onClick = { onPick(backup) }) }
                }
            }
            is RestoreStep.Confirming -> ConfirmRestoreDialog(step.backup, onConfirm, onDismissConfirm)
            is RestoreStep.Failed -> Box(Modifier.fillMaxSize().padding(AppTheme.spacing.s5), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    Text(step.reason, color = AppTheme.colors.danger)
                    TextButton(onClick = onRetry) { Text("重試") }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(backup: RemoteBackup, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.s1).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(AppTheme.spacing.s3)) {
            val date = Instant.ofEpochSecond(backup.createdAtEpochSec).atZone(ZoneId.systemDefault()).format(BACKUP_DATE_FORMAT)
            Text(date, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
            Text(
                "${backup.shotCount} 張 · ${backup.deviceName} · ${formatBytes(backup.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun ConfirmRestoreDialog(backup: RemoteBackup, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確定要還原這份備份？") },
        text = { Text("本機目前的收藏會被取代，未完成的取圖草稿也會捨棄。這個動作不能復原。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("還原", color = AppTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **Step 5: 寫 `RestoreScreenTest.kt`**

```kotlin
package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RestoreScreenTest {

    @get:Rule val compose = createComposeRule()
    private val backup = RemoteBackup("a", "library-a.db.gz", 1_700_000_000L, 2048, 1, 42, "Pixel", "sha")

    private fun setContent(
        step: RestoreStep,
        onPick: (RemoteBackup) -> Unit = {},
        onConfirm: () -> Unit = {},
        onDismissConfirm: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme { RestoreScreen(step, onBack = {}, onPick, onConfirm, onDismissConfirm, onRetry) }
        }
    }

    @Test
    fun Picking時列出備份張數與裝置名稱() {
        setContent(RestoreStep.Picking(listOf(backup)))
        compose.onNodeWithText("42 張 · Pixel · 2.0 KB").assertIsDisplayed()
    }

    @Test
    fun 點一份備份會呼叫onPick() {
        var picked: RemoteBackup? = null
        setContent(RestoreStep.Picking(listOf(backup)), onPick = { picked = it })
        compose.onNodeWithText("42 張 · Pixel · 2.0 KB").performClick()
        assert(picked == backup)
    }

    @Test
    fun Confirming顯示破壞性提示_按還原呼叫onConfirm() {
        var confirmed = false
        setContent(RestoreStep.Confirming(backup), onConfirm = { confirmed = true })
        compose.onNodeWithText("本機目前的收藏會被取代，未完成的取圖草稿也會捨棄。這個動作不能復原。").assertIsDisplayed()
        compose.onNodeWithText("還原").performClick()
        assert(confirmed)
    }

    @Test
    fun Failed顯示原因與重試按鈕() {
        var retried = false
        setContent(RestoreStep.Failed("雜湊不符"), onRetry = { retried = true })
        compose.onNodeWithText("雜湊不符").assertIsDisplayed()
        compose.onNodeWithText("重試").performClick()
        assert(retried)
    }
}
```

（`formatBytes(2048)` 要輸出 `"2.0 KB"` 這個假設要對照 `core/format` 既有的 `formatBytes` 實作調整——
如果格式不同（例如沒有小數位或單位字串不同），把測試裡的期望字串換成跟現有 `formatBytes`
單元測試一致的格式，不要改 `RestoreScreen.kt` 去湊測試。）

- [ ] **Step 6: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.RestoreScreenTest"`
Expected: PASS（4 個測試，視上面 `formatBytes` 的實際輸出調整字串後）

- [ ] **Step 7: `NavState.kt` 加 `Dest.RestoreFlow`**

在 `sealed interface Dest` 裡加：

```kotlin
    /**
     * 還原挑選流程（規格第十節）。**沒有底部導覽**——跟 [Lightbox]／[BatchEdit] 一樣是
     * 全螢幕、有終點的流程；還原成功會整個重啟 app，不會走到「回上一層」這條路。
     */
    data object RestoreFlow : Dest
```

`NavCodec.encode` 的 `when (dest)` 加一支：

```kotlin
                    is Dest.RestoreFlow -> "V"
```

`NavCodec.decode` 的 `when` 加一支（跟 `item == "R"` 同一組簡單字面比對，放在它旁邊）：

```kotlin
                    item == "V" -> Dest.RestoreFlow
```

在既有的 `NavStateTest.kt` 加一個編解碼案例（找到既有測試裡驗證 `Dest.BatchEdit` 或
`Dest.Lightbox` 編解碼往返的案例，抄同樣的寫法）：

```kotlin
    @Test
    fun RestoreFlow編解碼往返() {
        val nav = NavState().push(Dest.RestoreFlow)
        val decoded = NavCodec.decode(NavCodec.encode(nav))
        assertEquals(Dest.RestoreFlow, decoded.current)
    }
```

- [ ] **Step 8: `AppRootDeps.kt` 補 `restoreManager`**

```kotlin
    val restoreManager: com.xenyaa.videoshot.backup.RestoreManager
```

（正式寫檔時把型別改成頂部 import 的短名 `RestoreManager`。）

- [ ] **Step 9: `AppContainer.kt` 組出 `restoreManager`**

在 `backupManager` 那個 `by lazy` 之後加：

```kotlin
    override val restoreManager: com.xenyaa.videoshot.backup.RestoreManager by lazy {
        com.xenyaa.videoshot.backup.RestoreManager(
            store = backupStore,
            libraryDb = libraryDb,
            libraryDbFile = File(appContext.filesDir, "library.db"),
            cacheRepo = cacheRepo,
            draftsDir = File(appContext.filesDir, "drafts"),
            workDir = File(appContext.filesDir, "restore_work"),
            io = Dispatchers.IO,
        )
    }
```

- [ ] **Step 10: `AppRoot.kt` 接上 `Dest.RestoreFlow`**

`BackupScreen` 的 `onRestoreClick` 改成：

```kotlin
                                onRestoreClick = { nav = nav.push(com.xenyaa.videoshot.ui.shell.Dest.RestoreFlow) },
```

在 `Tab.ACCOUNT` 的 `when (val dest = nav.current)` 加一支（跟 `is Dest.AccountSetting` 平行）：

```kotlin
                        is Dest.RestoreFlow -> {
                            val restoreVm: RestoreViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T = RestoreViewModel(
                                        listBackups = { container.restoreManager.listBackups() },
                                        restore = { backup -> container.restoreManager.restore(backup) },
                                        localShotCount = {
                                            container.libraryRepo.accountStats(monthOf(LocalDate.now().toString())).totalShots
                                        },
                                        onRestartApp = { com.xenyaa.videoshot.backup.restartApp(context) },
                                    ) as T
                                },
                                key = "restore",
                            )
                            val restoreStep by restoreVm.step.collectAsStateWithLifecycle()
                            RestoreScreen(
                                step = restoreStep,
                                onBack = { nav = nav.pop() ?: nav },
                                onPick = restoreVm::pick,
                                onConfirm = restoreVm::confirmRestore,
                                onDismissConfirm = restoreVm::dismissConfirm,
                                onRetry = restoreVm::load,
                            )
                        }
```

`Dest.RestoreFlow` 跟 `Dest.BatchEdit`／`Dest.Lightbox` 一樣沒有底部導覽——檢查 `AppShell`（或
`AppRoot` 判斷「這個 `Dest` 要不要畫底部導覽」的那段邏輯，通常是一個 `when (dest) { is
Dest.Lightbox, is Dest.BatchEdit -> false; else -> true }` 之類的判斷）加上
`is Dest.RestoreFlow -> false` 這個分支。

- [ ] **Step 11: 執行全套帳號相關測試確認通過**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.account.*" --tests "com.xenyaa.videoshot.ui.shell.NavStateTest"
```
Expected: 全部通過

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/backup/RestartApp.kt \
  app/src/main/java/com/xenyaa/videoshot/ui/account/RestoreViewModel.kt \
  app/src/main/java/com/xenyaa/videoshot/ui/account/RestoreScreen.kt \
  app/src/main/java/com/xenyaa/videoshot/ui/shell/ \
  app/src/main/java/com/xenyaa/videoshot/di/AppContainer.kt \
  app/src/test/java/com/xenyaa/videoshot/ui/account/RestoreViewModelTest.kt \
  app/src/test/java/com/xenyaa/videoshot/ui/account/RestoreScreenTest.kt \
  app/src/test/java/com/xenyaa/videoshot/ui/shell/NavStateTest.kt
git commit -m "feat(帳號): 還原挑選畫面與確認框，帳號頁【從 Drive 還原】接上真的流程"
```

---

## Task 14: 首次開啟的選擇畫面

**Files:**
- Modify: `app/src/main/java/com/xenyaa/videoshot/data/settings/ShellSettings.kt`（補上 `restoreDecisionMade`／`markRestoreDecisionMade`——`AppRoot` 只認得這個窄介面，見它的 KDoc）
- Modify: `app/src/main/java/com/xenyaa/videoshot/data/settings/AppSettings.kt`（Task 8 寫的那兩個成員補上 `override`）
- Create: `app/src/main/java/com/xenyaa/videoshot/ui/onboarding/FirstRunChooserScreen.kt`
- Modify: `app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRoot.kt`（最外層加閘門）
- Test: `app/src/test/java/com/xenyaa/videoshot/ui/onboarding/FirstRunChooserScreenTest.kt`

**Interfaces:**
- Consumes: `ShellSettings.restoreDecisionMade`（本 Task 新增到介面）、`RestoreViewModel`／`RestoreScreen`（Task 13，原樣重用，不重寫）。
- Produces: `FirstRunChooserScreen(onRestoreClick, onStartFreshClick)`。`AppRoot` 是唯一的呼叫端。

- [ ] **Step 1: 修改 `ShellSettings.kt`**

在 `folderSort`／`setFolderSort` 之後加：

```kotlin
    /**
     * 全新安裝的第一個畫面（【從 Google Drive 還原】／【全新開始】）有沒有被回答過
     * （規格第十節「還原」入口）。`AppRoot` 靠它決定要不要在最外層擋住整個 app、
     * 先跳出 [com.xenyaa.videoshot.ui.onboarding.FirstRunChooserScreen]。
     */
    val restoreDecisionMade: Flow<Boolean>
    suspend fun markRestoreDecisionMade()
```

- [ ] **Step 2: 修改 `AppSettings.kt`**

把 Task 8 加的這兩行：

```kotlin
    val restoreDecisionMade: Flow<Boolean> = store.data.map { it[RESTORE_DECISION_MADE] ?: false }

    suspend fun markRestoreDecisionMade() {
```

改成：

```kotlin
    override val restoreDecisionMade: Flow<Boolean> = store.data.map { it[RESTORE_DECISION_MADE] ?: false }

    override suspend fun markRestoreDecisionMade() {
```

- [ ] **Step 3: 執行確認失敗**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: 編譯失敗（`FirstRunChooserScreen` 還不存在，`AppRoot.kt` 還沒有引用它——這一步先確認前兩個修改本身沒有語法錯誤）

- [ ] **Step 4: 寫 `FirstRunChooserScreen.kt`**

```kotlin
package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 全新安裝的第一個畫面（規格第十節「還原」入口；手冊 §一：可選【從 Google Drive 還原】或【全新開始】）。 */
@Composable
fun FirstRunChooserScreen(
    onRestoreClick: () -> Unit,
    onStartFreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("歡迎使用 videoshot", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.text)
        Text(
            "如果你之前備份過圖庫，可以直接還原；也可以先略過，之後隨時能在帳號頁連結。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRestoreClick, modifier = Modifier.fillMaxWidth()) { Text("從 Google Drive 還原") }
        TextButton(onClick = onStartFreshClick, modifier = Modifier.fillMaxWidth()) { Text("全新開始") }
    }
}
```

- [ ] **Step 5: 寫 `FirstRunChooserScreenTest.kt`**

```kotlin
package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FirstRunChooserScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 兩個按鈕都顯示() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").assertIsDisplayed()
        compose.onNodeWithText("全新開始").assertIsDisplayed()
    }

    @Test
    fun 點還原呼叫onRestoreClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = { called = true }, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").performClick()
        assert(called)
    }

    @Test
    fun 點全新開始呼叫onStartFreshClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = { called = true }) } }
        compose.onNodeWithText("全新開始").performClick()
        assert(called)
    }
}
```

- [ ] **Step 6: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.xenyaa.videoshot.ui.onboarding.FirstRunChooserScreenTest"`
Expected: PASS（3 個測試）

- [ ] **Step 7: 在 `AppRoot.kt` 最外層加閘門**

在 `fun AppRoot(container: AppRootDeps, onExitApp: () -> Unit) {` 的函式本體最前面（比
`var nav by rememberSaveable(...)` 還早）插入：

```kotlin
    // 全新安裝的第一個畫面（規格第十節「還原」入口）。用 null 當「還在讀 DataStore」的訊號——
    // 猜一個預設值再等真正的值回來會有畫面閃一下的風險，不如先留白一瞬間。
    val restoreDecisionMade by container.settings.restoreDecisionMade
        .collectAsStateWithLifecycle(initialValue = null as Boolean?)
    if (restoreDecisionMade == false) {
        FirstRunGate(container)
        return
    }
    if (restoreDecisionMade == null) return
```

在檔案裡（`AppRoot` 函式之後，同一個檔案即可）新增：

```kotlin
@Composable
private fun FirstRunGate(container: AppRootDeps) {
    var showingRestoreFlow by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showingRestoreFlow) {
        val restoreVm: RestoreViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = RestoreViewModel(
                    listBackups = { container.restoreManager.listBackups() },
                    restore = { backup -> container.restoreManager.restore(backup) },
                    localShotCount = { 0 }, // 全新安裝，本機一定沒有資料——天然跳過確認框
                    onRestartApp = { restartApp(context) },
                ) as T
            },
            key = "first-run-restore",
        )
        val step by restoreVm.step.collectAsStateWithLifecycle()
        RestoreScreen(
            step = step,
            onBack = { showingRestoreFlow = false },
            onPick = restoreVm::pick,
            onConfirm = restoreVm::confirmRestore,
            onDismissConfirm = restoreVm::dismissConfirm,
            onRetry = restoreVm::load,
        )
    } else {
        FirstRunChooserScreen(
            onRestoreClick = { showingRestoreFlow = true },
            onStartFreshClick = { scope.launch { container.settings.markRestoreDecisionMade() } },
        )
    }
}
```

補 import：

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import com.xenyaa.videoshot.backup.restartApp
import com.xenyaa.videoshot.ui.account.RestoreViewModel
import com.xenyaa.videoshot.ui.onboarding.FirstRunChooserScreen
```

（`RestoreScreen` 若前面 Task 13 已經 import 過就不用重複加。這裡沒有處理「還原成功後」的畫面
分支——因為 `onRestartApp` 一呼叫，整個 process 就會在 `restartApp()` 裡結束，這個 Composable
不會活著看到 `RestoreStep` 變成別的狀態，`Restoring` 那個畫面會是使用者看到的最後一幀，直到
新的 Activity 啟動蓋過去。）

- [ ] **Step 8: 執行確認通過**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: 全部通過（既有全部案例＋本階段新增的所有測試）

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/xenyaa/videoshot/data/settings/ShellSettings.kt \
  app/src/main/java/com/xenyaa/videoshot/data/settings/AppSettings.kt \
  app/src/main/java/com/xenyaa/videoshot/ui/onboarding/FirstRunChooserScreen.kt \
  app/src/main/java/com/xenyaa/videoshot/ui/shell/AppRoot.kt \
  app/src/test/java/com/xenyaa/videoshot/ui/onboarding/FirstRunChooserScreenTest.kt
git commit -m "feat(帳號): 全新安裝的第一個畫面——從 Drive 還原或全新開始"
```

**實機驗收**：`adb shell pm clear com.xenyaa.videoshot`（或全新安裝）後開 app，應該看到
「從 Google Drive 還原」／「全新開始」兩個按鈕，不是直接進首頁；按【全新開始】應該直接進首頁，
且**重新啟動 app 後不會再看到這個畫面**（`restoreDecisionMade` 已經是 true）。

---

## Task 15（後續、非本次合併的阻塞項）：T12.7——appDataFolder 是否跨 OAuth client 共用

這不是一個程式碼 Task，是規格第十八節的一個 ⏳ 開放項目，**依賴 Task 0 真的建好兩個 OAuth
client 之後才能實測**，跟 Task 1～14 的程式碼完成度無關，不擋這次的合併。

**問題**：同一個 Google Cloud 專案下，Android OAuth client（本階段用）跟未來 iOS／Chrome 擴充功能
會用的另一個 OAuth client，看到的 `appDataFolder` 是不是同一個？規格第十節：「日後的 iOS 版或
Chrome 擴充功能必須是同一個專案底下的另一個 OAuth client，才看得到這台手機的備份」——這句話
目前是**假設**，還沒有真的驗證過。

**驗證方式**（Task 0 完成、且這份計畫的 Task 1～12 都做完、真的上傳過至少一份備份之後）：

1. 在同一個 Google Cloud 專案下，額外建立第三個 OAuth client（型別「桌面應用程式」，
   本地測試最方便）。
2. 用 [OAuth 2.0 Playground](https://developers.google.com/oauthplayground) 或一段簡單的
   Python／curl 腳本，拿這個桌面用戶端的 client id／secret，走一次 OAuth 授權碼流程，
   scope 填 `https://www.googleapis.com/auth/drive.appdata`，同一個 Google 帳號登入。
3. 拿到 access token 後打 `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder`，
   看看回傳的檔案清單裡**有沒有** Android app 上傳過的那份 `library-*.db.gz`。
4. 兩種結果都要記錄下來：
   - **看得到**：appDataFolder 以 Google 帳號＋Cloud 專案為界，跟 OAuth client 無關——
     規格第十節「日後 iOS 版...另一個 OAuth client」這句話要**刪掉「另一個 OAuth client」
     這個限定**，改成「同一個 Cloud 專案即可看到」。
   - **看不到**：appDataFolder 確實以 OAuth client 為界——規格維持現有寫法即可，但要補一句
     「已實測確認」並附上驗證日期，同時第十八節那一列的 ⏳ 標記要解除。
5. 不管哪個結果，都要把 `docs/superpowers/specs/2026-09-10-videoshot-app-design.md` 第十節與
   第十八節同步更新（CLAUDE.md「規格永遠是現況」的維護規則），並在 CLAUDE.md 的階段 12 進度段落
   補上這次的實測結論。

---

## 整體驗收檢查清單

跟驗收操作手冊 §一（備份與還原）、§八（帳號頁）逐條對照：

| 手冊條目 | 對應 Task |
|---|---|
| 不連結 Google 也能完整使用 | Task 12（hero／BackupScreen 都有未連結分支） |
| 手動備份完成後顯示「上次備份：剛剛」與檔案大小 | Task 2（`lastBackupLabel`）＋ Task 12 |
| 自動備份：隔天不開 app 也會更新「上次備份」 | Task 10（WorkManager 每日排程） |
| 全新安裝的第一個畫面：還原／全新開始，清單顯示日期・張數・來源裝置 | Task 13＋14 |
| 還原後立刻能用 | Task 11（換檔後圖資／標籤／資料夾／手動補圖都在，縮圖回填留給階段 13） |
| 覆蓋前說清楚（本機 N 張會被取代） | Task 13（`RestoreStep.Confirming`） |
| 還原失敗不會壞掉 | Task 11（SHA-256／integrity_check／TOO_NEW 三道驗證都先於換檔） |
| 中斷連結在最底、破壞性樣式 | Task 12（`BackupScreen` 的確認框） |
| hero 顯示連結帳號的名稱／Email／頭像字母 | Task 12（顯示名稱與 Email；頭像字母沿用既有 hero 圓形圖示位置，這次沒有另外實作字母縮寫——若手冊在驗收時發現這一點被跳過，補一個小 Task 把 `linkedAccount.displayName.first()` 畫進那個圓形圖示即可） |

**已知不在本計畫範圍內**（留給後續階段，跟階段 11 的「刻意留到之後」清單同一種性質）：

- 縮圖回填（規格第十一節）——階段 13。
- T12.7 的實測——見上面 Task 15，依賴 Task 0，不擋合併。
- WAL 模式下 `libraryDb.close()` 之後、`renameTo` 之前那個極短的時間窗——如果系統在這個窗口
  殺掉 process，`library.db` 會維持原狀（rename 還沒發生），下次開啟時 `RestoreManager` 的
  暫存檔已經在 `workDir` 但沒被用到，不會自動清掉；可以之後加一個「app 啟動時清空
  `restore_work`／`backup_work` 目錄」的小任務，本計畫先不做，因為它不影響資料正確性，只是
  留下幾 MB 暫存檔。
