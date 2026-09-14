package com.xenyaa.videoshot.wizard.frames

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xenyaa.videoshot.core.similarity.Fingerprint
import com.xenyaa.videoshot.core.similarity.dHash
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.thumbs.coverUrl
import com.xenyaa.videoshot.thumbs.grayscale9x8
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 從 watch page 算出縮圖牆的形狀。
 *
 * @return plan 與選中的層級；解不出 storyboard 時層級是 null、plan.frameCount 是 0
 *         —— 空牆，但流程照走（規格第七節降級表）
 */
fun framePlanOf(videoId: String, page: WatchPage): Pair<FramePlan, StoryboardLevel?> {
    val spec = page.storyboardSpec?.let { Storyboard.parse(it) }
    val level = spec?.let { Storyboard.pickLevel(it) }
    if (level == null) {
        return FramePlan(videoId, level = 3, atSec = emptyList(), lowQuality = false) to null
    }
    return FramePlan(
        videoId = videoId,
        level = level.level,
        atSec = List(level.frameCount) { Storyboard.frameTimeSec(level, it) },
        // L3 是偏好層級；拿到別的代表這支影片沒有 L3（規格第五節「畫質與排列」）
        lowQuality = level.level < 3,
    ) to level
}

/**
 * 真正的縮圖來源：下載 sheet、寫進 `drafts/`、當場算指紋，之後要哪一格才從磁碟裁。
 *
 * **指紋與圖的生命週期刻意分開**：一張 L3 sheet 解開約 2 MB，17 張就 35 MB；
 * 148 格各留一份 320×180 也要 34 MB。sheet 解開的那一瞬間把 9 格的 dHash 算完就釋放，
 * 收斂因此不必等使用者捲到底，記憶體也壓得住（規格第二節第 6 點）。
 *
 * @param sheetsDir `filesDir/drafts/{videoId}/sheets` —— **不可用 cacheDir**（規格第四節）
 * @param refreshSpec sprite 回 403 時重抓 watch page 拿新 spec；拿不到回 null
 */
