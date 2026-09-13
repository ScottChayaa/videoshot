package com.xenyaa.videoshot.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xenyaa.videoshot.data.library.dao.FolderDao
import com.xenyaa.videoshot.data.library.dao.ShotDao
import com.xenyaa.videoshot.data.library.dao.TagDao
import com.xenyaa.videoshot.data.library.dao.VideoDao
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotImageEntity
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity

/** 要備份的那一個 DB：無法重建的東西都在這裡（規格第四節）。 */
@Database(
    entities = [
        VideoEntity::class,
        ShotEntity::class,
        ShotImageEntity::class,
        TagEntity::class,
        ShotTagEntity::class,
        FolderEntity::class,
        ShotFolderEntity::class,
    ],
    version = LIBRARY_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun shotDao(): ShotDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
}
