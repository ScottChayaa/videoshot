package com.xenyaa.videoshot.wizard.frames

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Color
import com.xenyaa.videoshot.core.similarity.Fingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 測試用的縮圖來源：不碰網路、不碰磁碟，格子是純色小圖。
 *
 * 放在 main 而不是測試目錄，理由與 [com.xenyaa.videoshot.player.FakePlayer] 相同 ——
 * `test` 與 `androidTest` 兩個 source set 之間不能互相引用。
 *
 * @param hashes 每一格的指紋；長度要等於 plan.frameCount
 * @param perSheet 幾格算一張 sheet，決定 [load] 分幾批發
 */
class FakeFrameSource(
    override val plan: FramePlan,
    private val perSheet: Int = 9,
    private val hashes: List<Long> = List(plan.frameCount) { spreadHash(it) },
    private val coverDegraded: Boolean = false,
) : FrameSource {

    /** 呼叫過幾次 bitmapOf —— 測試用它驗「有沒有只拿可見範圍的格子」。 */
    val bitmapRequests: MutableList<Int> = mutableListOf()

    var closed: Boolean = false
        private set

    override fun load(): Flow<SheetReady> = flow {
        (0 until plan.frameCount).chunked(perSheet).forEach { frames ->
            emit(
                SheetReady(
                    frameIndexes = frames,
                    // 降級成封面圖的批次**沒有指紋** —— 真實作
                    // （[StoryboardFrameSource]）拿不到 sheet 就算不出 dHash，
                    // 發的是 `emptyList()`。假實作照樣發滿指紋的話，
                    // 「降級之後牆上還有東西可看」這件事在測試裡永遠驗不到。
                    fingerprints = if (coverDegraded) emptyList() else frames.map { Fingerprint(it, hashes[it]) },
                    degradedToCover = coverDegraded,
                )
            )
        }
    }

    override suspend fun bitmapOf(frameIndex: Int): ImageBitmap? {
        bitmapRequests += frameIndex
        if (frameIndex !in 0 until plan.frameCount) return null
        // 小一點就好：測試只在乎「有沒有圖」，不在乎尺寸。
        // Robolectric 已設 @GraphicsMode(NATIVE)，這裡產生的是真的 bitmap。
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(frameIndex * 20 % 256, 128, 200))
        return bitmap.asImageBitmap()
    }

    override fun close() { closed = true }

    companion object {
        /**
         * splitmix64。**預設指紋一定要相隔夠遠** —— 相鄰格的漢明距離實測 23～43，
         * 遠大於最寬鬆的 `FilterStrength.HIGH`（門檻 10），
         * 所以沒有特別指定 hashes 的測試「一張都不會被收斂掉」是可以依賴的前提。
         *
         * 用 `index shl 8` 這種寫法會踩雷：相鄰兩格只差 1 個 bit，反而**全部**會被收斂成一張。
         */
        fun spreadHash(index: Int): Long {
            var z = index.toLong() * -0x61c8864680b583ebL + -0x7ee3623a03d3c83fL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        /** 最常用的形態：連續格號、固定間隔、每格指紋都隔得夠遠（所以完全不會被收斂掉）。 */
        fun of(
            frameCount: Int,
            intervalSec: Double = 10.0,
            perSheet: Int = 9,
            lowQuality: Boolean = false,
            coverDegraded: Boolean = false,
        ): FakeFrameSource = FakeFrameSource(
            plan = FramePlan(
                videoId = "fake-video",
                level = 3,
                atSec = List(frameCount) { it * intervalSec },
                lowQuality = lowQuality,
            ),
            perSheet = perSheet,
            coverDegraded = coverDegraded,
        )
    }
}