class StoryboardFrameSource(
    private val videoId: String,
    spec: StoryboardSpec,
    private val level: StoryboardLevel,
    private val sheetsDir: File,
    private val youtube: Youtube,
    private val refreshSpec: suspend () -> StoryboardSpec?,
    private val io: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
) : FrameSource {

    override val plan: FramePlan = FramePlan(
        videoId = videoId,
        level = level.level,
        atSec = List(level.frameCount) { Storyboard.frameTimeSec(level, it) },
        lowQuality = level.level < 3,
    )

    private var currentSpec = spec
    private var refreshed = false

    /** spec 換過幾次。並行下載時用它判斷「別人是不是剛換好了」，比物件比對可靠。 */
    private var specGeneration = 0

    /** 重抓 spec 只能有一個人做 —— 4 張同時 403 時不該打四次 watch page。 */
    private val refreshLock = Mutex()

    /** 全部降級成封面圖時，封面只解一次、所有格共用。 */
    private var cover: ImageBitmap? = null

    /** 封面試過一次就不再試 —— 失敗也算「試過」，否則每一個降級批次都會再打一次網路。 */
    private var coverAttempted = false

    /**
     * 保護 [cache] 與 [cover]：縮圖牆用 `produceState` 對每個可見格子各跑一個協程呼叫
     * [bitmapOf]，[load] 同時也在寫 [cover] —— `LinkedHashMap(accessOrder = true)` 連
     * `get()` 都會動內部順序，不鎖起來就是真的資料競爭，不是風格問題。
     */
    private val cacheLock = Mutex()

    /** 最近用過的幾格。捲動時上下來回，留一點就省掉重複解 sheet。 */
    private val cache = object : LinkedHashMap<Int, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>) = size > 24
    }

    private val perSheet = level.cols * level.rows

    /**
     * **同時 4 張**（規格第五節「載入」）。再多會跟上方的播放器搶頻寬，
     * 再少則第一屏的等待變得明顯。
     */
    private val parallelism = 4

    override fun load(): Flow<SheetReady> = flow {
        if (plan.frameCount == 0) return@flow
        withContext(io) { sheetsDir.mkdirs() }

        val sheetCount = (plan.frameCount + perSheet - 1) / perSheet
        for (group in (0 until sheetCount).chunked(parallelism)) {
            // 同時抓 4 張，但**照格號順序發出去** —— 收斂是時間順序的前向掃描
            val fetched = coroutineScope {
                group.map { sheetIndex -> async(io) { sheetIndex to fetchSheet(sheetIndex) } }.awaitAll()
            }

            for ((sheetIndex, bytes) in fetched) {
                val frames =
                    (sheetIndex * perSheet until minOf((sheetIndex + 1) * perSheet, plan.frameCount)).toList()

                if (bytes == null) {
                    // 重抓 spec 後仍 403 → 退回封面圖（規格第七節）。時間標籤照舊，使用者仍挑得動。
                    // 這裡先把封面抓好、快取起來，使用者捲到這幾格時 bitmapOf() 才不必再等一次網路
                    loadCover()
                    emit(SheetReady(frames, emptyList(), degradedToCover = true))
                    continue
                }

                withContext(io) { File(sheetsDir, "M$sheetIndex.jpg").writeBytes(bytes) }

                val fingerprints = withContext(compute) {
                    val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        // 下載成功卻解不出圖是一種失敗型態，不是例外（SheetHarvester 的同一個教訓）
                        ?: return@withContext emptyList<Fingerprint>()
                    val out = frames.map { frameIndex ->
                        Fingerprint(
                            frameIndex,
                            dHash(grayscale9x8(sheet, Storyboard.framePosition(level, frameIndex))),
                        )
                    }
                    sheet.recycle()   // 指紋算完立刻放掉 —— 這一行就是記憶體壓得住的原因
                    out
                }
                emit(SheetReady(frames, fingerprints))
            }
        }
    }

    /**
     * 抓一張 sheet，403 時重抓一次 spec 再試（規格第二節第 2 點：簽章效期不可知）。
     *
     * 並行下載讓這裡比 [com.xenyaa.videoshot.thumbs.SheetHarvester] 麻煩一點：
     * 4 張可能同時 403，但**重抓 watch page 只能做一次**。
     * 用 generation 而不是比對 spec 物件 —— 重抓回來的可能是一份內容相同的新物件。
     *
     * @return null 代表重抓之後仍然拿不到
     */
    private suspend fun fetchSheet(sheetIndex: Int): ByteArray? {
        repeat(2) {
            val (spec, gen) = refreshLock.withLock { currentSpec to specGeneration }
            try {
                return youtube.sheet(Storyboard.sheetUrl(spec, level, sheetIndex))
            } catch (e: SheetForbidden) {
                val advanced = refreshLock.withLock {
                    when {
                        specGeneration > gen -> true        // 別人剛換好了，直接拿新的再試一次
                        refreshed -> false                  // 已經重抓過而且沒有更新的 → 這支影片就是降級
                        else -> {
                            refreshed = true
                            val fresh = refreshSpec()
                            if (fresh == null) {
                                false
                            } else {
                                currentSpec = fresh
                                specGeneration++
                                true
                            }
                        }
                    }
                }
                if (!advanced) return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    override suspend fun bitmapOf(frameIndex: Int): ImageBitmap? {
        if (frameIndex !in 0 until plan.frameCount) return null
        cacheLock.withLock { cache[frameIndex] }?.let { return it }

        val pos = Storyboard.framePosition(level, frameIndex)
        val file = File(sheetsDir, "M${pos.sheetIndex}.jpg")
        val bitmap = if (file.exists()) {
            withContext(compute) {
                val sheet = BitmapFactory.decodeFile(file.path) ?: return@withContext null
                val frame = if (pos.x + pos.width <= sheet.width && pos.y + pos.height <= sheet.height) {
                    Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height)
                } else {
                    null
                }
                sheet.recycle()
                frame?.asImageBitmap()
            }
        } else {
            loadCover()
        }
        // 只鎖 map 的存取 —— 解圖裁圖不包在鎖裡，格子之間仍然並行
        bitmap?.let { b -> cacheLock.withLock { cache[frameIndex] = b } }
        return bitmap
    }

    /**
     * 封面無簽章、不會過期，所以缺圖時拿它頂著是安全的（規格第五節、第七節）。
     *
     * 成功與失敗都只試一次（[coverAttempted]）——不然封面端點連不上時，
     * 每一個降級批次、之後每一次 [bitmapOf] 都會再打一次網路，比不快取更糟。
     */
    private suspend fun loadCover(): ImageBitmap? = cacheLock.withLock {
        if (coverAttempted) return@withLock cover
        coverAttempted = true
        cover = try {
            val bytes = youtube.sheet(coverUrl(videoId))
            withContext(compute) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        } catch (e: CancellationException) {
            coverAttempted = false   // 取消不算「試過了」
            throw e
        } catch (e: Exception) {
            null
        }
        cover
    }

    /** **不刪 sheet** —— 它綁在草稿上，由精靈完成或捨棄草稿時整個 `drafts/{videoId}/` 一起刪（規格第四節）。 */
    override fun close() {
        cache.clear()
        cover = null
        coverAttempted = false
    }
}
