package com.xenyaa.videoshot.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xenyaa.videoshot.data.cache.dao.DraftDao
import com.xenyaa.videoshot.data.cache.dao.FacetRecentDao
import com.xenyaa.videoshot.data.cache.dao.ThumbStateDao
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.FacetRecentEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity

/**
 * **不備份**的那一個 DB：這台裝置自己的狀態。
 * 還原備份時整個清空重建（回填作業會重新掃一遍缺哪些圖）。
 */
@Database(
    entities = [ThumbStateEntity::class, DraftEntity::class, FacetRecentEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun thumbStateDao(): ThumbStateDao
    abstract fun draftDao(): DraftDao
    abstract fun facetRecentDao(): FacetRecentDao
}
