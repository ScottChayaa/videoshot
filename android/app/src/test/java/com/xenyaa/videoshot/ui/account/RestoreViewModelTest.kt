package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.backup.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun backup(id: String) = RemoteBackup(id, "library-$id.db.gz", 100, 1024, 1, 5, "裝置", "sha")

    private fun vm(
        backups: List<RemoteBackup> = listOf(backup("a")),
        localShotCount: Int = 0,
        restoreResult: RestoreResult = RestoreResult.Success,
        onRestartApp: () -> Unit = {},
    ) = RestoreViewModel(
        listBackups = { backups },
        restore = { restoreResult },
        localShotCount = { localShotCount },
        onRestartApp = onRestartApp,
    )

    @Test
    fun 一開始是Loading_讀完清單後變成Picking() = runTest {
        val model = vm(backups = listOf(backup("a"), backup("b")))
        advanceUntilIdle()
        val step = model.step.value
        assertTrue(step is RestoreStep.Picking)
        assertEquals(2, (step as RestoreStep.Picking).backups.size)
    }

    @Test
    fun 本機沒有資料時選了就直接還原_不跳確認框() = runTest {
        var restarted = false
        val model = vm(localShotCount = 0, restoreResult = RestoreResult.Success, onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertTrue(restarted)
    }

    @Test
    fun 本機有資料時選了先進Confirming() = runTest {
        val model = vm(localShotCount = 3)
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertTrue(model.step.value is RestoreStep.Confirming)
    }

    @Test
    fun 確認後才真的還原() = runTest {
        var restarted = false
        val model = vm(localShotCount = 3, onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        model.confirmRestore()
        advanceUntilIdle()
        assertTrue(restarted)
    }

    @Test
    fun 取消確認框回到Picking() = runTest {
        val model = vm(localShotCount = 3)
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        model.dismissConfirm()
        advanceUntilIdle()
        assertTrue(model.step.value is RestoreStep.Picking)
    }

    @Test
    fun 還原失敗顯示原因_不呼叫重啟() = runTest {
        var restarted = false
        val model = vm(localShotCount = 0, restoreResult = RestoreResult.Failure("雜湊不符"), onRestartApp = { restarted = true })
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        assertEquals(RestoreStep.Failed("雜湊不符"), model.step.value)
        assertTrue(!restarted)
    }

    /**
     * `RestoreManager.restore` 不保證每次都乾淨回傳 `RestoreResult`——換檔用的原子改名失敗、
     * 下載／解壓縮的例外都可能以未接住的例外往外傳（見它的 KDoc、Task 11 已知的落差）。
     * `doRestore` 必須接住任意例外轉成 `RestoreStep.Failed`，不能讓 ViewModel 整個崩潰或
     * 卡在 Restoring 不動。
     */
    @Test
    fun restore丟例外時轉成Failed_不崩潰不呼叫重啟() = runTest {
        var restarted = false
        val model = RestoreViewModel(
            listBackups = { listOf(backup("a")) },
            restore = { throw IllegalStateException("換檔失敗：磁碟空間不足") },
            localShotCount = { 0 },
            onRestartApp = { restarted = true },
        )
        advanceUntilIdle()
        model.pick(backup("a"))
        advanceUntilIdle()
        val step = model.step.value
        assertTrue(step is RestoreStep.Failed)
        assertTrue(!restarted)
    }
}
