package com.xenyaa.videoshot.core.color

import kotlin.math.pow

/**
 * WCAG 2.1 的相對亮度。放 :core 是因為它是純算術，而且它的用途是**守門**：
 * app 端每個色系的淺／深兩套都要通過對比檢查，新增色系時配色不合格會直接測試紅燈，
 * 不必等到驗收才發現某個字在深色模式下看不清楚（手冊 §零「深色模式」）。
 *
 * @param rgb `0xRRGGBB`，**不含 alpha**。半透明色（overlay、scrim）不適用這個公式，不要拿來檢查。
 */
fun relativeLuminance(rgb: Int): Double {
    fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((rgb shr 16) and 0xFF) +
        0.7152 * channel((rgb shr 8) and 0xFF) +
        0.0722 * channel(rgb and 0xFF)
}

/** 兩個不透明色的對比比值，1.0～21.0。AA 的內文門檻是 4.5。 */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
