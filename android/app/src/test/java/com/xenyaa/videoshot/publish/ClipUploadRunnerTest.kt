package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.backup.GoogleAuthRevoked
import com.xenyaa.videoshot.core.backup.UploadChunk
import com.xenyaa.videoshot.core.publish.NETWORK_WAIT_MS
import com.xenyaa.videoshot.core.publish.UploadErrorKind
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.edit.ClipExportNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class) // 沒預期的錯會呼叫 android.util.Log
@Config(sdk = [35])
class ClipUploadRunnerTest {

    @get:Rule val tmp = TemporaryFolder()

    // ---- 測試替身 ----

    private class RecordingRepo : FakeCacheRepo() {
        val progressWrites = mutableListOf<Pair<EditTaskState, Int>>()
        val urlWrites = mutableListOf<String?>()

        /** 寫進處理中的那一刻要檢查的事（例如成品還在不在）。 */
        var onUploaded: () -> Unit = {}

        override suspend fun setEditTaskProgress(id: Long, state: EditTaskState, progress: Int, now: Long) {
            progressWrites += state to progress
            super.setEditTaskProgress(id, state, progress, now)
        }

        override suspend fun setEditTaskUploadUrl(id: Long, url: String?, now: Long) {
            urlWrites += url
            super.setEditTaskUploadUrl(id, url, now)
        }

        override suspend fun setEditTaskUploaded(id: Long, videoId: String, now: Long) {
            onUploaded()
            super.setEditTaskUploaded(id, videoId, now)
        }
    }

    /**
     * 照腳本回應的 uploader。每個方法先用排好的回應（依序取用），沒排的用預設：
     * 開工作階段回新網址、分段照實收下（最後一段回影片編號）；查詢沒排就算測試寫錯。
     */
    private class ScriptedUploader : YoutubeUploader {
        val calls = mutableListOf<String>()
        val starts = mutableListOf<Triple<String, String, Long>>() // 權杖、標題、大小
        val putTokens = mutableListOf<String>()
        val puts = mutableListOf<UploadChunk>()
        val startScript = ArrayDeque<suspend () -> String>()
        val queryScript = ArrayDeque<suspend () -> UploadStatus>()
        val putScript = ArrayDeque<suspend (UploadChunk) -> UploadStatus>()
        var defaultQuery: (suspend () -> UploadStatus)? = null
        var defaultPut: suspend (UploadChunk) -> UploadStatus = { chunk ->
            if (chunk.endInclusive + 1 == chunk.total) UploadStatus.Done(VIDEO_ID)
            else UploadStatus.Partial(chunk.endInclusive + 1)
        }
        private var sessions = 0

        override suspend fun startSession(token: String, title: String, sizeBytes: Long): String {
            calls += "start"
            starts += Triple(token, title, sizeBytes)
            return startScript.removeFirstOrNull()?.invoke() ?: "https://upload/session-${++sessions}"
        }

        override suspend fun query(sessionUrl: String, token: String, sizeBytes: Long): UploadStatus {
            calls += "query $sessionUrl"
            val next = queryScript.removeFirstOrNull() ?: defaultQuery ?: throw AssertionError("沒排查詢的回應")
            return next()
        }

        override suspend fun putChunk(sessionUrl: String, token: String, file: File, chunk: UploadChunk): UploadStatus {
            calls += "put ${chunk.start} $sessionUrl"
            putTokens += token
            puts += chunk
            return (putScript.removeFirstOrNull() ?: defaultPut)(chunk)
        }
    }

    private class FakeNotifier : ClipExportNotifier {
        val progress = mutableListOf<Int>()
        val failed = mutableListOf<Pair<EditTask, String>>()
        val waiting = mutableListOf<EditTask>()
        override fun progress(task: EditTask, percent: Int) { progress += percent }
        override fun finished(task: EditTask) = throw AssertionError("上傳不該呼叫 finished")
        override fun done(task: EditTask) = throw AssertionError("上傳不該呼叫 done")
        override fun failed(task: EditTask, reason: String) { failed += task to reason }
        override fun waitingQuota(task: EditTask) { waiting += task }
    }

