package com.xenyaa.videoshot.ui.clip

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.publish.UploadAuthStep

/**
 * 剪輯功能用得到的組裝點。窄介面，理由同 `AppRootDeps`：測試不必為了選素材畫面造真的
 * `ContentResolver`。權限狀態用函式而不是屬性——每次問都要拿當下的授權（使用者可能剛從系統設定回來）。
 */
interface ClipDeps {
    val mediaLibrary: MediaLibrary
    val mediaThumbLoader: MediaThumbLoader

    /** 目前的 Android API 等級（判定權限用）。 */
    fun sdkInt(): Int

    /** 目前已授權的相片／影片相關權限名稱。 */
    fun grantedMediaPermissions(): Set<String>

    /** 剪輯草稿存在 `cache.db`（`edit_draft`），只走 [CacheRepo]。 */
    val cacheRepo: CacheRepo

    /** 現在的時間（毫秒）；存草稿時蓋更新時間用，測試可以換掉。 */
    fun now(): Long

    /**
     * 排一個輸出工作（【導出】建好任務之後呼叫）。預設什麼都不做：只有真的接上 WorkManager 的組裝點
     * （`AppContainer`）會覆寫，測試用這裡記下被排的任務編號。
     */
    fun enqueueExport(taskId: Long) = Unit

    /** 排一次等 YouTube 產生縮圖的查詢（處理中的任務按【重試】時呼叫）。預設什麼都不做，`AppContainer` 覆寫成馬上查。 */
    fun enqueueStoryboardPoll(taskId: Long) = Unit

    /** 收掉這個任務等 YouTube 縮圖的輪詢（取消或刪除任務時呼叫，要在刪資料列之前或之後都行，但 [resumeExports] 一定在刪之後）。預設什麼都不做，`AppContainer` 覆寫。 */
    fun cancelStoryboardPoll(taskId: Long) = Unit

    /**
     * 上傳前的授權（見 `UploadAuthFlow`）：前景才能呼叫。預設直接通過，給既有的測試替身用；`AppContainer` 覆寫。
     * 回兩種 `Needs…` 時由畫面跳出系統畫面，回來後呼叫 [afterUploadAuthConsent]。
     */
    suspend fun beginUploadAuth(activity: Activity): UploadAuthStep = UploadAuthStep.Granted

    /** 使用者在 [beginUploadAuth] 跳出的系統畫面按了同意：把帶回的 [data] 交給 [step] 那一步，回下一步。 */
    suspend fun afterUploadAuthConsent(activity: Activity, step: UploadAuthStep, data: Intent): UploadAuthStep =
        UploadAuthStep.Granted

    /** 這個任務的輸出工作正在跑才取消它，等取消真的套用才回來；排隊中的不動（見 `cancelClipExport`）。預設什麼都不做，`AppContainer` 覆寫。 */
    suspend fun cancelExport(taskId: Long) = Unit

    /** 把沒有輸出工作的未完成任務補排（見 `resumeClipExports`）；取消一個任務後，排在它後面被連帶取消的要靠這個補回來。 */
    suspend fun resumeExports() = Unit

    /** 刪掉這個任務在 app 裡的成品資料夾（相簿裡的不動）；刪除或取消任務時呼叫。預設什麼都不做，`AppContainer` 覆寫。 */
    suspend fun deleteExportFiles(taskId: Long) = Unit

    /** 使用者正停在這個任務的導出畫面（null＝離開了）：結果已經在畫面上，這段期間它不另外跳通知，留著的那則也收掉。預設什麼都不做，`AppContainer` 覆寫。 */
    fun viewingExport(taskId: Long?) = Unit

    /** 收掉這個任務的結果通知；刪除或取消任務時呼叫。預設什麼都不做，`AppContainer` 覆寫。 */
    fun dismissExportResult(taskId: Long) = Unit

    /** 刪掉 app 裡對不到任務的成品資料夾（見 `deleteOrphanEditDirs`）；app 啟動時跟 [resumeExports] 一起呼叫一次。預設什麼都不做，`AppContainer` 覆寫。 */
    suspend fun cleanupExportFiles() = Unit

    /** 還需要向使用者要「通知」權限嗎（Android 13 起、尚未授權才是 true）；輸出進度與結果都靠通知。 */
    fun notificationPermissionMissing(): Boolean = false

    /** 建一個編輯畫面用的預覽播放器；測試換成不碰 Media3 的假實作。 */
    fun newPreview(context: Context): ClipPreview = Media3ClipPreview(context)
}

/** 沒有接上手機的替身：清單空、權限空。既有的 `AppRootDeps` 測試替身靠它不必改。 */
object NoClipDeps : ClipDeps {
    override val mediaLibrary: MediaLibrary = object : MediaLibrary {
        override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
    }
    override val mediaThumbLoader: MediaThumbLoader = MediaThumbLoader(decode = { null })
    override fun sdkInt(): Int = 34
    override fun grantedMediaPermissions(): Set<String> = emptySet()

    /** 沒有資料庫可用：這個替身只給不會開編輯畫面的測試，真的去讀會直接丟例外，不默默吞掉。 */
    override val cacheRepo: CacheRepo get() = error("NoClipDeps 沒有接上資料庫")
    override fun now(): Long = 0L
}
