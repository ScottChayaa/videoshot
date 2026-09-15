package com.xenyaa.videoshot.thumbs

import android.graphics.BitmapFactory
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @param written 這次真的寫進 `thumbs/` 的格子（已存在而跳過的不算）
 * @param missing 裁不出來的格號 —— 呼叫端要把它們記成 `thumb_state = 'missing'`，交給回填（規格第十一節）
 */
data class CropResult(val written: List<ThumbKey>, val missing: List<Int>)

/**
 * 取圖第三步進場時的裁圖：**sheet 已經在本機，不下載**（規格第七節「流程（第三步進場時）」）。
 *
 * 與 [SheetHarvester] 的差別只有這一件事。裁切、編碼、寫檔共用 [writeFrameToThumbs]。
 *
 * @param compute 裁切與編碼是重運算，規格第三節設計原則第 5 條要求放 Default
 */
class LocalSheetCropper(
    private val thumbs: Thumbs,
    private val io: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
) {

    /**
     * @param sheetsDir `drafts/{videoId}/sheets/L{level}` —— 第二步寫進去的那一個
     * @param frameIndexes 要裁的格號。**超出 `level.frameCount` 的一律忽略** ——
     *        那些是手動補圖，圖在 `shot_image` 的 BLOB，不走 `thumbs/`
     * @param onProgress (已完成, 總數)。sheet 已在本機，這個進度幾乎一閃而過
     */
    suspend fun crop(
        videoId: String,
        level: StoryboardLevel,
        sheetsDir: File,
        frameIndexes: List<Int>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): CropResult {
        val storyboardFrames = frameIndexes.filter { it in 0 until level.frameCount }
        val total = storyboardFrames.size
        val written = mutableListOf<ThumbKey>()
        val missing = mutableListOf<Int>()
        var done = 0

        // 同一張 sheet 上的格子一起處理 —— 一張只解一次圖
        val bySheet = storyboardFrames.groupBy { Storyboard.framePosition(level, it).sheetIndex }

        for ((sheetIndex, frames) in bySheet) {
            val file = File(sheetsDir, "M$sheetIndex.jpg")
            // 已經全部存在就不必解這張 sheet（規格第七節步驟 3）
            val todo = frames.filterNot { thumbs.exists(ThumbKey(videoId, level.level, it)) }
            if (todo.isEmpty()) {
                done += frames.size
                onProgress(done, total)
                continue
            }

            val sheet = withContext(io) {
                // 檔案不見了、內容壞掉、解到一半 OOM —— 都只是「這幾格沒有圖」，不是當機的理由
                runCatching { if (file.exists()) BitmapFactory.decodeFile(file.path) else null }
                    .getOrNull()
            }
            if (sheet == null) {
                // **只記 todo，不是整組 frames** —— 這一組裡可能有上一輪就已經裁好的格子，
                // 它們的檔案好好地躺在 thumbs/ 裡。把它們也記成 missing，回填作業會為了
                // 已經存在的縮圖再下載一次 sheet
                missing += todo
                done += frames.size
                onProgress(done, total)
                continue
            }

            for (frameIndex in todo) {
                val key = ThumbKey(videoId, level.level, frameIndex)
                val ok = withContext(compute) {
                    runCatching {
                        writeFrameToThumbs(sheet, Storyboard.framePosition(level, frameIndex), key, thumbs)
                    }.getOrDefault(false)
                }
                if (ok) written += key else missing += frameIndex
            }
            sheet.recycle()   // 裁完立刻放掉 —— 一張 L3 sheet 解開約 2 MB
            done += frames.size
            onProgress(done, total)
        }
        return CropResult(written, missing)
    }
}
