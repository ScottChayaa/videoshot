package com.xenyaa.videoshot.publish

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 等預設網路「驗證過」（真的連得到網際網路，不是要登入的 Wi-Fi），最多 [timeoutMs] 毫秒；等到回 true。
 * 一開始就有驗證過的網路立刻回 true。結束時（等到、逾時、取消）一定取消註冊。
 * 給 `ClipUploadRunner` 斷網時用。
 */
suspend fun awaitValidatedNetwork(context: Context, timeoutMs: Long): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    if (cm.getNetworkCapabilities(cm.activeNetwork).isValidated()) return true

    val validated = CompletableDeferred<Unit>()
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.isValidated()) validated.complete(Unit)
        }
    }
    cm.registerDefaultNetworkCallback(callback)
    return try {
        withTimeoutOrNull(timeoutMs) { validated.await() } != null
    } finally {
        cm.unregisterNetworkCallback(callback)
    }
}

private fun NetworkCapabilities?.isValidated(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
