package com.xenyaa.videoshot.ui.shell

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.util.Log
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.statusBarsPadding
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.mediaPermissionsFor
import com.xenyaa.videoshot.ui.clip.ClipDeps
import com.xenyaa.videoshot.ui.clip.ClipPickerScreen
import com.xenyaa.videoshot.ui.clip.ClipPickerViewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.player.isLandscapeVideo
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.account.AccountScreen
import com.xenyaa.videoshot.ui.account.AccountViewModel
import com.xenyaa.videoshot.ui.account.AiRangeScreen
import com.xenyaa.videoshot.ui.account.BackupScreen
import com.xenyaa.videoshot.ui.account.CaptureSettingScreen
import com.xenyaa.videoshot.ui.account.GeminiKeyScreen
import com.xenyaa.videoshot.ui.account.RestoreScreen
import com.xenyaa.videoshot.ui.account.RestoreViewModel
import com.xenyaa.videoshot.ui.account.PlaceManagementScreen
import com.xenyaa.videoshot.ui.account.TagManagementScreen
import com.xenyaa.videoshot.ui.account.ThumbsUsageScreen
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.restartApp
import com.xenyaa.videoshot.ui.clip.ClipDraftSummary
import com.xenyaa.videoshot.ui.clip.ClipEditorScreen
import com.xenyaa.videoshot.ui.clip.ClipExportRoute
import com.xenyaa.videoshot.ui.clip.UploadAuthResult
import com.xenyaa.videoshot.ui.clip.ClipEditorViewModel
import com.xenyaa.videoshot.ui.clip.ClipHomeScreen
import com.xenyaa.videoshot.ui.clip.cancelExportTask
import com.xenyaa.videoshot.ui.clip.deleteExportTask
import com.xenyaa.videoshot.ui.clip.retryExportTask
import com.xenyaa.videoshot.ui.clip.NO_AUTH_CANT_UPLOAD
import com.xenyaa.videoshot.ui.clip.NO_AUTH_NO_UPLOAD
import com.xenyaa.videoshot.ui.clip.rememberYoutubeAuthorizer
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.ui.clip.ClipResultRoute
import com.xenyaa.videoshot.ui.clip.toSummary
import com.xenyaa.videoshot.ui.clip.ExportSheet
import com.xenyaa.videoshot.ui.clip.OrientationSheet
import com.xenyaa.videoshot.core.timeline.defaultClipTitle
import com.xenyaa.videoshot.core.timeline.usedSources
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsTopBar
import androidx.compose.foundation.layout.Column
import java.time.ZoneId
import com.xenyaa.videoshot.ui.edit.ShotEditSheet
import com.xenyaa.videoshot.ui.folders.AddToFolderSheet
import com.xenyaa.videoshot.ui.folders.FolderScreen
import com.xenyaa.videoshot.ui.folders.FolderState
import com.xenyaa.videoshot.ui.folders.FolderViewModel
import com.xenyaa.videoshot.ui.folders.ArchivedFoldersScreen
import com.xenyaa.videoshot.ui.folders.FoldersScreen
import com.xenyaa.videoshot.ui.folders.FoldersViewModel
import com.xenyaa.videoshot.ui.home.HomeScreen
import com.xenyaa.videoshot.ui.home.HomeViewModel
import com.xenyaa.videoshot.ui.home.toFilterOption
import com.xenyaa.videoshot.ui.lightbox.LightboxActions
import com.xenyaa.videoshot.ui.lightbox.LightboxClipDeps
import com.xenyaa.videoshot.ui.lightbox.LightboxScreen
import com.xenyaa.videoshot.ui.lightbox.shareTextOf
import com.xenyaa.videoshot.ui.onboarding.FirstRunChooserScreen
import com.xenyaa.videoshot.ui.common.avatarInitialOf
import com.xenyaa.videoshot.player.ClipSource
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.ui.play.PlayScreen
import com.xenyaa.videoshot.ui.play.PlayViewModel
import com.xenyaa.videoshot.wizard.WizardScreen
import com.xenyaa.videoshot.wizard.WizardViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
 * @param openClipSignal 「切到剪輯頁」的訊號：點輸出通知時 `MainActivity` 把它加一（冷啟動帶著就是 1），
 *   每次變成大於 0 的新值就回到剪輯分頁的根畫面。用累加的整數而不是布林，連點兩次通知才都會觸發。
 */
