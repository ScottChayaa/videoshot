package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDbTest {

    private lateinit var db: CacheDatabase

    @Before fun setUp() { db = inMemoryCacheDb() }
    @After fun tearDown() { db.close() }

    private fun state(frameIndex: Int, state: String = "missing") =
        ThumbStateEntity("v1", 3, frameIndex, state, 0, 0L, null)

    @Test
    fun 同一格只會有一列() = runTest {
        db.thumbStateDao().upsert(state(0))
        db.thumbStateDao().upsert(state(0, state = "ok"))
        assertEquals(1, db.thumbStateDao().countOfVideo("v1"))
        assertEquals("ok", db.thumbStateDao().byKey("v1", 3, 0)!!.state)
    }

    @Test
    fun 同一支影片的不同層級互不干擾() = runTest {
        db.thumbStateDao().upsert(state(0))
        db.thumbStateDao().upsert(ThumbStateEntity("v1", 2, 0, "missing", 0, 0L, null))
        assertEquals(2, db.thumbStateDao().countOfVideo("v1"))
    }

    @Test
    fun 撈得出到期該重試的缺圖() = runTest {
        db.thumbStateDao().upsert(state(0).copy(nextTryAt = 100L))
        db.thumbStateDao().upsert(state(1).copy(nextTryAt = 900L))
        db.thumbStateDao().upsert(state(2, state = "ok"))
        db.thumbStateDao().upsert(state(3, state = "lost").copy(lostReason = "retries_exhausted"))

        val due = db.thumbStateDao().dueForRetry(now = 500L, limit = 10)
        assertEquals(listOf(0), due.map { it.frameIndex })
    }

    @Test
    fun 草稿只留最近一支() = runTest {
        db.draftDao().put(DraftEntity("v1", 2, """{"picked":[1,2]}""", 1L))
        db.draftDao().put(DraftEntity("v2", 3, """{"picked":[9]}""", 2L))
        val current = db.draftDao().current()
        assertEquals("v2", current!!.videoId)
        assertEquals(1, db.draftDao().count())
    }

    @Test
    fun 草稿刪得掉() = runTest {
        db.draftDao().put(DraftEntity("v1", 1, "{}", 1L))
        db.draftDao().clear()
        assertNull(db.draftDao().current())
    }
}

@RunWith(AndroidJUnit4::class)
class CacheRepoTest {

    private lateinit var db: CacheDatabase
    private lateinit var repo: com.xenyaa.videoshot.data.repo.CacheRepo

    @Before fun setUp() {
        db = inMemoryCacheDb()
        repo = com.xenyaa.videoshot.data.repo.RoomCacheRepo(db, kotlinx.coroutines.Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    @Test
    fun 批次寫入縮圖狀態並撈出該重試的() = runTest {
        repo.putThumbStates(
            listOf(
                ThumbStateEntity("v1", 3, 0, "missing", 0, 100L, null),
                ThumbStateEntity("v1", 3, 1, "missing", 0, 900L, null),
                ThumbStateEntity("v1", 3, 2, "ok", 0, 0L, null),
            )
        )
        assertEquals(listOf(0), repo.thumbsDueForRetry(now = 500L, limit = 10).map { it.frameIndex })
        assertEquals("ok", repo.thumbState("v1", 3, 2)!!.state)
    }

    @Test
    fun 刪掉一支影片的縮圖狀態() = runTest {
        repo.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null)))
        repo.forgetVideoThumbs("v1")
        assertNull(repo.thumbState("v1", 3, 0))
    }

    @Test
    fun 草稿存取與清除() = runTest {
        repo.saveDraft(DraftEntity("v1", 2, """{"picked":[1]}""", 1L))
        assertEquals(2, repo.currentDraft()!!.step)
        repo.clearDraft()
        assertNull(repo.currentDraft())
    }

    @Test
    fun 還原備份後整個清空() = runTest {
        repo.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null)))
        repo.saveDraft(DraftEntity("v1", 1, "{}", 1L))
        repo.clearAll()
        assertNull(repo.thumbState("v1", 3, 0))
        assertNull(repo.currentDraft())
    }
}
