package com.xenyaa.videoshot.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xenyaa.videoshot.core.player.previewLevelOf
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 詳情頁拖曳預覽的圖（規格第六節「預覽縮圖的來源」）：**拖到哪才下載哪一張 sheet**。
 *
 * 跟第二步的 `StoryboardFrameSource` 不同：那邊一進場就照順序下載整支影片的 sheet（要算指紋收斂），
 * 這裡使用者可能只拖一下，整支下載是白花流量。sheet 放在快取目錄（[dir]），被系統清掉只是下次重新下載。
 *
 * 任何失敗都回 null（預覽泡泡只顯示時間），不丟例外——預覽是錦上添花，不該讓拖曳壞掉。
 */
class PreviewFrames(
    spec: StoryboardSpec,
    private val level: StoryboardLevel,
    private val dir: File,
    private val youtube: Youtube,
    private val refreshSpec: suspend () -> StoryboardSpec?,
    private val io: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
) {
    private var currentSpec = spec
    private var refreshed = false

    /** 一次只下載一張；拖曳很快時同一張 sheet 不會被同時要好幾次。 */
    private val lock = Mutex()

    /** 最近解開的那一張 sheet。拖曳時大多在同一張裡移動，留一張就省掉反覆解圖。 */
    private var decoded: Pair<Int, Bitmap>? = null

    suspend fun at(sec: Double): ImageBitmap? = try {
        val pos = Storyboard.frameAt(level, sec)
        lock.withLock {
            val sheet = decoded?.takeIf { it.first == pos.sheetIndex }?.second
                ?: loadSheet(pos.sheetIndex)
                ?: return@withLock null
            withContext(compute) {
                if (pos.x + pos.width <= sheet.width && pos.y + pos.height <= sheet.height) {
                    Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height).asImageBitmap()
                } else {
                    null
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /**
     * 載入一張 sheet（讀檔、下載、寫檔、解圖、記進 [decoded]）。整段包在 [NonCancellable] 裡：
     * 阻塞的 HTTP 下載不理會協程取消，拖曳時每換一個秒數就取消上一個 [at]，若下載完才被丟掉，
     * 檔案沒寫、下一個 [at] 又重抓同一張，預覽永遠出不來。讓下載照樣跑完存進磁碟，下一次就直接讀。
     * [at] 外層的取消照常往上丟。
     */
    private suspend fun loadSheet(sheetIndex: Int): Bitmap? = withContext(NonCancellable) {
        val file = File(dir, "M$sheetIndex.jpg")
        // 磁碟上的檔案讀壞了（被截斷之類）就當沒有：刪掉、改走下載
        val fromDisk = withContext(io) { file.takeIf { it.exists() }?.readBytes() }
            ?.let { decode(it) ?: null.also { withContext(io) { file.delete() } } }
        val bitmap = fromDisk ?: download(sheetIndex)?.let { bytes ->
            withContext(io) { dir.mkdirs(); file.writeBytes(bytes) }
            decode(bytes)
        }
        bitmap?.also { decoded = sheetIndex to it }
    }

    private suspend fun decode(bytes: ByteArray): Bitmap? =
        withContext(compute) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }

    /** 403 時重抓一次 watch page 刷新 spec（同第二步）；整個物件只重抓一次。 */
    private suspend fun download(sheetIndex: Int): ByteArray? {
        repeat(2) {
            try {
                return youtube.sheet(Storyboard.sheetUrl(currentSpec, level, sheetIndex))
            } catch (e: SheetForbidden) {
                if (refreshed) return null
                refreshed = true
                currentSpec = refreshSpec() ?: return null
            }
        }
        return null
    }
}

/**
 * 從 watch page 建預覽來源；沒有 storyboard 回 null（預覽只顯示時間，規格第六節）。
 * 放在 `cacheDir/preview/{videoId}/L{level}/`。
 */
fun previewFramesOf(videoId: String, page: WatchPage, cacheDir: File, youtube: Youtube): PreviewFrames? {
    val spec = page.storyboardSpec?.let { Storyboard.parse(it) } ?: return null
    val level = previewLevelOf(spec) ?: return null
    return PreviewFrames(
        spec = spec,
        level = level,
        dir = File(cacheDir, "preview/$videoId/L${level.level}"),
        youtube = youtube,
        refreshSpec = { youtube.watchPage(videoId).storyboardSpec?.let { Storyboard.parse(it) } },
        io = Dispatchers.IO,
        compute = Dispatchers.Default,
    )
}
