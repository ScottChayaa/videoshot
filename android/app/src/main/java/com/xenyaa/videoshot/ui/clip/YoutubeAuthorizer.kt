package com.xenyaa.videoshot.ui.clip

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.xenyaa.videoshot.publish.UploadAuthStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 上傳前要授權的地方（【開始】、勾選框打勾、上傳失敗的【重試】）共用：[authorize] 依 [ClipDeps.beginUploadAuth]
 * 一步步走，遇到要同意的系統畫面就用 [requestConsent] 跳出來等結果。
 * 使用者按取消（不是 `RESULT_OK` 或沒帶 `data`）、或流程失敗都回 false。
 * 同時按兩次會排隊：第二次等第一次走完再走（通常已經授權、靜默通過）。
 */
class YoutubeAuthorizer(
    private val deps: ClipDeps,
    private val activity: () -> Activity?,
    /** 跳出系統畫面並等它回來；跳不出來回 null。 */
    private val requestConsent: suspend (IntentSender) -> ActivityResult?,
) {
    private val mutex = Mutex()

    suspend fun authorize(): Boolean = mutex.withLock {
        val activity = activity() ?: return@withLock false
        try {
            var step = deps.beginUploadAuth(activity)
            while (true) {
                val sender = when (val current = step) {
                    UploadAuthStep.Granted, UploadAuthStep.Failed -> break
                    is UploadAuthStep.NeedsLinkConsent -> current.intentSender
                    is UploadAuthStep.NeedsYoutubeConsent -> current.intentSender
                }
                val result = requestConsent(sender)
                val data = result?.data
                if (result?.resultCode != Activity.RESULT_OK || data == null) return@withLock false
                step = deps.afterUploadAuthConsent(activity, step, data)
            }
            step == UploadAuthStep.Granted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VsClipUpload", "上傳授權失敗", e)
            false
        }
    }
}

/**
 * 建一個接上系統畫面的 [YoutubeAuthorizer]，寫法同 `AppRoot` 還原流程的 `consentLauncher`。
 * 系統畫面回來時畫面若已經重建（設定變更），等結果的那個呼叫早就跟著舊畫面的 scope 取消了，結果直接丟掉。
 */
@Composable
fun rememberYoutubeAuthorizer(deps: ClipDeps): YoutubeAuthorizer {
    val context = LocalContext.current
    val waiting = remember { arrayOfNulls<CompletableDeferred<ActivityResult>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        waiting[0]?.complete(result)
        waiting[0] = null
    }
    return remember(deps, context, launcher) {
        YoutubeAuthorizer(deps, activity = { context.findActivity() }) { sender ->
            val deferred = CompletableDeferred<ActivityResult>()
            waiting[0] = deferred
            try {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
                deferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VsClipUpload", "跳不出授權畫面", e)
                null
            } finally {
                if (waiting[0] === deferred) waiting[0] = null
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
