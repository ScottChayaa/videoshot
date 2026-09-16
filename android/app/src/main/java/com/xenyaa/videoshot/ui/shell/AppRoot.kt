package com.xenyaa.videoshot.ui.shell

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.di.AppContainer
import com.xenyaa.videoshot.ui.common.ComingSoonScreen
import com.xenyaa.videoshot.ui.edit.ShotEditSheet
import com.xenyaa.videoshot.ui.home.HomeScreen
import com.xenyaa.videoshot.ui.home.HomeViewModel
import com.xenyaa.videoshot.ui.lightbox.LightboxActions
import com.xenyaa.videoshot.ui.lightbox.LightboxScreen
import com.xenyaa.videoshot.ui.lightbox.shareTextOf
import com.xenyaa.videoshot.wizard.WizardScreen
import com.xenyaa.videoshot.wizard.WizardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private val NavSaver = Saver<NavState, String>(
    save = { NavCodec.encode(it) },
    restore = { NavCodec.decode(it) },
)

/**
 * 組裝點：導覽狀態、各分頁的內容、系統返回鍵。
 *
 * @param onExitApp 已經在最外層還按返回 —— 交還給系統（結束 Activity）
 */
@Composable
fun AppRoot(container: AppContainer, onExitApp: () -> Unit) {
    var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(NavState()) }

    val homeVm: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container.libraryRepo) as T
        },
        key = "home",
    )
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    val homeListState = rememberLazyGridState()

    // 精靈的 VM 建在這裡（不是 CaptureTab 裡）—— finished 是沒有 replay 的 SharedFlow，
    // 只有切到取圖分頁才組合的地方收集會漏掉事件；同一個 key 仍確保實例不重複
    val wizardFactory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WizardViewModel(
                    data = container.wizardData,
                    frameSourceFactory = { container.frameSourceFor(it) },
                    strength = container.settings.filterStrength,
                    hintSeen = container.settings.gridHintSeen,
                    onHintSeen = { container.settings.markGridHintSeen() },
                    manualImages = { container.manualImagesFor(it) },
                    captureFor = { container.captureFor(it) },
                    today = { LocalDate.now().toString() },
                ) as T
        }
    }
    val wizardVm: WizardViewModel = viewModel(factory = wizardFactory, key = "wizard")

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scrollToMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val lightboxHintSeen by container.settings.lightboxHintSeen.collectAsStateWithLifecycle(initialValue = true)
    // 【編輯圖資】掛在 Lightbox 外層，不是 LightboxActions 裡直接開 —— sheet 需要自己的
    // produceState（地點／標籤建議、既有圖資），Lightbox 本身不該知道這些
    var editing by remember { mutableStateOf<ShotRow?>(null) }

    BackHandler { nav.pop()?.let { nav = it } ?: onExitApp() }

    // 取圖完成：回首頁、記下要捲到的月份、重新整理、跳 snackbar（手冊 §四第三步最後一條）
    LaunchedEffect(wizardVm) {
        wizardVm.finished.collect { done ->
            homeVm.reload()
            scrollToMonth = monthOf(done.eventDate)
            nav = nav.select(Tab.HOME)
            snackbarHostState.showSnackbar("已新增 ${done.count} 張")
        }
    }

    // Lightbox 蓋掉整個外殼（連底部導覽一起），所以判斷放在 AppShell 外面（規格第六節）
    when (val dest = nav.current) {
        is Dest.Lightbox -> LightboxScreen(
            items = homeState.items,
            total = homeState.total,
            startIndex = dest.startIndex,
            loader = container.thumbLoader,
            hintSeen = lightboxHintSeen,
            onHintSeen = { scope.launch { container.settings.markLightboxHintSeen() } },
            onClose = { nav.pop()?.let { nav = it } },
            onLoadMore = homeVm::loadMore,
            // 把目前這一張寫回導覽堆疊：轉螢幕或被系統回收重建之後，回來還在同一張
            onIndexChange = { index -> nav = nav.pop()?.push(Dest.Lightbox(index)) ?: nav },
            actions = LightboxActions(
                // 詳情頁是階段 9、分類是階段 8。按鈕照畫（分層才驗得了），但要說得出為什麼還沒反應
                onPlay = { scope.launch { snackbarHostState.showSnackbar("播放頁在階段 9") } },
                onAddToFolder = { scope.launch { snackbarHostState.showSnackbar("分類在階段 8") } },
                onShare = { shot ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareTextOf(shot))
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                onEdit = { editing = it },
                onDelete = { /* Task 11 接上 */ },
            ),
        )

        Dest.Root -> AppShell(nav = nav, onSelectTab = { nav = nav.select(it) }, snackbarHostState = snackbarHostState) { tab ->
            when (tab) {
                Tab.HOME -> HomeScreen(
                    state = homeState,
                    loader = container.thumbLoader,
                    listState = homeListState,
                    onOpen = { nav = nav.push(Dest.Lightbox(it)) },
                    onLoadMore = homeVm::loadMore,
                    onPickMonth = homeVm::setFilter,
                    scrollToMonth = scrollToMonth,
                    onScrolledToMonth = { scrollToMonth = null },
                    // 查詢頁是階段 10。點了沒反應會被當成壞掉，先說清楚
                    onFacetClick = { _, facet ->
                        scope.launch { snackbarHostState.showSnackbar("「${facet.name}」的查詢在階段 10") }
                    },
                )
                Tab.SEARCH -> ComingSoonScreen("查詢", "標籤與地點的查詢會在階段 10 做好")
                Tab.FOLDERS -> ComingSoonScreen("分類", "資料夾會在階段 8 做好")
                Tab.ACCOUNT -> ComingSoonScreen("帳號", "備份、設定與標籤管理會在階段 11～12 做好")
                Tab.CAPTURE -> WizardScreen(
                    vm = wizardVm,
                    haptics = container.haptics,
                    onExit = { nav = nav.select(nav.returnTo) },
                )
            }
        }
    }

    // Lightbox 與首頁的【編輯圖資】共用同一顆 sheet（Task 9），掛在 nav 的 when 外面 ——
    // 開著編輯時 Lightbox／首頁都可能是目前畫面，不屬於任何一支分支
    editing?.let { shot ->
        EditingSheet(
            shot = shot,
            container = container,
            homeVm = homeVm,
            scope = scope,
            snackbarHostState = snackbarHostState,
            onDismiss = { editing = null },
        )
    }
}

