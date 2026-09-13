package com.xenyaa.videoshot.core.storyboard

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * storyboard spec 的解析與定位。移植自 src/lib/storyboard.ts。
 * spec 格式：{baseURL}|{L0}|{L1}|{L2}|{L3}，每個 level 為 width#height#frameCount#cols#rows#intervalMs#nameReplacement#sigh。
 */
object Storyboard {

    private val sqpPattern = Regex("[?&]sqp=([^&]*)")

    fun parse(spec: String): StoryboardSpec? {
        if (spec.isEmpty()) return null
        val parts = spec.split('|')
        if (parts.size < 2) return null

        val urlPart = parts[0]
        if (!urlPart.startsWith("http")) return null
        val sqp = sqpPattern.find(urlPart)?.groupValues?.get(1) ?: ""
        val baseUrl = urlPart.substringBefore('?')

        val levels = parts.drop(1).mapIndexedNotNull { index, levelPart ->
            val fields = levelPart.split('#')
            if (fields.size < 8) return@mapIndexedNotNull null
            val numbers = fields.take(6).map { it.toIntOrNull() ?: return@mapIndexedNotNull null }
            StoryboardLevel(
                level = index,
                width = numbers[0],
                height = numbers[1],
                frameCount = numbers[2],
                cols = numbers[3],
                rows = numbers[4],
                intervalMs = numbers[5],
                sigh = fields[7],
            )
        }
        if (levels.isEmpty()) return null
        return StoryboardSpec(baseUrl, sqp, levels)
    }

    /** 取指定層級；不存在時退回最高可用的層級。intervalMs、cols、rows、frameCount 任一為 0 的層級不可用。 */
    fun pickLevel(spec: StoryboardSpec, preferred: Int = 3): StoryboardLevel? {
        val usable = spec.levels.filter { it.intervalMs > 0 && it.cols > 0 && it.rows > 0 && it.frameCount > 0 }
        if (usable.isEmpty()) return null
        return usable.find { it.level == preferred } ?: usable.last()
    }

    /**
     * 第 k 格代表影片的 t = k × interval，因此取「最近的一格」而非「之前的一格」。
     * 對照 YouTube 播放器 hover 預覽驗證：間隔 5s 的影片在 t=102.5（100 與 105 的中點）才從第 20 格換到第 21 格。
     */
    fun frameIndexAt(level: StoryboardLevel, t: Double): Int {
        if (level.intervalMs <= 0 || level.frameCount <= 0) return 0
        val raw = (max(0.0, t) / (level.intervalMs / 1000.0)).roundToInt()
        return raw.coerceIn(0, level.frameCount - 1)
    }

    fun framePosition(level: StoryboardLevel, frameIndex: Int): FramePos {
        val perSheet = level.cols * level.rows
        val sheetIndex = frameIndex / perSheet
        val posInSheet = frameIndex % perSheet
        val col = posInSheet % level.cols
        val row = posInSheet / level.cols
        return FramePos(
            frameIndex = frameIndex,
            sheetIndex = sheetIndex,
            col = col,
            row = row,
            x = col * level.width,
            y = row * level.height,
            width = level.width,
            height = level.height,
            sheetWidth = level.cols * level.width,
            sheetHeight = level.rows * level.height,
        )
    }

    fun frameAt(level: StoryboardLevel, t: Double): FramePos = framePosition(level, frameIndexAt(level, t))

    fun frameTimeSec(level: StoryboardLevel, frameIndex: Int): Double = frameIndex * level.intervalMs / 1000.0

    fun sheetUrl(spec: StoryboardSpec, level: StoryboardLevel, sheetIndex: Int): String {
        val path = spec.baseUrl.replace("\$L", level.level.toString()).replace("\$N", "M$sheetIndex")
        return "$path?sqp=${spec.sqp}&sigh=${level.sigh}"
    }
}
