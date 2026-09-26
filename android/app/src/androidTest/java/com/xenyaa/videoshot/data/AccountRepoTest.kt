package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepoTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private fun video(id: String) = VideoEntity(id, "片名", "頻道", "2026-02-02T10:00:00Z", 600, "public", null, 1L)

    private fun pick(eventDate: String, frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = eventDate, place = null, description = null, webp = null,
    )

    @Test
    fun 統計總張數本月新增來源影片數() = runTest {
        repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0), pick("2026-03-05", 1)))
        repo.commitPicks(video("v2"), listOf(pick("2026-03-10", 0)))

        val stats = repo.accountStats(thisMonth = "2026-03")

        assertEquals(3, stats.totalShots)
        assertEquals(2, stats.thisMonthShots)
        assertEquals(2, stats.distinctVideos)
    }

    @Test
    fun 沒有任何收藏時三格都是零() = runTest {
        val stats = repo.accountStats(thisMonth = "2026-03")
        assertEquals(0, stats.totalShots)
        assertEquals(0, stats.thisMonthShots)
        assertEquals(0, stats.distinctVideos)
    }
}
