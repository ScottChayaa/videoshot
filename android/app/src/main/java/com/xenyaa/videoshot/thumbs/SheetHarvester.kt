package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.xenyaa.videoshot.core.similarity.grayscale9x8
import com.xenyaa.videoshot.core.storyboard.FramePos
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * @param written 這次真的寫進 thumbs/ 的格子（已存在而跳過的不算）
 * @param failed 拿不到的格號
 * @param degradedToCover 重抓 spec 之後仍然 403 —— 呼叫端該顯示封面圖（規格第七節）
 */
data class HarvestResult(
    val written: List<ThumbKey>,
    val failed: List<Int>,
    val degradedToCover: Boolean,
)

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
     * @param frameIndexes 要取的格號
     * @param refreshSpec sprite 回 403 時重抓 watch page 拿新 spec；拿不到回 null
     */
    suspend fun harvest(
        videoId: String,
        spec: StoryboardSpec,
        level: StoryboardLevel,
        frameIndexes: List<Int>,
        refreshSpec: suspend () -> StoryboardSpec?,
    ): HarvestResult {
        val written = mutableListOf<ThumbKey>()
        val failed = mutableListOf<Int>()
        var degraded = false
        var currentSpec = spec
        var refreshed = false

        // 同一張 sheet 上的格子一起處理，一張只下載一次
        val todo = frameIndexes.filterNot { thumbs.exists(ThumbKey(videoId, level.level, it)) }
        val bySheet = todo.groupBy { Storyboard.framePosition(level, it).sheetIndex }

        for ((sheetIndex, frames) in bySheet) {
            val bytes = try {
                youtube.sheet(Storyboard.sheetUrl(currentSpec, level, sheetIndex))
            } catch (e: SheetForbidden) {
                // 簽章效期不可知（規格第二節第 2 點）：重抓一次 watch page 拿新 spec 再試
                if (refreshed) {
                    failed += frames; degraded = true; continue
                }
                refreshed = true
                val fresh = refreshSpec()
                if (fresh == null) {
                    failed += frames; degraded = true; continue
                }
                currentSpec = fresh
                try {
                    youtube.sheet(Storyboard.sheetUrl(currentSpec, level, sheetIndex))
                } catch (e2: Exception) {
                    failed += frames; degraded = true; continue
                }
            } catch (e: Exception) {
                failed += frames; continue
            }

            val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (sheet == null) {
                // 下載成功但解不出圖 —— 截圖 POC 學到的教訓：這是一種失敗型態，不是例外
                failed += frames
                continue
            }

            for (frameIndex in frames) {
                val key = ThumbKey(videoId, level.level, frameIndex)
                val ok = withContext(default) {
                    runCatching { writeFrame(sheet, Storyboard.framePosition(level, frameIndex), key) }
                        .getOrDefault(false)
                }
                if (ok) written += key else failed += frameIndex
            }
            sheet.recycle()
        }
        return HarvestResult(written, failed, degraded)
    }

    private fun writeFrame(sheet: Bitmap, pos: FramePos, key: ThumbKey): Boolean {
        if (pos.x + pos.width > sheet.width || pos.y + pos.height > sheet.height) return false
        val frame = Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height)
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format =
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        val encoded = frame.compress(format, 75, out)
        frame.recycle()
        if (!encoded) return false
        val file = thumbs.fileOf(key)
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
        return true
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
