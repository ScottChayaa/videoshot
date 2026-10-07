package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 統計表由觸發器維護（規格第四節）。每個測試做一種寫入，然後拿「從圖資重數一遍」的結果
 * 跟統計表比：每月明細與總數都要一模一樣，而且不能出現負數。
 */
@RunWith(AndroidJUnit4::class)
class StatsTriggerTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: RoomLibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private fun video(id: String) = VideoEntity(id, "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L)

    private fun pick(i: Int, date: String, place: String?, tags: List<String>) = NewShot(
        atSec = i.toDouble(), source = "storyboard", frameIndex = i, sbLevel = 3,
        eventDate = date, place = place, description = null, webp = null, tagNames = tags,
    )

    /** 3 支影片、12 張圖，跨 3 個月、3 個地點、4 個標籤，有的沒地點、有的沒標籤。 */
    private suspend fun seed(): Map<String, List<Long>> = mapOf(
        "v1" to repo.commitPicks(video("v1"), listOf(
            pick(0, "2026-01-05", "宜蘭", listOf("露營", "阿明")),
            pick(1, "2026-01-06", "宜蘭", listOf("露營")),
            pick(2, "2026-02-01", null, listOf("阿明")),
            pick(3, "2026-02-02", "花蓮", emptyList()),
        )),
        "v2" to repo.commitPicks(video("v2"), listOf(
            pick(0, "2026-02-10", "花蓮", listOf("美食")),
            pick(1, "2026-03-01", "台北", listOf("美食", "夜景")),
            pick(2, "2026-03-02", "台北", listOf("夜景")),
            pick(3, "2026-03-03", null, emptyList()),
        )),
        "v3" to repo.commitPicks(video("v3"), listOf(
            pick(0, "2026-01-20", "台北", listOf("露營")),
            pick(1, "2026-02-20", "宜蘭", listOf("阿明", "美食")),
            pick(2, "2026-03-20", "宜蘭", listOf("夜景")),
            pick(3, "2026-03-21", "花蓮", listOf("露營", "阿明", "美食")),
        )),
    )

    /** 從圖資重數一遍：每月 × 種類 × 對象 × 張數。 */
    private val truth = """
        SELECT substr(event_date, 1, 7) AS m, 0 AS k, 0 AS r, COUNT(*) AS n FROM shot GROUP BY 1
        UNION ALL
        SELECT substr(event_date, 1, 7), 1, place_id, COUNT(*) FROM shot WHERE place_id IS NOT NULL GROUP BY 1, 3
        UNION ALL
        SELECT substr(s.event_date, 1, 7), 2, st.tag_id, COUNT(*) FROM shot_tag st JOIN shot s ON s.id = st.shot_id GROUP BY 1, 3
    """

    private suspend fun assertStatsMatch() {
        val expected = db.readAllText("SELECT m || '|' || k || '|' || r || '|' || n FROM ($truth) ORDER BY 1")
        val actual = db.readAllText("SELECT month || '|' || kind || '|' || ref_id || '|' || cnt FROM shot_stat WHERE cnt <> 0 ORDER BY 1")
        assertEquals("每月明細", expected, actual)

        val expectedTotal = db.readAllText("SELECT k || '|' || r || '|' || SUM(n) FROM ($truth) GROUP BY k, r ORDER BY 1")
        val actualTotal = db.readAllText("SELECT kind || '|' || ref_id || '|' || cnt FROM shot_stat_total WHERE cnt <> 0 ORDER BY 1")
        assertEquals("總數", expectedTotal, actualTotal)

        assertEquals("不能有負數", 0L, db.readSingleLong(
            "SELECT (SELECT COUNT(*) FROM shot_stat WHERE cnt < 0) + (SELECT COUNT(*) FROM shot_stat_total WHERE cnt < 0)"
        ))
    }

    @Test fun 取圖入庫() = runTest { seed(); assertStatsMatch() }

    @Test fun 改日期換月份() = runTest {
        val ids = seed()
        repo.patchShots(ids.getValue("v1"), ShotPatch(eventDate = "2025-12-31", place = null, description = null, tagIds = null))
        assertStatsMatch()
    }

    @Test fun 改日期但同一個月() = runTest {
        val ids = seed()
        repo.patchShots(listOf(ids.getValue("v2")[1]), ShotPatch(eventDate = "2026-03-28", place = null, description = null, tagIds = null))
        assertStatsMatch()
    }

    @Test fun 改地點_清空地點_同時改日期與地點() = runTest {
        val ids = seed()
        repo.patchShots(ids.getValue("v1").take(2), ShotPatch(eventDate = null, place = "台東", description = null, tagIds = null))
        repo.patchShots(listOf(ids.getValue("v2")[0]), ShotPatch(eventDate = null, place = "", description = null, tagIds = null))
        repo.patchShots(listOf(ids.getValue("v3")[0]), ShotPatch(eventDate = "2024-07-07", place = "綠島", description = null, tagIds = null))
        assertStatsMatch()
    }

    @Test fun 整組換標籤() = runTest {
        val ids = seed()
        repo.patchShots(ids.getValue("v3"), ShotPatch(eventDate = null, place = null, description = null, tagIds = null, tagNames = listOf("新標籤")))
        assertStatsMatch()
    }

    @Test fun 刪一張與刪整支() = runTest {
        val ids = seed()
        repo.deleteShot(ids.getValue("v1")[0])
        repo.deleteVideo("v3")
        assertStatsMatch()
    }

    @Test fun 標籤合併與刪除標籤() = runTest {
        seed()
        val tags = repo.allTagsWithUsage().associateBy { it.name }
        repo.renameTag(tags.getValue("阿明").id, "露營", "other", emptyList()) // 改名撞名＝合併
        repo.deleteTag(tags.getValue("夜景").id)
        assertStatsMatch()
    }

    @Test fun 地點合併與刪除地點() = runTest {
        seed()
        val yilan = db.placeDao().byName("宜蘭")!!.id
        val hualien = db.placeDao().byName("花蓮")!!.id
        val taipei = db.placeDao().byName("台北")!!.id
        db.execOnWriter("UPDATE shot SET place_id = $hualien WHERE place_id = $yilan")
        db.placeDao().deleteById(yilan)
        db.placeDao().deleteById(taipei) // 圖的地點被外鍵設成 NULL
        assertStatsMatch()
    }

    @Test fun 刪掉的標籤與地點不留統計列() = runTest {
        seed()
        val tagId = repo.allTagsWithUsage().first { it.name == "美食" }.id
        repo.deleteTag(tagId)
        val placeId = db.placeDao().byName("花蓮")!!.id
        db.placeDao().deleteById(placeId)
        assertEquals(0L, db.readSingleLong("SELECT COUNT(*) FROM shot_stat WHERE kind = 2 AND ref_id = $tagId"))
        assertEquals(0L, db.readSingleLong("SELECT COUNT(*) FROM shot_stat_total WHERE kind = 1 AND ref_id = $placeId"))
    }
}
