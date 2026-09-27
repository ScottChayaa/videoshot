package com.xenyaa.videoshot.backup

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
class BackupWorkerTest {

    @Test
    fun 有需要備份時回success_呼叫時force是false() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var calledForce: Boolean? = null
        val factory = BackupWorkerFactory(runIfDue = { force -> calledForce = force; true })

        val worker = TestListenableWorkerBuilder<BackupWorker>(context)
            .setWorkerFactory(factory)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(false, calledForce)
    }

    @Test
    fun BackupManager丟例外時回retry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BackupWorkerFactory(runIfDue = { throw IllegalStateException("網路壞了") })

        val worker = TestListenableWorkerBuilder<BackupWorker>(context)
            .setWorkerFactory(factory)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
