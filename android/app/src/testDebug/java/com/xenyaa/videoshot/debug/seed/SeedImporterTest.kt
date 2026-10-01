package com.xenyaa.videoshot.debug.seed

import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SeedImporterTest {
    private class Recorder : FakeLibraryRepo() {
        val deletedFolders = mutableListOf<Long>()
        val committed = mutableListOf<Pair<VideoEntity, List<NewShot>>>()
        val kindChanges = mutableMapOf<String, String>()
        val folders = mutableMapOf<Long, String>()
        val links = mutableListOf<Pair<Long, Long>>()
        private var nextShot = 100L
        private var nextFolder = 1L
        override suspend fun recentVideos(limit: Int) = listOf(RecentVideo("old1", "舊片", 0L, 3))
        override suspend fun folderTree() = listOf(FolderNode(9L, null, "舊資料夾", 1), FolderNode(10L, 9L, "子", 2))
        override suspend fun deleteFolder(id: Long) { deletedFolders += id }
        override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> {
            committed += video to picks
            return picks.map { nextShot++ }
        }
        override suspend fun allTagsWithUsage() = listOf(TagUsage(1L, "夜潛", "other", emptyList(), 1), TagUsage(2L, "龍蝦", "other", emptyList(), 1))
        override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>) { kindChanges[name] = kind }
        override suspend fun createFolder(parentId: Long?, name: String): Long = nextFolder++.also { folders[it] = name }
        override suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long) { links += shotId to folderId }
    }

    private val plan = SeedPlanner.plan(Json.decodeFromString(File("src/debug/assets/seed/mock-seed.json").readText()), 0L)

    @Test fun 先清空舊資料再寫入() = runBlocking {
        val repo = Recorder()
        val deletedVideos = mutableListOf<String>()
        val r = SeedImporter(repo) { deletedVideos += it }.import(plan)
        assertEquals(listOf("old1"), deletedVideos)
        assertEquals(listOf(9L), repo.deletedFolders) // 只刪根層，子層跟著刪
        assertEquals(13, r.shots)
        assertEquals(11, repo.committed.size)
    }

    @Test fun 標籤kind改成原型的種類() = runBlocking {
        val repo = Recorder()
        SeedImporter(repo) {}.import(plan)
        assertEquals("topic", repo.kindChanges["夜潛"])
        // 龍蝦在原型是 other，跟現值一樣就不必改
        assertEquals(null, repo.kindChanges["龍蝦"])
    }

    @Test fun 資料夾連到對應的shot() = runBlocking {
        val repo = Recorder()
        val r = SeedImporter(repo) {}.import(plan)
        assertEquals(5, r.folders)
        val caribbean = repo.folders.entries.first { it.value == "加勒比海之旅" }.key
        assertEquals(4, repo.links.count { it.second == caribbean })
    }
}
