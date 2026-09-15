package com.xenyaa.videoshot.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.settings.AppSettings
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.thumbs.FileThumbs
import com.xenyaa.videoshot.thumbs.SheetHarvester
import com.xenyaa.videoshot.thumbs.Thumbs
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
import android.graphics.BitmapFactory
import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.capture.WebViewCapture
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.WebViewPlayer
import java.io.File

/**
 * 手動注入的組裝點（階段 2 決定不用 Hilt）。
 * 兩個 DB 都建在 filesDir —— 不可用 cacheDir，系統或使用者清快取會把圖庫清掉（規格第四節）。
 */
class AppContainer(context: Context) {

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

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val libraryRepo: LibraryRepo by lazy {
        RoomLibraryRepo(libraryDb, Dispatchers.IO) { settings.markChanged() }
    }

    val cacheRepo: CacheRepo by lazy { RoomCacheRepo(cacheDb, Dispatchers.IO) }

    /** 整個 app 共用一個 OkHttpClient —— 它自帶連線池與執行緒池，每次 new 一個會把資源用光。 */
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val youtube: Youtube by lazy { OkHttpYoutube(httpClient, Dispatchers.IO) }

    val thumbs: Thumbs by lazy {
        FileThumbs(File(appContext.filesDir, "thumbs"), Dispatchers.IO) { libraryRepo.shotImage(it) }
    }

    val sheetHarvester: SheetHarvester by lazy { SheetHarvester(youtube, thumbs, Dispatchers.Default) }

    /** 精靈只用得到這三件事，所以不把整個 LibraryRepo 交出去（見 WizardData 的註解）。 */
    val wizardData: WizardData by lazy {
        object : WizardData {
            override suspend fun watchPage(videoId: String) = youtube.watchPage(videoId)
            override suspend fun recentVideos(limit: Int) = libraryRepo.recentVideos(limit)
            // 直接用 repo 的查詢 —— 它把層級寫進 SQL。自己撈 shotsOfVideo 再 mapNotNull
            // 會漏掉 sbLevel 這個條件，把別的層級的格號當成這一層的鎖定格
            override suspend fun takenFrameIndexes(videoId: String, level: Int) =
                libraryRepo.takenFrameIndexes(videoId, level)
        }
    }

    val haptics: Haptics by lazy { SystemHaptics(appContext) }

    /**
     * 這支影片的手動補圖存放處。與 sheet 同樣綁在草稿上：
     * `drafts/{videoId}/manual/`，精靈完成或捨棄時整個 `drafts/{videoId}/` 一起刪（規格第四節）。
     */
    fun manualImagesFor(videoId: String): ManualImageStore =
        ManualImageStore(File(appContext.filesDir, "drafts/$videoId/manual"))

    /**
     * 播放器接上之後建對應的截圖器。
     *
     * 只有 `WebViewPlayer` 截得到圖 —— 它握著那個 WebView 的 JS 執行入口。
     * 其他實作（例如測試用的 `FakePlayer`）回 null，畫面上按【截圖】會得到「播放器還沒準備好」。
     */
    fun captureFor(player: Player): Capture? = when (player) {
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
    fun frameSourceFor(video: LoadedVideo): FrameSource {
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
            // 加一層 L{level}：續做時重抓 spec 可能換到別的層級，而 M{n}.jpg 的檔名裡沒有層級 ——
            // 沿用舊檔就是拿 L2 的 sheet 去裁 L3 的座標，裁出來的是別的畫面而且看起來完全正常
            sheetsDir = File(appContext.filesDir, "drafts/${video.videoId}/sheets/L${level.level}"),
            youtube = youtube,
            refreshSpec = { youtube.watchPage(video.videoId).storyboardSpec?.let { Storyboard.parse(it) } },
            io = Dispatchers.IO,
            compute = Dispatchers.Default,
        )
    }
}
