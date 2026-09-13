package com.xenyaa.videoshot.core.storyboard

/** YouTube storyboard 的一個畫質層級（L0～L3）。level 是它在 spec 字串裡的順位。 */
data class StoryboardLevel(
    val level: Int,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val cols: Int,
    val rows: Int,
    val intervalMs: Int,
    val sigh: String,
)

data class StoryboardSpec(
    val baseUrl: String,
    val sqp: String,
    val levels: List<StoryboardLevel>,
)

/** 某一格在 sheet 裡的位置。x／y 是該格左上角在 sheet 內的像素座標。 */
data class FramePos(
    val frameIndex: Int,
    val sheetIndex: Int,
    val col: Int,
    val row: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val sheetWidth: Int,
    val sheetHeight: Int,
)
