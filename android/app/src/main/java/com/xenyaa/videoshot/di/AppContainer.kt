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
import com.xenyaa.videoshot.thumbs.FileThumbs
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.youtube.OkHttpYoutube
import com.xenyaa.videoshot.youtube.Youtube
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
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
}
