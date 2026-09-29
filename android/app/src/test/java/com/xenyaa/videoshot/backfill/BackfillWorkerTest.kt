package com.xenyaa.videoshot.backfill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackfillWorkerTest {

    @Test
    fun 沒有到期工作時回success() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackfillWorkerFactory(runBatch = { BackfillRunResult.Idle })
        val worker = TestListenableWorkerBuilder<BackfillWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun 遇到暫停回retry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackfillWorkerFactory(runBatch = { BackfillRunResult.Paused })
        val worker = TestListenableWorkerBuilder<BackfillWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun runBatch丟例外時回retry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackfillWorkerFactory(runBatch = { throw IllegalStateException("網路壞了") })
        val worker = TestListenableWorkerBuilder<BackfillWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun 還有工作時回success且不炸掉即使排下一個work失敗() = runTest {
        // 這裡不驗證「有沒有真的排出下一個 WorkRequest」——那需要真的 WorkManager 執行環境，
        // 留給 Task 13 的實機整合驗證。這裡只驗證 doWork 本身的回傳值不受影響。
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackfillWorkerFactory(runBatch = { BackfillRunResult.Continued(remaining = true) })
        val worker = TestListenableWorkerBuilder<BackfillWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}

/**
 * 掃描不該被網路約束卡住——`BackfillScanWorker` 是這次實機驗收發現的缺口修法：
 * `scanForMissing()` 原本只在 `runBatch()`（網路受限的 `BackfillWorker`）裡執行，裝置從來
 * 沒連過 Wi-Fi 的話 `thumb_state` 永遠是空的，帳號頁連「有東西缺圖」都不知道，
 * 【用行動網路繼續】按鈕的顯示條件（`total > done`）也永遠成立不了。這個 worker 沒有任何
 * constraint，純粹是本機 DB／檔案掃描，不該被排除在外。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackfillScanWorkerTest {

    @Test
    fun 掃描成功回success() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var scanned = false
        val factory = BackfillScanWorkerFactory(scanForMissing = { scanned = true })
        val worker = TestListenableWorkerBuilder<BackfillScanWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(true, scanned)
    }

    @Test
    fun 掃描丟例外時回retry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackfillScanWorkerFactory(scanForMissing = { throw IllegalStateException("讀不到 DB") })
        val worker = TestListenableWorkerBuilder<BackfillScanWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }
}
