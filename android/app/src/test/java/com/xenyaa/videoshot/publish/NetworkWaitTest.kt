package com.xenyaa.videoshot.publish

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkWaitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun validated(): NetworkCapabilities = ShadowNetworkCapabilities.newInstance().also {
        shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Test
    fun 已經有驗證過的網路就立刻回true() = runTest {
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, validated())

        assertTrue(awaitValidatedNetwork(context, 600_000L))
        assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
    }

    @Test
    fun 等不到網路時逾時回false且取消註冊() = runTest {
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, ShadowNetworkCapabilities.newInstance())

        assertFalse(awaitValidatedNetwork(context, 600_000L))
        assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
    }

    @Test
    fun 網路驗證通過時回true且取消註冊() = runTest {
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, ShadowNetworkCapabilities.newInstance())

        val waiting = async { awaitValidatedNetwork(context, 600_000L) }
        runCurrent()
        val callback = shadowOf(cm).networkCallbacks.single()
        callback.onCapabilitiesChanged(cm.activeNetwork!!, ShadowNetworkCapabilities.newInstance()) // 還沒驗證：繼續等
        runCurrent()
        assertFalse(waiting.isCompleted)
        callback.onCapabilitiesChanged(cm.activeNetwork!!, validated())

        assertTrue(waiting.await())
        assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
    }
}
