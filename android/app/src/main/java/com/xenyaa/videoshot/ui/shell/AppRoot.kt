package com.xenyaa.videoshot.ui.shell

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import com.xenyaa.videoshot.ui.common.ComingSoonScreen
import com.xenyaa.videoshot.ui.edit.ShotEditSheet
import com.xenyaa.videoshot.ui.folders.AddToFolderSheet
import com.xenyaa.videoshot.ui.folders.FolderScreen
import com.xenyaa.videoshot.ui.folders.FolderState
import com.xenyaa.videoshot.ui.folders.FolderViewModel
import com.xenyaa.videoshot.ui.folders.FoldersScreen
import com.xenyaa.videoshot.ui.folders.FoldersViewModel
import com.xenyaa.videoshot.ui.home.HomeScreen
import com.xenyaa.videoshot.ui.home.HomeViewModel
import com.xenyaa.videoshot.ui.lightbox.LightboxActions
import com.xenyaa.videoshot.ui.lightbox.LightboxScreen
import com.xenyaa.videoshot.ui.lightbox.shareTextOf
import com.xenyaa.videoshot.wizard.WizardScreen
import com.xenyaa.videoshot.wizard.WizardViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
            homeVm.reload()
            scrollToMonth = monthOf(done.eventDate)
            nav = nav.select(Tab.HOME)
            scope.launch { snackbarHostState.showSnackbar("已新增 ${done.count} 張") }
        }
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
            // 來源依目前在哪一格切換：分類分頁開著資料夾頁時，左右滑動範圍與「共 M 張」
            // 是那個資料夾本層，不是首頁的 homeState（規格第六節：「資料夾＝該資料夾本層」）。
            val inFolder = nav.tab == Tab.FOLDERS && folderVm != null
            val folderState by (folderVm?.state ?: MutableStateFlow(FolderState())).collectAsStateWithLifecycle()
            val items = if (inFolder) folderState.items else homeState.items
            val total = if (inFolder) folderState.total else homeState.total
            val loadMore: () -> Unit = if (inFolder) folderVm!!::loadMore else homeVm::loadMore
            LightboxScreen(
                items = items,
                total = total,
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
                    // 詳情頁是階段 9。按鈕照畫（分層才驗得了），但要說得出為什麼還沒反應
                    onPlay = { scope.launch { snackbarHostState.showSnackbar("播放頁在階段 9") } },
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

        Dest.Root, is Dest.Folder -> AppShell(nav = nav, onSelectTab = { nav = nav.select(it) }, snackbarHostState = snackbarHostState) { tab ->
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
            folderVm = folderVm,
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
    container: AppRootDeps,
    homeVm: HomeViewModel,
    folderVm: FolderViewModel?,
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
                container.libraryRepo.shotById(shot.id)?.let(homeVm::onShotChanged)
                folderVm?.reload()
                snackbarHostState.showSnackbar("已儲存")
            }
        },
    )
}
