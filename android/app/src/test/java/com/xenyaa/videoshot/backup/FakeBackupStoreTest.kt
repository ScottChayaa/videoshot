package com.xenyaa.videoshot.backup

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeBackupStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun 上傳後可以列出來_按時間新到舊() = runTest {
        val store = FakeBackupStore()
        val older = newBackup(tmp, "a.db.gz", createdAtEpochSec = 100)
        val newer = newBackup(tmp, "b.db.gz", createdAtEpochSec = 200)

        store.upload(older)
        store.upload(newer)

        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(200L, list[0].createdAtEpochSec)
        assertEquals(100L, list[1].createdAtEpochSec)
    }

    @Test
    fun 下載拿回上傳的原始內容() = runTest {
        val store = FakeBackupStore()
        val source = tmp.newFile("src.db.gz").apply { writeBytes("payload".toByteArray()) }
        val remote = store.upload(NewBackup(source, "sha", 1, 10, "device", 100))

        val dest = tmp.newFile("dest.db.gz")
        store.download(remote.id, dest)

        assertEquals("payload", dest.readText())
    }

    @Test
    fun 刪除後list看不到它() = runTest {
        val store = FakeBackupStore()
        val remote = store.upload(newBackup(tmp, "c.db.gz", 100))

        store.delete(remote.id)

        assertTrue(store.list().isEmpty())
    }

    @Test
    fun failNextUpload會讓下一次上傳丟例外_之後恢復正常() = runTest {
        val store = FakeBackupStore()
        store.failNextUpload = IllegalStateException("模擬網路錯誤")

        val threw = runCatching { store.upload(newBackup(tmp, "d.db.gz", 100)) }.isFailure
        assertTrue(threw)

        // 恢復正常——第二次呼叫不該再丟
        store.upload(newBackup(tmp, "e.db.gz", 200))
        assertEquals(1, store.list().size)
    }

    private fun newBackup(tmp: TemporaryFolder, name: String, createdAtEpochSec: Long): NewBackup {
        val file = tmp.newFile(name).apply { writeBytes("x".toByteArray()) }
        return NewBackup(file, "sha-$name", 1, 0, "device", createdAtEpochSec)
    }
}
