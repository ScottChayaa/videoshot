package com.xenyaa.videoshot.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender

/** [GoogleAuth] 的假版本——測試，以及 Task 0（Google Cloud 設定）完成前先讓其他功能能開發。 */
class FakeGoogleAuth(initiallyLinked: LinkedGoogleAccount? = null) : GoogleAuth {

    var linked: LinkedGoogleAccount? = initiallyLinked

    /** null＝下一次 `beginLink` 直接回傳一個預設帳號的 [LinkOutcome.Linked]。 */
    var nextLinkOutcome: LinkOutcome? = null
    var failNextBeginLink: Exception? = null
    var failNextAccessToken: Exception? = null

    /** 是否已同意過 `youtube.upload`。 */
    var youtubeGranted = false

    /** 已同意過、但被撤銷了：`youtubeAccessToken` 丟 [GoogleAuthRevoked]、`beginYoutubeAuth` 要重新同意。 */
    var youtubeRevoked = false

    /** null＝`NeedsConsent` 用 `activity` 現做一個沒作用的 IntentSender。 */
    var youtubeConsentSender: IntentSender? = null

    /** `beginYoutubeAuth`／`youtubeAccessToken` 被要求過的 email，依呼叫順序。 */
    val youtubeRequestedEmails = mutableListOf<String>()

    override suspend fun beginLink(activity: Activity): LinkOutcome {
        failNextBeginLink?.let { failNextBeginLink = null; throw it }
        val outcome = nextLinkOutcome ?: LinkOutcome.Linked(LinkedGoogleAccount("測試用戶", "test@example.com"))
        nextLinkOutcome = null
        if (outcome is LinkOutcome.Linked) linked = outcome.account
        return outcome
    }

    override suspend fun finishLink(data: Intent): LinkedGoogleAccount =
        linked ?: LinkedGoogleAccount("測試用戶", "test@example.com").also { linked = it }

    override suspend fun accessToken(): String {
        failNextAccessToken?.let { failNextAccessToken = null; throw it }
        return linked?.let { "fake-access-token" } ?: error("尚未連結 Google 帳號")
    }

    override suspend fun beginYoutubeAuth(activity: Activity, email: String): YoutubeAuthOutcome {
        youtubeRequestedEmails += email
        if (youtubeGranted && !youtubeRevoked) return YoutubeAuthOutcome.Granted
        val sender = youtubeConsentSender
            ?: PendingIntent.getActivity(activity, 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender
        return YoutubeAuthOutcome.NeedsConsent(sender)
    }

    override suspend fun finishYoutubeAuth(data: Intent) {
        youtubeGranted = true
        youtubeRevoked = false
    }

    override suspend fun youtubeAccessToken(email: String): String {
        youtubeRequestedEmails += email
        if (!youtubeGranted || youtubeRevoked) throw GoogleAuthRevoked("YouTube 授權已失效，需要使用者重新同意")
        return "fake-youtube-token"
    }

    override suspend fun unlink(email: String) {
        linked = null
        youtubeGranted = false
        youtubeRevoked = false
    }
}
