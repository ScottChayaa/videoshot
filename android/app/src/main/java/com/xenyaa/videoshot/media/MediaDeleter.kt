package com.xenyaa.videoshot.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 剪輯上傳完成後刪除用到的原檔（規格第十九節「完成後」）。本機相片與影片的刪除只走這裡（邊界 6）。
 *
 * 刪除一律交給系統刪除視窗（`createDeleteRequest`，真的刪、不進垃圾桶）；使用者給了「媒體管理」權限時
 * 系統不跳視窗、直接刪。Android 9、10 沒有這個視窗，不提供刪除（scott，2026-10-31）。
 */
interface MediaDeleter {
    /** 這台手機能不能用系統刪除視窗（API 30 起）。 */
    val canDelete: Boolean

    /** [uris] 裡現在查得到的（依原順序）；查詢出錯的當作不在——寧可少刪，也不把查不到的交給系統（會丟例外）。 */
    suspend fun existing(uris: List<String>): List<String>

    /** 系統刪除視窗；[canDelete] 是 false 或 [uris] 空回 null。 */
    fun deleteRequest(uris: List<String>): IntentSender?

    /** 要不要顯示「不想每次確認？」那一行：API 31 起且還沒有媒體管理權限。 */
    fun suggestManageMedia(): Boolean

    /** 開「媒體管理」設定頁；API 31 以下回 null。 */
    fun manageMediaSettingsIntent(): Intent?
}

/** 測試替身與沒接手機時用：不能刪、什麼都不在。 */
object NoMediaDeleter : MediaDeleter {
    override val canDelete: Boolean = false
    override suspend fun existing(uris: List<String>): List<String> = emptyList()
    override fun deleteRequest(uris: List<String>): IntentSender? = null
    override fun suggestManageMedia(): Boolean = false
    override fun manageMediaSettingsIntent(): Intent? = null
}

/** 以 MediaStore 實作的 [MediaDeleter]。 */
class MediaStoreDeleter(
    private val context: Context,
    private val sdkInt: Int,
    private val io: CoroutineDispatcher,
) : MediaDeleter {

    override val canDelete: Boolean get() = sdkInt >= Build.VERSION_CODES.R

    override suspend fun existing(uris: List<String>): List<String> = withContext(io) {
        uris.filter { uri ->
            ensureActive()
            exists(uri)
        }
    }

    private fun exists(uri: String): Boolean = try {
        context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // 沒權限（部分允許時沒被選的那些）、提供者出錯：當作不在
        false
    }

    // 版本由建構時的 sdkInt 判斷（測試要能換），lint 看不出來
    @SuppressLint("NewApi")
    override fun deleteRequest(uris: List<String>): IntentSender? =
        if (!canDelete || uris.isEmpty()) null
        else MediaStore.createDeleteRequest(context.contentResolver, uris.map(Uri::parse)).intentSender

    @SuppressLint("NewApi")
    override fun suggestManageMedia(): Boolean =
        sdkInt >= Build.VERSION_CODES.S && !MediaStore.canManageMedia(context)

    override fun manageMediaSettingsIntent(): Intent? =
        if (sdkInt < Build.VERSION_CODES.S) null
        else Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}"))
}
