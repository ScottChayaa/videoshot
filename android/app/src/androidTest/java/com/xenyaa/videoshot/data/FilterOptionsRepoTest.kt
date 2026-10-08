package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 階段 17：首頁篩選抽屜的候選清單與月份選單（讀統計表）。 */
@RunWith(AndroidJUnit4::class)
class FilterOptionsRepoTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: RoomLibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private fun video(id: String) = VideoEntity(id, "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L)

    private fun pick(i: Int, date: String, place: String?, tags: List<String> = emptyList()) = NewShot(
        atSec = i.toDouble(), source = "storyboard", frameIndex = i, sbLevel = 3,
        eventDate = date, place = place, description = null, webp = null, tagNames = tags,
    )

    /** 2026-01：宜蘭×2（露營×2）；2026-02：台北×3（美食×3）；2026-03：花蓮×1（露營）。 */
    private suspend fun seed() {
        repo.commitPicks(video("v1"), listOf(
            pick(0, "2026-01-05", "宜蘭", listOf("露營")),
            pick(1, "2026-01-06", "宜蘭", listOf("露營")),
            pick(2, "2026-02-01", "台北", listOf("美食")),
            pick(3, "2026-02-02", "台北", listOf("美食")),
            pick(4, "2026-02-03", "台北", listOf("美食")),
            pick(5, "2026-03-01", "花蓮", listOf("露營")),
        ))
    }

    @Test fun 候選依全部時間張數排序_地點標籤混排_帶別名() = runTest {
        seed()
        repo.renamePlace(db.placeDao().byName("宜蘭")!!.id, "宜蘭", listOf("噶瑪蘭"))
        val all = repo.filterOptions(upToMonth = null)
        assertEquals(listOf("台北", "美食", "露營", "宜蘭", "花蓮"), all.map { it.name })
        assertEquals(listOf(true, false, false, true, true), all.map { it.isPlace })
        assertEquals(listOf("噶瑪蘭"), all.first { it.name == "宜蘭" }.aliases)
        assertEquals("other", all.first { it.name == "美食" }.tagKind)
    }

    @Test fun 候選依時間範圍過濾_排序不變() = runTest {
        seed()
        assertEquals(listOf("露營", "宜蘭"), repo.filterOptions(upToMonth = "2026-01").map { it.name })
    }

    @Test fun 有圖的月份_新到舊() = runTest {
        seed()
        assertEquals(listOf("2026-03", "2026-02", "2026-01"), repo.months())
    }

    @Test fun 篩選中的月份_任一選取項目有圖() = runTest {
        seed()
        assertEquals(listOf("2026-03", "2026-01"), repo.monthsMatching(places = emptySet(), tagNames = setOf("露營")))
        assertEquals(listOf("2026-02", "2026-01"), repo.monthsMatching(places = setOf("宜蘭", "台北"), tagNames = emptySet()))
        assertEquals(listOf("2026-03", "2026-02", "2026-01"), repo.monthsMatching(places = setOf("台北"), tagNames = setOf("露營")))
        assertEquals(emptyList<String>(), repo.monthsMatching(places = setOf("不存在"), tagNames = emptySet()))
    }

    @Test fun 篩選中的月份_認得別名() = runTest {
        seed()
        repo.renamePlace(db.placeDao().byName("宜蘭")!!.id, "宜蘭", listOf("噶瑪蘭"))
        assertEquals(listOf("2026-01"), repo.monthsMatching(places = setOf("噶瑪蘭"), tagNames = emptySet()))
    }
}
