package com.xenyaa.videoshot.backup

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** Play Services 帳號類型常數（`GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE`），[RevokeAccessRequest] 需要用它組出 [Account]。 */
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

/**
 * 真正的 Google 帳號連結：Credential Manager 拿身分（只為了畫面顯示，**不驗證 idToken 簽章**——
 * 這裡不是安全判斷，安全判斷完全交給 Google 的授權流程本身），`AuthorizationClient` 拿
 * `drive.appdata` 授權（規格第十節）。
 *
 * @param webClientId Task 0 步驟 5 在 Google Cloud Console 另外建立的「網頁應用程式」OAuth
 *        client id——Credential Manager 的登入流程需要它當 ID token 的受眾，不是密鑰。
 *
 * **已知限制（Task 0 完成前）**：`webClientId` 若還是 strings.xml 裡的佔位字串，
 * `beginLink` 會在 Credential Manager 那一步丟例外，不會走到 `AuthorizationClient`。
 *
 * **已知限制（跨行程）**：[pendingAccount] 只存在記憶體裡，app 行程被殺掉、重開後會是
 * `null`。[unlink] 需要一個 [Account] 才能呼叫 Play Services 的 `revokeAccess`——如果那時候
 * `pendingAccount` 是 `null`（例如重開 app 後直接進帳號頁按【中斷連結】，中間沒有再呼叫過
 * `beginLink`／`finishLink`），這裡會丟例外。Task 12 若要在跨行程情境下也能撤銷，需要把
 * `email` 從別的地方（例如 DataStore 裡持久化的顯示用身分）傳進來——這已經超出這個介面
 * （[GoogleAuth.unlink] 不收參數）目前的設計範圍，留給 Task 12 決定怎麼補。
 */
class GisGoogleAuth(
    private val context: Context,
    private val webClientId: String,
    private val io: CoroutineDispatcher,
) : GoogleAuth {

    /** `beginLink` 到 `finishLink` 之間的身分結果——授權若需要額外同意，要先記住身分，等 `finishLink` 再一起回傳。 */
    private var pendingAccount: LinkedGoogleAccount? = null

    override suspend fun beginLink(activity: Activity): LinkOutcome = withContext(io) {
        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val response = credentialManager.getCredential(activity, request)
        val idTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
        val email = decodeEmailFromIdToken(idTokenCredential.idToken) ?: idTokenCredential.id
        val account = LinkedGoogleAccount(displayName = idTokenCredential.displayName ?: email, email = email)
        pendingAccount = account

        val authClient = Identity.getAuthorizationClient(activity)
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Tasks.await(authClient.authorize(authRequest))
        val pendingIntent = result.pendingIntent
        if (result.hasResolution() && pendingIntent != null) {
            LinkOutcome.NeedsConsent(pendingIntent.intentSender)
        } else {
            LinkOutcome.Linked(account)
        }
    }

    override suspend fun finishLink(data: Intent): LinkedGoogleAccount = withContext(io) {
        val authClient = Identity.getAuthorizationClient(context)
        authClient.getAuthorizationResultFromIntent(data) // 授權失敗會丟 ApiException，讓它往上傳
        checkNotNull(pendingAccount) { "finishLink 呼叫順序錯了——要先呼叫過 beginLink" }
    }

    override suspend fun accessToken(): String = withContext(io) {
        val authClient = Identity.getAuthorizationClient(context)
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Tasks.await(authClient.authorize(authRequest))
        if (result.hasResolution()) error("Drive 授權已失效，需要使用者重新連結")
        result.accessToken ?: error("Drive 授權沒有回傳存取權杖")
    }

    override suspend fun unlink(): Unit = withContext(io) {
        // revokeAccess 需要指定帳號（官方文件範例是 .setAccount(account).setScopes(scopes)，
        // 不能只呼叫 builder().build()——那樣沒有目標帳號，撤銷不到東西）。
        val email = pendingAccount?.email
            ?: error("尚未連結 Google 帳號（或行程重啟後遺失連結狀態），無法中斷連結")
        val account = Account(email, GOOGLE_ACCOUNT_TYPE)
        val authClient = Identity.getAuthorizationClient(context)
        val revokeRequest = RevokeAccessRequest.builder()
            .setAccount(account)
            .build()
        Tasks.await(authClient.revokeAccess(revokeRequest))
        pendingAccount = null
    }

    private fun decodeEmailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        JSONObject(json).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()
}
