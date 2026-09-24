package com.xenyaa.videoshot.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 一個色系在**一種明暗模式**下的全部顏色。
 *
 * 欄位名綁**語意**不綁顏色 —— `danger` 而不是 `red`。這是「紅色只用在破壞性動作」（手冊 §零）
 * 能在換色系之後仍然成立的原因：畫面寫的是「這個動作是破壞性的」，不是「這裡塗紅色」。
 *
 * 少填一個欄位就編譯不過，所以新增色系不可能漏掉某個狀態色。
 * 色值的來源是 `mockups/uiux-v2/styles.css`，那份 CSS 仍然是視覺參考。
 */
@Immutable
data class Palette(
    // ── 底層 ──
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val borderStrong: Color,

    // ── 文字（三階）──
    val text: Color,
    val textDim: Color,
    /** 只用在卡片（surface）上的極次要資訊；放在 bg 上會過不了 AA。 */
    val textFaint: Color,

    // ── 主色系 ──
    val accent: Color,
    /** 主色上面的字。 */
    val accentInk: Color,
    val accentWeak: Color,
    val accentLine: Color,

    // ── 副色系（帳號頁 hero 漸層，階段 11 會用到）──
    val hero1: Color,
    val hero2: Color,

    // ── 語意色 ──
    val danger: Color,
    /** 破壞色上面的字。 */
    val dangerInk: Color,
    val dangerWeak: Color,
    val warn: Color,
    val warnWeak: Color,
    val ok: Color,
    val okWeak: Color,

    // ── 覆蓋層（半透明，不參與對比檢查）──
    val overlay: Color,
    val scrim: Color,
    /** Lightbox 的底。**必須不透明**（規格第六節）。 */
    val lightboxBg: Color,
    val focusRing: Color,
)

/**
 * 一個「色系」＝淺色一套 ＋ 深色一套。
 *
 * @param id 存進 DataStore 的識別碼，**不可更動**（改了使用者選過的主題會失效）
 * @param label 設定頁顯示的名稱（階段 11 的帳號頁）
 */
@Immutable
data class ThemeSpec(
    val id: String,
    val label: String,
    val light: Palette,
    /**
     * 深色的那一套。**可以不做**（節慶主題通常只配一套顏色）——
     * 留 null 時深色模式沿用 [light]，而不是掉回預設色系：
     * 使用者選了聖誕卻在晚上看到靛藍，會以為主題壞了。
     *
     * 預設色系（`Palettes.DEFAULT`）例外，它一定要兩套都有，`ThemeSpecTest` 會擋。
     */
    val dark: Palette? = null,
)

/** 這個模式實際要用哪一套配色。 */
fun ThemeSpec.paletteFor(dark: Boolean): Palette = if (dark) this.dark ?: light else light

/**
 * Material3 的底層 `ColorScheme` 要不要用深色的那一份。
 *
 * 跟著**實際用的配色**走，不是跟著系統走 —— 只有淺色的主題在深色模式下用的是淺色配色，
 * 底層 scheme 若還套深色，現成元件（Button／TextField）的預設值會跟 token 打架。
 */
fun ThemeSpec.usesDarkScheme(dark: Boolean): Boolean = dark && this.dark != null
