package com.xenyaa.videoshot.backup

import android.app.Activity
import android.content.Intent
import android.content.IntentSender

/** 連結完成後要顯示的東西（規格第九節 hero：「名稱、Email、頭像字母」，這裡只需要前兩者）。 */
data class LinkedGoogleAccount(val displayName: String, val email: String)

/**
 * `beginLink` 的結果：[Linked] 代表授權早就同意過，靜默完成；[NeedsConsent] 代表要由呼叫端
 * 跳出系統畫面（`ActivityResultContracts.StartIntentSenderForResult`），使用者同意後把拿到的
 * `Intent` 餵回 [GoogleAuth.finishLink]。
 */
sealed interface LinkOutcome {
    data class Linked(val account: LinkedGoogleAccount) : LinkOutcome
    data class NeedsConsent(val intentSender: IntentSender) : LinkOutcome
}

/**
 * Google 帳號連結：身分（顯示名稱／Email）＋ `drive.appdata` 授權（規格第十節）。
 * 真正實作見 [GisGoogleAuth]；測試與 Task 0 完成前的開發用 [FakeGoogleAuth]。
 * **模組邊界**：這是唯一允許碰 Google Identity／Drive 授權 API 的地方（規格第三節）。
 */
interface GoogleAuth {
    /** 前景才能呼叫——會跳出系統的帳號選擇／同意畫面。 */
    suspend fun beginLink(activity: Activity): LinkOutcome

    /** 使用者從 [LinkOutcome.NeedsConsent] 的系統畫面回來後呼叫，`data` 是 activity result 帶回的 Intent。 */
    suspend fun finishLink(data: Intent): LinkedGoogleAccount

    /** 背景可呼叫：拿目前授權下可用的存取權杖。未連結、或授權已失效需要重新同意時丟例外。 */
    suspend fun accessToken(): String

    /** 撤銷授權（規格「中斷連結」，破壞性樣式按鈕）。 */
    suspend fun unlink()
}
