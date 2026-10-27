package com.xenyaa.videoshot.edit

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
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ClipExportRunnerTest {

    @get:Rule val tmp = TemporaryFolder()

    // ---- 測試替身 ----

    private class RecordingRepo : FakeCacheRepo() {
        val progressWrites = mutableListOf<Pair<EditTaskState, Int>>()
        override suspend fun setEditTaskProgress(id: Long, state: EditTaskState, progress: Int, now: Long) {
            progressWrites += state to progress
            super.setEditTaskProgress(id, state, progress, now)
        }
    }

    private class FakeLibrary(var items: List<MediaItem>) : MediaLibrary {
        override suspend fun list(filter: MediaFilter): List<MediaItem> = items
    }

    private class FakeExporter(
        var behavior: suspend (ClipDraft, File, (Int) -> Unit) -> Unit = { _, out, _ -> out.writeText("video") },
    ) : ClipExporter {
        var calls = 0
        var lastDraft: ClipDraft? = null
        override suspend fun export(draft: ClipDraft, output: File, onProgress: (Int) -> Unit) {
            calls++
            lastDraft = draft
            behavior(draft, output, onProgress)
        }
    }

    private class FakeSaver(var result: String? = "content://media/external/video/9") : MediaSaver {
        val saved = mutableListOf<Pair<File, String>>()
        override suspend fun saveVideo(file: File, title: String): String? {
            saved += file to title
            return result
        }
    }

    private class FakeNotifier : ClipExportNotifier {
        val progress = mutableListOf<Int>()
        val finished = mutableListOf<EditTask>()
        val failed = mutableListOf<Pair<EditTask, String>>()
        override fun progress(task: EditTask, percent: Int) { progress += percent }
        override fun finished(task: EditTask) { finished += task }
        override fun failed(task: EditTask, reason: String) { failed += task to reason }
    }

    // ---- 素材與草稿 ----

    private fun video(id: Long, durationMs: Long) =
        ClipSource(id, "content://media/$id", SourceKind.VIDEO, 1_000L * id, durationMs, 1080, 1920, 0)

    private fun photo(id: Long) =
        ClipSource(id, "content://media/$id", SourceKind.PHOTO, 1_000L * id, null, 1080, 1920, 0)

    private fun item(id: Long) = MediaItem(
        id = id, uri = "content://media/$id", kind = MediaKind.PHOTO,
        takenAtMs = 1_000L * id, durationMs = null, width = 1080, height = 1920, rotation = 0,
    )

    private fun draftOf(sources: List<ClipSource>, segments: List<Segment>) =
        ClipDraft(ClipOrientation.PORTRAIT, sources, EditHistory(Timeline(segments)))

    /** 一支 10 分鐘的影片（空間門檻 1.5 GB）。 */
    private val longDraft = draftOf(listOf(video(1, 600_000)), listOf(Segment(1, 0, 600_000)))

    /** 一張照片＋一張照片，3 秒各一。 */
    private val twoPhotos = draftOf(listOf(photo(1), photo(2)), listOf(Segment(1, 0, 3000), Segment(2, 0, 3000)))

    private val repo = RecordingRepo()
    private val library = FakeLibrary(listOf(item(1), item(2)))
    private val exporter = FakeExporter()
    private val saver = FakeSaver()
    private val notifier = FakeNotifier()
    private var free = 10_000_000_000L

    private fun runner() = ClipExportRunner(
        repo = repo,
        library = library,
        exporter = exporter,
        saver = saver,
        notifier = notifier,
        dirFor = { File(tmp.root, "edits/$it") },
        freeBytes = { free },
        now = { 123L },
    )

    private fun outFor(id: Long) = File(tmp.root, "edits/$id/out.mp4")

    private suspend fun start(draft: ClipDraft = twoPhotos, autoUpload: Boolean = true): Long {
        val id = repo.startEditTask(draft, "我的片", 1L)
        repo.setEditTaskAutoUpload(id, autoUpload, 1L)
        return id
    }

    private suspend fun task(id: Long): EditTask? = repo.editTask(id).first()

    // ---- 失敗與檢查 ----

    @Test fun `空間不足就失敗並說還差多少`() = runTest {
        free = 1_000_000_000
        val id = start(longDraft)

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("空間不足，還需要 500 MB", t.failReason)
        assertEquals(0, exporter.calls)
        assertEquals(listOf("空間不足，還需要 500 MB"), notifier.failed.map { it.second })
        assertFalse(outFor(id).exists())
    }

    @Test fun `素材全不見就失敗`() = runTest {
        library.items = listOf(item(99)) // 清單不是空的，但草稿用的素材都不在
        val id = start()

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("素材都已不存在", t.failReason)
        assertEquals(0, exporter.calls)
        assertEquals(listOf("素材都已不存在"), notifier.failed.map { it.second })
    }

    @Test fun `清單整份空不修剪而是失敗`() = runTest {
        library.items = emptyList() // 沒權限或查詢失敗時 MediaLibrary 回空清單
        val id = start()

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("讀不到相片和影片，請確認存取權", t.failReason)
        assertEquals(0, exporter.calls)
        // 不能把素材全當成被刪：任務裡的草稿原封不動
        assertEquals(twoPhotos.sources, t.draft.sources)
        assertEquals(twoPhotos.timeline, t.draft.timeline)
    }

    @Test fun `部分素材不見照樣輸出並記略過數`() = runTest {
        library.items = listOf(item(1)) // 素材 2 不見了
        val id = start(autoUpload = true)

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.RENDERED, t.state)
        assertEquals(1, t.skippedSources)
        // 輸出的是修剪後的草稿：只剩素材 1
        assertEquals(listOf(1L), exporter.lastDraft!!.sources.map { it.id })
        assertEquals(listOf(Segment(1, 0, 3000)), exporter.lastDraft!!.timeline.segments)
        // 通知拿到的是寫完略過數之後的任務
        assertEquals(1, notifier.finished.single().skippedSources)
    }

    // ---- 成功 ----

    @Test fun `有勾自動上傳停在輸出完成`() = runTest {
        val id = start(autoUpload = true)

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.RENDERED, t.state)
        assertEquals(100, t.progress)
        assertNull(t.galleryUri)
        assertTrue(outFor(id).exists()) // 成品留在 app 裡等檢查或上傳
        assertTrue(saver.saved.isEmpty())
        assertEquals(1, notifier.finished.size)
        assertEquals(id, notifier.finished.single().id)
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `沒勾存相簿後刪掉app裡的檔`() = runTest {
        val id = start(autoUpload = false)

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.RENDERED, t.state)
        assertEquals("content://media/external/video/9", t.galleryUri)
        assertEquals(listOf(outFor(id) to "我的片"), saver.saved)
        assertFalse(outFor(id).exists())
        assertEquals("content://media/external/video/9", notifier.finished.single().galleryUri)
    }

    @Test fun `存相簿失敗算輸出失敗`() = runTest {
        saver.result = null
        val id = start(autoUpload = false)

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("輸出失敗", t.failReason)
        assertNull(t.galleryUri)
        assertFalse(outFor(id).exists()) // 重試會從頭輸出，不留舊成品
        assertTrue(notifier.finished.isEmpty())
        assertEquals(listOf("輸出失敗"), notifier.failed.map { it.second })
    }

    @Test fun `輸出完成時以最新的勾選狀態為準`() = runTest {
        val id = start(autoUpload = true)
        exporter.behavior = { _, out, _ ->
            out.writeText("v")
            repo.setEditTaskAutoUpload(id, false, 5L) // 輸出途中使用者取消勾選
        }

        runner().run(id)

        assertNotNull(task(id)!!.galleryUri)
        assertEquals(1, saver.saved.size)
    }

    // ---- 輸出失敗、刪除、取消 ----

    @Test fun `輸出失敗刪掉半成品`() = runTest {
        exporter.behavior = { _, out, _ ->
            out.writeText("半支")
            throw ClipExportException("Transformer: Decoder init failed")
        }
        val id = start()

        runner().run(id)

        val t = task(id)!!
        assertEquals(EditTaskState.FAILED, t.state)
        assertEquals("輸出失敗", t.failReason) // 不是 Media3 的英文訊息
        assertFalse(outFor(id).exists())
        assertEquals(listOf("輸出失敗"), notifier.failed.map { it.second })
        assertTrue(notifier.finished.isEmpty())
    }

    @Test fun `輸出失敗之後進度的寫入不會蓋掉失敗`() = runTest {
        exporter.behavior = { _, _, onProgress ->
            onProgress(30)
            throw ClipExportException("boom")
        }
        val id = start()

        runner().run(id)

        assertEquals(EditTaskState.FAILED, task(id)!!.state)
    }

    @Test fun `輸出中任務被刪不會復活且刪檔`() = runTest {
        exporter.behavior = { _, out, onProgress ->
            out.writeText("video")
            onProgress(50)
            repo.deleteEditTask(1L) // 使用者在輸出途中刪掉任務
        }
        val id = start()
        assertEquals(1L, id)

        runner().run(id)

        assertNull(task(id)) // 沒有被寫回來
        assertFalse(outFor(id).exists())
        assertTrue(notifier.finished.isEmpty())
        assertTrue(notifier.failed.isEmpty())
        assertTrue(saver.saved.isEmpty())
    }

    @Test fun `輸出失敗時任務已被刪就不通知也不復活`() = runTest {
        exporter.behavior = { _, out, _ ->
            out.writeText("半支")
            repo.deleteEditTask(1L)
            throw ClipExportException("boom")
        }
        val id = start()

        runner().run(id)

        assertNull(task(id))
        assertFalse(outFor(id).exists())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `取消時刪掉半成品並把取消丟出去`() = runTest {
        exporter.behavior = { _, out, _ ->
            out.writeText("半支")
            throw CancellationException("取消導出")
        }
        val id = start()

        var cancelled = false
        try {
            runner().run(id)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertFalse(outFor(id).exists())
        assertTrue(notifier.finished.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    // ---- 進度與其他 ----

    @Test fun `進度相同不重複寫`() = runTest {
        exporter.behavior = { _, out, onProgress ->
            out.writeText("v")
            listOf(0, 10, 10, 10, 42, 42, 100).forEach(onProgress)
        }
        val id = start()

        runner().run(id)

        // 開始時寫一次 rendering 0%，之後只在百分比變動時寫
        assertEquals(
            listOf(
                EditTaskState.RENDERING to 0,
                EditTaskState.RENDERING to 10,
                EditTaskState.RENDERING to 42,
                EditTaskState.RENDERING to 100,
            ),
            repo.progressWrites,
        )
        assertEquals(listOf(0, 10, 42, 100), notifier.progress)
        assertEquals(EditTaskState.RENDERED, task(id)!!.state)
    }

    @Test fun `開始輸出時狀態是輸出中`() = runTest {
        val id = start()
        var seen: EditTaskState? = null
        exporter.behavior = { _, out, _ ->
            out.writeText("v")
            seen = task(id)!!.state
        }

        runner().run(id)

        assertEquals(EditTaskState.RENDERING, seen)
    }

    @Test fun `輸出前先刪掉重試留下的舊檔並建好資料夾`() = runTest {
        val id = start()
        outFor(id).also { it.parentFile!!.mkdirs() }.writeText("上次的半成品")
        var existedAtStart: Boolean? = null
        exporter.behavior = { _, out, _ ->
            existedAtStart = out.exists()
            out.writeText("v")
        }

        runner().run(id)

        assertEquals(false, existedAtStart)
        assertEquals(EditTaskState.RENDERED, task(id)!!.state)
    }

    @Test fun `資料夾還沒建時也能輸出`() = runTest {
        val id = start()
        var dirExisted: Boolean? = null
        exporter.behavior = { _, out, _ ->
            dirExisted = out.parentFile!!.isDirectory
            out.writeText("v")
        }

        runner().run(id)

        assertEquals(true, dirExisted)
    }

    @Test fun `任務不存在或已經結束就什麼都不做`() = runTest {
        runner().run(999L)
        assertEquals(0, exporter.calls)

        val done = start()
        repo.setEditTaskRendered(done, 0, null, 1L)
        val failed = start()
        repo.setEditTaskFailed(failed, "輸出失敗", 1L)

        runner().run(done)
        runner().run(failed)

        assertEquals(0, exporter.calls)
        assertTrue(notifier.finished.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test fun `被中斷留在輸出中的任務可以重新輸出`() = runTest {
        val id = start()
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 60, 1L)

        runner().run(id)

        assertEquals(EditTaskState.RENDERED, task(id)!!.state)
        assertEquals(1, exporter.calls)
    }

    @Test fun `素材整份清單為空但草稿也沒素材時不當成讀不到`() = runTest {
        library.items = emptyList()
        val id = start(draftOf(emptyList(), emptyList()))

        runner().run(id)

        // 草稿本來就沒有素材：不是讀取失敗，而是「素材都已不存在」
        assertEquals("素材都已不存在", task(id)!!.failReason)
    }
}
