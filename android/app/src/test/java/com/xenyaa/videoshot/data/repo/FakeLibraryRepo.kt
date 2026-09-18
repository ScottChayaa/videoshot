package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow

/**
 * 測試用的假 repo，每個方法都有「什麼都沒有」的預設值。
 *
 * 各測試 `open class` 覆寫自己在意的那幾個就好 —— 之前每個檔案各自實作整個介面，
 * repo 一長新方法就要同時補三處，那是編譯錯誤而不是測試失敗，讀起來像壞掉。
 */
open class FakeLibraryRepo : LibraryRepo {
    override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page<ShotRow>(emptyList(), null)
    override suspend fun monthCounts(): List<MonthCount> = emptyList()
    override suspend fun shotsOfVideo(videoId: String): List<ShotRow> = emptyList()
    override suspend fun shotById(id: Long): ShotRow? = null
    override suspend fun shotCount(upToMonth: String?): Int = 0
    override suspend fun monthFacets(month: String): List<MonthFacet> = emptyList()
    override suspend fun tagsOfShot(shotId: Long): List<String> = emptyList()
    override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> = emptyList()
    override suspend fun distinctPlaces(): List<String> = emptyList()
    override suspend fun allTagNames(): List<String> = emptyList()
    override suspend fun patchShots(ids: List<Long>, patch: ShotPatch) = Unit
    override suspend fun deleteShot(id: Long) = Unit
    override suspend fun deleteVideo(videoId: String) = Unit
    override suspend fun createFolder(parentId: Long?, name: String): Long = 0
    override suspend fun renameFolder(id: Long, name: String) = Unit
    override suspend fun deleteFolder(id: Long) = Unit
    override suspend fun folderNode(id: Long): FolderNode? = null
    override suspend fun folderTree(): List<FolderNode> = emptyList()
    override suspend fun folderCards(parentId: Long?): List<FolderCard> = emptyList()
    override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int) = FolderPage(emptyList(), null)
    override suspend fun folderShotCount(folderId: Long): Int = 0
    override suspend fun foldersOf(shotId: Long): Set<Long> = emptySet()
    override suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long) = Unit
    override suspend fun removeShotFromFolder(shotId: Long, folderId: Long) = Unit
    override suspend fun shotImage(shotId: Long): ByteArray? = null
    override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
    override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
}
