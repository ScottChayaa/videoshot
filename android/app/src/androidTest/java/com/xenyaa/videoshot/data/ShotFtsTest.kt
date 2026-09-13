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
                place = place, description = description,
                aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 1L,
            )
        )
    }

    @Test
    fun 三個字以上的關鍵字用_MATCH_命中() = runTest {
        val id = insertShot(0, "加勒比海夜潛看到的大蝦", "加勒比海")
        assertEquals(listOf(id), db.searchDao().matchIds("夜潛看"))
    }

    @Test
    fun 地點也進了全文索引() = runTest {
        val id = insertShot(1, null, "宜蘭外澳")
        assertEquals(listOf(id), db.searchDao().matchIds("宜蘭外"))
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
        db.shotDao().updateDescriptionAndPlace(id, "換成完全不同的內容", null)
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
        db.shotDao().updateDescriptionAndPlace(id, "後來才補的描述", null)
        assertEquals(listOf(id), db.searchDao().matchIds("後來才"))
    }
}