@Composable
fun AppRoot(container: AppRootDeps, openClipSignal: Int = 0, onExitApp: () -> Unit) {
    // app 回到前景：把沒有輸出工作的未完成任務補排（被殺、重開機、之前系統不讓開前景服務），
    // 再刪掉 app 裡對不到任務的成品資料夾（規格第十九節「資料」）。
    // 放在所有 return 之前，不管首次使用閘門擋不擋都要做；只在第一次組合時做一次（這時 app 在前景，開得了前景服務）
    LaunchedEffect(container) {
        try {
            container.clipDeps.resumeExports()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 補排失敗不能把 app 炸掉；任務留在原狀態，下次打開 app 再補
            Log.w("VsClip", "補排輸出失敗", e)
        }
        try {
            container.clipDeps.cleanupExportFiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 清不掉只是多佔空間，下次打開 app 再清
            Log.w("VsClip", "清理輸出資料夾失敗", e)
        }
    }
    // 播放器全螢幕時底部導覽也要藏起來（規格第六節「全螢幕與畫面方向」）
    val fullscreen = LocalPlayerFullscreen.current
    // 全新安裝的第一個畫面（規格第十節「還原」入口）。用 null 當「還在讀 DataStore」的訊號——
    // 猜一個預設值再等真正的值回來會有畫面閃一下的風險，不如先留白一瞬間。
    val restoreDecisionMade by container.settings.restoreDecisionMade
        .collectAsStateWithLifecycle(initialValue = null as Boolean?)

    // 旗標是 false 不等於「全新安裝」：既有安裝升級到這一版時，DataStore 裡本來就沒有這個 key
    // （AppSettings 的預設值是 false），但本機圖庫早就有資料——那台裝置下一次啟動會被這個
    // 閘門擋在「歡迎使用 videoshot／從 Drive 還原／全新開始」前面，明顯是錯的。所以再多問
    // 一句「本機有資料嗎」：有就當作「這個選擇已經回答過」，順便把旗標補寫回去，之後每次啟動
    // 都能只看旗標短路，不必每次都去問一次 repo（最終審查 Important 2）。
    //
    // null＝還在查。這個 LaunchedEffect 一定要寫在下面那幾個 return 之前才會被註冊。
    var localHasData by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(restoreDecisionMade) {
        if (restoreDecisionMade != false || localHasData != null) return@LaunchedEffect
        // totalShots 不受月份參數影響（accountStats 只有「本月新增」那一欄才看月份），
        // 所以這裡傳今天所在的月份就行——跟 AccountViewModel／RestoreViewModel 同一個呼法
        val hasData = try {
            container.libraryRepo.accountStats(monthOf(LocalDate.now().toString())).totalShots > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 查不出來就當作沒有資料、照原本的閘門邏輯走：真的是全新安裝遠比「repo 壞掉」
            // 可能，而且誤判成「有資料」會直接把還原入口整個吃掉，那是更糟的方向
            false
        }
        localHasData = hasData
        if (hasData) {
            // 這一段是啟動時自動跑的，沒有使用者動作在等結果——寫入失敗不能把 app 炸掉。
            // localHasData 已經是 true，這一輪照樣往下走一般畫面，代價只是下一次啟動
            // 再問一次 repo
            try {
                container.settings.markRestoreDecisionMade()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 補寫旗標失敗——下次啟動再試，不影響這一輪
            }
        }
    }

    if (restoreDecisionMade == null) return
    if (restoreDecisionMade == false) {
        when (localHasData) {
            // 還在查本機有沒有資料——先留白一瞬間，理由同 restoreDecisionMade == null：
            // 先閃一下閘門再收掉比留白難看得多
            null -> return
            false -> { FirstRunGate(container); return }
            // 既有安裝：markRestoreDecisionMade() 已經寫下去了，旗標下一瞬間就會翻成 true。
            // 這一瞬間直接往下走一般畫面，不要讓閘門閃出來
            true -> Unit
        }
    }

    var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(NavState()) }

    val homeVm: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(
                    container.libraryRepo,
                    filterOptionsSource = container.facetUsage::filterOptions,
                    markFacetsUsed = container.facetUsage::markUsed,
                ) as T
        },
        key = "home",
    )
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    val homeListState = rememberLazyGridState()

    val foldersVm: FoldersViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FoldersViewModel(container.libraryRepo, container.settings) as T
        },
        key = "folders",
    )
    val foldersState by foldersVm.state.collectAsStateWithLifecycle()

    val accountVm: AccountViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AccountViewModel(container.accountDeps) as T
        },
        key = "account",
    )
    val accountState by accountVm.state.collectAsStateWithLifecycle()

    // Google 帳號連結：授權若需要額外同意（LinkOutcome.NeedsConsent），accountVm.beginLink
    // 把跳系統畫面這件事透過 callback 交回這裡——AccountViewModel／AccountDeps 不該知道
    // ActivityResultContracts 這種 Compose／Activity 層才有的東西。使用者同意完回來後
    // 把拿到的 Intent 餵回 accountVm.finishLink；使用者取消（resultCode 不是 RESULT_OK 或
    // data 是 null）就什麼都不做，linkedAccount 維持未連結。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            accountVm.finishLink(data)
        }
    }

    // 上傳前的 Google 連結＋YouTube 授權：【開始】、導出中畫面的勾選框打勾、上傳失敗後的【重試】共用一份
    val youtubeAuthorizer = rememberYoutubeAuthorizer(container.clipDeps)

    // 目前打開的資料夾（分類分頁的堆疊裡最後一個 Dest.Folder）。key 帶 id：換一個資料夾
    // 就是換一個 VM，否則會看到上一個資料夾的內容
    val openFolderId = nav.openFolderId()
    val folderVm: FolderViewModel? = openFolderId?.let { id ->
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FolderViewModel(container.libraryRepo, id) as T
            },
            key = "folder-$id",
        )
    }

    // N4 已知 #13：父資料夾頁 → 子資料夾頁 → 在子頁刪圖／改圖資 → 返回父頁,父頁上半的子資料夾
    // 卡片（張數／預覽拼貼）要重查。`viewModel(key = "folder-$id")` 回傳的是同一個實例
    // （同一個 id 再開一次不會重建 VM），它的 children 停在上次離開時查到的舊值，
    // 不會因為子頁那邊發生過什麼就自動更新。這裡用 openFolderId 當 key：換一個資料夾
    // （不管是往下開新的還是往上退回父層）都重查一次——比新增一條全域失效匯流排更簡單，
    // 這一輪的範圍也只需要這樣（見全盤覆查 N4：匯流排式的方案留到階段 9 有第三個消費者時再做）。
    LaunchedEffect(openFolderId) { folderVm?.reload() }

    // 原片播放的 VM：以「影片＋從哪一張進來」當 key——同一支影片從另一張進來要從另一個秒數播，
    // 只用 videoId 當 key 會拿到快取的舊實例、跳去上一次的秒數
    val playDest = nav.current as? Dest.Play
    val playVm: PlayViewModel? = playDest?.let { dest ->
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlayViewModel(
                        videoId = dest.videoId,
                        startShotId = dest.shotId,
                        library = container.libraryRepo,
                        watchPage = container.wizardData::watchPage,
                        landscapeOf = { shot -> container.thumbLoader.load(shot)?.let { isLandscapeVideo(it.width, it.height) } },
                        previewFor = { page -> container.previewFramesFor(dest.videoId, page) },
                    ) as T
            },
            key = "play-${dest.videoId}-${dest.shotId}",
        )
    }

    // 選素材的 VM：只在選素材畫面存在（建構時就會讀 MediaStore，不必在每次開 app 都讀）。
    // 選完【下一步】選素材就從堆疊換掉（replaceTop），所以編輯畫面用不到它；key 固定，重新從選方向進來靠 reset() 清掉
    val clipOpen = nav.current is Dest.ClipPicker
    val clipVm: ClipPickerViewModel? = if (clipOpen) {
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClipPickerViewModel(container.clipDeps) as T
            },
            key = "clip-picker",
        )
    } else null
    // 編輯畫面的 VM：每進一次編輯畫面就換一個新的（key 帶次數）。VM 掛在 Activity 上、離開畫面不會清掉，
    // 共用同一個實例的話，上一次留下的「沒有草稿」或舊草稿會在新的一次載入完成前被當成現況（甚至直接退回剪輯頁）
    var clipEditorRun by rememberSaveable { mutableStateOf(0) }
    val clipEditorVm: ClipEditorViewModel? = if (nav.current is Dest.ClipEditor) {
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClipEditorViewModel(container.clipDeps) as T
            },
            key = "clip-editor-$clipEditorRun",
        )
    } else null
    // 剪輯頁「編輯中」那一列的摘要；null＝沒有草稿（或還沒讀到）。每次回到剪輯頁根畫面都重讀一次
    var clipDraftSummary by remember { mutableStateOf<ClipDraftSummary?>(null) }
    val onClipHome = nav.tab == Tab.CLIP && nav.current == Dest.Root
    // 剪輯頁的導出任務清單（新的在前）。只在剪輯頁根畫面開著、且 app 在前景時收集；離開後留著最後一份，
    // 下次回來先顯示舊的、再被新的蓋掉（不會先閃一下空狀態）
    var clipTasks by remember { mutableStateOf<List<EditTask>>(emptyList()) }
    val clipLifecycle = LocalLifecycleOwner.current.lifecycle
    // 導覽列【剪輯】的紅點：已完成任務的個數，app 在前景時一直收集（資料庫的 count 查詢，變了才推新值）
    var clipBadge by remember { mutableIntStateOf(0) }
    LaunchedEffect(container) {
        clipLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                container.clipDeps.cacheRepo.doneEditTaskCount().collect { clipBadge = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 讀不出來就不畫紅點（維持上一個數字）；任務本身不受影響
                Log.w("VsClip", "讀取已完成任務個數失敗", e)
            }
        }
    }
    LaunchedEffect(onClipHome, container) {
        if (!onClipHome) return@LaunchedEffect
        clipLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                container.clipDeps.cacheRepo.editTasks().collect { clipTasks = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 讀不出來就維持上一份：任務還在資料庫裡，不會因此被動到
                Log.w("VsClip", "讀取輸出任務失敗", e)
            }
        }
    }
    LaunchedEffect(onClipHome) {
        if (!onClipHome) return@LaunchedEffect
        clipDraftSummary = try {
            container.clipDeps.cacheRepo.currentClipDraft()?.toSummary()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 讀不出來就不顯示「編輯中」：草稿還在資料庫裡，不會因此被動到
            null
        }
    }
    // 【下一步】建草稿期間為 true：連點只建一次
    var clipBuilding by remember { mutableStateOf(false) }
    // true＝剛從選方向進來、選素材畫面還沒處理過這一次進入（清選取、必要時自動請求權限各做一次）
    var clipEntryPending by remember { mutableStateOf(false) }
    // 系統的權限視窗問過了沒：判斷「被永久拒絕」要用（沒問過時 shouldShowRationale 也是 false）
    var clipPermissionAsked by rememberSaveable { mutableStateOf(false) }
    // 通知權限（輸出進度與結果）只在第一次按【開始】問一次；跟 clipPermissionAsked 一樣放 rememberSaveable，不另外落地
    var notificationAsked by rememberSaveable { mutableStateOf(false) }

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
                    manualImages = { container.manualImagesFor(it) },
                    captureFor = { container.captureFor(it) },
                    today = { LocalDate.now().toString() },
                    markFacetsUsed = container.facetUsage::markUsed,
                ) as T
        }
    }
    val wizardVm: WizardViewModel = viewModel(factory = wizardFactory, key = "wizard")

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scrollToMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val clipMuted by container.settings.clipMuted.collectAsStateWithLifecycle(initialValue = false)
    // 【編輯圖資】掛在 Lightbox 外層，不是 LightboxActions 裡直接開 —— sheet 需要自己的
    // produceState（地點／標籤建議、既有圖資），Lightbox 本身不該知道這些
    var editing by remember { mutableStateOf<ShotRow?>(null) }
    // 剪輯頁【新增剪輯】的「選影片方向」抽屜：true＝開著
    var choosingOrientation by remember { mutableStateOf(false) }
    // 點輸出通知：切到剪輯分頁、回到它的根畫面（導出中畫面、成品播放等都收掉），讓使用者從任務清單找結果
    LaunchedEffect(openClipSignal) {
        if (openClipSignal <= 0) return@LaunchedEffect
        choosingOrientation = false
        val toClip = nav.select(Tab.CLIP)
        nav = toClip.copy(stacks = toClip.stacks + (Tab.CLIP to listOf(Dest.Root)))
    }
    // 就地編輯存檔完成的次數：Lightbox 的標籤列靠它重讀（標籤不在 ShotRow 裡，比不出差異）
    var shotEdits by remember { mutableIntStateOf(0) }

    // 【加入分類】同樣掛在外層。勾選要即時反映，所以把「這張圖在哪些資料夾」放進自己的狀態，
    // 寫完就更新——AddToFolderSheet 本身不碰 repo（見它的 KDoc）
    var addingTo by remember { mutableStateOf<ShotRow?>(null) }
    var checkedFolders by remember { mutableStateOf(emptySet<Long>()) }
    var folderTree by remember { mutableStateOf(emptyList<FolderNode>()) }

    // 照片頁多選：已選的照片 id（空＝不在多選模式）。轉螢幕後還在（存成 LongArray）
    var homeSelection by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }
    // 多選的【加入相簿】：非 null＝sheet 開著，值是開的當下選的那幾張。勾選＝那幾張「全部都在」的相簿
    var addingSelection by remember { mutableStateOf<Set<Long>?>(null) }
    var selectionCheckedFolders by remember { mutableStateOf(emptySet<Long>()) }
    // 這次 sheet 開著時有沒有真的寫過——有的話關掉 sheet 就順便離開多選（事情做完了）
    var selectionAlbumChanged by remember { mutableStateOf(false) }
    suspend fun foldersOfAll(ids: Set<Long>): Set<Long> =
        ids.map { container.libraryRepo.foldersOf(it) }.reduceOrNull { acc, f -> acc intersect f }.orEmpty()

    LaunchedEffect(addingSelection) {
        val ids = addingSelection ?: return@LaunchedEffect
        folderTree = container.libraryRepo.folderTree()
        selectionCheckedFolders = foldersOfAll(ids)
    }

    LaunchedEffect(addingTo) {
        val shot = addingTo ?: return@LaunchedEffect
        folderTree = container.libraryRepo.folderTree()
        checkedFolders = container.libraryRepo.foldersOf(shot.id)
    }

    // 取圖完成：回首頁、記下要捲到的月份、重新整理、跳 snackbar（手冊 §四第三步最後一條）。
    //
    // showSnackbar 要用 scope.launch 另開一個 coroutine，不能直接 await 在收集區塊裡 ——
    // `_finished` 是沒有 replay／buffer 的 SharedFlow，`emit` 會一路等到這個收集區塊
    // 執行完才返回。showSnackbar 本身又會等到 snackbar 被關掉才返回，兩個疊在一起，
    // WizardViewModel 那邊的 resetToStart()／committing = false 就會被吊住一整段
    // snackbar 顯示的時間；這段空檔按【取圖】會先看到剛送出的第三步、才又跳回第一步
    // （見階段 7 全盤覆查第 5 點）。
    LaunchedEffect(wizardVm) {
        wizardVm.finished.collect { done ->
            // 篩選中完成取圖：清掉首頁的地點與標籤篩選（裁定 H）——新圖多半不符合篩選，留著的話
            // 新圖看不到，下面「捲到新圖的月份」也會落空。clearFilter 自己會重新載入；
            // 沒有篩選時照舊 reload()
            if (homeVm.state.value.filter.isEmpty) homeVm.reload() else homeVm.clearFilter()
            // 帳號頁的統計卡（收藏片段／本月新增／來源影片）跟這次新增的張數直接相關,
            // accountVm 在背景分頁一樣要跟著重查,不能只等使用者自己切過去才看到新數字
            // （最終審查 Important 2：帳號頁的重查是無條件的,不是只有在前景才做）
            accountVm.reload()
            scrollToMonth = monthOf(done.eventDate)
            nav = nav.select(Tab.HOME)
            scope.launch { snackbarHostState.showSnackbar("已新增 ${done.count} 張") }
        }
    }

    // 地點或標籤改名、合併、刪除完成——首頁已套用的篩選清空（階段 17 設計決議 8）：舊名稱可能
    // 已經不存在，留著只會查不到東西又看不出原因。clearFilter 本身會重新載入清單與月份，
    // 首頁月份標籤與已載入的圖上的地點名稱也因此一併刷新，不必另外 reload()
    LaunchedEffect(accountVm) {
        accountVm.labelsChanged.collect { homeVm.clearFilter() }
    }

    // 封存／取消封存的結果（同一個 foldersVm 服務相簿與封存相簿兩個畫面）。
    // 另開 coroutine 顯示，理由同上面取圖完成那一段：showSnackbar 會等到 snackbar 關掉才返回
    LaunchedEffect(foldersVm) {
        foldersVm.notices.collect { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    }

    // 資料夾頁的返回鍵，以及刪掉自己之後要做的事：退一層；如果因此落回分類清單頁
    // （分類分頁的堆疊只剩 Dest.Root），順便讓 foldersVm 重查——張數與預覽拼貼可能都變了，
    // FoldersViewModel 自己不會知道資料夾頁那邊發生過什麼
    fun backFromFolder() {
        nav.pop()?.let { popped ->
            nav = popped
            if (popped.current == Dest.Root || popped.current == Dest.ArchivedFolders) foldersVm.reload()
        }
    }

    // 系統返回：在資料夾頁要走 backFromFolder()，落回相簿清單時才會重查（改名／封存後清單才不過期）
    BackHandler {
        if (nav.current is Dest.Folder) backFromFolder() else nav.pop()?.let { nav = it } ?: onExitApp()
    }

    // Lightbox 蓋掉整個外殼（連底部導覽一起），所以判斷放在 AppShell 外面（規格第六節）
    when (val dest = nav.current) {
        // Lightbox 換掉整個 AppShell（連它 Scaffold 裡的 SnackbarHost 一起），所以這裡要自己
        // 疊一顆——不疊的話，這一支分支底下任何一個 showSnackbar（編輯存檔、刪除失敗、
        // 兩個階段未到的動作）在使用者關掉 Lightbox 之前都不會被畫出來，「已儲存」看起來像
        // 沒反應、「刪除失敗」更糟：使用者只會看到那張圖還在，以為刪除成功了（見這一段覆查）。
        //
        // 對齊方式跟裡面的動作列同一個安全區：Lightbox 早先就是因為 chrome 畫到系統列下面
        // 被修過一次（統一走 navigationBarsPadding／statusBarsPadding），這裡疊的 host
        // 用同一個 navigationBarsPadding 讓開，不能再讓同一個問題在新地方重演。
        is Dest.Lightbox -> Box(Modifier.fillMaxSize()) {
            // 來源依目前在哪一格切換：分類分頁開著資料夾頁時，左右滑動範圍
            // 是那個資料夾本層，不是首頁的 homeState（規格第六節：「資料夾＝該資料夾本層」）。
            val inFolder = nav.tab == Tab.FOLDERS && folderVm != null
            val folderState by (folderVm?.state ?: MutableStateFlow(FolderState())).collectAsStateWithLifecycle()
            // 規格第六節：Lightbox 左右滑動的範圍是「進來時的清單」——資料夾本層、
            // 首頁目前顯示中的時間軸（有篩選時是篩選後的清單）二選一，依目前在哪一格決定
            val items = when {
                inFolder -> folderState.items
                else -> homeState.items
            }
            val hasMore = when {
                inFolder -> !folderState.endReached
                else -> !homeState.endReached
            }
            val loadMore: () -> Unit = when {
                inFolder -> folderVm!!::loadMore
                else -> homeVm::loadMore
            }
            // 自動播放片段的來源（規格第六節「自動播放片段」）：只要 embed 可不可用與總長，
            // 抓 YouTube 仍然只走 youtube 模組。remember 住：同一個 Lightbox 裡控制器靠它快取
            val lightboxClip = remember {
                LightboxClipDeps(source = { videoId ->
                    container.wizardData.watchPage(videoId).meta
                        ?.let { ClipSource(playableInEmbed = it.playableInEmbed, durationSec = it.durationSec) }
                })
            }
            LightboxScreen(
                items = items,
                hasMore = hasMore,
                startIndex = dest.startIndex,
                loader = container.thumbLoader,
                onClose = { nav.pop()?.let { nav = it } },
                onLoadMore = loadMore,
                tagsOf = { shot -> container.libraryRepo.tagsOfShot(shot.id) },
                tagsRefreshKey = shotEdits,
                clip = lightboxClip,
                // 【加入相簿】與【詳細資料】抽屜是 AppRoot 畫的，蓋在 Lightbox 上時片段要停
                covered = addingTo != null || editing != null,
                muted = clipMuted,
                onMutedChange = { value -> scope.launch { container.settings.setClipMuted(value) } },
                // 把目前這一張寫回導覽堆疊：轉螢幕或被系統回收重建之後，回來還在同一張。
                //
                // 一定要先確認堆疊頂真的是 Dest.Lightbox 才能換掉它 —— pop() 對只有一層、
                // 非 HOME 分頁的堆疊會回傳 copy(tab = HOME)，不是「移除頂層」。階段 8 的
                // 資料夾頁會從別的分頁開出 Lightbox，屆時 nav.pop() 不見得還是 Lightbox 頂層，
                // 誤換的話這一推會把畫面送去 HOME（見階段 7 全盤覆查第 8 點第 1 項）
                onIndexChange = { index ->
                    if (nav.current is Dest.Lightbox) {
                        nav = nav.pop()?.push(Dest.Lightbox(index)) ?: nav
                    }
                },
                actions = LightboxActions(
                    // 疊在 Lightbox 上面：返回回到 Lightbox 原本那一張（規格第六節「原片播放」）
                    onPlay = { shot -> nav = nav.push(Dest.Play(shot.videoId, shot.id)) },
                    onAddToFolder = { addingTo = it },
                    onShare = { shot ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareTextOf(shot))
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onEdit = { editing = it },
                    onDelete = { shot ->
                        scope.launch {
                            // repo／檔案系統的例外不接住的話會直接把 process 帶走（見階段 7 全盤覆查
                            // 第 2 點）；接住之後至少讓使用者知道要再試一次，而不是靜默失敗。
                            // CancellationException 要重丟，不然這個 scope 被取消時反而會跳一個
                            // 「刪除失敗」的 snackbar
                            try {
                                container.shotDeleter.delete(shot.id)
                                container.thumbLoader.evict(shot.id)
                                homeVm.onShotDeleted(shot.id)
                                // 那張圖從資料夾裡也消失了，張數與預覽都要重算。不能用
                                // if (inFolder) 擋住——資料夾頁可能只是切到別的分頁背景還活著
                                // （每一格各自一個堆疊，folderVm 不會因為切分頁被清掉），這時
                                // inFolder 是 false，但那個資料夾頁的舊資料還是要更新，不然
                                // 切回去看到的張數／預覽是刪除前的（Important 1）。
                                //
                                // 用 onShotDeleted 就地拔掉那一列，不能用 reload()——資料夾
                                // 超過一頁、使用者已經往下捲過時，reload() 會把 items 清空重撈
                                // 第一頁，分頁跳回開頭（見階段 8 全盤覆查 N3／已知 #12）；
                                // items 一旦被清空成空清單，Lightbox 甚至會誤判成「沒東西可看」
                                // 自動關掉自己（LightboxScreen 的 items.isEmpty() 那段）
                                folderVm?.onShotDeleted(shot.id)
                                // 分類分頁（清單頁）也要跟著更新——這張圖所屬資料夾的張數與
                                // 預覽拼貼都可能變了，原本只有加入分類那條路徑會呼叫（N4）
                                foldersVm.reload()
                                // 帳號頁的「收藏片段」統計少了一張,同一個理由——背景分頁也要
                                // 跟著重查,不能等使用者自己切過去（最終審查 Important 2）
                                accountVm.reload()
                                snackbarHostState.showSnackbar("已刪除 1 張")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("刪除失敗，請再試一次")
                            }
                        }
                    },
                ),
            )
            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )

            // 【加入分類】：勾一下寫一次（本階段決定 3）。這張 sheet 不碰 repo（見它的 KDoc），
            // 接線是呼叫端的事——先更新畫面再寫 DB，寫失敗就把勾勾改回去並 snackbar 提示，
            // 不能停在一個沒有寫進 DB 的畫面狀態
            addingTo?.let { shot ->
                AddToFolderSheet(
                    tree = folderTree,
                    checked = checkedFolders,
                    onToggle = { folderId, checked ->
                        checkedFolders = if (checked) checkedFolders + folderId else checkedFolders - folderId
                        scope.launch {
                            try {
                                if (checked) {
                                    container.libraryRepo.addShotToFolder(shot.id, folderId)
                                } else {
                                    container.libraryRepo.removeShotFromFolder(shot.id, folderId)
                                }
                                foldersVm.reload()
                                // 同 Important 1：不用 if (inFolder) 擋——資料夾頁可能在背景
                                // 分頁活著，folderVm 不會因為不在前景就自己刷新。
                                //
                                // 取消勾選、而且取消的正是目前開著的那個資料夾時，這張圖會從
                                // 它的本層清單裡消失——跟 N3 的刪除同一種形狀（資料夾超過一頁、
                                // 使用者已經往下捲過時，reload() 會把分頁跳回開頭，甚至讓
                                // Lightbox 誤判成清單是空的自動關掉自己），所以一樣改成就地
                                // 移除。勾起來（checked）不會發生在這個分支：這張圖已經顯示在
                                // 目前開著的資料夾的 Lightbox 裡，代表它本來就是成員，不可能
                                // 又是「加入」——那種情況（在背景資料夾裡加入一張新圖）沒有
                                // 現成的列可以拔，只能整頁重查
                                if (!checked && folderId == openFolderId) {
                                    folderVm?.onShotDeleted(shot.id)
                                } else {
                                    folderVm?.reload()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                checkedFolders = container.libraryRepo.foldersOf(shot.id)
                                snackbarHostState.showSnackbar("加入相簿失敗，請再試一次")
                            }
                        }
                    },
                    // 【＋新增資料夾】建在根層，建完自動把這張圖放進去——使用者按這顆鈕
                    // 就是為了放這張圖
                    onCreate = { name ->
                        scope.launch {
                            try {
                                val id = container.libraryRepo.createFolder(null, name)
                                container.libraryRepo.addShotToFolder(shot.id, id)
                                folderTree = container.libraryRepo.folderTree()
                                checkedFolders = container.libraryRepo.foldersOf(shot.id)
                                foldersVm.reload()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IllegalArgumentException) {
                                // 名稱規則（同層不重名、上限 50 字）——訊息留在對話框裡的錯誤提示
                                // 由 FolderNameDialog 自己處理不到，這裡只能退回 snackbar
                                snackbarHostState.showSnackbar(e.message ?: "名稱不能用")
                            } catch (e: Exception) {
                                // repo／檔案系統的例外不接住的話會直接把 process 帶走
                                // （同這個檔案別處的註解、見階段 7 全盤覆查第 2 點）
                                snackbarHostState.showSnackbar("新增相簿失敗，請再試一次")
                            }
                        }
                    },
                    onDismiss = { addingTo = null },
                )
            }
        }

        // 原片播放跟 Lightbox 一樣蓋掉整個外殼（規格第六節）；自己疊一顆 SnackbarHost，理由同 Lightbox 分支
        is Dest.Play -> Box(Modifier.fillMaxSize()) {
            val vm = playVm!!
            val pState by vm.state.collectAsStateWithLifecycle()
            PlayScreen(
                state = pState,
                onBack = { nav.pop()?.let { nav = it } },
                onPlayerReady = vm::attachPlayer,
                onPlayerReleased = vm::detachPlayer,
                onRetryPlayer = vm::loadPlayerInfo,
                // 不刪舊收藏，直接開精靈第二步（已收藏的格子鎖定，行為同第一步點最近影片）；
                // 原片播放與 Lightbox 一樣收掉，離開精靈回到照片清單、看得到新加的圖
                onContinueCapture = {
                    var popped = nav
                    if (popped.current is Dest.Play) popped = popped.pop() ?: popped
                    if (popped.current is Dest.Lightbox) popped = popped.pop() ?: popped
                    wizardVm.openRecent(dest.videoId)
                    nav = popped.select(Tab.CAPTURE)
                },
                onRecapture = {
                    scope.launch {
                        try {
                            // 先刪再開精靈：第二步要看到「這支影片一張都沒收藏」，從零開始挑
                            container.shotDeleter.deleteVideo(dest.videoId)
                            // 原片播放與底下的 Lightbox 都收掉——那支影片的圖已經不在了，切回這一格要回到清單
                            var popped = nav
                            if (popped.current is Dest.Play) popped = popped.pop() ?: popped
                            if (popped.current is Dest.Lightbox) popped = popped.pop() ?: popped
                            nav = popped
                            homeVm.reload()
                            foldersVm.reload()
                            // 相簿裡的那幾張也一起不見了，開著的相簿頁張數與預覽要重查
                            folderVm?.reload()
                            accountVm.reload()
                            wizardVm.openRecent(dest.videoId)
                            nav = nav.select(Tab.CAPTURE)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("刪除失敗，請再試一次")
                        }
                    }
                },
            )
            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        // 還原挑選流程跟 Lightbox 一樣全螢幕、沒有底部導覽（規格第十節、
        // Dest.RestoreFlow 的 KDoc）——還原成功會整個重啟 app，不會走到「回上一層」這條路。
        is Dest.RestoreFlow -> {
            val restoreVm: RestoreViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = RestoreViewModel(
                        listBackups = { container.listBackups() },
                        restore = { backup -> container.restore(backup) },
                        localShotCount = {
                            container.libraryRepo.accountStats(monthOf(LocalDate.now().toString())).totalShots
                        },
                        onRestartApp = { restartApp(context) },
                    ) as T
                },
                key = "restore",
            )
            val restoreStep by restoreVm.step.collectAsStateWithLifecycle()
            RestoreScreen(
                step = restoreStep,
                onBack = { nav.pop()?.let { nav = it } },
                onPick = restoreVm::pick,
                onConfirm = restoreVm::confirmRestore,
                onDismissConfirm = restoreVm::dismissConfirm,
                onRetry = restoreVm::load,
            )
        }

        // 選素材與編輯畫面：全螢幕、沒有底部導覽（同 Dest.Play）
        is Dest.ClipPicker -> ClipPickerRoute(
            vm = clipVm!!,
            clipDeps = container.clipDeps,
            entryPending = clipEntryPending,
            onEntryHandled = { clipEntryPending = false },
            permissionAsked = clipPermissionAsked,
            onPermissionAsked = { clipPermissionAsked = true },
            onBack = { nav.pop()?.let { nav = it } },
            onNext = {
                if (!clipBuilding) {
                    clipBuilding = true
                    scope.launch {
                        try {
                            // 重讀素材庫、拿掉已經不在的，存成草稿再換成編輯畫面（選素材不留在堆疊裡）
                            val draft = clipVm!!.buildDraft(dest.orientation)
                            if (draft != null && nav.current is Dest.ClipPicker) {
                                container.clipDeps.cacheRepo.saveClipDraft(draft, container.clipDeps.now())
                                // 存檔期間使用者可能已經按了返回：堆疊最上層不是選素材就不換頁
                                // （否則 replaceTop 會把剪輯頁的根畫面換掉）。草稿已存，剪輯頁會顯示「編輯中」
                                if (nav.current is Dest.ClipPicker) {
                                    clipEditorRun++
                                    nav = nav.replaceTop(Dest.ClipEditor(dest.orientation))
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 存檔失敗：留在選素材、不換頁。這個畫面沒有 SnackbarHost，只記 log；
                            // 選取還在，使用者可以再按一次【下一步】
                            Log.w("VsClip", "建立剪輯草稿失敗", e)
                        } finally {
                            clipBuilding = false
                        }
                    }
                }
            },
        )

        is Dest.ClipEditor -> Box(Modifier.fillMaxSize()) {
            val editorVm = clipEditorVm!!
            val editorState by editorVm.state.collectAsStateWithLifecycle()
            // 導出確認抽屜：開關跟著這個畫面（離開畫面就一起丟掉）；開著的時候失敗訊息要顯示在抽屜裡
            var exportSheetOpen by rememberSaveable { mutableStateOf(false) }
            var exportStarting by remember { mutableStateOf(false) }
            // 等通知權限的對話框回來之後才開始建立任務，標題先放在這裡
            var pendingExportTitle by remember { mutableStateOf<String?>(null) }

            // 先要 YouTube 授權 → 建立輸出任務（同時清掉草稿）→ 排工作 → 換成導出中畫面。建立失敗：留在編輯畫面、抽屜不關、草稿還在。
            // 授權被拒照樣建任務，只是不打勾自動上傳；授權服務叫不動（離線、Play 服務出錯）照樣打勾、不提示
            fun startExport(title: String) {
                val draft = editorVm.state.value.draft ?: return
                if (exportStarting) return
                exportStarting = true
                scope.launch {
                    try {
                        val autoUpload = youtubeAuthorizer.authorize() != UploadAuthResult.DECLINED
                        val taskId = container.clipDeps.cacheRepo.startEditTask(draft, title, autoUpload = autoUpload, now = container.clipDeps.now())
                        try {
                            container.clipDeps.enqueueExport(taskId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 任務已經建好、草稿已清掉：排工作失敗不回頭，app 下次回到前景會把沒排著的任務補排
                            Log.w("VsClip", "排輸出工作失敗", e)
                        }
                        // 寫入期間使用者可能已經按了返回：最上層不是編輯畫面就不換頁（否則會換掉剪輯頁的根畫面）
                        if (nav.current is Dest.ClipEditor) {
                            exportSheetOpen = false
                            nav = nav.replaceTop(Dest.ClipExport(taskId))
                        }
                        if (!autoUpload) scope.launch { snackbarHostState.showSnackbar(NO_AUTH_NO_UPLOAD) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("VsClip", "建立輸出任務失敗", e)
                        // showSnackbar 會等到訊息消失才返回：另開 coroutine，不然 exportStarting 要等幾秒才放開，期間按【開始】沒反應
                        scope.launch { snackbarHostState.showSnackbar("導出失敗，請再試一次") }
                    } finally {
                        exportStarting = false
                    }
                }
            }

            // Android 13 起通知要先要權限：第一次按【開始】問一次，不管結果都繼續（沒有通知只是看不到進度）
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                pendingExportTitle?.let { title ->
                    pendingExportTitle = null
                    startExport(title)
                }
            }

            ClipEditorRoute(
                vm = editorVm,
                clipDeps = container.clipDeps,
                permissionAsked = clipPermissionAsked,
                onPermissionAsked = { clipPermissionAsked = true },
                onBack = { nav.pop()?.let { nav = it } },
                onExport = { exportSheetOpen = true },
                onNotice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
            val exportDraft = editorState.draft
            if (exportSheetOpen && exportDraft != null) {
                ExportSheet(
                    orientation = exportDraft.orientation,
                    durationMs = exportDraft.timeline.durationMs,
                    defaultTitle = remember { defaultClipTitle(exportDraft.usedSources(), ZoneId.systemDefault()) },
                    onStart = { title ->
                        if (container.clipDeps.notificationPermissionMissing() && !notificationAsked) {
                            notificationAsked = true
                            pendingExportTitle = title
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startExport(title)
                        }
                    },
                    onDismiss = { exportSheetOpen = false },
                    snackbarHostState = snackbarHostState,
                )
            } else {
                SnackbarHost(
                    snackbarHostState,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }

        // 導出中畫面：全螢幕、沒有底部導覽；任務被取消（刪掉）就退回剪輯頁，播放成品進 Dest.ClipResult
        is Dest.ClipExport -> Box(Modifier.fillMaxSize()) {
            ClipExportRoute(
                taskId = dest.taskId,
                deps = container.clipDeps,
                scope = scope,
                onBack = { nav.pop()?.let { nav = it } },
                onGone = { if (nav.current is Dest.ClipExport) nav.pop()?.let { nav = it } },
                onPlay = { nav = nav.push(Dest.ClipResult(dest.taskId)) },
                authorize = youtubeAuthorizer::authorize,
                onNotice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }

        // 成品播放：全螢幕、沒有底部導覽（同 Dest.Play）；返回回到導出中畫面
        is Dest.ClipResult -> ClipResultRoute(
            taskId = dest.taskId,
            cacheRepo = container.clipDeps.cacheRepo,
            onBack = { nav.pop()?.let { nav = it } },
        )

        Dest.Root, is Dest.Folder, Dest.ArchivedFolders, is Dest.AccountSetting -> AppShell(nav = nav,
            onSelectTab = { foldersVm.clearSelection(); nav = nav.select(it) },
            snackbarHostState = snackbarHostState, accountInitial = avatarInitialOf(accountState.linkedAccount?.displayName),
            accountAvatar = accountState.avatar,
            clipBadge = clipBadge,
            // 照片頁、相簿畫面、封存相簿畫面多選時藏起底部導覽，由各自畫面的動作列取代
            fullscreen = fullscreen.active,
            showBottomNav = !fullscreen.active && !(
                (nav.tab == Tab.HOME && nav.current == Dest.Root && homeSelection.isNotEmpty()) ||
                    (nav.tab == Tab.FOLDERS && (nav.current == Dest.Root || nav.current == Dest.ArchivedFolders) &&
                        foldersState.selection.isNotEmpty())
                )) { tab ->
            when (val current = nav.current) {
                else -> when (tab) {
                    Tab.HOME -> {
                    HomeScreen(
                        state = homeState,
                        loader = container.thumbLoader,
                        listState = homeListState,
                        onOpen = { nav = nav.push(Dest.Lightbox(it)) },
                        onLoadMore = homeVm::loadMore,
                        onPickMonth = homeVm::setFilter,
                        phoneColumns = accountState.thumbColumns,
                        scrollToMonth = scrollToMonth,
                        onScrolledToMonth = { scrollToMonth = null },
                        // 階段 17 設計決議 6：月份標籤點下去直接在首頁只篩這一個，不再跳到查詢分頁
                        onFacetClick = { homeVm.applySingle(it.toFilterOption()) },
                        onOpenFilter = homeVm::openFilter,
                        onToggleDraft = homeVm::toggleDraft,
                        onDraftQuery = homeVm::setDraftQuery,
                        onClearDraft = homeVm::clearDraft,
                        onApplyFilter = homeVm::applyFilter,
                        onDismissFilter = homeVm::dismissFilter,
                        onRetryFilterOptions = homeVm::retryFilterOptions,
                        onClearFilter = homeVm::clearFilter,
                        selection = homeSelection,
                        onToggleSelect = { id ->
                            homeSelection = if (id in homeSelection) homeSelection - id else homeSelection + id
                        },
                        onClearSelection = { homeSelection = emptySet() },
                        onDeleteSelected = {
                            val ids = homeSelection
                            homeSelection = emptySet()
                            scope.launch {
                                // 一張一張刪（每張都要清縮圖檔與 cache，同 Lightbox 的單張刪除）；
                                // 中途失敗時已刪掉的照樣從畫面拿掉，沒刪到的留著
                                var done = 0
                                try {
                                    for (id in ids) {
                                        container.shotDeleter.delete(id)
                                        container.thumbLoader.evict(id)
                                        homeVm.onShotDeleted(id)
                                        done++
                                    }
                                    snackbarHostState.showSnackbar("已刪除 $done 張")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("刪除失敗，請再試一次")
                                } finally {
                                    if (done > 0) {
                                        // 相簿的張數與預覽、帳號頁統計都可能變了（同 Lightbox 單張刪除的理由）
                                        folderVm?.reload()
                                        foldersVm.reload()
                                        accountVm.reload()
                                    }
                                }
                            }
                        },
                        onAddSelectedToAlbum = {
                            selectionAlbumChanged = false
                            addingSelection = homeSelection
                        },
                    )
                    // 多選的【加入相簿】：同 Lightbox 的那張 sheet（勾一下寫一次），只是一次寫進所有選了的照片。
                    // 勾起來＝這幾張全部加進去；取消勾選＝這幾張全部移出
                    addingSelection?.let { ids ->
                        AddToFolderSheet(
                            tree = folderTree,
                            checked = selectionCheckedFolders,
                            onToggle = { folderId, checked ->
                                selectionCheckedFolders =
                                    if (checked) selectionCheckedFolders + folderId else selectionCheckedFolders - folderId
                                scope.launch {
                                    try {
                                        for (id in ids) {
                                            if (checked) {
                                                container.libraryRepo.addShotToFolder(id, folderId)
                                            } else {
                                                container.libraryRepo.removeShotFromFolder(id, folderId)
                                            }
                                        }
                                        selectionAlbumChanged = true
                                        foldersVm.reload()
                                        folderVm?.reload()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        selectionCheckedFolders = foldersOfAll(ids)
                                        snackbarHostState.showSnackbar("加入相簿失敗，請再試一次")
                                    }
                                }
                            },
                            // 【＋新增相簿】建在根層，建完把選了的照片全部放進去
                            onCreate = { name ->
                                scope.launch {
                                    try {
                                        val folderId = container.libraryRepo.createFolder(null, name)
                                        for (id in ids) container.libraryRepo.addShotToFolder(id, folderId)
                                        selectionAlbumChanged = true
                                        folderTree = container.libraryRepo.folderTree()
                                        selectionCheckedFolders = foldersOfAll(ids)
                                        foldersVm.reload()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: IllegalArgumentException) {
                                        snackbarHostState.showSnackbar(e.message ?: "名稱不能用")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("新增相簿失敗，請再試一次")
                                    }
                                }
                            },
                            onDismiss = {
                                addingSelection = null
                                if (selectionAlbumChanged) homeSelection = emptySet()
                            },
                            title = "把 ${ids.size} 張加入相簿",
                        )
                    }
                    }
                    Tab.FOLDERS -> when (nav.current) {
                        // 資料夾頁：上半子資料夾、下半本層的圖。folderVm 一定不是 null——
                        // openFolderId 非 null 時它才會被建出來，兩者同一個條件
                        is Dest.Folder -> folderVm?.let { vm ->
                            val folderState by vm.state.collectAsStateWithLifecycle()
                            FolderScreen(
                                state = folderState,
                                loader = container.thumbLoader,
                                onBack = ::backFromFolder,
                                onOpenChild = { child -> nav = nav.push(Dest.Folder(child.id)) },
                                onOpenShot = { index -> nav = nav.push(Dest.Lightbox(index)) },
                                onLoadMore = vm::loadMore,
                                onStartRename = vm::startRename,
                                onAskDeleteSelf = vm::askDeleteSelf,
                                onRenameChild = vm::startRenameChild,
                                onAskDeleteChild = vm::askDeleteChild,
                                onEditorName = vm::editName,
                                onConfirmEditor = vm::confirmEditor,
                                onDismissEditor = vm::dismissEditor,
                                // 刪掉的是這一頁自己：退回上一層，落地清單頁的話順便重查
                                // （confirmDelete 內部已經用 deleting.id == folderId 分辨
                                // 「自己」跟「上半列出的子資料夾」兩條路徑，這裡只接自己那一條）
                                onConfirmDelete = { vm.confirmDelete(::backFromFolder) },
                                onDismissDelete = vm::dismissDelete,
                                phoneColumns = accountState.thumbColumns,
                            )
                        }
                        // 兩個畫面共用 foldersVm 的選取：進出封存相簿、開相簿之前都先清掉
                        Dest.ArchivedFolders -> ArchivedFoldersScreen(
                            state = foldersState,
                            loader = container.thumbLoader,
                            onBack = { foldersVm.clearSelection(); nav.pop()?.let { nav = it } },
                            onOpen = { card -> foldersVm.clearSelection(); nav = nav.push(Dest.Folder(card.id)) },
                            onRename = foldersVm::rename,
                            onDeleteSelected = foldersVm::deleteSelected,
                            onEditorName = foldersVm::editName,
                            onDismissEditor = foldersVm::dismissEditor,
                            onDismissDelete = foldersVm::dismissDelete,
                            onRetry = foldersVm::reload,
                            onToggleSelect = foldersVm::toggleSelect,
                            onClearSelection = foldersVm::clearSelection,
                            onRenameSelected = foldersVm::startRenameSelected,
                            onUnarchiveSelected = foldersVm::unarchiveSelected,
                            onAskDeleteSelected = foldersVm::askDeleteSelected,
                        )
                        else -> FoldersScreen(
                            state = foldersState,
                            loader = container.thumbLoader,
                            onOpen = { card -> foldersVm.clearSelection(); nav = nav.push(Dest.Folder(card.id)) },
                            onCreate = foldersVm::create,
                            onRename = foldersVm::rename,
                            onDeleteSelected = foldersVm::deleteSelected,
                            onQuery = foldersVm::setQuery,
                            onEditorName = foldersVm::editName,
                            onSearching = foldersVm::setSearching,
                            onSort = foldersVm::setSort,
                            onStartCreate = foldersVm::startCreate,
                            onDismissEditor = foldersVm::dismissEditor,
                            onDismissDelete = foldersVm::dismissDelete,
                            onRetry = foldersVm::reload,
                            onOpenArchive = { foldersVm.clearSelection(); nav = nav.push(Dest.ArchivedFolders) },
                            onToggleSelect = foldersVm::toggleSelect,
                            onClearSelection = foldersVm::clearSelection,
                            onRenameSelected = foldersVm::startRenameSelected,
                            onArchiveSelected = foldersVm::archiveSelected,
                            onAskDeleteSelected = foldersVm::askDeleteSelected,
                        )
                    }
                    Tab.ACCOUNT -> when (val dest = nav.current) {
                        is Dest.AccountSetting -> when (dest.section) {
                            AccountSection.BACKUP -> BackupScreen(
                                linkedAccount = accountState.linkedAccount,
                                lastBackupAtEpochSec = accountState.lastBackupAtEpochSec,
                                backingUp = accountState.backingUp,
                                backupError = accountState.backupError,
                                onBack = { nav = nav.pop() ?: nav },
                                onLinkClick = {
                                    accountVm.beginLink(context as Activity) { intentSender ->
                                        consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                    }
                                },
                                onUnlinkClick = accountVm::unlink,
                                onBackupNowClick = accountVm::backupNow,
                                onRestoreClick = { nav = nav.push(Dest.RestoreFlow) },
                            )
                            AccountSection.THUMBS -> {
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        accountVm.refreshBackfillProgress()
                                        delay(3_000)
                                    }
                                }
                                ThumbsUsageScreen(
                                    usageBytes = accountState.storageUsageBytes,
                                    thumbColumns = accountState.thumbColumns,
                                    onSelectThumbColumns = accountVm::setThumbColumns,
                                    backfillProgress = accountState.backfillProgress,
                                    backfillActionError = accountState.backfillActionError,
                                    onBack = { nav = nav.pop() ?: nav },
                                    onRetryLost = accountVm::retryLostThumbs,
                                    // 【刪除這些收藏】刪掉的是呼叫端不知道是哪幾張的一批 shot
                                    // （可能跨多支影片），沒辦法像單張刪除那樣用 onShotDeleted(id)
                                    // 就地拔列，只能等它做完再讓各分頁重查——首頁、分類、帳號頁
                                    // 統計都可能在背景分頁活著、留著刪除前的資料（同這個檔案別處
                                    // 刪除流程的既有理由；最終審查 Important 發現）
                                    onDeleteLost = {
                                        scope.launch {
                                            accountVm.deleteLostThumbs().join()
                                            homeVm.reload()
                                            foldersVm.reload()
                                            // 開著的資料夾頁本層張數與預覽拼貼都可能變了，
                                            // 同 onDeleteVideo 的既有處理
                                            folderVm?.reload()
                                            accountVm.reload()
                                        }
                                    },
                                    onContinueOnMobileData = accountVm::continueBackfillOnMobileData,
                                )
                            }
                            AccountSection.CAPTURE -> CaptureSettingScreen(
                                current = accountState.filterStrength,
                                onBack = { nav = nav.pop() ?: nav },
                                onSelect = accountVm::setFilterStrength,
                            )
                            AccountSection.GEMINI -> GeminiKeyScreen(
                                keySet = accountState.geminiKeySet,
                                onBack = { nav = nav.pop() ?: nav },
                                onSave = accountVm::saveGeminiKey,
                                onClear = accountVm::clearGeminiKey,
                            )
                            AccountSection.AI -> AiRangeScreen(
                                beforeSec = accountState.aiRangeBeforeSec,
                                afterSec = accountState.aiRangeAfterSec,
                                onBack = { nav = nav.pop() ?: nav },
                                onChange = accountVm::setAiRange,
                            )
                            AccountSection.PLACES -> PlaceManagementScreen(
                                state = accountState,
                                onBack = { nav = nav.pop() ?: nav },
                                onOpenEditor = accountVm::openPlaceEditor,
                                onDismissEditor = accountVm::dismissPlaceEditor,
                                onEditName = accountVm::editPlaceName,
                                onEditAliases = accountVm::editPlaceAliases,
                                onRequestSave = accountVm::requestSavePlace,
                                onConfirmMerge = accountVm::confirmPlaceMerge,
                                onDismissMerge = accountVm::dismissPlaceMerge,
                                onAskDelete = accountVm::askDeletePlace,
                                onDismissDelete = accountVm::dismissDeletePlace,
                                onConfirmDelete = accountVm::confirmDeletePlace,
                                onStartMerge = accountVm::startPlaceMerge,
                                onPickMergeTarget = accountVm::pickMergeTarget,
                                onDismissMergePicker = accountVm::dismissMergePicker,
                                onCancelMergeTarget = accountVm::cancelMergeTarget,
                                onConfirmMergeTarget = accountVm::confirmMergeTarget,
                                onRetry = accountVm::reload,
                            )
                            AccountSection.TAGS -> TagManagementScreen(
                                state = accountState,
                                onBack = { nav = nav.pop() ?: nav },
                                onOpenEditor = accountVm::openTagEditor,
                                onDismissEditor = accountVm::dismissTagEditor,
                                onEditName = accountVm::editTagName,
                                onEditAliases = accountVm::editTagAliases,
                                onRequestSave = accountVm::requestSaveTag,
                                onConfirmMerge = accountVm::confirmMerge,
                                onDismissMerge = accountVm::dismissMergeConfirm,
                                onAskDelete = accountVm::askDeleteTag,
                                onDismissDelete = accountVm::dismissDeleteTag,
                                onConfirmDelete = accountVm::confirmDeleteTag,
                                onStartMerge = accountVm::startTagMerge,
                                onPickMergeTarget = accountVm::pickMergeTarget,
                                onDismissMergePicker = accountVm::dismissMergePicker,
                                onCancelMergeTarget = accountVm::cancelMergeTarget,
                                onConfirmMergeTarget = accountVm::confirmMergeTarget,
                                onRetry = accountVm::reload,
                            )
                        }
                        else -> AccountScreen(
                            state = accountState,
                            onOpenSection = { nav = nav.push(Dest.AccountSetting(it)) },
                            onOpenStat = { nav = nav.select(Tab.HOME) },
                        )
                    }
                    Tab.CLIP -> ClipHomeScreen(
                        onNewClip = { choosingOrientation = true },
                        draft = clipDraftSummary,
                        tasks = clipTasks,
                        thumbLoader = container.clipDeps.mediaThumbLoader,
                        onOpenTask = { taskId -> nav = nav.push(Dest.ClipExport(taskId)) },
                        onCancelTask = { taskId -> scope.launch { cancelExportTask(container.clipDeps, taskId) } },
                        onDeleteTask = { taskId -> scope.launch { deleteExportTask(container.clipDeps, taskId) } },
                        onRetryTask = { taskId ->
                            scope.launch {
                                retryExportTask(container.clipDeps, taskId, youtubeAuthorizer::authorize) {
                                    scope.launch { snackbarHostState.showSnackbar(NO_AUTH_CANT_UPLOAD) }
                                }
                            }
                        },
                        onOpenDraft = {
                            clipDraftSummary?.let { draft ->
                                clipEditorRun++
                                nav = nav.push(Dest.ClipEditor(draft.orientation))
                            }
                        },
                        onDiscardDraft = {
                            clipDraftSummary = null
                            scope.launch {
                                try {
                                    container.clipDeps.cacheRepo.clearClipDraft()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // 清不掉就不當機；草稿還在資料庫裡，下次進剪輯頁會再看到
                                    Log.w("VsClip", "放棄剪輯草稿失敗", e)
                                }
                            }
                        },
                    )
                    Tab.CAPTURE -> WizardScreen(
                        vm = wizardVm,
                        haptics = container.haptics,
                        onExit = { nav = nav.select(nav.returnTo) },
                    )
                }
            }
        }
    }

    // 剪輯頁【新增剪輯】的選方向抽屜：掛在 nav 的 when 外面（剪輯分頁在 AppShell 分支裡，
    // 抽屜不屬於任何一支分支）。選完把「這是新的一次選素材」記下來，選素材畫面進來時據此清掉上一次的選取
    if (choosingOrientation) {
        OrientationSheet(
            onPick = { orientation ->
                choosingOrientation = false
                clipEntryPending = true
                nav = nav.push(Dest.ClipPicker(orientation))
            },
            onDismiss = { choosingOrientation = false },
        )
    }

    // Lightbox 與首頁的【編輯圖資】共用同一顆 sheet（Task 9），掛在 nav 的 when 外面 ——
    // 開著編輯時 Lightbox／首頁都可能是目前畫面，不屬於任何一支分支
    editing?.let { shot ->
        EditingSheet(
            shot = shot,
            container = container,
            homeVm = homeVm,
            folderVm = folderVm,
            scope = scope,
            snackbarHostState = snackbarHostState,
            onDismiss = { editing = null },
            onSaved = { shotEdits++ },
        )
    }
}

/**
 * 選素材畫面的接線：權限請求、系統設定、回到前景重判。畫面本身（[ClipPickerScreen]）只吃狀態與回呼。
 *
 * - [entryPending]＝剛從選方向進來：先 `reset()`（清選取、回「全部」），再重判權限；
 *   沒有權限（不是部分允許）就自動請求一次。從編輯返回不是新的進入，選取要留著，也不會再自動請求。
 * - 回到前景（從系統設定回來、或背景時被改權限）一律重判。
 * - 【前往設定】只在「問過、而且系統不再顯示說明」時出現（永久拒絕）；還能再問就是【允許存取】。
 */
@Composable
private fun ClipPickerRoute(
    vm: ClipPickerViewModel,
    clipDeps: ClipDeps,
    entryPending: Boolean,
    onEntryHandled: () -> Unit,
    permissionAsked: Boolean,
    onPermissionAsked: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember(clipDeps) { mediaPermissionsFor(clipDeps.sdkInt()).toTypedArray() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionAsked()
        vm.refreshAccess()
    }

    LaunchedEffect(Unit) {
        if (!entryPending) return@LaunchedEffect
        onEntryHandled()
        vm.reset()
        // 不在這裡重判權限：ON_RESUME 的重判（下面）在進入畫面時已經跑過，access 是新的
        // 只有「完全沒權限」才自動跳系統視窗；部分允許的人靠清單上方的【選取更多】擴充，不要每次進來都打擾
        if (vm.state.value.access == MediaAccess.DENIED) launcher.launch(permissions)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshAccess() }

    val activity = context.findActivity()
    val canAskAgain = !permissionAsked ||
        (activity != null && permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) })

    ClipPickerScreen(
        state = state,
        thumbLoader = clipDeps.mediaThumbLoader,
        orderOf = vm::orderOf,
        onBack = onBack,
        onFilter = vm::setFilter,
        onToggle = vm::toggle,
        onRequestAccess = { launcher.launch(permissions) },
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 沒有可以開設定頁的 app：沒有別的辦法，維持原畫面
            }
        },
        canAskAgain = canAskAgain,
        onNext = onNext,
    )
}

