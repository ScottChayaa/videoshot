package com.xenyaa.videoshot.ui.clip

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ApplicationProvider
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.publish.UploadAuthStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 授權流程遇到要同意的畫面就跳、等結果；使用者按取消就是沒拿到授權。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YoutubeAuthorizerTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val sender: IntentSender = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    /** begin 回 [first]；之後每次 afterConsent 依序回 [then]。 */
    private class Deps(val first: UploadAuthStep, val then: List<UploadAuthStep>) : ClipDeps {
        val consentSteps = mutableListOf<UploadAuthStep>()
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
        }
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = emptySet<String>()
        override val cacheRepo: CacheRepo get() = error("用不到")
        override fun now() = 0L
        override suspend fun beginUploadAuth(activity: Activity) = first
        override suspend fun afterUploadAuthConsent(activity: Activity, step: UploadAuthStep, data: Intent): UploadAuthStep {
            consentSteps += step
            return then[consentSteps.size - 1]
        }
    }

    private fun authorizer(deps: ClipDeps, results: List<ActivityResult?>): Pair<YoutubeAuthorizer, MutableList<IntentSender>> {
        val launched = mutableListOf<IntentSender>()
        val a = YoutubeAuthorizer(deps, activity = { activity }) { s -> launched += s; results[launched.size - 1] }
        return a to launched
    }

    private val ok = ActivityResult(Activity.RESULT_OK, Intent())

    @Test
    fun 已授權不跳畫面() = runTest {
        val (a, launched) = authorizer(Deps(UploadAuthStep.Granted, emptyList()), emptyList())
        assertTrue(a.authorize())
        assertEquals(emptyList<IntentSender>(), launched)
    }

    @Test
    fun 連結與YouTube兩次同意都按同意就通過() = runTest {
        val deps = Deps(
            UploadAuthStep.NeedsLinkConsent(sender),
            listOf(UploadAuthStep.NeedsYoutubeConsent(sender), UploadAuthStep.Granted),
        )
        val (a, launched) = authorizer(deps, listOf(ok, ok))
        assertTrue(a.authorize())
        assertEquals(2, launched.size)
        assertTrue(deps.consentSteps[0] is UploadAuthStep.NeedsLinkConsent)
        assertTrue(deps.consentSteps[1] is UploadAuthStep.NeedsYoutubeConsent)
    }

    @Test
    fun 使用者按取消回false() = runTest {
        val deps = Deps(UploadAuthStep.NeedsYoutubeConsent(sender), listOf(UploadAuthStep.Granted))
        val (a, _) = authorizer(deps, listOf(ActivityResult(Activity.RESULT_CANCELED, null)))
        assertFalse(a.authorize())
        assertEquals(emptyList<UploadAuthStep>(), deps.consentSteps)
    }

    @Test
    fun 失敗回false() = runTest {
        val (a, _) = authorizer(Deps(UploadAuthStep.Failed, emptyList()), emptyList())
        assertFalse(a.authorize())
    }
}
