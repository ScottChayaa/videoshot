package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo

/**
 * 取圖精靈的三個步驟（規格第五節）。
 *
 * **是三段不是四段**：v2 的「第四步 AI 補充」不先畫一格永遠灰著的格子 ——
 * 那等於每次取圖都展示一個做不到的承諾。代價是 v2 加第四步時進度列要改版，已知並接受。
 */
enum class WizardStep(val order: Int, val label: String) {
    URL(1, "貼網址"),
    PICK(2, "挑畫面"),
    DETAILS(3, "填資料"),
    ;

    /** 進度列上的顯示文字，如「1. 貼網址」。 */
    val indicator: String get() = "$order. $label"
}

/** 第一步的狀態。錯誤一律**就地顯示、不換頁**（規格第五節第一步的表）。 */
sealed interface Step1Status {
    data object Idle : Step1Status
    data object Loading : Step1Status
    data class Error(val message: String) : Step1Status
}

/**
 * 第一步抓到的東西，交給第二步用。
 *
 * **不含已收藏的格號** —— 那要等 storyboard 解析出層級之後才問得出來（見 [WizardData.takenFrameIndexes]）。
 */
data class LoadedVideo(
    val videoId: String,
    val page: WatchPage,
)

/**
 * 精靈需要的資料存取，**窄到可以在測試裡三行實作完**。
 *
 * 不直接注入 `LibraryRepo` —— 它有十幾個方法，而精靈只用得到兩個；
 * 為了測試一個進度列而實作十幾個 `error("不會用到")` 是浪費，也讓測試看不出重點。
 * 這與 `FileThumbs` 只收一個 `loadManualImage` 函式是同一個取捨。
 */
interface WizardData {
    suspend fun watchPage(videoId: String): WatchPage
    suspend fun recentVideos(limit: Int): List<RecentVideo>
    /**
     * 這支影片在**這個 storyboard 層級**已經收藏過的格號。
     * 層級不能省：frameIndex 只在某個層級之內有意義（規格第四節 `{videoId}/L{level}/{frameIndex}`）。
     */
    suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int>
}
