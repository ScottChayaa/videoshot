package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.xenyaa.videoshot.core.color.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PaletteTest {

    private fun rgb(color: androidx.compose.ui.graphics.Color): Int = color.toArgb() and 0xFFFFFF

    /**
     * 每個色系**做了的每一套**都要通過 AA 的 4.5:1。
     * **新增色系時這個測試會自動涵蓋它** —— Palettes.ALL 是唯一的註冊表。
     *
     * `dark` 是選配（節慶主題可以只做淺色），所以深色那一套只在有做的時候檢查；
     * 「只有淺色」本身不是缺陷，但做了就要合格。
     */
    @Test
    fun 每個色系的文字與語意色都過_AA() {
        for (spec in Palettes.ALL) {
            val sets = buildList {
                add("淺色" to spec.light)
                spec.dark?.let { add("深色" to it) }
            }
            for ((mode, p) in sets) {
                fun check(label: String, fg: Int, bg: Int) {
                    val ratio = contrastRatio(fg, bg)
                    assertTrue("${spec.id} $mode 的 $label 只有 ${"%.2f".format(ratio)}:1，低於 4.5", ratio >= 4.5)
                }
                check("主文字對底色", rgb(p.text), rgb(p.bg))
                check("主文字對卡片", rgb(p.text), rgb(p.surface))
                check("次要文字對底色", rgb(p.textDim), rgb(p.bg))
                // textFaint 依 mockup 的註解「只用在白底上的極次要資訊」，所以只檢查卡片底
                check("極次要文字對卡片", rgb(p.textFaint), rgb(p.surface))
                check("主色上的字", rgb(p.accentInk), rgb(p.accent))
                check("破壞色上的字", rgb(p.dangerInk), rgb(p.danger))
                check("破壞色對卡片", rgb(p.danger), rgb(p.surface))
                check("警示色對卡片", rgb(p.warn), rgb(p.surface))
                check("成功色對卡片", rgb(p.ok), rgb(p.surface))
                // accent 不只拿來填色，也直接當文字色用（導覽選取的那一格、排序鈕、篩選列的圖示），
                // 所以它自己也要在底色與卡片上讀得到 —— 亮色系的主色很容易在這裡破功
                check("主色當文字對底色", rgb(p.accent), rgb(p.bg))
                check("主色當文字對卡片", rgb(p.accent), rgb(p.surface))
            }
        }
    }

    /** Lightbox 的底是**不透明**的（規格第六節、手冊 §三第一條），alpha 必須是 1。 */
    @Test
    fun Lightbox_的底不透明() {
        for (spec in Palettes.ALL) {
            assertEquals(1f, spec.light.lightboxBg.alpha, 0f)
            spec.dark?.let { assertEquals(1f, it.lightboxBg.alpha, 0f) }
        }
    }

    @Test
    fun 註冊表有預設色系且_id_不重複() {
        assertTrue(Palettes.DEFAULT in Palettes.ALL)
        assertEquals(Palettes.ALL.size, Palettes.ALL.map { it.id }.distinct().size)
        assertEquals(Palettes.DEFAULT, Palettes.byId("不存在的色系"))
        assertEquals(Palettes.DEFAULT, Palettes.byId(null))
    }

    /**
     * 字級只有 6 階（手冊 §零「間距與字級有尺標」）。
     *
     * **列全部 15 個 slot**，不是只列目前用到的幾個 —— 漏列的 slot 會悄悄維持 Material
     * 內建預設（例如 `labelMedium` 預設 12sp），這個測試就抓不到，字級尺標形同虛設
     * （階段 7 全盤覆查第 8 點第 2 項踩過的坑：底部導覽列的文字正是走 `labelMedium`）。
     */
    @Test
    fun 字級只有六階() {
        val sizes = listOf(
            Typography.displayLarge, Typography.displayMedium, Typography.displaySmall,
            Typography.headlineLarge, Typography.headlineMedium, Typography.headlineSmall,
            Typography.titleLarge, Typography.titleMedium, Typography.titleSmall,
            Typography.bodyLarge, Typography.bodyMedium, Typography.bodySmall,
            Typography.labelLarge, Typography.labelMedium, Typography.labelSmall,
        ).map { it.fontSize.value }.distinct().sorted()
        assertEquals(listOf(13f, 15f, 17f, 20f, 24f, 28f), sizes)
    }

    /** 間距只有 4/8/12/16/24/32。 */
    @Test
    fun 間距尺標只有六格() {
        assertEquals(
            listOf(4f, 8f, 12f, 16f, 24f, 32f),
            listOf(Spacing.s1, Spacing.s2, Spacing.s3, Spacing.s4, Spacing.s5, Spacing.s6).map { it.value },
        )
    }

    /**
     * 新年主題（節慶）**只做淺色一套** —— 節慶配色不強制備齊深色（見 `ThemeSpec.dark` 的 KDoc）。
     * 對比檢查已由上面那條測試自動涵蓋它的淺色。
     */
    @Test
    fun 新年主題在註冊表裡且只做淺色() {
        val newYear = Palettes.ALL.firstOrNull { it.id == "newyear" }
        assertTrue("新年主題要在 Palettes.ALL 裡，使用者才選得到", newYear != null)
        assertEquals(null, newYear!!.dark)
        assertEquals("新年", newYear.label)
    }

    /** 節慶主題不能把主色換成紅的 —— 紅色要留給破壞性動作（手冊 §零第二條）。 */
    @Test
    fun 每個色系的主色都不是紅的() {
        for (spec in Palettes.ALL) {
            val a = spec.light.accent
            val d = spec.light.danger
            val 色相接近破壞色 = kotlin.math.abs(a.red - d.red) < 0.12f &&
                kotlin.math.abs(a.green - d.green) < 0.12f &&
                kotlin.math.abs(a.blue - d.blue) < 0.12f
            assertTrue("${spec.id} 的主色與破壞色太接近，紅色就分不出是不是破壞性動作了", !色相接近破壞色)
        }
    }
}
