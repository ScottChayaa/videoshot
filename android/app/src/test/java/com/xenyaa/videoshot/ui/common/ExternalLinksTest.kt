package com.xenyaa.videoshot.ui.common

import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalLinksTest {

    @Test
    fun 照網址開() {
        val opened = mutableListOf<String>()
        object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }.openSafely(PRIVACY_POLICY_URL)

        assertEquals(listOf("https://videoshot.scottchayaa.com/privacy"), opened)
    }

    @Test
    fun 沒有能開網頁的app時不丟例外() {
        object : UriHandler {
            override fun openUri(uri: String) = throw ActivityNotFoundException()
        }.openSafely(YOUTUBE_TERMS_URL)
    }
}
