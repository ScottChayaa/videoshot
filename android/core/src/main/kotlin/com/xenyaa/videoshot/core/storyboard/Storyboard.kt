package com.xenyaa.videoshot.core.storyboard

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
}
