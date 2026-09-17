package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.CropOutcome
import com.xenyaa.videoshot.wizard.FakeHaptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `AppRoot` 是階段 7 每個任務的接線都會經過、卻沒有任何任務的測試單獨照到的地方
 * （最終複審的結論：找到的每個跨任務缺陷都活在 `AppRoot` 或 `HomeViewModel`）。
 * 這裡釘住首頁縮圖牆 ↔ Lightbox ↔ 編輯 sheet 這一段接線本身，不重複底下各畫面
 * 自己的單元測試（`LightboxScreenTest`／`ShotEditSheetTest`……）已經照到的細節。
 *
 * 依賴收斂成 [AppRootDeps]（見它的 KDoc）——這裡用假的 `LibraryRepo`／`Thumbs`／`CacheRepo`
 * 組出一份真正能動的 `ShotDeleter`／`ThumbLoader`，不再假一層，不碰 Room／DataStore。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootLightboxTest {

    @get:Rule val compose = createComposeRule()

    private fun row(id: Long, atSec: Double) = ShotRow(
        id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-05", place = null, description = null,
    )

    /** `HomeViewModel` 自己的分頁／篩選邊界已經有 `HomeViewModelTest` 釘住，這裡只要撈得到資料。 */
    private class FakeLibraryRepo(seed: List<ShotRow>) : LibraryRepo {
        var items: List<ShotRow> = seed
        var nextId = 100L
        val patchCalls = mutableListOf<Pair<List<Long>, ShotPatch>>()
        val deleteCalls = mutableListOf<Long>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(items, null)
        override suspend fun monthCounts(): List<MonthCount> = emptyList()
        override suspend fun shotsOfVideo(videoId: String) = items.filter { it.videoId == videoId }
        override suspend fun shotById(id: Long) = items.find { it.id == id }
        override suspend fun shotCount(upToMonth: String?) = items.size
        override suspend fun monthFacets(month: String): List<MonthFacet> = emptyList()
        override suspend fun tagsOfShot(shotId: Long): List<String> = emptyList()
        override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> = emptyList()
        override suspend fun distinctPlaces(): List<String> = emptyList()
        override suspend fun allTagNames(): List<String> = emptyList()
        override suspend fun patchShots(ids: List<Long>, patch: ShotPatch) {
            patchCalls += ids to patch
            items = items.map { row ->
                if (row.id !in ids) row
                else row.copy(
                    eventDate = patch.eventDate ?: row.eventDate,
                    place = patch.place ?: row.place,
                    description = patch.description ?: row.description,
                )
            }
        }
        override suspend fun deleteShot(id: Long) {
            deleteCalls += id
            items = items.filterNot { it.id == id }
        }
        override suspend fun deleteVideo(videoId: String) { items = items.filterNot { it.videoId == videoId } }
        override suspend fun createFolder(parentId: Long?, name: String): Long = 0L
        override suspend fun shotImage(shotId: Long): ByteArray? = null
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
    }

    private class FakeCacheRepo : CacheRepo {
        override suspend fun putThumbStates(states: List<ThumbStateEntity>) = Unit
        override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? = null
        override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> = emptyList()
        override suspend fun forgetVideoThumbs(videoId: String) = Unit
        override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) = Unit
        override suspend fun saveDraft(draft: DraftEntity) = Unit
        override suspend fun currentDraft(): DraftEntity? = null
        override suspend fun clearDraft() = Unit
        override suspend fun clearAll() = Unit
    }

    /** 每張圖都回得出一個假的本機檔案來源，這樣才能透過解碼次數判斷 [ThumbLoader] 有沒有被清掉。 */
    private class FakeThumbs : Thumbs {
        override suspend fun thumbFor(shot: ShotRow) = ThumbSource.LocalFile(File("/fake/${shot.id}"))
        override fun fileOf(key: ThumbKey) = File("/unused")
        override fun exists(key: ThumbKey) = false
        override suspend fun delete(key: ThumbKey) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
    }

    private class NoopWizardData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces(): List<String> = emptyList()
        override suspend fun allTagNames(): List<String> = emptyList()
        override suspend fun cropThumbs(videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit) =
            CropOutcome(emptyList(), emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>): List<Long> = emptyList()
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
        override suspend fun clearDraft(videoId: String) = Unit
    }

    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true) // 提示已看過：測試不用先滑一次把提示蓋掉
        override suspend fun markLightboxHintSeen() = Unit
    }

    private class Fixture(seed: List<ShotRow>) : AppRootDeps {
        val repo = FakeLibraryRepo(seed)
        private val cache = FakeCacheRepo()
        val thumbs = FakeThumbs()
        var decodeCalls = 0

        override val libraryRepo: LibraryRepo = repo
        override val thumbLoader = ThumbLoader(
            thumbs = thumbs,
            decodeFile = { decodeCalls++; ImageBitmap(1, 1) },
            decodeBytes = { null },
            cover = { null },
        )
        override val shotDeleter = ShotDeleter(repo, thumbs, cache, Dispatchers.Default)
        override val wizardData: WizardData = NoopWizardData()
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = FakeShellSettings()
        override fun frameSourceFor(video: LoadedVideo): FrameSource = error("這組測試不碰精靈第二步")
        override fun manualImagesFor(videoId: String) = error("這組測試不碰手動圖")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
    }

    private fun show(seed: List<ShotRow> = (1L..3L).map { row(it, it * 10.0) }): Fixture {
        val fixture = Fixture(seed)
        compose.setContent {
            VideoshotTheme {
                AppRoot(container = fixture, onExitApp = {})
            }
        }
        return fixture
    }

    // ---- 1：點縮圖開啟 Lightbox，底部導覽整個從畫面上消失 ----

    @Test
    fun 點第二張縮圖會停在第二張_底部導覽消失() {
        show()
        compose.onNodeWithContentDescription("片段縮圖 00:20").performClick()
        compose.onNodeWithText("第 2 / 共 3 張").assertIsDisplayed()
        // assertDoesNotExist：不是被蓋住，是這一輪組合裡整個沒有這個節點（手冊 §六）
        for (label in listOf("首頁", "查詢", "取圖", "分類", "帳號")) {
            compose.onNodeWithText(label).assertDoesNotExist()
        }
    }

    // ---- 2：關閉 Lightbox 回到首頁分頁，縮圖牆還在 ----

    @Test
    fun 關閉_Lightbox_回到首頁分頁_縮圖牆還在() {
        show()
        compose.onNodeWithContentDescription("片段縮圖 00:10").performClick()
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithText("首頁").assertIsDisplayed()
        compose.onNodeWithContentDescription("片段縮圖 00:10").assertIsDisplayed()
        compose.onNodeWithContentDescription("片段縮圖 00:20").assertIsDisplayed()
    }

    /**
     * 3：滑動換張要把目前位置寫回導覽堆疊，但那一疊**只能有一層 Lightbox**，不能每滑一次疊一層
     * （AppRoot 的 onIndexChange：一定要先確認堆疊頂還是 Lightbox 才能換掉它，見 AppRoot.kt 的註解）。
     *
     * `nav` 是 `AppRoot` 內部的 `rememberSaveable` 私有狀態，測試碰不到，所以這裡只能驗**外顯行為**：
     * 滑了兩次（等於換了兩次目前索引）之後，**按一次**關閉就要整個退出 Lightbox。如果每滑一次都
     * 往堆疊多推一層，一次關閉只會退一層，還會停在 Lightbox 的某個索引上，不會看到首頁。
     */
    @Test
    fun 滑動多次之後按一次關閉就退出_不是疊了好幾層() {
        show()
        compose.onNodeWithContentDescription("片段縮圖 00:10").performClick()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("第 3 / 共 3 張").assertIsDisplayed()

        compose.onNodeWithContentDescription("關閉").performClick()

        compose.onNodeWithText("首頁").assertIsDisplayed()
        compose.onNodeWithText("第 3 / 共 3 張").assertDoesNotExist()
    }

    // ---- 5：刪除要讓首頁清單跟縮圖快取都真的清乾淨 ----

    @Test
    fun 刪除會從首頁清單移除_也會清掉縮圖快取() {
        val fixture = show()
        val target = fixture.repo.items[1] // id=2，00:20

        // 開圖之前先讓 loader 快取住這張圖：之後才分得出「快取命中」跟「重新解碼」
        runBlocking { fixture.thumbLoader.load(target) }
        val decodesBeforeDelete = fixture.decodeCalls
        assertTrue("要先確定真的快取住了，不然後面的比較沒有意義", decodesBeforeDelete >= 1)

        compose.onNodeWithContentDescription("片段縮圖 00:20").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()

        assertEquals("repo 要真的收到刪除", listOf(2L), fixture.repo.deleteCalls)
        // 刪到剩兩張，Lightbox 沒有關（不是刪到最後一張），回關閉鍵退回首頁再確認清單
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithContentDescription("片段縮圖 00:20").assertDoesNotExist()
        compose.onNodeWithContentDescription("片段縮圖 00:10").assertIsDisplayed()

        // 快取沒清掉的話這裡會是命中（decodeCalls 不動）；清掉了才會重新解碼一次
        val decodesAfterEvict = runBlocking {
            fixture.thumbLoader.load(target)
            fixture.decodeCalls
        }
        assertEquals(
            "evict() 沒被呼叫的話，這裡讀到的還會是刪除前快取住的舊圖",
            decodesBeforeDelete + 1,
            decodesAfterEvict,
        )
    }

    // ---- 6：階段 8／9 還沒做好的兩個按鈕要有反應，不是靜默不做事 ----
    //
    // 兩個都**不關 Lightbox 就直接斷言**：Lightbox 換掉整個 AppShell，原本只有 AppShell
    // 的 Scaffold 裡才有 SnackbarHost，訊息要等關掉 Lightbox 回到首頁才看得到——使用者
    // 在 Lightbox 裡按下去的當下等於什麼都沒發生。這裡疊了一顆自己的 SnackbarHost
    // 修掉這個洞（AppRoot.kt 的 Dest.Lightbox 分支），這兩個測試就是釘住修好之後的樣子：
    // 訊息要在 Lightbox**還開著**的時候就看得到。

    @Test
    fun 播放這一段還沒做好_跳出說明用的_snackbar() {
        show()
        compose.onNodeWithContentDescription("片段縮圖 00:10").performClick()
        compose.onNodeWithText("播放這一段").performClick()
        compose.onNodeWithText("播放頁在階段 9").assertIsDisplayed()
    }

    @Test
    fun 加入分類還沒做好_跳出說明用的_snackbar() {
        show()
        compose.onNodeWithContentDescription("片段縮圖 00:10").performClick()
        compose.onNodeWithContentDescription("加入分類").performClick()
        compose.onNodeWithText("分類在階段 8").assertIsDisplayed()
    }

    // ---- 7：編輯 sheet 存檔要走 repo，而且要同步回首頁清單 ----

    @Test
    fun 編輯圖資存檔會寫進_repo_也會同步回首頁清單() {
        val fixture = show()

        compose.onNodeWithContentDescription("片段縮圖 00:10").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("編輯圖資").performClick()
        // 改描述而不是地點：縮圖牆的 contentDescription 是「有描述用描述、沒有就用秒數」
        // （HomeScreen 的 labelOf），改這一欄才看得出首頁清單有沒有真的同步到新值。
        compose.onNodeWithContentDescription("描述").performTextReplacement("在龍洞浮潛")
        compose.onNodeWithText("儲存").performClick()
        compose.waitForIdle()

        assertEquals(1, fixture.repo.patchCalls.size)
        val (ids, patch) = fixture.repo.patchCalls.single()
        assertEquals(listOf(1L), ids)
        assertEquals("在龍洞浮潛", patch.description)

        // 「已儲存」跟播放／加入分類同一個洞——EditingSheet 掛在 Lightbox 之外、
        // onSave 存檔完直接跳 snackbar，存檔當下 Lightbox 還沒關。沒有補那顆 SnackbarHost
        // 之前，這句話要等關掉 Lightbox 才看得到，存檔在使用者眼裡像是什麼都沒發生。
        compose.onNodeWithText("已儲存").assertIsDisplayed()

        // 存檔後 EditingSheet 呼叫 homeVm.onShotChanged 把新值同步回首頁快取；
        // Lightbox 這時還開著，退回首頁要能立刻看到新描述 —— 不必等下一次整包 reload
        // （沒有同步的話，這裡還會是舊的「片段縮圖 00:10」）
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithContentDescription("在龍洞浮潛").assertIsDisplayed()
        compose.onNodeWithContentDescription("片段縮圖 00:10").assertDoesNotExist()
    }
}
