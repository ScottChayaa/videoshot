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
 * @param sheetsDir `filesDir/drafts/{videoId}/sheets/L{level}` —— **不可用 cacheDir**（規格第四節）。
 *        層級一定要在路徑裡：`M{n}.jpg` 的檔名裡沒有層級，續做時換了層級還沿用舊檔，
 *        就是拿別的層級的 sheet 去裁這一層的座標
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

    /** 全部降級成封面圖時，封面只解一次、所有格共用。[close] 會在別的執行緒上清掉它，所以是 volatile。 */
    @Volatile
    private var cover: ImageBitmap? = null

    /** 封面試過一次就不再試 —— 失敗也算「試過」，否則每一個降級批次都會再打一次網路。只在 [coverLock] 裡動。 */
    private var coverAttempted = false

    /** [close] 之後就不要再做白工，也不要再把東西放回已經清空的快取。 */
    @Volatile
    private var closed = false

    /**
     * 保護 [cache]：縮圖牆用 `produceState` 對每個可見格子各跑一個協程呼叫 [bitmapOf] ——
     * `LinkedHashMap(accessOrder = true)` 連 `get()` 都會動內部順序，不鎖起來就是真的資料競爭，
     * 不是風格問題。**只圈 map 存取**，解圖裁圖與封面的網路請求都不包在裡面。
     *
     * 用 `synchronized` 而不是 `Mutex`：[close] 不是 suspend（它由 `Step2Store.close()`
     * 在主執行緒上直接呼叫），拿不到 `Mutex`；先前 `cache.clear()` 就是在**沒有持鎖**的情況下
     * 動一個 access-ordered 的 `LinkedHashMap`，跟併發中的 [bitmapOf] 撞在一起就是
     * `ConcurrentModificationException`。圈住的只有 map 存取，裡面不會阻塞。
     */
    private val cacheGuard = Any()

    /**
     * 封面自己一把鎖，**不跟 [cache] 共用**。共用的話，一次封面的網路請求在飛的期間，
     * 任何一格「已經在快取裡」的查詢都要排隊等它 —— 而封面只在降級時才抓，
     * 那正是牆上最需要快速給圖的時候。兩把鎖從不巢狀取得，所以沒有死結風險。
     */
    private val coverLock = Mutex()

    /** 最近用過的幾格。捲動時上下來回，留一點就省掉重複解 sheet。只在 [cacheGuard] 裡動。 */
    private val cache = object : LinkedHashMap<Int, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>) = size > 24
    }

    private val perSheet = level.cols * level.rows

    /**
     * **同時 4 張**（規格第五節「載入」）。再多會跟上方的播放器搶頻寬，
     * 再少則第一屏的等待變得明顯。
     */
    private val parallelism = 4

    /**
     * 把一段可能失敗的處理圈起來：**失敗只降級這一張 sheet，不炸掉整條 flow**
     * （規格第三節設計原則第 6 條：功能降級，絕不當機）。
     *
     * `OutOfMemoryError` 也要接 —— sheet 解圖是整條管線最可能 OOM 的地方，而它不是 `Exception`。
     * `CancellationException` 照樣往上丟，否則 [close] 取消不掉還在跑的下載。
     */
    private inline fun <T> degrading(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    override fun load(): Flow<SheetReady> = flow {
        if (plan.frameCount == 0) return@flow
        // 建不出草稿目錄（儲存空間滿、路徑被佔住）不是當機的理由 —— 待會兒寫檔會失敗，
        // 那幾張就照 403 的路徑退回封面圖
        degrading { withContext(io) { sheetsDir.mkdirs() } }

        val sheetCount = (plan.frameCount + perSheet - 1) / perSheet
        for (group in (0 until sheetCount).chunked(parallelism)) {
            // 同時抓 4 張，但**照格號順序發出去** —— 收斂是時間順序的前向掃描
            val fetched = coroutineScope {
                group.map { sheetIndex -> async(io) { sheetIndex to fetchSheet(sheetIndex) } }.awaitAll()
            }

            for ((sheetIndex, bytes) in fetched) {
                val frames =
                    (sheetIndex * perSheet until minOf((sheetIndex + 1) * perSheet, plan.frameCount)).toList()

                // 寫不進草稿目錄跟拿不到 sheet 是同一種下場：磁碟上沒有這張圖，bitmapOf() 只能給封面。
                // 指紋就算算得出來也不發 —— 牆上那幾格畫的都是同一張封面，
                // 照指紋收斂等於藏掉使用者根本分辨不出差別的格子
                val stored = bytes != null && degrading {
                    withContext(io) { File(sheetsDir, "M$sheetIndex.jpg").writeBytes(bytes); true }
                } == true

                if (bytes == null || !stored) {
                    // 重抓 spec 後仍 403 → 退回封面圖（規格第七節）。時間標籤照舊，使用者仍挑得動。
                    // 這裡先把封面抓好、快取起來，使用者捲到這幾格時 bitmapOf() 才不必再等一次網路
                    degrading { loadCover() }
                    emit(SheetReady(frames, emptyList(), degradedToCover = true))
                    continue
                }

                // 算不出指紋不影響看圖：sheet 已經在磁碟上，那幾格照樣裁得出來。
                // 沒有指紋的格子由 Step2Store 當成「永遠保留」，不會從牆上消失
                val fingerprints = degrading {
                    withContext(compute) {
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
                } ?: emptyList()
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
        // 續做草稿時 sheet 往往還在本機 —— 重抓一次只是白花流量。
        // 讀壞了（檔案被截斷）就當作沒有，照常走下載
        val local = withContext(io) {
            runCatching { File(sheetsDir, "M$sheetIndex.jpg").takeIf { it.exists() }?.readBytes() }.getOrNull()
        }
        if (local != null && local.isNotEmpty()) return local

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
        if (closed) return null
        if (frameIndex !in 0 until plan.frameCount) return null
        synchronized(cacheGuard) { cache[frameIndex] }?.let { return it }

        val pos = Storyboard.framePosition(level, frameIndex)
        val file = File(sheetsDir, "M${pos.sheetIndex}.jpg")
        // 解圖與裁圖同樣是「拿不到就回 null，不丟例外」（FrameSource 的契約）——
        // 檔案被清掉、內容壞掉、格子解到一半 OOM，都只該讓這一格空著
        val bitmap = degrading {
            if (file.exists()) {
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
        }
        // 只鎖 map 的存取 —— 解圖裁圖不包在鎖裡，格子之間仍然並行
        bitmap?.let { b -> synchronized(cacheGuard) { if (!closed) cache[frameIndex] = b } }
        return bitmap
    }

    /**
     * 封面無簽章、不會過期，所以缺圖時拿它頂著是安全的（規格第五節、第七節）。
     *
     * 成功與失敗都只試一次（[coverAttempted]）——不然封面端點連不上時，
     * 每一個降級批次、之後每一次 [bitmapOf] 都會再打一次網路，比不快取更糟。
     */
    private suspend fun loadCover(): ImageBitmap? {
        if (closed) return null
        return coverLock.withLock {
            if (coverAttempted) return@withLock cover
            coverAttempted = true
            val loaded = try {
                val bytes = youtube.sheet(coverUrl(videoId))
                withContext(compute) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            } catch (e: CancellationException) {
                coverAttempted = false   // 取消不算「試過了」
                throw e
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            // 網路在飛的期間可能已經被 close 掉了 —— 那就不要把圖再放回來（[close] 的用意是放掉記憶體）
            if (closed) null else { cover = loaded; loaded }
        }
    }

    /**
     * **不刪 sheet** —— 它綁在草稿上，由精靈完成或捨棄草稿時整個 `drafts/{videoId}/` 一起刪（規格第四節）。
     *
     * 不是 suspend（呼叫端 `Step2Store.close()` 也不是），所以**不能等 [coverLock]**：
     * 改成先立旗標再清快取 —— 併發中的 [bitmapOf]／[loadCover] 看到 `closed` 就不再把東西放回來。
     */
    override fun close() {
        closed = true
        synchronized(cacheGuard) { cache.clear() }
        cover = null
    }
}
