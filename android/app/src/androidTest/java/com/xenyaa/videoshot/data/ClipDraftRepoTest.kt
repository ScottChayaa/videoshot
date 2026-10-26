package com.xenyaa.videoshot.data

import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.EditDraftEntity
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 剪輯草稿（`cache.db` 的 `edit_draft`）。一律用 in-memory 的 DB，不碰手機上真正的 cache.db。 */
@RunWith(AndroidJUnit4::class)
class ClipDraftRepoTest {

    private lateinit var db: CacheDatabase
    private lateinit var repo: RoomCacheRepo

    @Before fun setUp() {
        db = inMemoryCacheDb()
        repo = RoomCacheRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private val draft = newClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(
            ClipSource(1, "content://media/1", SourceKind.PHOTO, 1000, null, 1080, 1920, 0),
            ClipSource(2, "content://media/2", SourceKind.VIDEO, 2000, 5000, 1920, 1080, 90),
        ),
    )

    private suspend fun rowCount(): Long =
        db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM edit_draft") { s -> s.step(); s.getLong(0) } }

    @Test fun 存了再讀一樣() = runTest {
        repo.saveClipDraft(draft, now = 100)
        assertEquals(draft, repo.currentClipDraft())
    }

    @Test fun 再存一次覆蓋舊的_只有一列() = runTest {
        repo.saveClipDraft(draft, now = 100)
        val landscape = draft.copy(orientation = ClipOrientation.LANDSCAPE)
        repo.saveClipDraft(landscape, now = 200)
        assertEquals(landscape, repo.currentClipDraft())
        assertEquals(1L, rowCount())
    }

    @Test fun 清掉之後讀到null() = runTest {
        repo.saveClipDraft(draft, now = 100)
        repo.clearClipDraft()
        assertNull(repo.currentClipDraft())
    }

    @Test fun 沒存過讀到null() = runTest {
        assertNull(repo.currentClipDraft())
    }

    @Test fun 壞掉的payload讀到null_而且那一列被刪掉() = runTest {
        db.editDraftDao().put(EditDraftEntity(orientation = "PORTRAIT", payload = "{壞的", updatedAt = 1))
        assertEquals(1L, rowCount())
        assertNull(repo.currentClipDraft())
        assertEquals(0L, rowCount())
    }
}
