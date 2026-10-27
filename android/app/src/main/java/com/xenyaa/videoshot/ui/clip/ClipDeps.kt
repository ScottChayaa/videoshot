package com.xenyaa.videoshot.ui.clip

import android.content.Context
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader

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
