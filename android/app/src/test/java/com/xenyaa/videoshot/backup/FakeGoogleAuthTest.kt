package com.xenyaa.videoshot.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FakeGoogleAuthTest {

    @Test
    fun 預設直接連結成功() = runTest {
        val auth = FakeGoogleAuth()
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        val outcome = auth.beginLink(activity)

        assertTrue(outcome is LinkOutcome.Linked)
        assertEquals("test@example.com", (outcome as LinkOutcome.Linked).account.email)
    }

    @Test
    fun 需要同意時回NeedsConsent_finishLink後才算連結完成() = runTest {
        val auth = FakeGoogleAuth()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE,
        )
        auth.nextLinkOutcome = LinkOutcome.NeedsConsent(pendingIntent.intentSender)

        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val outcome = auth.beginLink(activity)
        assertTrue(outcome is LinkOutcome.NeedsConsent)
        assertNull(auth.linked) // 還沒 finishLink，不算連結完成

        val account = auth.finishLink(Intent())
        assertEquals("test@example.com", account.email)
    }

    @Test
    fun accessToken未連結時丟例外() = runTest {
        val auth = FakeGoogleAuth()
        val threw = runCatching { auth.accessToken() }.isFailure
        assertTrue(threw)
    }

    @Test
    fun unlink後accessToken再丟例外() = runTest {
        val auth = FakeGoogleAuth(initiallyLinked = LinkedGoogleAccount("阿明", "ming@example.com"))
        auth.accessToken() // 連結中，不丟

        auth.unlink()

        assertTrue(runCatching { auth.accessToken() }.isFailure)
    }
}
