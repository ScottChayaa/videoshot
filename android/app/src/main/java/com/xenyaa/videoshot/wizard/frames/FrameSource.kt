package com.xenyaa.videoshot.wizard.frames

import androidx.compose.ui.graphics.ImageBitmap
import com.xenyaa.videoshot.core.similarity.Fingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 這支影片的縮圖牆長什麼樣子。
 *
 * @param atSec 每一格對應的影片秒數，**index 就是 frameIndex**
 * @param lowQuality 沒有 L3、降到較低層級時為 true，畫面要提示（規格第五節「畫質與排列」）
 */
data class FramePlan(
    val videoId: String,
    val level: Int,
    val atSec: List<Double>,
    val lowQuality: Boolean,
) {
    /** 0 代表整支影片解不出 storyboard —— 空牆，但流程照走（規格第七節降級表）。 */
    val frameCount: Int get() = atSec.size
}

/**
 * 一張 sheet 好了。
 *
 * 指紋跟著格子一起發 —— sheet 解開的那一瞬間就把 dHash 算完，
 * bitmap 隨即釋放（規格第二節第 6 點：一張 L3 sheet 解開約 2 MB，17 張就 35 MB）。
 *
 * @param degradedToCover 這幾格拿不到真圖（重抓 spec 後仍 403），畫面上是封面圖（規格第七節）
 */
data class SheetReady(
    val frameIndexes: List<Int>,
    val fingerprints: List<Fingerprint>,
    val degradedToCover: Boolean = false,
)

/**
 * 縮圖牆的格子從哪裡來。
 *
 * 抽這一層的理由跟 [com.xenyaa.videoshot.player.Player] 一樣：
 * 真實作要網路與磁碟，而縮圖牆的規則（收斂、勾選、篩選）不該為了被測試而等 YouTube。
 */
interface FrameSource {

    /** 建構時就決定，之後不變。 */
    val plan: FramePlan

    /** 收到一張 sheet 發一次。全部發完就結束 —— 呼叫端據此判定收斂已完成。 */
    fun load(): Flow<SheetReady>

    /** 畫面要畫某一格時才呼叫。拿不到回 null，**不丟例外**。 */
    suspend fun bitmapOf(frameIndex: Int): ImageBitmap?

    /** 釋放快取與 bitmap。離開第二步時呼叫。 */
    fun close()
}

/**
 * 整支影片解不出 storyboard（`no_storyboard`／`parse_failed`）時的來源：什麼都沒有。
 *
 * 縮圖牆是空的，但**第二步仍然進得去**，之後由【截圖】補圖（規格第七節降級表）。
 * 這是正式路徑會用到的形態，所以不是 [FakeFrameSource] 的職責 ——
 * 把測試替身接進正式接線，之後沒有人分得出哪些程式碼是真的。
 */
class EmptyFrameSource(videoId: String) : FrameSource {
    override val plan = FramePlan(videoId, level = 3, atSec = emptyList(), lowQuality = false)
    override fun load(): Flow<SheetReady> = emptyFlow()
    override suspend fun bitmapOf(frameIndex: Int): ImageBitmap? = null
    override fun close() = Unit
}
