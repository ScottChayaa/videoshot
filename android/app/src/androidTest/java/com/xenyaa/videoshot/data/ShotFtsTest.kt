package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShotFtsTest {

    private lateinit var db: LibraryDatabase

    @Before fun setUp() { db = inMemoryLibraryDb() }
    @After fun tearDown() { db.close() }

    private suspend fun insertShot(frameIndex: Int, description: String?, place: String?): Long {
        db.videoDao().upsert(VideoEntity("v1", "t", "c", "2026-01-01T00:00:00Z", 60, "public", null, 1L))
        return db.shotDao().insert(
            ShotEntity(
                id = 0, videoId = "v1", atSec = frameIndex.toDouble(), source = "storyboard",
                frameIndex = frameIndex, sbLevel = 3, eventDate = "2026-01-01",
                // 16A：place 欄位改成 place_id
                placeId = db.placeIdOf(place), description = description,
                aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 1L,
            )
        )
    }

    @Test
    fun 三個字以上的關鍵字用_MATCH_命中() = runTest {
        val id = insertShot(0, "加勒比海夜潛看到的大蝦", "加勒比海")
        assertEquals(listOf(id), db.searchDao().matchIds("夜潛看"))
    }

    /** 16A：地點改放 place 表，不再進全文索引；關鍵字比對地點名稱改由 PlaceDao.idsNameContains 處理。 */
    @Test
    fun 地點不在全文索引裡() = runTest {
        insertShot(1, null, "宜蘭外澳")
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("宜蘭外"))
        assertEquals(listOf(db.placeDao().byName("宜蘭外澳")!!.id), db.placeDao().idsNameContains("外澳"))
    }

    @Test
    fun 少於三個字的關鍵字_MATCH_不會命中_要走_LIKE() = runTest {
        val id = insertShot(2, "加勒比海夜潛看到的大蝦", "加勒比海")
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("大蝦"))
        assertEquals(listOf(id), db.searchDao().likeIds("大蝦"))
    }

    @Test
    fun 改了描述之後索引跟著更新() = runTest {
        val id = insertShot(3, "原本的描述文字", null)
        db.shotDao().updateDescription(id, "換成完全不同的內容")
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("原本的"))
        assertEquals(listOf(id), db.searchDao().matchIds("完全不"))
    }

    @Test
    fun 刪了_shot_之後索引不再命中() = runTest {
        val id = insertShot(4, "會被刪掉的描述", null)
        db.shotDao().deleteById(id)
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("會被刪"))
    }

    @Test
    fun 描述是_null_也不會壞掉() = runTest {
        val id = insertShot(5, null, null)
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("任何東西"))
        db.shotDao().updateDescription(id, "後來才補的描述")
        assertEquals(listOf(id), db.searchDao().matchIds("後來才"))
    }

    @Test
    fun 改日期或地點不影響全文索引() = runTest {
        val id = insertShot(6, "改地點之前的描述", "宜蘭")
        db.shotDao().updatePlace(id, db.placeIdOf("花蓮"))
        db.shotDao().updateEventDate(id, "2020-05-05")
        assertEquals(listOf(id), db.searchDao().matchIds("改地點"))
    }
}
