package com.xenyaa.videoshot.ui.shell

import android.app.Activity
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.xenyaa.videoshot.ui.edit.ShotEditSheet
import com.xenyaa.videoshot.ui.folders.AddToFolderSheet
import com.xenyaa.videoshot.ui.folders.FolderScreen
import com.xenyaa.videoshot.ui.folders.FolderState
import com.xenyaa.videoshot.ui.folders.FolderViewModel
import com.xenyaa.videoshot.ui.folders.FoldersScreen
import com.xenyaa.videoshot.ui.folders.FoldersViewModel
import com.xenyaa.videoshot.ui.home.HomeScreen
import com.xenyaa.videoshot.ui.home.HomeViewModel
import com.xenyaa.videoshot.ui.home.toFilterOption
import com.xenyaa.videoshot.ui.lightbox.LightboxActions
import com.xenyaa.videoshot.ui.lightbox.LightboxScreen
import com.xenyaa.videoshot.ui.lightbox.shareTextOf
import com.xenyaa.videoshot.ui.onboarding.FirstRunChooserScreen
import com.xenyaa.videoshot.ui.common.avatarInitialOf
import com.xenyaa.videoshot.ui.detail.BatchEditScreen
import com.xenyaa.videoshot.ui.detail.BatchEditState
import com.xenyaa.videoshot.ui.detail.BatchEditViewModel
import com.xenyaa.videoshot.ui.detail.DetailScreen
import com.xenyaa.videoshot.ui.detail.DetailViewModel
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
 */