/**
 * 編輯畫面的接線：載入草稿、預覽播放器的建立與釋放、權限請求、把畫面的回呼分給 ViewModel 與預覽。
 * 畫面本身（[ClipEditorScreen]）只吃狀態與回呼。
 *
 * - 進入時 `load()` 一次；讀完沒有草稿（被放棄了）就退回剪輯頁。
 * - 草稿一變（載入完、分割、刪除、復原、重做）就用 `preview.show` 重新載入，並停在目前的播放頭。
 * - 播放頭要同步給預覽：拖動、停止播放時寫回；按播放前先把預覽跳到播放頭。
 * - 沒有權限時畫面是權限空狀態；回到前景發現仍沒權限就重讀草稿（使用者可能剛從系統設定回來）。
 *   這裡不自動請求權限：要不要允許交給使用者按【允許存取】。
 */
@Composable
private fun ClipEditorRoute(
    vm: ClipEditorViewModel,
    clipDeps: ClipDeps,
    permissionAsked: Boolean,
    onPermissionAsked: () -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember(clipDeps) { mediaPermissionsFor(clipDeps.sdkInt()).toTypedArray() }

    val preview = remember(clipDeps) { clipDeps.newPreview(context) }
    DisposableEffect(preview) { onDispose { preview.release() } }

    LaunchedEffect(vm) { vm.load() }
    LaunchedEffect(state.loading, state.draft) {
        if (!state.loading && state.draft == null) onBack()
    }
    val canPreview = state.access == MediaAccess.FULL || state.access == MediaAccess.PARTIAL
    LaunchedEffect(state.draft, canPreview) {
        val draft = state.draft
        if (draft != null && canPreview) preview.show(draft, state.playheadMs)
    }
    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        onNotice(message)
        vm.noticeShown()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionAsked()
        vm.load()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (vm.state.value.access == MediaAccess.DENIED) vm.load()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { preview.pause() }

    val activity = context.findActivity()
    val canAskAgain = !permissionAsked ||
        (activity != null && permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) })

    ClipEditorScreen(
        state = state,
        preview = preview,
        thumbLoader = clipDeps.mediaThumbLoader,
        onBack = onBack,
        onExport = onExport,
        onPlayPause = {
            if (preview.isPlaying.value) {
                preview.pause()
            } else {
                val current = vm.state.value
                val duration = current.draft?.timeline?.durationMs ?: 0L
                // 停在最尾端再按播放，從頭開始
                val from = if (current.playheadMs >= duration) 0L else current.playheadMs
                vm.seekTo(from)
                preview.seekTo(from)
                preview.play()
            }
        },
        onSeek = { ms ->
            vm.seekTo(ms)
            preview.seekTo(vm.state.value.playheadMs)
        },
        onSelect = vm::select,
        onSplit = vm::split,
        onDelete = vm::delete,
        onUndo = vm::undo,
        onRedo = vm::redo,
        onRequestAccess = { launcher.launch(permissions) },
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 沒有可以開設定頁的 app：沒有別的辦法，維持原畫面
            }
        },
        canAskAgain = canAskAgain,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 全新安裝的第一個畫面的閘門（規格第十節「還原」入口，Task 14）：【從 Google Drive 還原】
 * 或【全新開始】。跟 [Dest.RestoreFlow] 共用同一個 `RestoreViewModel`／`RestoreScreen`
 * （Task 13），只是這裡另起一份獨立實例（key 不同），不牽動 `AppRoot` 主體的 `nav` 狀態——
 * 這個閘門出現時 `nav` 那一整套還沒被建出來（[AppRoot] 在更早的 `return` 就先擋掉了）。
 *
 * **進還原流程之前一定要先把 Google 帳號連起來。** 全新安裝的裝置從來沒有授權過，
 * `GoogleAuth.accessToken()` 只做靜默檢查、沒有既有授權就直接丟例外——先前的寫法按下
 * 【從 Google Drive 還原】直接跳進 `RestoreViewModel`，`listBackups()` 必然失敗，畫面上只說
 * 「讀取備份清單失敗，請確認網路後再試一次」，而這個閘門裡根本沒有任何地方能觸發真正的同意
 * 流程——使用者除了退回去按【全新開始】之外沒有第二條路（最終審查 Critical 1）。所以這裡
 * 自己準備一份 `consentLauncher`：帳號頁那一份（`AppRoot` 主體裡的）建在這個閘門的 early
 * return 之後，拿不到。
 *
 * `localShotCount = { 0 }`——全新安裝，本機一定沒有資料，天然跳過「會被取代」的確認框
 * （[RestoreViewModel] 的 KDoc：這個分支只有 `localShotCount() > 0` 才會走到）。
 */
