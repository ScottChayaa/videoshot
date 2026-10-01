package com.xenyaa.videoshot.debug.seed

import com.xenyaa.videoshot.core.storyboard.Storyboard
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SeedPlannerTest {
    // 單元測試的工作目錄是 android/app
    private val file: SeedFile = Json.decodeFromString(File("src/debug/assets/seed/mock-seed.json").readText())
    private val plan = SeedPlanner.plan(file, now = 1_000L)

    // 原型的 13 個 clip 實際分布在 11 支影片（mock-data.js 的 VIDEOS 另有未使用的影片，不會匯入）。
    @Test fun 十三張分在十一支影片() {
        assertEquals(11, plan.batches.size)
        assertEquals(13, plan.batches.sumOf { it.picks.size })
    }

    @Test fun 影片id用真實id() {
        assertNotNull(plan.batches.find { it.video.id == "r4YnYow7L-4" })
    }

    @Test fun 格子編號依storyboard換算() {
        val batch = plan.batches.first { it.video.id == "Ea8ICLXTDaU" }
        val level = Storyboard.pickLevel(Storyboard.parse(batch.video.sbSpec!!)!!)!!
        val pick = batch.picks.first { it.atSec == 132.0 }
        assertEquals(level.level, pick.sbLevel)
        assertEquals(Storyboard.frameIndexAt(level, 132.0), pick.frameIndex)
        assertEquals("storyboard", pick.source)
    }

    @Test fun 發布日取該片最早的事件日() {
        assertEquals("2026-08-03", plan.batches.first { it.video.id == "Ea8ICLXTDaU" }.video.publishedAt)
    }

    @Test fun 地點與標籤分開() {
        val pick = plan.batches.first { it.video.id == "Ea8ICLXTDaU" }.picks.first { it.atSec == 132.0 }
        assertEquals("加勒比海", pick.place)
        assertEquals(listOf("夜潛", "龍蝦"), pick.tagNames)
        assertEquals("topic", plan.tagKinds["夜潛"])
        assertNull(plan.tagKinds["加勒比海"])
    }

    @Test fun 資料夾帶著shot的key() {
        assertEquals(listOf("c01", "c02", "c03", "c04"), plan.folders.first { it.name == "加勒比海之旅" }.shotKeys)
    }

    @Test fun 解不出spec就不給格子編號() {
        val broken = file.copy(videos = file.videos.map { it.copy(sbSpec = null) })
        val p = SeedPlanner.plan(broken, now = 0L)
        assertNull(p.batches.first().picks.first().frameIndex)
    }
}