    private val repo = RecordingRepo()
    private val uploader = ScriptedUploader()
    private val notifier = FakeNotifier()
    private val tokenScript = ArrayDeque<suspend () -> String>()
    private var tokenCalls = 0
    private val tokenForceRefresh = mutableListOf<Boolean>()
    private val networkWaits = mutableListOf<Long>()
    private var networkBack = true
    private val polls = mutableListOf<Pair<Long, Long>>()
    private val sleeps = mutableListOf<Long>()
    private val quotaResumes = mutableListOf<Pair<Long, Long>>()
    private var nowMs = 123L

    private fun runner() = ClipUploadRunner(
        repo = repo,
        uploader = uploader,
        token = { forceRefresh ->
            tokenCalls++
            tokenForceRefresh += forceRefresh
            tokenScript.removeFirstOrNull()?.invoke() ?: "tok"
        },
        awaitNetwork = { timeout ->
            networkWaits += timeout
            networkBack
        },
        schedulePoll = { id, delay -> polls += id to delay },
        scheduleQuotaResume = { id, delay -> quotaResumes += id to delay },
        notifier = notifier,
        dirFor = { File(tmp.root, "edits/$it") },
        now = { nowMs },
        sleep = { sleeps += it },
    )

    private fun outFor(id: Long) = File(tmp.root, "edits/$id/out.mp4")

    private val draft = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    /** 建一個上傳中的任務，成品是 20 MiB 的稀疏檔（分成 0、8、16 MiB 起的三段）。 */
    private suspend fun start(title: String = "我的片", url: String? = null, size: Long = SIZE): Long {
        val id = repo.startEditTask(draft, title, true, 1L)
        repo.setEditTaskReadyToUpload(id, 0, 2L)
        if (url != null) repo.setEditTaskUploadUrl(id, url, 2L)
        repo.urlWrites.clear()
        repo.progressWrites.clear()
        outFor(id).apply { parentFile!!.mkdirs() }.let { RandomAccessFile(it, "rw").use { f -> f.setLength(size) } }
        return id
    }

    private suspend fun task(id: Long): EditTask? = repo.editTask(id).first()

    private fun http(code: Int, kind: UploadErrorKind) = UploadHttpException(code, kind)

    // ---- 成功 ----

    @Test fun `從頭傳完進處理中並刪檔`() = runTest {
        val id = start(title = "  <貓>的一天 ")
        var urlWhenFirstPut: String? = null
        uploader.putScript += { chunk ->
            urlWhenFirstPut = task(id)!!.uploadUrl
            UploadStatus.Partial(chunk.endInclusive + 1)
        }
        var fileWhenUploaded: Boolean? = null
        repo.onUploaded = { fileWhenUploaded = outFor(id).exists() }

        runner().run(id)

        assertEquals(listOf(Triple("tok", "＜貓＞的一天", SIZE)), uploader.starts)
        assertEquals(
            listOf("start", "put 0 https://upload/session-1", "put $MIB8 https://upload/session-1",
                "put ${2 * MIB8} https://upload/session-1"),
            uploader.calls,
        )
        assertEquals(listOf(SIZE - 1, 2 * MIB8 - 1, MIB8 - 1).sorted(), uploader.puts.map { it.endInclusive }.sorted())
        assertEquals("https://upload/session-1", urlWhenFirstPut) // 網址先存進任務才開始傳
        assertEquals(true, fileWhenUploaded) // 先記下影片編號才刪檔
        val t = task(id)!!
        assertEquals(EditTaskState.PROCESSING, t.state)
        assertEquals(VIDEO_ID, t.videoId)
        assertNull(t.uploadUrl)
        assertFalse(outFor(id).exists())
        assertEquals(listOf(id to 30_000L), polls)
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `有上傳網址時從查到的位元組接著傳`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { UploadStatus.Partial(2 * MIB8) }

        runner().run(id)

        assertEquals(listOf("query https://upload/old", "put ${2 * MIB8} https://upload/old"), uploader.calls)
        assertEquals(2 * MIB8, uploader.puts.first().start)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
        assertTrue(repo.urlWrites.isEmpty()) // 沿用原本的網址，沒有開新的工作階段
    }

    @Test fun `查詢時已傳完直接進處理中不重傳`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { UploadStatus.Done("VID9") }

        runner().run(id)

