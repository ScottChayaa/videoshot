package com.xenyaa.videoshot.wizard.frames

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.similarity.Fingerprint
import com.xenyaa.videoshot.core.similarity.hammingDistance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 假實作本身也要測 —— 它是後面每一個 UI 測試的地基，
 * 地基算錯格號的話，上面所有測試都在驗一件錯的事。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FakeFrameSourceTest {

    @Test
    fun plan_的秒數由格數與間隔算出來() {
        val source = FakeFrameSource.of(frameCount = 4, intervalSec = 2.5)
        assertEquals(4, source.plan.frameCount)
        assertEquals(listOf(0.0, 2.5, 5.0, 7.5), source.plan.atSec)
    }

    @Test
    fun load_一張sheet發一次而且格號連續() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 5, perSheet = 2)
        val batches = source.load().toList()

        assertEquals(3, batches.size)
        assertEquals(listOf(0, 1), batches[0].frameIndexes)
        assertEquals(listOf(2, 3), batches[1].frameIndexes)
        assertEquals(listOf(4), batches[2].frameIndexes)
    }

    @Test
    fun load_帶出來的指紋格號對得上() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 3, perSheet = 3)
        val batch = source.load().toList().single()
        assertEquals(listOf(0, 1, 2), batch.fingerprints.map { it.frameIndex })
    }

    @Test
    fun 可以指定每一格的指紋() = runBlocking {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, listOf(0.0, 1.0, 2.0), lowQuality = false),
            perSheet = 3,
            hashes = listOf(0L, 0L, Long.MAX_VALUE),
        )
        val batch = source.load().toList().single()
        assertEquals(
            listOf(Fingerprint(0, 0L), Fingerprint(1, 0L), Fingerprint(2, Long.MAX_VALUE)),
            batch.fingerprints,
        )
    }

    @Test
    fun bitmapOf_格號在範圍內就給得出圖() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 2)
        assertNotNull(source.bitmapOf(0))
        assertNotNull(source.bitmapOf(1))
    }

    @Test
    fun bitmapOf_超出範圍回null而不是丟例外() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 2)
        assertNull(source.bitmapOf(9))
    }

    @Test
    fun 空的plan代表解不出storyboard_load不會發任何東西() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 0)
        assertEquals(0, source.plan.frameCount)
        assertEquals(emptyList<SheetReady>(), source.load().toList())
    }

    @Test
    fun EmptyFrameSource_是空牆而且不當機() = runBlocking {
        val source = EmptyFrameSource("vid")
        assertEquals(0, source.plan.frameCount)
        assertEquals("vid", source.plan.videoId)
        assertEquals(emptyList<SheetReady>(), source.load().toList())
        assertNull(source.bitmapOf(0))
        source.close()   // 不該丟例外
    }

    /**
     * 這一條是**其他測試的前提**：Step2StoreTest 有好幾個測試假設
     * 「用預設指紋就一張都不會被收斂掉」。前提壞了那些測試會驗出錯誤的結論，
     * 所以把前提本身寫成一條測試。
     */
    @Test
    fun 預設指紋相鄰夠遠_最寬鬆的強度也收斂不掉() {
        val hashes = (0 until 200).map { FakeFrameSource.spreadHash(it) }
        val worst = (0 until hashes.size - 1).minOf { hammingDistance(hashes[it], hashes[it + 1]) }
        assertTrue("相鄰最小距離 $worst 要大於 HIGH 的門檻 ${FilterStrength.HIGH.threshold}",
            worst > FilterStrength.HIGH.threshold)
    }

    @Test
    fun 降級成封面時每一批都標記得出來() = runBlocking {
        val source = FakeFrameSource.of(frameCount = 2, perSheet = 1, coverDegraded = true)
        assertEquals(listOf(true, true), source.load().toList().map { it.degradedToCover })
    }
}
