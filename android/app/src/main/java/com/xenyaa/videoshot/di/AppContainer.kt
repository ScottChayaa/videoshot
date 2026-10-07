package com.xenyaa.videoshot.di

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.R
import com.xenyaa.videoshot.backup.BackupManager
import com.xenyaa.videoshot.backup.BackupSnapshotter
import com.xenyaa.videoshot.backup.BackupStore
import com.xenyaa.videoshot.backup.DriveBackupStore
import com.xenyaa.videoshot.backup.GisGoogleAuth
import com.xenyaa.videoshot.backup.GoogleAuth
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.backup.RestoreManager
import com.xenyaa.videoshot.backup.RestoreResult
import com.xenyaa.videoshot.backfill.BackfillManager
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.settings.AppSettings
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.draft.DraftCodec
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.thumbs.FileThumbs
import com.xenyaa.videoshot.thumbs.LocalSheetCropper
import com.xenyaa.videoshot.thumbs.SheetHarvester
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.thumb.CoverFetcher
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.CropOutcome
import com.xenyaa.videoshot.wizard.Haptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.SystemHaptics
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.EmptyFrameSource
import com.xenyaa.videoshot.wizard.frames.FrameSource
import com.xenyaa.videoshot.wizard.frames.StoryboardFrameSource
import com.xenyaa.videoshot.wizard.frames.framePlanOf
import com.xenyaa.videoshot.youtube.OkHttpYoutube
import com.xenyaa.videoshot.youtube.Youtube
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.capture.WebViewCapture
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.WebViewPlayer
import com.xenyaa.videoshot.query.OkHttpGeminiClient
import com.xenyaa.videoshot.query.QueryResolver
import com.xenyaa.videoshot.ui.account.AccountDeps
import com.xenyaa.videoshot.ui.shell.AppRootDeps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate

/**
 * 手動注入的組裝點（階段 2 決定不用 Hilt）。
 * 兩個 DB 都建在 filesDir —— 不可用 cacheDir，系統或使用者清快取會把圖庫清掉（規格第四節）。
 *
 * 實作 [AppRootDeps]——`AppRoot` 認得的是那個窄介面，不是這整個類別；見它的 KDoc。
 */
class AppContainer(context: Context) : AppRootDeps {

    private val appContext = context.applicationContext

