package com.xenyaa.videoshot.publish

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.xenyaa.videoshot.backup.GoogleAuth
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.backup.YoutubeAuthOutcome
import kotlinx.coroutines.CancellationException

/**
 * 上傳授權走到哪一步：[Granted] 可以上傳；[Failed] 使用者沒給（取消帳號選擇）；
 * [Unavailable] 授權服務叫不動（離線、Play 服務出錯），不代表使用者拒絕；
 * 兩種 `Needs…` 要由畫面跳出系統畫面，回來後把結果餵給 [UploadAuthFlow.afterConsent]。
 */
sealed interface UploadAuthStep {
    data object Granted : UploadAuthStep
    data object Failed : UploadAuthStep
    data object Unavailable : UploadAuthStep
    data class NeedsLinkConsent(val intentSender: IntentSender) : UploadAuthStep
    data class NeedsYoutubeConsent(val intentSender: IntentSender) : UploadAuthStep
}

/**
 * 上傳前的授權（規格第十九節）：還沒連結 Google 帳號就先走帳號頁同一套連結（[beginLink]／[finishLink]，
 * 成功時會寫進設定），再對那個帳號要 `youtube.upload`；早就同意過的靜默通過。
 * 前景才能呼叫（會跳系統畫面）。
 */
class UploadAuthFlow(
    private val auth: GoogleAuth,
    private val linkedEmail: suspend () -> String?,
    private val beginLink: suspend (Activity) -> LinkOutcome,
    private val finishLink: suspend (Intent) -> LinkedGoogleAccount,
) {
    suspend fun begin(activity: Activity): UploadAuthStep = stepOnError {
        val email = linkedEmail()
        if (email != null) {
            youtube(activity, email)
        } else {
            when (val outcome = beginLink(activity)) {
                is LinkOutcome.Linked -> youtube(activity, outcome.account.email)
                is LinkOutcome.NeedsConsent -> UploadAuthStep.NeedsLinkConsent(outcome.intentSender)
            }
        }
    }

    /** [step] 是剛剛跳出系統畫面的那一步，[data] 是使用者同意後帶回來的 Intent。 */
    suspend fun afterConsent(activity: Activity, step: UploadAuthStep, data: Intent): UploadAuthStep = stepOnError {
        when (step) {
            is UploadAuthStep.NeedsLinkConsent -> youtube(activity, finishLink(data).email)
            is UploadAuthStep.NeedsYoutubeConsent -> {
                auth.finishYoutubeAuth(data)
                UploadAuthStep.Granted
            }
            UploadAuthStep.Granted, UploadAuthStep.Failed, UploadAuthStep.Unavailable -> step
        }
    }

    private suspend fun youtube(activity: Activity, email: String): UploadAuthStep =
        when (val outcome = auth.beginYoutubeAuth(activity, email)) {
            YoutubeAuthOutcome.Granted -> UploadAuthStep.Granted
            is YoutubeAuthOutcome.NeedsConsent -> UploadAuthStep.NeedsYoutubeConsent(outcome.intentSender)
        }

    /**
     * 只有使用者取消帳號選擇（Credential Manager 的 [GetCredentialCancellationException]）算 [UploadAuthStep.Failed]；
     * 其餘例外（離線、Play 服務出錯、`AuthorizationClient` 的 `ApiException` 等）分不出是不是使用者的意思，
     * 算 [UploadAuthStep.Unavailable]。同意畫面按返回不會走到這裡（畫面那邊就停了）。
     */
    private inline fun stepOnError(block: () -> UploadAuthStep): UploadAuthStep = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        Log.i("VsClipUpload", "使用者取消帳號選擇", e)
        UploadAuthStep.Failed
    } catch (e: Exception) {
        Log.w("VsClipUpload", "叫不動 Google 授權", e)
        UploadAuthStep.Unavailable
    }
}
