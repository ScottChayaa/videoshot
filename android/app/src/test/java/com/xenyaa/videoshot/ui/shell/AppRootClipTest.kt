package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.FacetUsage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.query.FakeGeminiClient
import com.xenyaa.videoshot.ui.account.AccountDeps
import com.xenyaa.videoshot.ui.clip.ClipDeps
import com.xenyaa.videoshot.ui.clip.ClipPreview
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.defaultClipTitle
import com.xenyaa.videoshot.core.timeline.usedSources
import kotlinx.coroutines.flow.first
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.media.mediaPermissionsFor
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.xenyaa.videoshot.ui.account.FakeAccountDeps
import com.xenyaa.videoshot.query.QueryResolver
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.CropOutcome
import com.xenyaa.videoshot.wizard.FakeHaptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 剪輯入口的接線：【新增剪輯】→ 選方向 → 選素材（沒有底部導覽）→ 【下一步】→ 編輯佔位 → 返回選取還在。
 * fixture 手法同 [AppRootPlayTest]；`clipDeps` 用可控的假實作。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootClipTest {

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(emptyList<ShotRow>(), null)
        override suspend fun monthCounts() = emptyList<com.xenyaa.videoshot.data.repo.model.MonthCount>()
    }

    private class FakeClipDeps(
        var granted: Set<String>,
        override val cacheRepo: com.xenyaa.videoshot.data.repo.FakeCacheRepo = com.xenyaa.videoshot.data.repo.FakeCacheRepo(),
    ) : ClipDeps {
        val all = (1L..3L).map { MediaItem(it, "content://media/$it", MediaKind.PHOTO, it * 1000, null, 1080, 1920, 0) }
        var listCalls = 0
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> { listCalls++; return all }
        }
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = granted
        override fun now() = 0L
        /** 【導出】【開始】排的輸出工作（編號）。 */
        val enqueued = mutableListOf<Long>()
        override fun enqueueExport(taskId: Long) { enqueued += taskId }
        /** 取消流程呼叫到的順序（取消工作、補排）。 */
        val exportCalls = mutableListOf<String>()
        override suspend fun cancelExport(taskId: Long) { exportCalls += "cancel$taskId" }
        override suspend fun resumeExports() { exportCalls += "resume" }
        /** 刪任務時一併刪掉 app 裡成品的任務編號。 */
        val deletedFiles = mutableListOf<Long>()
        override suspend fun deleteExportFiles(taskId: Long) { deletedFiles += taskId }
        /** 啟動時清理成品資料夾的次數。 */
        var cleanups = 0
        override suspend fun cleanupExportFiles() { cleanups++ }
        /** 導出畫面告訴通知「正在看哪一支」的紀錄（null＝離開）。 */
        val viewing = mutableListOf<Long?>()
        override fun viewingExport(taskId: Long?) { viewing += taskId }
        /** 收掉結果通知的任務編號。 */
        val dismissed = mutableListOf<Long>()
        override fun dismissExportResult(taskId: Long) { dismissed += taskId }
        var notificationMissing = false
        override fun notificationPermissionMissing() = notificationMissing
        val preview = NoOpPreview()
        override fun newPreview(context: android.content.Context): ClipPreview = preview
    }

    /** 不碰 Media3 的假預覽（Robolectric 裡不建真的 CompositionPlayer）。 */
    private class NoOpPreview : ClipPreview {
        override val positionMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val failed = kotlinx.coroutines.flow.MutableStateFlow(false)
        override fun show(draft: ClipDraft, positionMs: Long) = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun play() = Unit
        var pauseCalls = 0
        override fun pause() { pauseCalls++; isPlaying.value = false }
        override fun release() = Unit
    }

    private val full = mediaPermissionsFor(34).toSet()

    private fun clipSource(id: Long) = ClipSource(id, "content://media/$id", SourceKind.PHOTO, id * 1000, null, 1080, 1920, 0)

    /** 預先存好一份三張照片（總長 0:09）的草稿，模擬上次沒編完。 */
    private fun FakeClipDeps.withDraft(): FakeClipDeps {
        kotlinx.coroutines.runBlocking {
            cacheRepo.saveClipDraft(newClipDraft(ClipOrientation.PORTRAIT, listOf(clipSource(1), clipSource(2), clipSource(3))), 0)
        }
        return this
    }

    private fun com.xenyaa.videoshot.data.repo.FakeCacheRepo.currentClipDraftBlocking(): ClipDraft? =
        kotlinx.coroutines.runBlocking { currentClipDraft() }

    /** 跟 `AppRootFoldersTest.kt`／`AppRootLightboxTest.kt` 一樣，各檔案各自私有一份（沒有共用的測試替身）。 */
    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val clipMuted = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun setClipMuted(value: Boolean) = Unit
        override val playerMuted = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun setPlayerMuted(value: Boolean) = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit
        override val restoreDecisionMade = flowOf(true)
        override suspend fun markRestoreDecisionMade() = Unit
    }

    private val repo = Repo()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = java.io.File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun deps(clip: ClipDeps): AppRootDeps = object : AppRootDeps {
        override val libraryRepo: LibraryRepo = repo
        override val clipDeps: ClipDeps = clip
        override val thumbLoader: ThumbLoader = loader
        override val facetUsage: FacetUsage by lazy { FacetUsage(libraryRepo, FakeCacheRepo()) { 0L } }
        override val shotDeleter: ShotDeleter = ShotDeleter(
            repo,
            thumbs = object : Thumbs {
                override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
                override fun fileOf(key: ThumbKey) = java.io.File("/unused")
                override fun exists(key: ThumbKey) = false
                override suspend fun delete(key: ThumbKey) = Unit
                override suspend fun deleteVideo(videoId: String) = Unit
            },
            cache = object : CacheRepo {
                override suspend fun putThumbStates(states: List<ThumbStateEntity>) = Unit
                override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? = null
                override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> = emptyList()
                override suspend fun forgetVideoThumbs(videoId: String) = Unit
                override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) = Unit
                override suspend fun countByState(state: String): Int = 0
                override suspend fun lostThumbs(): List<ThumbStateEntity> = emptyList()
                override suspend fun resetLostToMissing(now: Long) = Unit
                override suspend fun saveDraft(draft: DraftEntity) = Unit
                override suspend fun currentDraft(): DraftEntity? = null
                override suspend fun clearDraft() = Unit
                override suspend fun touchFacets(refs: Collection<com.xenyaa.videoshot.data.repo.model.FacetRef>, usedAt: Long) = Unit
                override suspend fun facetRecent(): Map<com.xenyaa.videoshot.data.repo.model.FacetRef, Long> = emptyMap()
                override suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long) = Unit
                override suspend fun forgetFacetRecent(kind: Int, id: Long) = Unit
                override suspend fun saveClipDraft(draft: com.xenyaa.videoshot.core.timeline.ClipDraft, now: Long) = Unit
                override suspend fun currentClipDraft(): com.xenyaa.videoshot.core.timeline.ClipDraft? = null
                override suspend fun clearClipDraft() = Unit
                override suspend fun startEditTask(draft: com.xenyaa.videoshot.core.timeline.ClipDraft, title: String, autoUpload: Boolean, now: Long): Long = 0L
                override fun editTasks(): kotlinx.coroutines.flow.Flow<List<com.xenyaa.videoshot.data.repo.model.EditTask>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override fun editTask(id: Long): kotlinx.coroutines.flow.Flow<com.xenyaa.videoshot.data.repo.model.EditTask?> = kotlinx.coroutines.flow.flowOf(null)
                override suspend fun pendingEditTaskIds(): List<Long> = emptyList()
                override suspend fun setEditTaskProgress(id: Long, state: com.xenyaa.videoshot.data.repo.model.EditTaskState, progress: Int, now: Long) = Unit
                override suspend fun setEditTaskRendered(id: Long, skipped: Int, galleryUri: String?, now: Long) = Unit
                override suspend fun setEditTaskFailed(id: Long, reason: String, step: com.xenyaa.videoshot.data.repo.model.EditTaskStep, now: Long) = Unit
                override suspend fun setEditTaskAutoUpload(id: Long, on: Boolean, now: Long) = Unit
                override suspend fun requeueEditTask(id: Long, now: Long) = Unit
                override suspend fun processingEditTaskIds(): List<Long> = emptyList()
                override fun doneEditTaskCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun setEditTaskReadyToUpload(id: Long, skipped: Int, now: Long) = Unit
                override suspend fun setEditTaskUploadUrl(id: Long, url: String?, now: Long) = Unit
                override suspend fun setEditTaskUploaded(id: Long, videoId: String, now: Long) = Unit
                override suspend fun setEditTaskDone(id: Long, now: Long) = Unit
                override suspend fun retryEditTask(id: Long, now: Long): com.xenyaa.videoshot.data.repo.model.EditTaskState? = null
                override suspend fun deleteEditTask(id: Long) = Unit
                override suspend fun clearAll() = Unit
            },
            io = Dispatchers.Unconfined,
        )
        override val wizardData: WizardData = object : WizardData {
            override suspend fun watchPage(videoId: String) = WatchPage(
                FetchResult.OK,
                VideoMeta(videoId, "旅行影片", "c", "2026-03-05T00:00:00Z", 600, "public", true),
                null,
            )
            override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
            override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
            override suspend fun distinctPlaces() = emptyList<String>()
            override suspend fun tagSuggestions() = emptyList<String>()
            override suspend fun cropThumbs(videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit) =
                CropOutcome(emptyList(), emptyList())
            override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = emptyList<Long>()
            override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
            override suspend fun saveDraft(json: String) = Unit
            override suspend fun currentDraft(): String? = null
            override suspend fun clearDraft(videoId: String) = Unit
        }
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = FakeShellSettings()
        override val queryResolver: QueryResolver = QueryResolver(
            gemini = FakeGeminiClient(null),
            geminiKey = { null },
            vocabulary = { QueryVocabulary(emptyList(), emptyList()) },
        )
        // 【重新取圖】會開精靈第二步
        override fun frameSourceFor(video: LoadedVideo): FrameSource = FakeFrameSource.of(frameCount = 1, intervalSec = 0.0)
        override fun manualImagesFor(videoId: String) = throw UnsupportedOperationException("測試不用到")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
        override val accountDeps: AccountDeps = FakeAccountDeps()
        override suspend fun listBackups() = error("這組測試不碰還原")
        override suspend fun restore(backup: com.xenyaa.videoshot.backup.RemoteBackup) = error("這組測試不碰還原")
    }

    private fun openPicker(clip: ClipDeps) {
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("直式").performClick()
        compose.waitForIdle()
    }

    @Test
    fun 新增剪輯選直式後進到選素材且沒有底部導覽() {
        openPicker(FakeClipDeps(full))

        compose.onNodeWithText("選素材").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()
    }

    @Test
    fun 選完素材下一步進編輯_返回回剪輯頁_草稿還在() {
        val clip = FakeClipDeps(full)
        openPicker(clip)
        compose.onNodeWithText("下一步").assertIsNotEnabled()

        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onAllNodesWithContentDescription("照片")[1].performClick()
        compose.onNodeWithText("下一步（2）").performClick()
        compose.waitForIdle()

        // 編輯畫面：沒有底部導覽，草稿已存
        compose.onNodeWithText("編輯").assertIsDisplayed()
        compose.onNodeWithText("/ 0:06", substring = true).assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()
        assertEquals(2, clip.cacheRepo.currentClipDraftBlocking()!!.sources.size)

        // 返回直接回剪輯頁（選素材不在堆疊裡），「編輯中」在最上面
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("選素材").assertDoesNotExist()
        compose.onNodeWithText("編輯中").assertIsDisplayed()
        compose.onNodeWithText("直式・2 個素材・0:06").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertIsDisplayed()
    }

    @Test
    fun 系統返回鍵從編輯回剪輯頁() {
        val clip = FakeClipDeps(full)
        openPicker(clip)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("編輯中").assertIsDisplayed()
        compose.onNodeWithText("選素材").assertDoesNotExist()
    }

    @Test
    fun 點編輯中接著編輯() {
        val clip = FakeClipDeps(full).withDraft()
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("/ 0:09", substring = true).assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()
    }

    @Test
    fun 第二次進編輯不會被上一次的狀態影響() {
        val clip = FakeClipDeps(full)
        openPicker(clip)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("/ 0:03", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()

        // 放棄後再新增一份不同的草稿：編輯畫面要是新的一份，不能沿用上一次的 ViewModel
        compose.onNodeWithContentDescription("編輯中的更多操作").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").assertDoesNotExist()
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("橫式").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onAllNodesWithContentDescription("照片")[1].performClick()
        compose.onNodeWithText("下一步（2）").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("編輯").assertIsDisplayed()
        compose.onNodeWithText("/ 0:06", substring = true).assertIsDisplayed()
    }

    @Test
    fun 沒有草稿時進編輯會退回剪輯頁() {
        // 水位：草稿在進入前被別處清掉（例如放棄）時，編輯畫面讀完發現沒有就退回
        val clip = FakeClipDeps(full)
        openPicker(clip)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        kotlinx.coroutines.runBlocking { clip.cacheRepo.clearClipDraft() }

        // 「編輯中」仍留在畫面上（摘要是進剪輯頁時讀的）；點了續做 → 讀不到草稿 → 退回
        compose.onNodeWithText("編輯中").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        compose.onNodeWithText("編輯中").assertDoesNotExist()
    }

    @Test
    fun 沒有草稿退回之後再走一次正常流程_編輯畫面不會被立刻退回() {
        val clip = FakeClipDeps(full)
        openPicker(clip)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        kotlinx.coroutines.runBlocking { clip.cacheRepo.clearClipDraft() }
        // 點「編輯中」進去 → 讀不到草稿 → 退回剪輯頁（這一次的編輯 ViewModel 留下「沒有草稿」的狀態）
        compose.onNodeWithText("編輯中").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").assertDoesNotExist()

        // 再走一次：新增 → 直式 → 選素材 → 下一步
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("直式").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onAllNodesWithContentDescription("照片")[1].performClick()
        compose.onNodeWithText("下一步（2）").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("/ 0:06", substring = true).assertIsDisplayed()
        compose.onNodeWithText("新增剪輯").assertDoesNotExist()
    }

    @Test
    fun 存檔期間按返回_不換頁而且剪輯頁還回得去() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : com.xenyaa.videoshot.data.repo.FakeCacheRepo() {
            override suspend fun saveClipDraft(draft: ClipDraft, now: Long) {
                gate.await()
                super.saveClipDraft(draft, now)
            }
        }
        val clip = FakeClipDeps(full, repo)
        openPicker(clip)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()

        // 存檔還沒完成，使用者按返回回到剪輯頁
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()

        gate.complete(Unit)
        compose.waitForIdle()

        // 沒有被換頁：仍在剪輯頁，有底部導覽，也沒有編輯畫面
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertIsDisplayed()
        compose.onNodeWithText("/ 0:03", substring = true).assertDoesNotExist()
        // 再按一次系統返回是回照片分頁，不是卡在剪輯頁；草稿已存，回剪輯分頁看得到「編輯中」
        compose.onNodeWithText("照片").performClick()
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").assertIsDisplayed()
    }

    @Test
    fun 存檔失敗留在選素材_選取還在可以再按() {
        var failures = 1
        val repo = object : com.xenyaa.videoshot.data.repo.FakeCacheRepo() {
            override suspend fun saveClipDraft(draft: ClipDraft, now: Long) {
                if (failures-- > 0) throw IllegalStateException("磁碟滿了")
                super.saveClipDraft(draft, now)
            }
        }
        openPicker(FakeClipDeps(full, repo))
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("選素材").assertIsDisplayed()
        compose.onNodeWithText("下一步（1）").assertIsDisplayed()

        compose.onNodeWithText("下一步（1）").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("/ 0:03", substring = true).assertIsDisplayed()
    }

    /** 開編輯畫面（預先存好的三張照片草稿，每張 3 秒），然後假裝播放到時間軸上的 [positionMs]。 */
    private fun FakeClipDeps.openEditorPlayingAt(positionMs: Long) {
        compose.setContent { VideoshotTheme { AppRoot(deps(this@openEditorPlayingAt)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").performClick()
        compose.waitForIdle()
        preview.positionMs.value = positionMs
        preview.isPlaying.value = true
        compose.waitForIdle()
    }

    private fun com.xenyaa.videoshot.data.repo.FakeCacheRepo.segments() =
        currentClipDraftBlocking()!!.timeline.segments.map { Triple(it.sourceId, it.startMs, it.endMs) }

    @Test
    fun 播放中按分割_切在畫面上的播放位置() {
        val clip = FakeClipDeps(full).withDraft()
        clip.openEditorPlayingAt(4_500)

        // 播放頭在 VM 裡還停在 0，但畫面顯示的位置在第二張照片中間：分割要能按
        compose.onNodeWithText("分割").assertIsEnabled()
        compose.onNodeWithText("分割").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(Triple(1L, 0L, 3000L), Triple(2L, 0L, 1500L), Triple(2L, 1500L, 3000L), Triple(3L, 0L, 3000L)),
            clip.cacheRepo.segments(),
        )
        assertTrue(clip.preview.pauseCalls >= 1)
    }

    @Test
    fun 播放中按刪除_刪掉畫面上正在播的那一段() {
        val clip = FakeClipDeps(full).withDraft()
        clip.openEditorPlayingAt(4_500)

        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()

        assertEquals(listOf(Triple(1L, 0L, 3000L), Triple(3L, 0L, 3000L)), clip.cacheRepo.segments())
    }

    @Test
    fun 播放中按復原_先暫停並把播放位置寫回() {
        val clip = FakeClipDeps(full).withDraft()
        clip.openEditorPlayingAt(4_500)
        compose.onNodeWithText("分割").performClick()
        compose.waitForIdle()
        // 分割後預覽暫停了；再從 7_000（第三張照片）播放並按復原：播放頭要先移到 7_000，不是停在舊位置
        clip.preview.positionMs.value = 7_000
        clip.preview.isPlaying.value = true
        compose.waitForIdle()
        val pausesBefore = clip.preview.pauseCalls

        compose.onNodeWithContentDescription("復原").performClick()
        compose.waitForIdle()

        assertTrue(clip.preview.pauseCalls > pausesBefore)
        assertEquals(listOf(Triple(1L, 0L, 3000L), Triple(2L, 0L, 3000L), Triple(3L, 0L, 3000L)), clip.cacheRepo.segments())
        compose.onNodeWithText("0:07 / 0:09").assertIsDisplayed()
    }

    @Test
    fun 有草稿時新增剪輯先問放棄_取消後草稿還在() {
        val clip = FakeClipDeps(full).withDraft()
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()

        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertDoesNotExist()
        compose.onNodeWithText("影片方向").assertDoesNotExist()
        compose.onNodeWithText("編輯中").assertIsDisplayed()
        assertTrue(clip.cacheRepo.currentClipDraftBlocking() != null)
    }

    @Test
    fun 有草稿時新增剪輯確認放棄後清掉草稿並開選方向() {
        val clip = FakeClipDeps(full).withDraft()
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.waitForIdle()

        assertEquals(null, clip.cacheRepo.currentClipDraftBlocking())
        compose.onNodeWithText("影片方向").assertIsDisplayed()
        compose.onNodeWithText("編輯中").assertDoesNotExist()
    }

    @Test
    fun 從編輯中列放棄會清掉草稿() {
        val clip = FakeClipDeps(full).withDraft()
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("編輯中的更多操作").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.waitForIdle()

        assertEquals(null, clip.cacheRepo.currentClipDraftBlocking())
        compose.onNodeWithText("編輯中").assertDoesNotExist()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertIsDisplayed()
    }

    @Test
    fun 放棄草稿時資料庫出錯不當掉_草稿還在資料庫裡() {
        val repo = object : com.xenyaa.videoshot.data.repo.FakeCacheRepo() {
            override suspend fun clearClipDraft() = throw IllegalStateException("資料庫被鎖住")
        }
        val clip = FakeClipDeps(full, repo).withDraft()
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("編輯中的更多操作").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.waitForIdle()

        // 畫面照常運作（沒有當掉），清不掉的草稿還在資料庫裡
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        assertTrue(repo.currentClipDraftBlocking() != null)
    }

    @Test
    fun 沒權限時選素材顯示允許存取() {
        openPicker(FakeClipDeps(emptySet()))

        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()
    }

    @Test
    fun 重新從選方向進來會清掉上一次的選取() {
        openPicker(FakeClipDeps(full))
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").assertIsDisplayed()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("橫式").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }

    /** 系統權限視窗是用 startActivity 開的；Robolectric 記得最近一次開了什麼。 */
    private fun permissionRequests(): Int {
        val shadow = org.robolectric.Shadows.shadowOf(compose.activity)
        var n = 0
        while (shadow.nextStartedActivity != null) n++
        return n
    }

    @Test
    fun 沒權限進選素材會自動請求一次() {
        openPicker(FakeClipDeps(emptySet()))
        assertEquals(1, permissionRequests())
    }

    @Test
    fun 部分允許進選素材不自動請求權限() {
        openPicker(FakeClipDeps(setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")))
        assertEquals(0, permissionRequests())
        compose.onNodeWithText("選素材").assertIsDisplayed()
    }

    @Test
    fun 完整存取進選素材不請求權限() {
        openPicker(FakeClipDeps(full))
        assertEquals(0, permissionRequests())
    }

    @Test
    fun 回到前景會重查權限_從系統設定給了權限後格子出現() {
        val clip = FakeClipDeps(emptySet())
        openPicker(clip)
        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()

        // 使用者在系統設定裡開了完整存取，回到 app
        clip.granted = full
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("需要相片和影片的存取權").assertDoesNotExist()
        assertEquals(3, compose.onAllNodesWithContentDescription("照片").fetchSemanticsNodes().size)
    }

    /** 開編輯畫面（預先存好三張照片、總長 0:09 的草稿），按【導出】把確認抽屜打開。 */
    private fun openExportSheet(clip: FakeClipDeps, content: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit = { it() }) {
        compose.setContent { content { VideoshotTheme { AppRoot(deps(clip)) {} } } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("編輯中").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("導出").performClick()
        compose.waitForIdle()
    }

    private fun tasksOf(repo: com.xenyaa.videoshot.data.repo.FakeCacheRepo) =
        kotlinx.coroutines.runBlocking { repo.editTasks().first() }

    @Test
    fun 按導出開確認抽屜_有預設標題與摘要() {
        val clip = FakeClipDeps(full).withDraft()
        openExportSheet(clip)

        val expected = defaultClipTitle(clip.cacheRepo.currentClipDraftBlocking()!!.usedSources(), java.time.ZoneId.systemDefault())
        compose.onNodeWithText("0:09・直式・會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText(expected).assertIsDisplayed()
        compose.onNodeWithText("開始").assertIsDisplayed()
    }

    @Test
    fun 按開始建立任務清掉草稿並進導出中畫面() {
        val clip = FakeClipDeps(full).withDraft()
        openExportSheet(clip)
        val expected = defaultClipTitle(clip.cacheRepo.currentClipDraftBlocking()!!.usedSources(), java.time.ZoneId.systemDefault())

        compose.onNodeWithText("開始").performClick()
        compose.waitForIdle()

        val tasks = tasksOf(clip.cacheRepo)
        assertEquals(1, tasks.size)
        assertEquals(expected, tasks[0].title)
        assertEquals(null, clip.cacheRepo.currentClipDraftBlocking())
        assertEquals(listOf(tasks[0].id), clip.enqueued)
        // 進了導出中畫面：編輯畫面與抽屜都不在了
        compose.onNodeWithText("編輯").assertDoesNotExist()
        compose.onNodeWithText("開始").assertDoesNotExist()
        compose.onNodeWithText("導出").assertIsDisplayed()
        // 停在導出畫面：告訴通知正在看這一支
        assertEquals(tasks[0].id, clip.viewing.last())

        // 返回直接回剪輯頁（不再經過編輯畫面），離開後不再算在看
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        assertEquals(null, clip.viewing.last())
    }

    @Test
    fun 導出中取消確認後刪任務補排並退回剪輯頁() {
        val clip = FakeClipDeps(full).withDraft()
        openExportSheet(clip)
        compose.onNodeWithText("開始").performClick()
        compose.waitForIdle()
        val taskId = tasksOf(clip.cacheRepo).single().id
        compose.onNodeWithText("等待中…").assertIsDisplayed()
        clip.exportCalls.clear() // 打開 app 時的那一次補排不算在取消流程裡

        compose.onNodeWithText("取消導出").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()
        // 先不取消
        compose.onNodeWithText("繼續導出").performClick()
        compose.waitForIdle()
        assertTrue(clip.exportCalls.isEmpty())
        assertEquals(1, tasksOf(clip.cacheRepo).size)

        compose.onNodeWithText("取消導出").performClick()
        compose.onAllNodes(androidx.compose.ui.test.hasText("取消導出") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog()))
            .onFirst().performClick()
        compose.waitForIdle()

        // 取消正在跑的工作 → 刪任務 → 再查一次 → 刪 app 裡的資料夾 → 補排；任務不在了就退回剪輯頁（只退一層）
        assertEquals(listOf("cancel$taskId", "cancel$taskId", "resume"), clip.exportCalls)
        assertEquals(listOf(taskId), clip.deletedFiles)
        assertEquals(listOf(taskId), clip.dismissed)
        assertTrue(tasksOf(clip.cacheRepo).isEmpty())
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test
    fun 建立任務失敗留在編輯畫面草稿還在() {
        val repo = object : com.xenyaa.videoshot.data.repo.FakeCacheRepo() {
            override suspend fun startEditTask(draft: ClipDraft, title: String, autoUpload: Boolean, now: Long): Long =
                throw IllegalStateException("資料庫被鎖住")
        }
        val clip = FakeClipDeps(full, repo).withDraft()
        openExportSheet(clip)

        compose.onNodeWithText("開始").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("導出失敗，請再試一次").assertIsDisplayed()
        // 抽屜沒關、草稿還在、沒有排工作
        compose.onNodeWithText("開始").assertIsDisplayed()
        assertTrue(clip.enqueued.isEmpty())
        assertTrue(repo.currentClipDraftBlocking() != null)
        assertTrue(tasksOf(repo).isEmpty())
    }

    @Test
    fun 第一次按開始要通知權限_不管結果都繼續_第二次不再要() {
        val requested = mutableListOf<String>()
        val registry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) {
                requested += input as String
                dispatchResult(requestCode, false) // 使用者拒絕
            }
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        var failFirst = true
        val repo = object : com.xenyaa.videoshot.data.repo.FakeCacheRepo() {
            override suspend fun startEditTask(draft: ClipDraft, title: String, autoUpload: Boolean, now: Long): Long {
                if (failFirst) { failFirst = false; throw IllegalStateException("資料庫被鎖住") }
                return super.startEditTask(draft, title, autoUpload, now)
            }
        }
        val clip = FakeClipDeps(full, repo).withDraft().apply { notificationMissing = true }
        openExportSheet(clip) { inner ->
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.activity.compose.LocalActivityResultRegistryOwner provides owner,
            ) { inner() }
        }

        // 第一次：先要通知權限（被拒絕），照樣往下建立任務——這次寫入失敗，抽屜留著
        compose.onNodeWithText("開始").performClick()
        compose.waitForIdle()
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), requested)
        compose.onNodeWithText("導出失敗，請再試一次").assertIsDisplayed()

        // 第二次：問過了就不再問，直接建立
        compose.onNodeWithText("開始").performClick()
        compose.waitForIdle()
        assertEquals(1, requested.size)
        assertEquals(1, tasksOf(repo).size)
        assertEquals(1, clip.enqueued.size)
    }

    // ---- 剪輯頁的任務清單與點通知回剪輯頁（任務 9）----

    private fun FakeClipDeps.withTask(title: String = "我的旅行"): Long = kotlinx.coroutines.runBlocking {
        cacheRepo.startEditTask(newClipDraft(ClipOrientation.PORTRAIT, listOf(clipSource(1), clipSource(2))), title, true, 0)
    }

    private fun openClipTab(clip: FakeClipDeps) {
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
    }

    @Test
    fun 帶開剪輯的intent進到剪輯頁() {
        val signal = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { VideoshotTheme { AppRoot(deps(FakeClipDeps(full)), onExitApp = {}, openClipSignal = signal.intValue) } }
        compose.onNodeWithText("新增剪輯").assertDoesNotExist()

        // app 已經開著時點通知（onNewIntent）
        signal.intValue = 1
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test
    fun 冷啟動帶開剪輯的intent一開始就是剪輯頁() {
        compose.setContent { VideoshotTheme { AppRoot(deps(FakeClipDeps(full)), onExitApp = {}, openClipSignal = 1) } }
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test
    fun 點通知時人在導出中畫面也回到剪輯頁根() {
        val clip = FakeClipDeps(full)
        val taskId = clip.withTask("我的旅行")
        val signal = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { VideoshotTheme { AppRoot(deps(clip), onExitApp = {}, openClipSignal = signal.intValue) } }
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("我的旅行").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("等待中…").assertIsDisplayed()

        signal.intValue = 1
        compose.waitForIdle()
        compose.onNodeWithText("等待中…").assertDoesNotExist()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        assertEquals(1, tasksOf(clip.cacheRepo).count { it.id == taskId })
    }

    @Test
    fun 打開app時把沒做完的任務補排一次() {
        val clip = FakeClipDeps(full)
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.waitForIdle()
        assertEquals(listOf("resume"), clip.exportCalls)
        assertEquals(1, clip.cleanups) // 也清一次對不到任務的成品資料夾

        // 之後切分頁不會再補排、再清理
        compose.onNodeWithText("剪輯").performClick()
        compose.waitForIdle()
        assertEquals(listOf("resume"), clip.exportCalls)
        assertEquals(1, clip.cleanups)
    }

    @Test
    fun 剪輯頁列出任務_有任務就沒有空狀態() {
        val clip = FakeClipDeps(full)
        clip.withTask("我的旅行")
        openClipTab(clip)

        compose.onNodeWithText("我的旅行").assertIsDisplayed()
        compose.onNodeWithText("等待中").assertIsDisplayed()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertDoesNotExist()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test
    fun 點任務進導出中畫面() {
        val clip = FakeClipDeps(full)
        clip.withTask("我的旅行")
        openClipTab(clip)

        compose.onNodeWithText("我的旅行").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("等待中…").assertIsDisplayed()
        compose.onNodeWithText("取消導出").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()
        // 返回回到剪輯頁
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test
    fun 剪輯頁長按任務取消_取消工作後刪任務再補排() {
        val clip = FakeClipDeps(full)
        val taskId = clip.withTask("我的旅行")
        openClipTab(clip)
        clip.exportCalls.clear()

        compose.onNodeWithText("我的旅行").performTouchInput { longClick() }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()
        compose.onNodeWithText("取消導出").performClick()
        compose.waitForIdle()

        assertEquals(listOf("cancel$taskId", "cancel$taskId", "resume"), clip.exportCalls)
        assertEquals(listOf(taskId), clip.deletedFiles)
        assertTrue(tasksOf(clip.cacheRepo).isEmpty())
        compose.onNodeWithText("我的旅行").assertDoesNotExist()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertIsDisplayed()
    }

    @Test
    fun 剪輯頁刪除完成的任務_連app裡的成品一起刪再補排() {
        val clip = FakeClipDeps(full)
        val taskId = clip.withTask("我的旅行")
        kotlinx.coroutines.runBlocking { clip.cacheRepo.setEditTaskRendered(taskId, 0, null, 0) }
        openClipTab(clip)
        clip.exportCalls.clear()

        compose.onNodeWithText("我的旅行").performTouchInput { longClick() }
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("app 裡輸出的影片會一起刪掉。").assertIsDisplayed()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()

        assertTrue(tasksOf(clip.cacheRepo).isEmpty())
        assertEquals(listOf(taskId), clip.deletedFiles)
        assertEquals(listOf(taskId), clip.dismissed)
        assertEquals(listOf("resume"), clip.exportCalls)
    }

    @Test
    fun 剪輯頁失敗任務重試_回到等待中並重新排程() {
        val clip = FakeClipDeps(full)
        val taskId = clip.withTask("我的旅行")
        kotlinx.coroutines.runBlocking { clip.cacheRepo.setEditTaskFailed(taskId, "輸出失敗", com.xenyaa.videoshot.data.repo.model.EditTaskStep.RENDER, 0) }
        openClipTab(clip)
        compose.onNodeWithText("失敗：輸出失敗").assertIsDisplayed()

        compose.onNodeWithText("重試").performClick()
        compose.waitForIdle()

        assertEquals(EditTaskState.QUEUED, tasksOf(clip.cacheRepo).single().state)
        assertEquals(listOf(taskId), clip.enqueued)
        compose.onNodeWithText("等待中").assertIsDisplayed()
    }
}
