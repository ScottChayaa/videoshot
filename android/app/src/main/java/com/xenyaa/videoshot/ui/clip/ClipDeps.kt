package com.xenyaa.videoshot.ui.clip

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
}

/** 沒有接上手機的替身：清單空、權限空。既有的 `AppRootDeps` 測試替身靠它不必改。 */
object NoClipDeps : ClipDeps {
    override val mediaLibrary: MediaLibrary = object : MediaLibrary {
        override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
    }
    override val mediaThumbLoader: MediaThumbLoader = MediaThumbLoader(decode = { null })
    override fun sdkInt(): Int = 34
    override fun grantedMediaPermissions(): Set<String> = emptySet()
}
