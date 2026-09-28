package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xenyaa.videoshot.core.similarity.grayscale9x8
import com.xenyaa.videoshot.core.storyboard.FramePos
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * @param written 這次真的寫進 thumbs/ 的格子（已存在而跳過的不算）
 * @param failed 拿不到的格號——**用的是目標識別碼的 frameIndex**（`harvestRelocated` 呼叫時
 *        是 [RelocatedFrame.targetFrameIndex]，不是來源座標）
 * @param degradedToCover 重抓 spec 之後仍然 403 —— 呼叫端該顯示封面圖（規格第七節）
 */
data class HarvestResult(
    val written: List<ThumbKey>,
    val failed: List<Int>,
    val degradedToCover: Boolean,
)

/** 讀取座標跟寫入的縮圖識別碼不同時用（回填的層級重新定位，見 [SheetHarvester.harvestRelocated]）。 */
data class RelocatedFrame(val targetFrameIndex: Int, val sourceFrameIndex: Int)

/**
 * 下載 storyboard sheet、裁出指定的格子、編成 320×180 WebP 存進 thumbs/。
 *
 * **存單格不存 sheet**（規格第七節）：一張 sheet 54 KB / 9 格，單格 5.9 KB，
 * 要收藏到 9.2 格才划算 —— 但一張只有 9 格，所以存 sheet 永遠不會比較省。
 */
class SheetHarvester(
    private val youtube: Youtube,
    private val thumbs: Thumbs,
    /** 裁切與編碼是重運算，規格第三節設計原則第 5 條要求放 Default。 */
    private val default: CoroutineDispatcher,
) {

    /**
     * @param frameIndexes 要取的格號——讀取座標與寫入識別碼用的是同一個 [level]／格號
     * @param refreshSpec sprite 回 403 時重抓 watch page 拿新 spec；拿不到回 null
     */
    suspend fun harvest(
        videoId: String,
        spec: StoryboardSpec,
        level: StoryboardLevel,
        frameIndexes: List<Int>,
        refreshSpec: suspend () -> StoryboardSpec?,
    ): HarvestResult = harvestFrames(
        videoId = videoId,
        spec = spec,
        targetLevel = level.level,
        sourceLevel = level,
        frames = frameIndexes.map { RelocatedFrame(targetFrameIndex = it, sourceFrameIndex = it) },
        refreshSpec = refreshSpec,
    )

    /**
     * 跟 [harvest] 一樣，但讀取座標（[sourceLevel]／[RelocatedFrame.sourceFrameIndex]）可以
     * 跟寫入的縮圖識別碼（[targetLevel]／[RelocatedFrame.targetFrameIndex]）不同——回填遇到
     * `shot.sbLevel` 那層已經不存在時，用 `shot.atSec` 在目前可用的最高層級重新定位，但裁出
     * 來的圖仍要寫回原本的識別碼（規格第十一節：「裁出的圖仍寫入原路徑...DB 不改，路徑只是
     * 識別碼」）。
     */
    suspend fun harvestRelocated(
        videoId: String,
        spec: StoryboardSpec,
        targetLevel: Int,
        sourceLevel: StoryboardLevel,
        frames: List<RelocatedFrame>,
        refreshSpec: suspend () -> StoryboardSpec?,
    ): HarvestResult = harvestFrames(videoId, spec, targetLevel, sourceLevel, frames, refreshSpec)

    private suspend fun harvestFrames(
        videoId: String,
        spec: StoryboardSpec,
        targetLevel: Int,
        sourceLevel: StoryboardLevel,
        frames: List<RelocatedFrame>,
        refreshSpec: suspend () -> StoryboardSpec?,
    ): HarvestResult {
        val written = mutableListOf<ThumbKey>()
        val failed = mutableListOf<Int>()
        var degraded = false
        var currentSpec = spec
        var refreshed = false

        // 同一張 sheet 上的格子一起處理，一張只下載一次。分組依據是「讀取座標」的 sheetIndex。
        val todo = frames.filterNot { thumbs.exists(ThumbKey(videoId, targetLevel, it.targetFrameIndex)) }
        val bySheet = todo.groupBy { Storyboard.framePosition(sourceLevel, it.sourceFrameIndex).sheetIndex }

        for ((sheetIndex, group) in bySheet) {
            val bytes = try {
                youtube.sheet(Storyboard.sheetUrl(currentSpec, sourceLevel, sheetIndex))
            } catch (e: SheetForbidden) {
                // 簽章效期不可知（規格第二節第 2 點）：重抓一次 watch page 拿新 spec 再試
                if (refreshed) {
                    failed += group.map { it.targetFrameIndex }; degraded = true; continue
                }
                refreshed = true
                val fresh = refreshSpec()
                if (fresh == null) {
                    failed += group.map { it.targetFrameIndex }; degraded = true; continue
                }
                currentSpec = fresh
                try {
                    youtube.sheet(Storyboard.sheetUrl(currentSpec, sourceLevel, sheetIndex))
                } catch (e2: Exception) {
                    failed += group.map { it.targetFrameIndex }; degraded = true; continue
                }
            } catch (e: Exception) {
                failed += group.map { it.targetFrameIndex }; continue
            }

            val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (sheet == null) {
                // 下載成功但解不出圖 —— 截圖 POC 學到的教訓：這是一種失敗型態，不是例外
                failed += group.map { it.targetFrameIndex }
                continue
            }

            for (frame in group) {
                val key = ThumbKey(videoId, targetLevel, frame.targetFrameIndex)
                val pos = Storyboard.framePosition(sourceLevel, frame.sourceFrameIndex)
                val ok = withContext(default) {
                    runCatching { writeFrameToThumbs(sheet, pos, key, thumbs) }.getOrDefault(false)
                }
                if (ok) written += key else failed += frame.targetFrameIndex
            }
            sheet.recycle()
        }
        return HarvestResult(written, failed, degraded)
    }
}

/**
 * 把 sheet 上的某一格取樣成 9×8 灰階，給 dHash 用（規格第五節第二步）。
 *
 * 取樣數學在 `:core`（純邏輯、JVM 就測得完）；這裡只負責把那一格的像素**一次**搬出來。
 * 用 `getPixels` 整塊複製而不是逐點 `getPixel`：區域平均要掃過整格的像素，
 * 一格 320×180 就是 5.7 萬個點，逐點跨 JNI 呼叫會慢到不能接受。
 */
fun grayscale9x8(sheet: Bitmap, pos: FramePos): IntArray {
    val pixels = IntArray(pos.width * pos.height)
    sheet.getPixels(pixels, 0, pos.width, pos.x, pos.y, pos.width, pos.height)
    return grayscale9x8(pixels, pos.width, pos.height)
}
