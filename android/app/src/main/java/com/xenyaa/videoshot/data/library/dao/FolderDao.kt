package com.xenyaa.videoshot.data.library.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long

    @Insert suspend fun link(link: ShotFolderEntity)

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM folder")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM shot_folder WHERE shot_id = :shotId")
    suspend fun linkCountOfShot(shotId: Long): Int

    @Query("SELECT COUNT(*) FROM folder WHERE name = :name AND parent_id IS :parentId")
    suspend fun countSameNameInLayer(parentId: Long?, name: String): Int

    @Query("SELECT parent_id FROM folder WHERE id = :id")
    suspend fun parentOf(id: Long): Long?
}