        assertEquals(listOf("query https://upload/old"), uploader.calls)
        val t = task(id)!!
        assertEquals(EditTaskState.PROCESSING, t.state)
        assertEquals("VID9", t.videoId)
        assertFalse(outFor(id).exists())
        assertEquals(listOf(id to 30_000L), polls)
    }

    @Test fun `已有影片編號直接進處理中`() = runTest {
        val id = start(url = "https://upload/old")
        repo.setEditTaskUploaded(id, "VID7", 3L)
        repo.setEditTaskProgress(id, EditTaskState.UPLOADING, 100, 3L) // 寫進處理中之前被中斷的樣子

        runner().run(id)

        assertTrue(uploader.calls.isEmpty())
        assertEquals(0, tokenCalls)
        val t = task(id)!!
        assertEquals(EditTaskState.PROCESSING, t.state)
        assertEquals("VID7", t.videoId)
        assertFalse(outFor(id).exists())
        assertEquals(listOf(id to 30_000L), polls)
    }

    @Test fun `不是上傳中就什麼都不做`() = runTest {
        runner().run(999L)
        val id = start()
        repo.setEditTaskFailed(id, "上傳失敗", EditTaskStep.UPLOAD, 3L)

        runner().run(id)

        assertTrue(uploader.calls.isEmpty())
        assertEquals(0, tokenCalls)
        assertTrue(outFor(id).exists())
    }

    // ---- 斷網 ----

    @Test fun `斷網後網路回來接著傳`() = runTest {
        val id = start()
        uploader.putScript += { UploadStatus.Partial(MIB8) }
        uploader.putScript += { throw IOException("連線中斷") }
        uploader.queryScript += { UploadStatus.Partial(MIB8 + 1024) }

        runner().run(id)

        assertEquals(listOf(NETWORK_WAIT_MS), networkWaits)
        assertEquals(
            listOf("start", "put 0 https://upload/session-1", "put $MIB8 https://upload/session-1",
                "query https://upload/session-1", "put ${MIB8 + 1024} https://upload/session-1",
                "put ${2 * MIB8 + 1024} https://upload/session-1"),
            uploader.calls,
        )
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `斷網等不到網路失敗並保留網址`() = runTest {
        val id = start()
        networkBack = false
        uploader.putScript += { throw IOException("連線中斷") }

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("沒有網路連線", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/session-1", t.uploadUrl) // 重試從斷掉的位元組接著傳
        assertTrue(outFor(id).exists())
        assertEquals(listOf("沒有網路連線"), notifier.failed.map { it.second })
        assertTrue(polls.isEmpty())
    }

    @Test fun `網路一直不穩退避五次後失敗`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.defaultQuery = { throw IOException("連線中斷") }

        runner().run(id)

        assertEquals(5, networkWaits.size) // 第 6 次斷線直接失敗，不再等網路
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), sleeps)
        val t = task(id)!!
        assertEquals("沒有網路連線", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/old", t.uploadUrl)
    }

    @Test fun `拿權杖失敗當成斷網_網路回來再拿一次`() = runTest {
        val id = start()
        tokenScript += { throw ExecutionException(IOException("GMS 離線")) }

        runner().run(id)

        assertEquals(listOf(NETWORK_WAIT_MS), networkWaits)
        assertEquals(2, tokenCalls)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
    }

    @Test fun `拿權杖失敗且等不到網路就說沒有網路連線`() = runTest {
        val id = start()
        networkBack = false
        tokenScript += { throw ExecutionException(IOException("GMS 離線")) }

        runner().run(id)

        val t = task(id)!!
        assertEquals("沒有網路連線", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test fun `網路回來後還是拿不到權杖就上傳失敗`() = runTest {
        val id = start()
        tokenScript += { throw ExecutionException(IOException("GMS 離線")) }
        tokenScript += { throw IllegalStateException("還是不行") }

        runner().run(id)

        assertEquals("上傳失敗", task(id)!!.failReason)
        assertTrue(uploader.calls.isEmpty())
    }

    // ---- 授權與伺服器回應 ----

    @Test fun `授權被撤銷失敗於上傳步驟`() = runTest {
        val id = start(url = "https://upload/old")
        tokenScript += { throw GoogleAuthRevoked("撤銷了") }

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("Google 授權已失效，請按重試重新授權", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/old", t.uploadUrl)
        assertTrue(outFor(id).exists())
        assertTrue(uploader.calls.isEmpty())
        assertTrue(networkWaits.isEmpty())
    }

    @Test fun `401重拿權杖一次後成功`() = runTest {
        val id = start()
        tokenScript += { "t1" }
        tokenScript += { "t2" }
        uploader.putScript += { throw http(401, UploadErrorKind.AUTH) }
        uploader.queryScript += { UploadStatus.Partial(0) }

        runner().run(id)

        assertEquals(listOf(false, true), tokenForceRefresh) // 401 之後那次要換新的，不能回快取
        assertEquals(listOf("t1", "t2", "t2", "t2"), uploader.putTokens)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
    }

    @Test fun `重拿權杖後仍是401就請使用者重新授權`() = runTest {
        val id = start()
        uploader.putScript += { throw http(401, UploadErrorKind.AUTH) }
        uploader.putScript += { throw http(401, UploadErrorKind.AUTH) }
        uploader.defaultQuery = { UploadStatus.Partial(0) }

        runner().run(id)

        assertEquals(2, tokenCalls)
        val t = task(id)!!
        assertEquals("Google 授權已失效，請按重試重新授權", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
    }

    @Test fun `沒頻道的原因`() = runTest {
        val cases = listOf(
            UploadErrorKind.NO_CHANNEL to "這個 Google 帳號還沒有 YouTube 頻道",
            UploadErrorKind.FATAL to "上傳失敗",
        )
        for ((kind, reason) in cases) {
            val id = start()
            uploader.startScript += { throw http(403, kind) }

            runner().run(id)

            val t = task(id)!!
            assertEquals(EditTaskState.FAILED, t.state)
            assertEquals(reason, t.failReason)
            assertEquals(EditTaskStep.UPLOAD, t.failedStep)
            assertTrue(outFor(id).exists())
        }
        assertEquals(cases.map { it.second }, notifier.failed.map { it.second })
        assertTrue(sleeps.isEmpty())
    }

    @Test fun `網址失效從0重開一次`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { throw http(404, UploadErrorKind.SESSION_GONE) }

        runner().run(id)

        assertEquals(
            listOf("query https://upload/old", "start", "put 0 https://upload/session-1",
                "put $MIB8 https://upload/session-1", "put ${2 * MIB8} https://upload/session-1"),
            uploader.calls,
        )
        assertEquals(listOf(null, "https://upload/session-1"), repo.urlWrites)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
    }

    @Test fun `新開的網址又失效就上傳失敗`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { throw http(404, UploadErrorKind.SESSION_GONE) }
        uploader.putScript += { throw http(410, UploadErrorKind.SESSION_GONE) }

        runner().run(id)

        assertEquals(1, uploader.starts.size)
        val t = task(id)!!
        assertEquals("上傳失敗", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
    }

    @Test fun `伺服器錯誤退避五次後失敗`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.defaultQuery = { UploadStatus.Partial(MIB8) }
        uploader.defaultPut = { throw http(503, UploadErrorKind.TRANSIENT) }

        runner().run(id)

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), sleeps)
        assertEquals(6, uploader.puts.size)
        assertTrue(uploader.puts.all { it.start == MIB8 }) // 每次都先查起點再傳
        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("上傳失敗", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/old", t.uploadUrl)
        assertTrue(outFor(id).exists())
    }

    @Test fun `伺服器錯誤退避後恢復就接著傳`() = runTest {
        val id = start()
        uploader.putScript += { throw http(500, UploadErrorKind.TRANSIENT) }
        uploader.putScript += { throw http(429, UploadErrorKind.TRANSIENT) }
        uploader.defaultQuery = { UploadStatus.Partial(0) }

        runner().run(id)

        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
    }

    // ---- 檔案、刪除、取消 ----

    @Test fun `檔案不見失敗於輸出步驟`() = runTest {
        val id = start(url = "https://upload/old")
        outFor(id).delete()

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("輸出的影片不見了，請按重試重新輸出", t.failReason)
        assertEquals(EditTaskStep.RENDER, t.failedStep)
        assertNull(t.uploadUrl) // 重新輸出是另一個檔，舊的工作階段不能再用
        assertTrue(uploader.calls.isEmpty())
        assertEquals(listOf("輸出的影片不見了，請按重試重新輸出"), notifier.failed.map { it.second })
    }

    @Test fun `傳到一半檔案被刪失敗於輸出步驟且不等網路`() = runTest {
        val id = start()
        uploader.putScript += { UploadStatus.Partial(MIB8) }
        uploader.putScript += {
            outFor(id).delete()
            throw FileNotFoundException("out.mp4 (No such file or directory)")
        }

        runner().run(id)

        assertTrue(networkWaits.isEmpty())
        val t = task(id)!!
        assertEquals("輸出的影片不見了，請按重試重新輸出", t.failReason)
        assertEquals(EditTaskStep.RENDER, t.failedStep)
        assertNull(t.uploadUrl)
    }

    @Test fun `上傳中任務被刪不會復活且刪檔`() = runTest {
        val id = start()
        uploader.putScript += { UploadStatus.Partial(MIB8) }
        uploader.putScript += {
            repo.deleteEditTask(id) // 使用者在上傳途中刪掉任務
            UploadStatus.Partial(2 * MIB8)
        }

        runner().run(id)

        assertNull(task(id))
        assertEquals(2, uploader.puts.size) // 不再傳下一段
        assertFalse(outFor(id).exists())
        assertTrue(polls.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `最後一段傳完時任務已被刪就不排輪詢`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { UploadStatus.Partial(2 * MIB8) }
        uploader.putScript += {
            repo.deleteEditTask(id)
            UploadStatus.Done(VIDEO_ID)
        }

        runner().run(id)

        assertNull(task(id))
        assertFalse(outFor(id).exists())
        assertTrue(polls.isEmpty())
    }

    @Test fun `失敗時任務已被刪就不通知也不復活且刪檔`() = runTest {
        val id = start()
        uploader.startScript += {
            repo.deleteEditTask(id)
            throw http(403, UploadErrorKind.NO_CHANNEL)
        }

        runner().run(id)

        assertNull(task(id))
        assertFalse(outFor(id).exists())
        assertTrue(notifier.failed.isEmpty())
    }

    // ---- 超過每日上傳上限：不算失敗，停在等待中 ----

    @Test fun `額度用完停在等待中不刪檔並排明天`() = runTest {
        val id = start()
        uploader.startScript += { throw http(403, UploadErrorKind.QUOTA) }

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.WAITING_QUOTA, t.state)
        assertEquals("系統目前上傳已超過每日配額，請隔天再繼續上傳", t.failReason)
        assertNull(t.failedStep)
        assertEquals(123L, t.updatedAt) // 撞到上限的時間
        assertTrue(outFor(id).exists()) // 重置後接著傳，不重新輸出
        // now＝123 毫秒＝太平洋時間 1969-12-31 16:00 → 1970-01-01 00:00 PST（08:00Z）＋5 分
        assertEquals(listOf(id to 29_100_000L - 123L), quotaResumes)
        assertEquals(EditTaskState.WAITING_QUOTA, notifier.waiting.single().state)
        assertEquals(id, notifier.waiting.single().id)
        assertTrue(notifier.failed.isEmpty())
        assertTrue(polls.isEmpty())
        assertTrue(sleeps.isEmpty()) // 不在工作裡等，串可以輪到下一支
    }

    @Test fun `頻道上限一樣等且保留上傳網址`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { throw http(400, UploadErrorKind.CHANNEL_LIMIT) }

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.WAITING_QUOTA, t.state)
        assertEquals("這個頻道今天的上傳次數已達上限，請隔天再繼續上傳", t.failReason)
        assertEquals("https://upload/old", t.uploadUrl)
        assertTrue(outFor(id).exists())
        assertEquals(listOf(id), quotaResumes.map { it.first })
        assertEquals(listOf(id), notifier.waiting.map { it.id })
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `等待中不寫updated_at`() = runTest {
        val id = start()
        uploader.startScript += { throw http(403, UploadErrorKind.QUOTA) }
        runner().run(id)

        // 等待期間又被叫到（例如工作被重排）：不是上傳中，什麼都不做
        nowMs = 5_000L
        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.WAITING_QUOTA, t.state)
        assertEquals(123L, t.updatedAt)
        assertEquals(listOf("start"), uploader.calls)
        assertEquals(1, quotaResumes.size)
    }

    @Test fun `撞到上限時任務已被刪就不排不通知且刪檔`() = runTest {
        val id = start()
        uploader.startScript += {
            repo.deleteEditTask(id)
            throw http(403, UploadErrorKind.QUOTA)
        }

        runner().run(id)

        assertNull(task(id))
        assertFalse(outFor(id).exists())
        assertTrue(quotaResumes.isEmpty())
        assertTrue(notifier.waiting.isEmpty())
    }

    @Test fun `取消時不刪檔也不清網址`() = runTest {
        val id = start()
        uploader.putScript += { UploadStatus.Partial(MIB8) }
        uploader.putScript += { throw CancellationException("取消") }

        var cancelled = false
        try {
            runner().run(id)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertTrue(outFor(id).exists())
        val t = task(id)!!
        assertEquals(EditTaskState.UPLOADING, t.state)
        assertEquals("https://upload/session-1", t.uploadUrl)
        assertTrue(notifier.failed.isEmpty())
        assertTrue(polls.isEmpty())
    }

    @Test fun `沒預期的錯算上傳失敗並保留檔案與網址`() = runTest {
        val id = start(url = "https://upload/old")
        uploader.queryScript += { throw IllegalStateException("壞了") }

        runner().run(id)

        val t = task(id)!!
        assertEquals("上傳失敗", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/old", t.uploadUrl)
        assertTrue(outFor(id).exists())
    }

    // ---- 進度 ----

    @Test fun `進度相同不重複寫`() = runTest {
        val id = start()
        uploader.putScript += { UploadStatus.Partial(MIB8) } // 40%
        uploader.putScript += { UploadStatus.Partial(MIB8) } // 伺服器沒收下這段：還是 40%
        uploader.putScript += { UploadStatus.Partial(2 * MIB8) } // 80%

        runner().run(id)

        assertEquals(
            listOf(EditTaskState.UPLOADING to 40, EditTaskState.UPLOADING to 80),
            repo.progressWrites,
        )
        assertEquals(listOf(40, 80), notifier.progress)
        assertEquals(EditTaskState.PROCESSING, task(id)!!.state)
    }

    @Test fun `接著傳時查到起點之前不把進度寫回0`() = runTest {
        val id = start(url = "https://upload/old")
        repo.setEditTaskProgress(id, EditTaskState.UPLOADING, 40, 2L) // 上次傳到 40% 被中斷
        repo.progressWrites.clear()
        uploader.queryScript += { UploadStatus.Partial(2 * MIB8) }

        runner().run(id)

        assertEquals(listOf(EditTaskState.UPLOADING to 80), repo.progressWrites)
        assertEquals(listOf(80), notifier.progress)
    }

    @Test fun `斷線後等網路時不把進度寫回0`() = runTest {
        val id = start(url = "https://upload/old")
        repo.setEditTaskProgress(id, EditTaskState.UPLOADING, 40, 2L)
        repo.progressWrites.clear()
        networkBack = false
        uploader.queryScript += { throw IOException("連線中斷") }

        runner().run(id)

        assertTrue(repo.progressWrites.isEmpty())
        assertTrue(notifier.progress.isEmpty())
        assertEquals("沒有網路連線", task(id)!!.failReason)
    }

    @Test fun `分段一直沒前進就退避五次後失敗`() = runTest {
        val id = start()
        uploader.defaultPut = { chunk -> UploadStatus.Partial(chunk.start) } // 伺服器回 308 卻沒多收

        runner().run(id)

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), sleeps)
        assertEquals(6, uploader.puts.size)
        val t = task(id)!!
        assertEquals("上傳失敗", t.failReason)
        assertEquals(EditTaskStep.UPLOAD, t.failedStep)
        assertEquals("https://upload/session-1", t.uploadUrl)
    }

    private companion object {
        const val VIDEO_ID = "VIDEO1"
        const val MIB8 = 8L * 1024 * 1024
        const val SIZE = 20L * 1024 * 1024
    }
}
