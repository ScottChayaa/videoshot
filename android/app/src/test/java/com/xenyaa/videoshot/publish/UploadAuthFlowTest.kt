package com.xenyaa.videoshot.publish

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.test.core.app.ApplicationProvider
import com.xenyaa.videoshot.backup.FakeGoogleAuth
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 上傳前的授權：沒連結先連結（帳號頁同一套），再要 `youtube.upload`；已授權靜默通過。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadAuthFlowTest {

    private val auth = FakeGoogleAuth()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    /** 帳號頁的 `beginLink`／`finishLink` 成功時會寫進設定；這裡用一個變數代替。 */
    private var linkedEmail: String? = null
    private val flow = UploadAuthFlow(
        auth = auth,
        linkedEmail = { linkedEmail },
        beginLink = { act ->
            auth.beginLink(act).also { if (it is LinkOutcome.Linked) linkedEmail = it.account.email }
        },
        finishLink = { data -> auth.finishLink(data).also { linkedEmail = it.email } },
    )

    private fun sender() = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(), 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    @Test
    fun 沒連結先連結再要YouTube() = runTest {
        val linkSender = sender()
        auth.nextLinkOutcome = LinkOutcome.NeedsConsent(linkSender)

        val first = flow.begin(activity)
        assertTrue(first is UploadAuthStep.NeedsLinkConsent)
        assertSame(linkSender, (first as UploadAuthStep.NeedsLinkConsent).intentSender)
        assertEquals(emptyList<String>(), auth.youtubeRequestedEmails) // 連結同意回來之前不要 YouTube

        val second = flow.afterConsent(activity, first, Intent())
        assertTrue(second is UploadAuthStep.NeedsYoutubeConsent)
        assertEquals("test@example.com", linkedEmail)
        assertEquals(listOf("test@example.com"), auth.youtubeRequestedEmails)

        val third = flow.afterConsent(activity, second, Intent())
        assertEquals(UploadAuthStep.Granted, third)
        assertTrue(auth.youtubeGranted)
    }

    @Test
    fun 沒連結但帳號早就同意過就直接要YouTube() = runTest {
        // beginLink 預設回 Linked
        val step = flow.begin(activity)
        assertTrue(step is UploadAuthStep.NeedsYoutubeConsent)
        assertEquals(listOf("test@example.com"), auth.youtubeRequestedEmails)
    }

    @Test
    fun 已連結直接要YouTube() = runTest {
        linkedEmail = "ming@example.com"
        auth.failNextBeginLink = IllegalStateException("不該再連結")

        val step = flow.begin(activity)
        assertTrue(step is UploadAuthStep.NeedsYoutubeConsent)
        assertEquals(listOf("ming@example.com"), auth.youtubeRequestedEmails)
    }

    @Test
    fun 已授權靜默通過() = runTest {
        linkedEmail = "ming@example.com"
        auth.linked = LinkedGoogleAccount("阿明", "ming@example.com")
        auth.youtubeGranted = true

        assertEquals(UploadAuthStep.Granted, flow.begin(activity))
    }

    @Test
    fun 取消帳號選擇回Failed() = runTest {
        auth.failNextBeginLink = GetCredentialCancellationException("使用者取消")

        assertEquals(UploadAuthStep.Failed, flow.begin(activity))
        assertEquals(emptyList<String>(), auth.youtubeRequestedEmails)
    }

    @Test
    fun 授權呼叫丟例外回Unavailable() = runTest {
        // 離線、Play 服務出錯：不是使用者拒絕
        auth.failNextBeginLink = IllegalStateException("沒有網路")

        assertEquals(UploadAuthStep.Unavailable, flow.begin(activity))
        assertEquals(emptyList<String>(), auth.youtubeRequestedEmails)
    }

    @Test
    fun 同意回來後服務出錯回Unavailable() = runTest {
        val failing = UploadAuthFlow(
            auth = auth,
            linkedEmail = { null },
            beginLink = { auth.beginLink(it) },
            finishLink = { throw RuntimeException("Play 服務出錯") },
        )
        assertEquals(UploadAuthStep.Unavailable, failing.afterConsent(activity, UploadAuthStep.NeedsLinkConsent(sender()), Intent()))
    }
}
