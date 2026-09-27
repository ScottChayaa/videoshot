package com.xenyaa.videoshot.backup

import android.app.Activity
import android.content.Intent

/** [GoogleAuth] 的假版本——測試，以及 Task 0（Google Cloud 設定）完成前先讓其他功能能開發。 */
class FakeGoogleAuth(initiallyLinked: LinkedGoogleAccount? = null) : GoogleAuth {

    var linked: LinkedGoogleAccount? = initiallyLinked

    /** null＝下一次 `beginLink` 直接回傳一個預設帳號的 [LinkOutcome.Linked]。 */
    var nextLinkOutcome: LinkOutcome? = null
    var failNextBeginLink: Exception? = null
    var failNextAccessToken: Exception? = null

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

    override suspend fun unlink(email: String) { linked = null }
}
