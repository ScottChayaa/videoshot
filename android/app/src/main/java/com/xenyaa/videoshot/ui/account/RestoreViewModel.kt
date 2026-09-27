package com.xenyaa.videoshot.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.backup.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RestoreStep {
    data object Loading : RestoreStep
    data class Picking(val backups: List<RemoteBackup>) : RestoreStep
    data class Confirming(val backup: RemoteBackup) : RestoreStep
    data object Restoring : RestoreStep
    data class Failed(val reason: String) : RestoreStep
}

/**
 * 還原挑選畫面的狀態機（規格第十節的還原流程圖）。首次開啟（Task 14）與帳號頁【從 Drive 還原】
 * （Task 12 的 `BackupScreen.onRestoreClick`）共用這一個 ViewModel／畫面，不重寫兩份。
 *
 * 不直接依賴具體的 `RestoreManager`（Task 11）——那個類別的建構子需要真的
 * `LibraryDatabase`／`CacheRepo`，直接收它的話這個 ViewModel 的單元測試就要整套組出一個真的
 * 資料庫殼才能跑，而這裡真正要測的只是狀態機怎麼轉換。改成收兩個函式，跟 `BackupManager`
 * 故意不依賴具體 `BackupSnapshotter`、只收一個 `snapshotTo` 函式是同一個理由（Task 9）。
 *
 * @param listBackups 列出 Drive 上的備份——通常是 `RestoreManager::listBackups`。
 * @param restore 下載並換檔——通常是 `RestoreManager::restore`。**可能丟出未預期的例外**
 *        （下載的 `IOException`、解壓縮的 `ZipException` 都可能以未接住的例外往外傳，不保證
 *        每次都乾淨回傳 [RestoreResult]）——[doRestore] 因此要接住並轉成 [RestoreStep.Failed]，
 *        不能只靠 `when` 窮盡 [RestoreResult] 的兩個子型別就當作處理完畢。
 *        另見 [RestoreResult.Failure.needsRestart]：換檔本身失敗是「乾淨回傳的失敗」，
 *        但那時資料庫連線已經關掉，[doRestore] 仍然得呼叫 [onRestartApp]。
 * @param localShotCount 本機目前有幾張收藏——大於 0 才要跳「會被取代」的確認框（流程圖的
 *        `本機已有資料？` 分支）；首次開啟時這裡永遠是 0，天然跳過確認框。
 * @param onRestartApp 還原成功後呼叫——通常是 [com.xenyaa.videoshot.backup.restartApp]。
 * @param onRestoreSucceeded 還原成功、但**在 [onRestartApp] 之前**呼叫的收尾動作。預設是空的
 *        no-op——帳號頁【從 Drive 還原】（`Dest.RestoreFlow`）進來時 `restoreDecisionMade`
 *        早就是 `true`，沒有東西需要標記。首次開啟的閘門（Task 14 的 `FirstRunGate`）才會
 *        真的傳一個會寫 DataStore 的函式進來——還原成功也要標記「首次開啟的選擇已經回答過」，
 *        不然 [onRestartApp] 重啟後 `AppRoot` 的閘門會看到 `restoreDecisionMade` 還是
 *        `false`，永遠卡在還原/全新開始這一頁重複跳出來。**一定要在 [onRestartApp] 之前
 *        `await`（不是 `launch` 之後不等）**——`restartApp()` 最後一步是
 *        `Runtime.getRuntime().exit(0)`，process 說沒就沒，沒等到 DataStore 真的落盤
 *        就重啟的話這次標記會遺失。
 */
class RestoreViewModel(
    private val listBackups: suspend () -> List<RemoteBackup>,
    private val restore: suspend (RemoteBackup) -> RestoreResult,
    private val localShotCount: suspend () -> Int,
    private val onRestartApp: () -> Unit,
    private val onRestoreSucceeded: suspend () -> Unit = {},
) : ViewModel() {

    private val _step = MutableStateFlow<RestoreStep>(RestoreStep.Loading)
    val step: StateFlow<RestoreStep> = _step.asStateFlow()

    init { load() }

    fun load() {
        _step.value = RestoreStep.Loading
        viewModelScope.launch {
            try {
                _step.value = RestoreStep.Picking(listBackups())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _step.value = RestoreStep.Failed("讀取備份清單失敗，請確認網路後再試一次")
            }
        }
    }

    fun pick(backup: RemoteBackup) = viewModelScope.launch {
        if (localShotCount() > 0) {
            _step.value = RestoreStep.Confirming(backup)
        } else {
            doRestore(backup)
        }
    }

    fun confirmRestore() {
        val current = _step.value as? RestoreStep.Confirming ?: return
        viewModelScope.launch { doRestore(current.backup) }
    }

    fun dismissConfirm() { if (_step.value is RestoreStep.Confirming) load() }

    private suspend fun doRestore(backup: RemoteBackup) {
        _step.value = RestoreStep.Restoring
        // RestoreManager.restore 不保證每次都乾淨回傳 RestoreResult——下載的 IOException、
        // 解壓縮的 ZipException 都可能以未接住的例外往外傳（見它的 KDoc）。這裡是第一個真的
        // 呼叫 restore() 的地方，不接住的話任何一種都會把整個 process 帶走或卡住 Restoring 畫面。
        // CancellationException 要重丟，不然這個 scope 被取消時反而會被吞掉、誤判成失敗。
        val result = try {
            restore(backup)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _step.value = RestoreStep.Failed("還原失敗，請再試一次")
            return
        }
        when (result) {
            // 先 await 收尾動作、才呼叫 onRestartApp——onRestartApp 通常會用
            // Runtime.getRuntime().exit(0) 結束整個 process，必須確保收尾動作（例如寫
            // DataStore）真的落盤完成，不能只是 launch 之後不等就繼續（見建構子參數的 KDoc）
            is RestoreResult.Success -> {
                onRestoreSucceeded()
                onRestartApp()
            }
            is RestoreResult.Failure -> {
                _step.value = RestoreStep.Failed(result.reason)
                // 換檔那一步失敗的話，資料庫連線已經在換檔之前被關掉了（見
                // RestoreResult.Failure.needsRestart 的 KDoc）——這個行程再跑下去,
                // 任何一次資料庫讀取都會炸。原本的 library.db 沒有被動過,重啟就會乾淨地
                // 重新開啟它。先把失敗原因寫進 step 再重啟：真正的 onRestartApp 會結束整個
                // process（畫面上大概只閃一下），但測試看得到這個狀態,而且萬一呼叫端傳進來的
                // onRestartApp 是個 no-op,畫面至少還停在一個說得出原因的失敗頁而不是空白。
                if (result.needsRestart) onRestartApp()
            }
        }
    }
}
