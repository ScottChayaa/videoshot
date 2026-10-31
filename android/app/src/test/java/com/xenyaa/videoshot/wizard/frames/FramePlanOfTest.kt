package com.xenyaa.videoshot.wizard.frames

import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** 縮圖牆的格數：YouTube 的 spec 一律多給一格，時間點超過片長的那一格不上牆（2026-10-20）。 */
class FramePlanOfTest {

    // -8Irr9Ah2i4 的真實數字：178.845 秒、L3 每 2 秒一格、spec 寫 91 格
    private val spec = "https://i.ytimg.com/sb/v1/storyboard3_L\$L/\$N.jpg?sqp=q" +
        "|48#27#100#10#10#0#default#rs\$a" +
        "|25#45#91#10#10#2000#M\$M#rs\$b" +
        "|50#90#91#5#5#2000#M\$M#rs\$c" +
        "|101#180#91#3#3#2000#M\$M#rs\$d"

    private fun page(durationSec: Int, durationMs: Long) = WatchPage(
        FetchResult.OK,
        VideoMeta("v1", "t", "c", "2026-01-01", durationSec, "public", true, durationMs = durationMs),
        spec,
    )

    @Test
    fun 最後一格超過片長就不上牆() {
        val (plan, _) = framePlanOf("v1", page(durationSec = 179, durationMs = 178_845))
        assertEquals(90, plan.frameCount)
        assertEquals(178.0, plan.atSec.last(), 0.0)
    }

    @Test
    fun 讀不到精確片長時用整數秒的片長() {
        val (plan, _) = framePlanOf("v1", page(durationSec = 179, durationMs = 0))
        assertEquals(90, plan.frameCount)
    }

    // l1deHCTkRBo 的真實數字：10 秒左右的剪輯，YouTube 只給 L0（100 格、間隔寫 0）
    @Test
    fun 很短的片只有L0時用L0並依片長換算時間() {
        val onlyL0 = WatchPage(
            FetchResult.OK,
            VideoMeta("v1", "t", "c", "2026-01-01", 11, "unlisted", true, durationMs = 10_500),
            "https://i.ytimg.com/sb/v1/storyboard3_L\$L/\$N.jpg?sqp=q|48#27#100#10#10#0#default#rs\$a",
        )
        val (plan, level) = framePlanOf("v1", onlyL0)
        assertEquals(0, level!!.level)
        assertEquals(0, plan.level)
        assertEquals(100, plan.frameCount)
        assertEquals(0.105, plan.atSec[1], 1e-9)
        assertEquals(true, plan.lowQuality)
    }
}