    val libraryDb: LibraryDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            LibraryDatabase::class.java,
            File(appContext.filesDir, "library.db").path,
        )
            .setDriver(BundledSQLiteDriver())
            .addCallback(LibrarySchemaCallback)
            .addMigrations(*LIBRARY_MIGRATIONS)
            .build()
    }

    val cacheDb: CacheDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            CacheDatabase::class.java,
            File(appContext.filesDir, "cache.db").path,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    override val settings: AppSettings by lazy { AppSettings(appContext) }

    override val queryResolver: QueryResolver by lazy {
        QueryResolver(
            gemini = OkHttpGeminiClient(httpClient, Dispatchers.IO),
            geminiKey = { settings.geminiKey.first() },
            // 每次查詢都重新取得——理由見 QueryResolver 建構子參數的 KDoc
            vocabulary = { libraryRepo.queryVocabulary() },
        )
    }

    override val libraryRepo: LibraryRepo by lazy {
        RoomLibraryRepo(libraryDb, Dispatchers.IO, onChanged = { settings.markChanged() })
    }

    val cacheRepo: CacheRepo by lazy { RoomCacheRepo(cacheDb, Dispatchers.IO) }

    override val shotDeleter: ShotDeleter by lazy { ShotDeleter(libraryRepo, thumbs, cacheRepo, Dispatchers.IO) }

    /** 整個 app 共用一個 OkHttpClient —— 它自帶連線池與執行緒池，每次 new 一個會把資源用光。 */
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val youtube: Youtube by lazy { OkHttpYoutube(httpClient, Dispatchers.IO) }

    val thumbs: Thumbs by lazy {
        FileThumbs(
            root = File(appContext.filesDir, "thumbs"),
            io = Dispatchers.IO,
            loadManualImage = { libraryRepo.shotImage(it) },
            isLost = { key -> cacheRepo.thumbState(key.videoId, key.level, key.frameIndex)?.state == "lost" },
        )
    }

    /**
     * 記憶體 LRU 的容量依裝置可用記憶體估：一張 320×180 的 ARGB_8888 約 230 KB，
     * 取可用堆疊的 1/8 換算張數，夾在 60～240 之間。
     * 固定寫死一個數字的話，低階機會 OOM、高階機又白白重複解碼。
     */
    override val thumbLoader: ThumbLoader by lazy {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val budgetBytes = am.memoryClass.toLong() * 1024 * 1024 / 8
        val entries = (budgetBytes / (320L * 180 * 4)).toInt().coerceIn(60, 240)
        ThumbLoader(
            thumbs = thumbs,
            decodeFile = { file -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() } },
            decodeBytes = { bytes ->
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            },
            cover = coverFetcher::invoke,
            maxEntries = entries,
        )
    }

    private val coverFetcher: CoverFetcher by lazy {
        CoverFetcher(
            client = httpClient,
            dir = File(appContext.cacheDir, "covers"),
            io = Dispatchers.IO,
            decode = { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() },
        )
    }

    val sheetHarvester: SheetHarvester by lazy { SheetHarvester(youtube, thumbs, Dispatchers.Default) }

    val backfillManager: BackfillManager by lazy {
        BackfillManager(
            libraryRepo = libraryRepo,
            cacheRepo = cacheRepo,
            shotDeleter = shotDeleter,
            thumbs = thumbs,
            fetchWatchPage = { videoId -> youtube.watchPage(videoId) },
            harvest = { videoId, spec, level, frameIndexes ->
                sheetHarvester.harvest(videoId, spec, level, frameIndexes) {
                    youtube.watchPage(videoId).storyboardSpec?.let { Storyboard.parse(it) }
                }
            },
            harvestRelocated = { videoId, spec, targetLevel, sourceLevel, frames ->
                sheetHarvester.harvestRelocated(videoId, spec, targetLevel, sourceLevel, frames) {
                    youtube.watchPage(videoId).storyboardSpec?.let { Storyboard.parse(it) }
                }
            },
            io = Dispatchers.IO,
            nowSec = { System.currentTimeMillis() / 1000 },
        )
    }

    val localSheetCropper: LocalSheetCropper by lazy {
        LocalSheetCropper(thumbs, Dispatchers.IO, Dispatchers.Default)
    }

    /** 精靈用得到的資料存取。窄介面的理由見 `WizardData` 的註解。 */
    override val wizardData: WizardData by lazy {
        object : WizardData {
            override suspend fun watchPage(videoId: String) = youtube.watchPage(videoId)
            override suspend fun recentVideos(limit: Int) = libraryRepo.recentVideos(limit)
            // 直接用 repo 的查詢 —— 它把層級寫進 SQL。自己撈 shotsOfVideo 再 mapNotNull
            // 會漏掉 sbLevel 這個條件，把別的層級的格號當成這一層的鎖定格
            override suspend fun takenFrameIndexes(videoId: String, level: Int) =
                libraryRepo.takenFrameIndexes(videoId, level)
            override suspend fun distinctPlaces() = libraryRepo.distinctPlaces()
            override suspend fun allTagNames() = libraryRepo.allTagNames()

            override suspend fun cropThumbs(
                videoId: String,
                sbSpec: String?,
                frameIndexes: List<Int>,
                onProgress: (Int, Int) -> Unit,
            ): CropOutcome {
                // 純解析，**不連網** —— sheet 已經在本機，這一步不該再等 YouTube。
                // pickLevel 對同一份 spec 是決定性的，所以挑到的層級與第二步寫 sheet 時完全一致
                val picked = sbSpec?.let { Storyboard.parse(it) }?.let { Storyboard.pickLevel(it) }
                    ?: return CropOutcome(emptyList(), frameIndexes)
                val result = localSheetCropper.crop(
                    videoId = videoId,
                    level = picked,
                    sheetsDir = sheetsDirFor(videoId, picked.level),
                    frameIndexes = frameIndexes,
                    onProgress = onProgress,
                )
                return CropOutcome(result.written.map { it.frameIndex }, result.missing)
            }

            override suspend fun commit(video: VideoEntity, picks: List<NewShot>) =
                libraryRepo.commitPicks(video, picks)

            override suspend fun markThumbStates(
                videoId: String,
                level: Int,
                ok: List<Int>,
                missing: List<Int>,
            ) {
                val now = System.currentTimeMillis() / 1000
                cacheRepo.putThumbStates(
                    ok.map {
                        ThumbStateEntity(videoId, level, it, "ok", 0, 0, null)
                    } + missing.map {
                        // 排進回填：現在就可以重試（規格第十一節的退避從第一次失敗開始算）
                        ThumbStateEntity(videoId, level, it, "missing", 0, now, null)
                    }
                )
            }

            /**
             * `draft` 表把 `video_id` 與 `step` 拉成獨立欄位（規格第四節），所以這裡要解一次 JSON
             * 才填得出來。**介面收的仍然是字串** —— 讓接線層認得 payload 的欄位，
             * 就等於讓草稿格式漏到 `:core` 之外。
             */
            override suspend fun saveDraft(json: String) {
                val payload = DraftCodec.decode(json) ?: return
                cacheRepo.saveDraft(
                    DraftEntity(
                        videoId = payload.videoId,
                        step = payload.step,
                        payload = json,
                        updatedAt = System.currentTimeMillis() / 1000,
                    )
                )
            }

            override suspend fun currentDraft(): String? = cacheRepo.currentDraft()?.payload

            /** 清草稿**連目錄一起刪** —— 手動圖在完成前都在那裡（規格第五節）。 */
            override suspend fun clearDraft(videoId: String) {
                cacheRepo.clearDraft()
                withContext(Dispatchers.IO) {
                    File(appContext.filesDir, "drafts/$videoId").deleteRecursively()
                }
            }
        }
    }

    override val accountDeps: AccountDeps by lazy {
        object : AccountDeps {
            override val filterStrength = settings.filterStrength
            override suspend fun setFilterStrength(value: FilterStrength) = settings.setFilterStrength(value)

            override val aiRangeBeforeSec = settings.aiRangeBeforeSec
            override val aiRangeAfterSec = settings.aiRangeAfterSec
            override suspend fun setAiRange(beforeSec: Int, afterSec: Int) =
                settings.setAiRange(beforeSec, afterSec)

            override val geminiKeySet = settings.geminiKey.map { it != null }
            override suspend fun setGeminiKey(plain: String) = settings.setGeminiKey(plain)
            override suspend fun clearGeminiKey() = settings.clearGeminiKey()

            override suspend fun stats(thisMonth: String) = libraryRepo.accountStats(thisMonth)
            override suspend fun tags() = libraryRepo.allTagsWithUsage()
            override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>) =
                libraryRepo.renameTag(id, name, kind, aliases)
            override suspend fun deleteTag(id: Long) = libraryRepo.deleteTag(id)

            override val thumbColumns = settings.thumbColumns
            override suspend fun setThumbColumns(value: Int) = settings.setThumbColumns(value)

            /**
             * `thumbs/` 遞迴掃檔案大小 ＋ `library.db` 的檔案大小。**不含 `cache.db`／草稿**——
             * 那些是可重建的快取（AGENTS.md「無法重建的在 library.db，DB 外面的都能重建」），
             * 使用者關心的是「圖庫本身佔多少空間」，不是暫存檔。
             */
            override suspend fun storageUsageBytes(): Long = withContext(Dispatchers.IO) {
                val thumbsBytes = File(appContext.filesDir, "thumbs").walkTopDown()
                    .filter { it.isFile }.sumOf { it.length() }
                val dbBytes = File(appContext.filesDir, "library.db").let { if (it.exists()) it.length() else 0L }
                thumbsBytes + dbBytes
            }

            override val linkedAccount = settings.linkedAccount
            override suspend fun beginLink(activity: Activity): LinkOutcome {
                val outcome = googleAuth.beginLink(activity)
                if (outcome is LinkOutcome.Linked) {
                    settings.setLinkedAccount(outcome.account)
                }
                return outcome
            }
            override suspend fun finishLink(data: Intent): LinkedGoogleAccount {
                val account = googleAuth.finishLink(data)
                settings.setLinkedAccount(account)
                return account
            }
            /**
             * 沒有連結時是無害的 no-op——沒有帳號可斷（`settings.linkedAccount` 是這個行程／裝置
             * 目前真正的連結狀態，不能靠 `GisGoogleAuth.pendingAccount` 那種只在 beginLink／
             * finishLink 同一次互動內有效的記憶體欄位，理由見 `GoogleAuth.unlink` 的 KDoc）。
             */
            override suspend fun unlink() {
                val email = settings.linkedAccount.first()?.email ?: return
                googleAuth.unlink(email)
                settings.clearLinkedAccount()
            }

            override val lastBackupAtEpochSec = settings.lastBackupAt
            override suspend fun backupNow() = backupManager.runIfDue(force = true)

            override suspend fun backfillProgress() = backfillManager.progress()
            /**
             * 重設成 missing 之後**一定要再排一次回填作業**：開機時排的那個 work 在使用者按到
             * 這顆按鈕時通常早就跑完進入終結狀態了，不重排的話這些剛變回 missing 的格子要等到
             * 下次開 app 才有人處理（全分支最終審查 Important 發現）。用預設的 allowMobileData
             * ＝false／replace＝false——【稍後重試】沒有承諾要用行動網路，也不該蓋掉已經在排隊
             * 的工作。
             */
            override suspend fun retryLostThumbs() {
                backfillManager.retryLost()
                com.xenyaa.videoshot.backfill.scheduleBackfill(appContext)
            }
            override suspend fun deleteLostThumbs() = backfillManager.deleteLostShots()
            override suspend fun continueBackfillOnMobileData() {
                com.xenyaa.videoshot.backfill.scheduleBackfill(appContext, allowMobileData = true, replace = true)
            }
        }
    }

    val googleAuth: GoogleAuth by lazy {
        GisGoogleAuth(
            context = appContext,
            webClientId = appContext.getString(R.string.google_signin_web_client_id),
            io = Dispatchers.IO,
        )
    }

    val backupStore: BackupStore by lazy {
        DriveBackupStore(
            http = httpClient,
            accessToken = { googleAuth.accessToken() },
            io = Dispatchers.IO,
        )
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            snapshotTo = { dest -> BackupSnapshotter(libraryDb, Dispatchers.IO).snapshotTo(dest) },
            store = backupStore,
            workDir = File(appContext.filesDir, "backup_work"),
            io = Dispatchers.IO,
            deviceName = { android.os.Build.MODEL ?: "Android" },
            shotCount = { libraryRepo.accountStats(monthOf(LocalDate.now().toString())).totalShots },
            // 沒連結帳號就沒有備份的去處，連【立即備份】也不做（見 BackupManager.runIfDue）
            isLinked = { settings.linkedAccount.first() != null },
            lastChangedAtSec = { settings.lastChangedAt.first() },
            lastBackupAtSec = { settings.lastBackupAt.first() },
            nowSec = { System.currentTimeMillis() / 1000 },
            markBackedUp = { at -> settings.markBackedUp(at) },
        )
    }

    /** 見 `AppRootDeps.listBackups`／`restore` 的 KDoc：介面不收整個 `RestoreManager`,這裡是唯一組出它的地方。 */
    private val restoreManager: RestoreManager by lazy {
        RestoreManager(
            store = backupStore,
            libraryDb = libraryDb,
            libraryDbFile = File(appContext.filesDir, "library.db"),
            cacheRepo = cacheRepo,
            draftsDir = File(appContext.filesDir, "drafts"),
            workDir = File(appContext.filesDir, "restore_work"),
            io = Dispatchers.IO,
        )
    }

    override suspend fun listBackups(): List<RemoteBackup> = restoreManager.listBackups()

    override suspend fun restore(backup: RemoteBackup): RestoreResult = restoreManager.restore(backup)

    /**
     * 這支影片在這個層級的 sheet 目錄。**寫的人（第二步下載）與讀的人（第三步裁圖）必須同一個路徑**，
     * 不一致的話第三步一張圖都裁不出來，而且看起來只像「縮圖沒好」—— 所以這個約定不能是
     * 兩處各寫一次的字串常值。
     *
     * 層級進路徑是必要的：續做時重抓 spec 可能換到別的層級，而 `M{n}.jpg` 的檔名裡沒有層級，
     * 沿用舊檔就是拿 L2 的 sheet 去裁 L3 的座標 —— 裁出來的是別的畫面而且看起來完全正常。
     */
    private fun sheetsDirFor(videoId: String, level: Int): File =
        File(appContext.filesDir, "drafts/$videoId/sheets/L$level")

    override val haptics: Haptics by lazy { SystemHaptics(appContext) }

    /**
     * 這支影片的手動補圖存放處。與 sheet 同樣綁在草稿上：
     * `drafts/{videoId}/manual/`，精靈完成或捨棄時整個 `drafts/{videoId}/` 一起刪（規格第四節）。
     */
    override fun manualImagesFor(videoId: String): ManualImageStore =
        ManualImageStore(File(appContext.filesDir, "drafts/$videoId/manual"))

    /**
     * 播放器接上之後建對應的截圖器。
     *
     * 只有 `WebViewPlayer` 截得到圖 —— 它握著那個 WebView 的 JS 執行入口。
     * 其他實作（例如測試用的 `FakePlayer`）回 null，畫面上按【截圖】會得到「播放器還沒準備好」。
     */
    override fun captureFor(player: Player): Capture? = when (player) {
        is WebViewPlayer -> WebViewCapture(
            eval = { player.evaluate(it) },
            decode = { BitmapFactory.decodeByteArray(it, 0, it.size) },
        )
        else -> null
    }

    /**
     * 第二步的縮圖來源。解不出 storyboard 時給一個空的來源 ——
     * 空牆，但第二步仍然進得去（規格第七節降級表）。
     */
    override fun frameSourceFor(video: LoadedVideo): FrameSource {
        val (_, level) = framePlanOf(video.videoId, video.page)
        val spec = video.page.storyboardSpec?.let { Storyboard.parse(it) }
        if (level == null || spec == null) {
            // 空牆，但第二步進得去（規格第七節降級表）。plan 由 framePlanOf 算出來的那一份已經是空的
            return EmptyFrameSource(video.videoId)
        }
        return StoryboardFrameSource(
            videoId = video.videoId,
            spec = spec,
            level = level,
            sheetsDir = sheetsDirFor(video.videoId, level.level),
            youtube = youtube,
            refreshSpec = { youtube.watchPage(video.videoId).storyboardSpec?.let { Storyboard.parse(it) } },
            io = Dispatchers.IO,
            compute = Dispatchers.Default,
        )
    }
}
