package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
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

    suspend fun distinctPlaces(): List<String>
    suspend fun allTagNames(): List<String>

    /**
     * 第三步進場時把勾選的 storyboard 格從**本機 sheet** 裁進 `thumbs/`（規格第七節）。
     * 手動格（格號 ≥ frameCount）由實作端忽略。
     *
     * **收 spec 原字串而不是層級編號**：裁切要 cols／rows／單格尺寸，只有層級編號不夠。
     * 傳原字串的話實作端 `Storyboard.parse` 一下就有了（純邏輯）——
     * 收層級編號則會逼實作端再打一次 watch page，而這一步的重點正是「不必連網」。
     */
    suspend fun cropThumbs(
        videoId: String,
        sbSpec: String?,
        frameIndexes: List<Int>,
        onProgress: (Int, Int) -> Unit,
    ): CropOutcome

    /** 整批入庫（規格第五節「完成」第 1 步）。 */
    suspend fun commit(video: VideoEntity, picks: List<NewShot>): List<Long>

    /** 記下這一批縮圖在本機的狀態（第 2 步）。失敗不影響完成 —— 狀態可由檔案重新推導。 */
    suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>)

    /** 草稿：存、讀、清。payload 是 JSON 字串，格式在 `:core` 的 `DraftPayload`（任務 8）。 */
    suspend fun saveDraft(json: String)
    suspend fun currentDraft(): String?
    suspend fun clearDraft(videoId: String)
}

/** 進場裁圖的結果。`missing` 交給回填（規格第十一節）。 */
data class CropOutcome(val written: List<Int>, val missing: List<Int>)

/** 抽屜的既有值建議（規格第五節欄位表）。 */
data class Suggestions(val places: List<String> = emptyList(), val tags: List<String> = emptyList())

/** 完成之後要告訴外殼的兩件事：捲到哪個月份、加了幾張。 */
data class Finished(val eventDate: String, val count: Int)

/**
 * 截圖失敗的原因。**不是一個 boolean** —— 畫面要講得出為什麼：
 * 廣告是「等一下再試」、黑畫面是「這一格截不到，改用相簿」，兩者的處置完全不同。
 */
enum class CaptureError { BLACK_FRAME, NOT_DECODABLE, AD_PLAYING, NOT_READY, SAVE_FAILED }

/** 「上次《宜蘭兩天一夜》做到第三步，要繼續嗎？」的內容（規格第五節草稿）。 */
data class DraftPrompt(val videoId: String, val step: Int)
