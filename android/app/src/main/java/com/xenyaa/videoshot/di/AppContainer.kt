package com.xenyaa.videoshot.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
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
            .build()
    }
}