@Composable
fun AppRoot(container: AppRootDeps, onExitApp: () -> Unit) {
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
                HomeViewModel(container.libraryRepo) as T
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

    // 詳情頁（或疊在它上面的批次編輯）的 VM，跟 folderVm 同一個手法：以 videoId 當 key，
    // 換一支影片就是換一個實例；**同一支影片**重查靠 Dest.Detail 分支自己的
    // LaunchedEffect(Unit)（見下面那個分支），不能靠這裡的 key 帶 videoId ——
    // 同一支影片再進一次詳情頁，這個 key 沒變，viewModel() 會回傳快取的舊實例，
    // 以 detailVideoId 當 key 的 LaunchedEffect 不會重新跑（最終審查 Finding 2）
    val detailVideoId = nav.currentDetailVideoId()
    val detailVm: DetailViewModel? = detailVideoId?.let { vid ->
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(
                        videoId = vid,
                        // 只有第一次建構（nav.current 真的是 Dest.Detail）這個值才有意義；
                        // 疊了 BatchEdit 之後 viewModel(key=...) 會回傳既有實例，這個工廠不會再被呼叫
                        initialFocusShotId = (nav.current as? Dest.Detail)?.focusShotId ?: 0L,
                        library = container.libraryRepo,
                        watchPage = container.wizardData::watchPage,
                    ) as T
            },
            key = "detail-$vid",
        )
    }

    val batchEditVm: BatchEditViewModel? = if (nav.current is Dest.BatchEdit) {
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BatchEditViewModel(videoId = detailVideoId!!, library = container.libraryRepo) as T
            },
            key = "batchedit-$detailVideoId",
        )
    } else null

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

    // 【加入分類】同樣掛在外層。勾選要即時反映，所以把「這張圖在哪些資料夾」放進自己的狀態，
    // 寫完就更新——AddToFolderSheet 本身不碰 repo（見它的 KDoc）
    var addingTo by remember { mutableStateOf<ShotRow?>(null) }
    var checkedFolders by remember { mutableStateOf(emptySet<Long>()) }
    var folderTree by remember { mutableStateOf(emptyList<FolderNode>()) }

    LaunchedEffect(addingTo) {
        val shot = addingTo ?: return@LaunchedEffect
        folderTree = container.libraryRepo.folderTree()
        checkedFolders = container.libraryRepo.foldersOf(shot.id)
    }

    BackHandler { nav.pop()?.let { nav = it } ?: onExitApp() }

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

    // 資料夾頁的返回鍵，以及刪掉自己之後要做的事：退一層；如果因此落回分類清單頁
    // （分類分頁的堆疊只剩 Dest.Root），順便讓 foldersVm 重查——張數與預覽拼貼可能都變了，
    // FoldersViewModel 自己不會知道資料夾頁那邊發生過什麼
    fun backFromFolder() {
        nav.pop()?.let { popped ->
            nav = popped
            if (popped.current == Dest.Root) foldersVm.reload()
        }
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
            LightboxScreen(
                items = items,
                hasMore = hasMore,
                startIndex = dest.startIndex,
                loader = container.thumbLoader,
                hintSeen = lightboxHintSeen,
                onHintSeen = { scope.launch { container.settings.markLightboxHintSeen() } },
                onClose = { nav.pop()?.let { nav = it } },
                onLoadMore = loadMore,
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
                    // Lightbox 換成詳情頁——pop 掉 Lightbox 再 push 詳情頁，
                    // 不是疊上去：返回鍵從詳情頁退一步該回到清單，不是回到 Lightbox
                    onPlay = { shot ->
                        nav = (nav.pop() ?: nav).push(Dest.Detail(shot.videoId, shot.id))
                    },
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
                                snackbarHostState.showSnackbar("加入分類失敗，請再試一次")
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
                                snackbarHostState.showSnackbar("新增資料夾失敗，請再試一次")
                            }
                        }
                    },
                    onDismiss = { addingTo = null },
                )
            }
        }

        // 批次編輯跟 Lightbox 一樣全螢幕、沒有底部導覽（Task 6 的 BatchEditScreen KDoc）
        is Dest.BatchEdit -> {
            val vm = batchEditVm!!
            // 每次「真的進場」都要重查——vm 是用 videoId 當 key 快取的，同一支影片第二次
            // 進批次編輯拿到的是同一個實例，不會再跑一次 init{}。LaunchedEffect(Unit) 放在
            // 這個 when 分支的內容裡：切到別的分支再切回來，Compose 會把這個分支的子樹
            // 整個拆掉重建，key 不變也一樣重新啟動——不能像 folderVm 那樣以 id 當 key，
            // 因為這裡同一支影片重進兩次是常態，videoId 不會變（最終審查 Finding 1）
            LaunchedEffect(Unit) { vm.reload() }
            val beState by vm.state.collectAsStateWithLifecycle()
            val batchBitmapFor: suspend (Int) -> ImageBitmap? = remember(beState) {
                val fn: suspend (Int) -> ImageBitmap? = { cell ->
                    (beState as? BatchEditState.Ready)?.shots?.getOrNull(cell)?.let { container.thumbLoader.load(it) }
                }
                fn
            }
            BatchEditScreen(
                state = beState,
                bitmapFor = batchBitmapFor,
                onToggle = vm::toggle,
                onSelectAll = vm::selectAll,
                onSelectNone = vm::selectNone,
                onInvert = vm::invert,
                onSelectUnapplied = vm::selectUnapplied,
                onEditEventDate = vm::editEventDate,
                onEditPlace = vm::editPlace,
                onEditDescription = vm::editDescription,
                onEditTags = vm::editTags,
                onApply = vm::apply,
                onFinish = vm::finish,
                onClose = { nav.pop()?.let { nav = it } },
            )
            LaunchedEffect(vm) {
                vm.finished.collect {
                    nav.pop()?.let { nav = it }
                    homeVm.reload()
                    detailVm?.reload()
                    // 詳情頁可能是從分類分頁的資料夾頁開出來的（Lightbox【播放這一段】），
                    // 批次編輯改的圖資可能就是那個資料夾本層預覽或子資料夾卡片用到的那幾張，
                    // 不重查的話切回分類分頁看到的還是編輯前的舊圖資（最終審查 Finding 5，
                    // 同 EditingSheet.onSave 已經在做的事）
                    folderVm?.reload()
                    // 批次編輯可能透過 patchShots 的 tagNames 建立新標籤,帳號頁「N 個標籤」
                    // 的計數與統計卡都可能變了,同一個理由跟著重查（最終審查 Important 2）
                    accountVm.reload()
                }
            }
        }

        // 還原挑選流程跟 Lightbox／BatchEdit 一樣全螢幕、沒有底部導覽（規格第十節、
        // Dest.RestoreFlow 的 KDoc）——還原成功會整個重啟 app，不會走到「回上一層」這條路,
        // 也因此不需要像 BatchEdit 那樣在 finished 之後手動 pop／重查別的 VM。
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

        Dest.Root, is Dest.Folder, is Dest.Detail, is Dest.AccountSetting -> AppShell(nav = nav, onSelectTab = { nav = nav.select(it) }, snackbarHostState = snackbarHostState, accountInitial = avatarInitialOf(accountState.linkedAccount?.displayName)) { tab ->
            when (val current = nav.current) {
                is Dest.Detail -> {
                    val vm = detailVm!!
                    // 每次「真的進場」都要用這次導覽目的地帶來的 focusShotId 重新聚焦——
                    // vm 是用 videoId 當 key 快取的，同一支影片第二次進詳情頁（例如 Lightbox
                    // 播了同一支影片的另一張）拿到的是同一個實例，reload() 不帶參數的話會
                    // 沿用舊聚焦，看起來像按了播放這一段卻沒反應（最終審查 Finding 2）。
                    // LaunchedEffect(Unit) 放在這個 when 分支裡：從別的分支（Lightbox、
                    // BatchEdit）切回來，Compose 會把這個分支的子樹整個拆掉重建，
                    // key 不變也一樣重新啟動——理由同 BatchEdit 分支那個 LaunchedEffect(Unit)
                    LaunchedEffect(Unit) { vm.reload(focusShotId = current.focusShotId) }
                    val dState by vm.state.collectAsStateWithLifecycle()
                    DetailScreen(
                        state = dState,
                        loader = container.thumbLoader,
                        onBack = { nav.pop()?.let { nav = it } },
                        onPlayerReady = vm::attachPlayer,
                        onPlayerReleased = vm::detachPlayer,
                        onRetryPlayer = vm::loadPlayerInfo,
                        onFocus = vm::focus,
                        onEdit = { editing = it },
                        onContinueCapture = {
                            wizardVm.openRecent(current.videoId)
                            nav = nav.select(Tab.CAPTURE)
                        },
                        onBatchEdit = { nav = nav.push(Dest.BatchEdit(current.videoId)) },
                        onDeleteVideo = {
                            scope.launch {
                                try {
                                    container.shotDeleter.deleteVideo(current.videoId)
                                    // 退回詳情頁底下那一層、換到首頁分頁（規格第六節：「導回首頁」）
                                    nav = (nav.pop() ?: nav).select(Tab.HOME)
                                    homeVm.reload()
                                    foldersVm.reload()
                                    // 詳情頁可能是從分類分頁的資料夾頁開出來的，刪掉整支影片
                                    // 也可能刪掉那個資料夾本層的某幾張，不重查的話切回去看到的
                                    // 還是刪除前的張數與預覽（最終審查 Finding 5）
                                    folderVm?.reload()
                                    // 帳號頁的「收藏片段」／「來源影片」統計整支都少了,同一個
                                    // 理由——背景分頁也要跟著重查（這次最終審查 Important 2）
                                    accountVm.reload()
                                    snackbarHostState.showSnackbar("已刪除整支收藏")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("刪除失敗，請再試一次")
                                }
                            }
                        },
                    )
                }
                else -> when (tab) {
                    Tab.HOME -> HomeScreen(
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
                        onExpandDraft = homeVm::expandDraft,
                        onClearDraft = homeVm::clearDraft,
                        onApplyFilter = homeVm::applyFilter,
                        onDismissFilter = homeVm::dismissFilter,
                        onRetryFilterOptions = homeVm::retryFilterOptions,
                        onClearFilter = homeVm::clearFilter,
                    )
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
                                onStartCreateChild = vm::startCreateChild,
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
                        else -> FoldersScreen(
                            state = foldersState,
                            loader = container.thumbLoader,
                            onOpen = { card -> nav = nav.push(Dest.Folder(card.id)) },
                            onCreate = foldersVm::create,
                            onRename = foldersVm::rename,
                            onDelete = foldersVm::delete,
                            onQuery = foldersVm::setQuery,
                            onEditorName = foldersVm::editName,
                            onSearching = foldersVm::setSearching,
                            onSort = foldersVm::setSort,
                            onStartCreate = foldersVm::startCreate,
                            onStartRename = foldersVm::startRename,
                            onAskDelete = foldersVm::askDelete,
                            onDismissEditor = foldersVm::dismissEditor,
                            onDismissDelete = foldersVm::dismissDelete,
                            onRetry = foldersVm::reload,
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
                                onEditKind = accountVm::editTagKind,
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
                    Tab.CAPTURE -> WizardScreen(
                        vm = wizardVm,
                        haptics = container.haptics,
                        onExit = { nav = nav.select(nav.returnTo) },
                    )
                }
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
            folderVm = folderVm,
            detailVm = detailVm,
            scope = scope,
            snackbarHostState = snackbarHostState,
            onDismiss = { editing = null },
        )
    }
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
    detailVm: DetailViewModel?,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
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
    val allTags by produceState(emptyList<String>()) {
        value = runCatching { container.libraryRepo.allTagNames() }.getOrDefault(emptyList())
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
                    detailVm?.onShotChanged(updated)
                }
                folderVm?.reload()
                snackbarHostState.showSnackbar("已儲存")
            }
        },
    )
}
