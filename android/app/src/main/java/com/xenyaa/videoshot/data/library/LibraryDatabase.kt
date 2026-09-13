package com.xenyaa.videoshot.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xenyaa.videoshot.data.library.dao.VideoDao
import com.xenyaa.videoshot.data.library.entity.VideoEntity

/** 要備份的那一個 DB：無法重建的東西都在這裡（規格第四節）。 */
@Database(
    entities = [VideoEntity::class],
    version = LIBRARY_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
