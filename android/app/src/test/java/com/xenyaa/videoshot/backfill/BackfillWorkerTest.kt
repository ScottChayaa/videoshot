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
