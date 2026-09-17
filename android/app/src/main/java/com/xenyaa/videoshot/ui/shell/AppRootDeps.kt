package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.Haptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.FrameSource

/**
 * `AppRoot` 用得到的組裝點。窄介面而不是直接收 `AppContainer`——`AppContainer` 是
 * 具體類別，建構就會牽動 Room（兩個資料庫）、OkHttp、DataStore、WebView，Robolectric
 * 測試沒辦法便宜地造一個，也不該為了測試去造一個真的（規格第十三節：儀器測試才碰真的
 * DB／網路）。
 *
 * `AppContainer` 實作這個介面，production 的組裝點仍然只有它一個（`AppContainer` 的
 * KDoc：「階段 2 決定不用 Hilt」）——`MainActivity` 照樣把它整個傳進來，只是 `AppRoot`
 * 收到的型別收斂成這裡列出的幾樣，其餘的（Room、OkHttp……）它本來就用不到。
 *
 * 每一樣成員本身已經是介面或是只靠介面組成的小型具體類別（`ShotDeleter`／`ThumbLoader`
 * 都只收 `LibraryRepo`／`Thumbs`／`CacheRepo` 這些介面），所以測試可以直接用假實作組出
 * 一份真正能動的 `AppRootDeps`，不必再假一層。
 */
interface AppRootDeps {
    val libraryRepo: LibraryRepo
    val thumbLoader: ThumbLoader
    val shotDeleter: ShotDeleter
    val wizardData: WizardData
    val haptics: Haptics
    val settings: ShellSettings

    /** 精靈第二步的縮圖來源。見 `AppContainer.frameSourceFor` 的 KDoc。 */
    fun frameSourceFor(video: LoadedVideo): FrameSource

    /** 這支影片的手動補圖存放處。見 `AppContainer.manualImagesFor` 的 KDoc。 */
    fun manualImagesFor(videoId: String): ManualImageStore

    /** 播放器接上之後建對應的截圖器。見 `AppContainer.captureFor` 的 KDoc。 */
    fun captureFor(player: Player): Capture?
}