/**
 * 就地編輯一張圖的圖資（Lightbox 的【編輯圖資】、之後首頁也會共用）。
 * 從 [AppRoot] 抽出來是因為它自成一段完整流程 —— 撈既有圖資與建議、存檔、
 * 把存檔結果同步回首頁快取、跳 snackbar —— 掛在 [AppRoot] 主體裡會把導覽分派
 * 和這段編輯流程攪在一起，兩件事其實互不相干。
 */
@Composable
private fun EditingSheet(
    shot: ShotRow,
    container: AppContainer,
    homeVm: HomeViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val details by produceState(ShotDetails(shot.eventDate), shot) {
        value = ShotDetails(
            eventDate = shot.eventDate,
            place = shot.place,
            description = shot.description,
            tags = container.libraryRepo.tagsOfShot(shot.id),
        )
    }
    val places by produceState(emptyList<String>()) { value = container.libraryRepo.distinctPlaces() }
    val allTags by produceState(emptyList<String>()) { value = container.libraryRepo.allTagNames() }
    ShotEditSheet(
        details = details,
        placeSuggestions = places,
        tagSuggestions = allTags,
        onDismiss = onDismiss,
        onSave = { patch ->
            onDismiss()
            scope.launch {
                container.libraryRepo.patchShots(
                    listOf(shot.id),
                    ShotPatch(
                        eventDate = patch.eventDate,
                        place = patch.place,
                        description = patch.description,
                        tagIds = null,
                        tagNames = patch.tags,
                    ),
                )
                // 存檔後把這張圖寫回首頁快取 —— 不然使用者回到首頁還會看到編輯前的舊圖資
                container.libraryRepo.shotById(shot.id)?.let(homeVm::onShotChanged)
                snackbarHostState.showSnackbar("已儲存")
            }
        },
    )
}
