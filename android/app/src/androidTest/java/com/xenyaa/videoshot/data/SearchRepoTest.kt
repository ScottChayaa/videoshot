package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 查詢頁（階段 10）的 repo 層整合測試。用真的 in-memory Room DB——這批查詢有 UNION ALL、
 * CTE、row-value 比較，光靠 DAO 的假實作測不出 SQL 本身寫對了沒。
 */
@RunWith(AndroidJUnit4::class)
class SearchRepoTest {

    private val libraryDb = inMemoryLibraryDb()
    private val repo = RoomLibraryRepo(libraryDb, Dispatchers.Default)

    @After fun tearDown() { libraryDb.close() }

    private suspend fun seedShot(videoId: String, atSec: Double, eventDate: String, place: String?, tagNames: List<String> = emptyList()): Long {
        libraryDb.videoDao().upsert(VideoEntity(videoId, "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L))
        val id = libraryDb.shotDao().insert(
            com.xenyaa.videoshot.data.library.entity.ShotEntity(
                id = 0, videoId = videoId, atSec = atSec, source = "storyboard", frameIndex = atSec.toInt(),
                sbLevel = 3, eventDate = eventDate, place = place, description = null,
                aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 0L,
            )
        )
        for (name in tagNames) {
            val tagId = libraryDb.tagDao().byName(name)?.id
                ?: libraryDb.tagDao().insert(TagEntity(0, name, "topic", "[]"))
            libraryDb.tagDao().link(ShotTagEntity(id, tagId, "human"))
        }
        return id
    }

    @Test
    fun searchFacets_地點與標籤混排依張數排序() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭", tagNames = listOf("露營"))
        seedShot("v1", 10.0, "2026-03-02", place = "宜蘭", tagNames = listOf("露營"))
        seedShot("v1", 20.0, "2026-02-01", place = "台北", tagNames = listOf("美食"))

        val facets = repo.searchFacets(upToMonth = null, limit = 10)

        assertEquals(listOf("宜蘭", "露營"), facets.filter { it.count == 2 }.map { it.name }.sorted())
        assertEquals(1, facets.count { it.name == "台北" })
    }

    @Test
    fun searchFacets_時間篩選只看該月以前() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-01-01", place = "台北")

        val facets = repo.searchFacets(upToMonth = "2026-01", limit = 10)

        assertEquals(listOf("台北"), facets.map { it.name })
    }

    @Test
    fun queryVocabulary_地點與標籤含別名() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        libraryDb.tagDao().insert(TagEntity(0, "小橘", "pet", """["我家的貓","橘貓"]"""))

        val vocab = repo.queryVocabulary()

        assertEquals(listOf("宜蘭"), vocab.places)
        assertEquals("小橘", vocab.tags.single().name)
        assertEquals(listOf("我家的貓", "橘貓"), vocab.tags.single().aliases)
    }
}