@Composable
private fun FirstRunGate(container: AppRootDeps) {
    var showingRestoreFlow by rememberSaveable { mutableStateOf(false) }
    // 連結進行中／失敗。不用 rememberSaveable：橫跨 process 重建之後重新按一次就好，
    // 半途的「連結中」狀態留下來反而會卡住畫面
    var linking by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 同意流程回來：拿到的 Intent 餵回 finishLink，成功才進還原流程。使用者在系統畫面按取消
    // （resultCode 不是 RESULT_OK 或 data 是 null）就只是把「連結中」收掉，留在選擇畫面
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            linking = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                container.accountDeps.finishLink(data)
                showingRestoreFlow = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                linkError = "連結 Google 帳號失敗，請確認網路後再試一次"
            } finally {
                linking = false
            }
        }
    }

    // 按【從 Google Drive 還原】先做的事：把帳號連起來。beginLink 回 Linked 就直接進還原流程；
    // 回 NeedsConsent 要先跳系統同意畫面（linking 保持 true 等 launcher 回來）。
    // AccountDeps 的實作（AppContainer）內部已經把連結結果寫進 settings.linkedAccount，
    // 這裡不必自己存
    fun startLinkThenRestore() {
        linkError = null
        linking = true
        scope.launch {
            try {
                when (val outcome = container.accountDeps.beginLink(context as Activity)) {
                    is LinkOutcome.Linked -> { showingRestoreFlow = true; linking = false }
                    is LinkOutcome.NeedsConsent ->
                        consentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 使用者在系統的帳號選擇畫面按取消也會走到這裡（Credential Manager 丟例外）——
                // 訊息寫得中性一點，別斷言是網路問題
                linking = false
                linkError = "連結 Google 帳號失敗，請再試一次"
            }
        }
    }

    if (showingRestoreFlow) {
        val restoreVm: RestoreViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = RestoreViewModel(
                    listBackups = { container.listBackups() },
                    restore = { backup -> container.restore(backup) },
                    localShotCount = { 0 }, // 全新安裝，本機一定沒有資料——天然跳過確認框
                    onRestartApp = { restartApp(context) },
                    // 還原成功也要標記首次開啟的選擇已經回答過，不然重啟後這個閘門會看到
                    // restoreDecisionMade 還是 false，永遠卡在這一頁重複跳出來
                    // （RestoreViewModel 的 KDoc：這個函式在 onRestartApp 之前被 await 完）
                    onRestoreSucceeded = { container.settings.markRestoreDecisionMade() },
                ) as T
            },
            key = "first-run-restore",
        )
        val step by restoreVm.step.collectAsStateWithLifecycle()
        RestoreScreen(
            step = step,
            onBack = { showingRestoreFlow = false },
            onPick = restoreVm::pick,
            onConfirm = restoreVm::confirmRestore,
            onDismissConfirm = restoreVm::dismissConfirm,
            onRetry = restoreVm::load,
        )
    } else {
        FirstRunChooserScreen(
            onRestoreClick = { startLinkThenRestore() },
            onStartFreshClick = { scope.launch { container.settings.markRestoreDecisionMade() } },
            linking = linking,
            error = linkError,
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
    container: AppRootDeps,
    homeVm: HomeViewModel,
    folderVm: FolderViewModel?,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    // 這三個都是輔助性質的讀取（既有圖資、建議清單）——查不到的話 sheet 照樣能用，
    // 只是少了建議可以點。接住例外，不然任何一個 repo 呼叫失敗都會把整個畫面炸掉
    // （見階段 7 全盤覆查第 2 點）
    val details by produceState(ShotDetails(shot.eventDate), shot) {
        value = runCatching {
            ShotDetails(
                eventDate = shot.eventDate,
                place = shot.place,
                description = shot.description,
                tags = container.libraryRepo.tagsOfShot(shot.id),
            )
        }.getOrDefault(ShotDetails(eventDate = shot.eventDate, place = shot.place, description = shot.description))
    }
    val places by produceState(emptyList<String>()) {
        value = runCatching { container.libraryRepo.distinctPlaces() }.getOrDefault(emptyList())
    }
    // 用過越多張的排越前面
    val allTags by produceState(emptyList<String>()) {
        value = runCatching { container.libraryRepo.tagSuggestions() }.getOrDefault(emptyList())
    }
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
                // 存檔後把這張圖寫回首頁快取 —— 不然使用者回到首頁還會看到編輯前的舊圖資。
                // 資料夾頁的預覽拼貼跟本層列表也可能用到這張圖的圖資，一併重查
                container.libraryRepo.shotById(shot.id)?.let { updated ->
                    homeVm.onShotChanged(updated)
                }
                folderVm?.reload()
                onSaved()
                snackbarHostState.showSnackbar("已儲存")
            }
        },
    )
}

/** 照片頁多選的已選 id：存成 LongArray，轉螢幕或被系統回收重建之後還在。 */
private val LongSetSaver = Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toSet() },
)
