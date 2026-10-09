package com.xenyaa.videoshot.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 圖示集。path 資料逐一取自 `mockups/uiux-v2/app.js` 的 `ICONS`（Lucide 風格，24×24、線寬 2），
 * 所以 app 的圖示與原型完全一致。
 *
 * **不引 material-icons**：core 那包沒有 folder，extended 那包很大而
 * release 的 optimization 是關掉的（見 `app/build.gradle.kts`），整包會進 APK。
 * 原型的 `<circle>`／`<rect>`／`<line>`／`<polyline>` 在這裡已經改寫成等價的 path。
 *
 * 顏色留黑色由 `Icon(tint = …)` 的 ColorFilter 蓋掉 —— 這是 Material3 圖示的標準做法。
 */
private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in pathData) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object VsIcons {

    val Home: ImageVector by lazy {
        strokeIcon("home", "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10")
    }

    val Search: ImageVector by lazy {
        // 原型的 <circle cx=11 cy=11 r=8> 改寫成兩段半圓弧
        strokeIcon("search", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", "m21 21-4.3-4.3")
    }

    val Plus: ImageVector by lazy { strokeIcon("plus", "M5 12h14", "M12 5v14") }

    val Folder: ImageVector by lazy {
        strokeIcon(
            "folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val FolderPlus: ImageVector by lazy {
        strokeIcon(
            "folder-plus",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
            "M12 10v6",
            "M9 13h6",
        )
    }

    /** 帳號格用 person 而不是頭像字母 —— app 不需要登入，沒有頭像可放（規格詞彙表）。 */
    val Person: ImageVector by lazy {
        strokeIcon(
            "person",
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        )
    }

    val Calendar: ImageVector by lazy {
        strokeIcon(
            "calendar",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M16 2v4",
            "M8 2v4",
            "M3 10h18",
        )
    }

    val Close: ImageVector by lazy { strokeIcon("close", "M18 6 6 18", "m6 6 12 12") }

    val Back: ImageVector by lazy { strokeIcon("back", "M19 12H5", "m12 19-7-7 7-7") }

    val Play: ImageVector by lazy { strokeIcon("play", "M6 3 20 12 6 21Z") }

    val Share: ImageVector by lazy {
        strokeIcon(
            "share",
            "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8",
            "M16 6 12 2 8 6",
            "M12 2v13",
        )
    }

    /** 三個點的 `⋯`（原型是三個 r=1 的圓）。 */
    val More: ImageVector by lazy {
        strokeIcon(
            "more",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }

    val Edit: ImageVector by lazy {
        strokeIcon(
            "edit",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        )
    }

    val Trash: ImageVector by lazy {
        strokeIcon(
            "trash",
            "M3 6h18",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    /** 只看已選（原型 `ICONS.eye`；`<circle cx=12 cy=12 r=3>` 改寫成兩段弧線）。 */
    val Eye: ImageVector by lazy {
        strokeIcon(
            "eye",
            "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }

    val Check: ImageVector by lazy { strokeIcon("check", "M20 6 9 17l-5-5") }

    /** 下拉欄位的箭頭（原型 `chevronDown`）。 */
    val ChevronDown: ImageVector by lazy { strokeIcon("chevron-down", "m6 9 6 6 6-6") }

    /** 帳號頁選單列的箭頭（原型 `chevronRight`）。 */
    val ChevronRight: ImageVector by lazy { strokeIcon("chevron-right", "m9 18 6-6-6-6") }

    /** 備份（規格第九節：雲＝備份）。Lucide `cloud`，原型 `ICONS` 沒有這個 key，直接取自 Lucide 的標準路徑。 */
    val Cloud: ImageVector by lazy {
        strokeIcon("cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")
    }

    /** 縮圖（規格第九節：圖片＝縮圖）。原型 `imagePlus`；圓形改寫成兩段弧線（同 [Search] 的做法）。 */
    val ImagePlus: ImageVector by lazy {
        strokeIcon(
            "image-plus",
            "M16 5h6",
            "M19 2v6",
            "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    /**
     * 篩選（首頁篩選鈕、取圖第二步【隱藏相似】）。原型 `filter` 是 `<polygon>`，改寫成封閉路徑（隱式 lineto）。
     * 帳號頁「取圖」那一列 2026-10-09 起改用 [Sliders]，避免跟首頁篩選鈕同圖示不同語意。
     */
    val Filter: ImageVector by lazy {
        strokeIcon("filter", "M22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3Z")
    }

    /** 取圖設定（帳號頁「取圖」列：調整過濾相似強度）。Lucide `sliders-horizontal`，`<line>` 改寫成 path。 */
    val Sliders: ImageVector by lazy {
        strokeIcon(
            "sliders-horizontal",
            "M21 4h-7", "M10 4H3",
            "M21 12h-9", "M8 12H3",
            "M21 20h-5", "M12 20H3",
            "M14 2v4", "M8 10v4", "M16 18v4",
        )
    }

    /** AI 分析（規格第九節：星芒＝AI 分析）。原型 `sparkles`；中心圓改寫成兩段弧線。 */
    val Sparkles: ImageVector by lazy {
        strokeIcon(
            "sparkles",
            "M12 3v4",
            "M12 17v4",
            "M3 12h4",
            "M17 12h4",
            "M7.5 7.5 5 5",
            "M19 19l-2.5-2.5",
            "M16.5 7.5 19 5",
            "M5 19l2.5-2.5",
            "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        )
    }

    /** 標籤管理選單列（規格第九節：標籤＝標籤管理）。原型 `tag`；小圓孔改寫成兩段弧線。 */
    val Tag: ImageVector by lazy {
        strokeIcon(
            "tag",
            "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
            "M6 7.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0",
        )
    }

    /** 標籤 kind＝主題（原型 `KIND.topic`，四條線畫出一個表格感的符號）。 */
    val Topic: ImageVector by lazy {
        strokeIcon("topic", "M4 9 20 9", "M4 15 20 15", "M10 3 8 21", "M16 3 14 21")
    }

    /** 標籤 kind＝動物（原型 `KIND.pet`，三個圓＋一個爪墊形狀）。 */
    val Pet: ImageVector by lazy {
        strokeIcon(
            "pet",
            "M9.1 4.5a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0",
            "M15.8 8a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0",
            "M17.7 15.4a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0",
            "M9.2 10.2a4.8 4.8 0 0 1 4.8 4.8v3.3a3.3 3.3 0 0 1-6.5 1Q6.5 17.4 4.6 16.8a3.3 3.3 0 0 1 1-6.6z",
        )
    }

    /** 標籤 kind＝其他（原型 `KIND.other`，菱形）。命名 `OtherKind` 避免跟 Kotlin 的一般用語混淆。 */
    val OtherKind: ImageVector by lazy { strokeIcon("other-kind", "M12 3 21 12 12 21 3 12z") }

    /** 地點（原型 `KIND.place`）。 */
    val MapPin: ImageVector by lazy {
        strokeIcon(
            "map-pin",
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            "M15 10a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }

    /** 影片（原型 `ICONS.film`；取圖紀錄的列圖示，計畫 B 用）。 */
    val Film: ImageVector by lazy {
        strokeIcon(
            "film",
            "M4.18 2h15.64A2.18 2.18 0 0 1 22 4.18v15.64A2.18 2.18 0 0 1 19.82 22H4.18A2.18 2.18 0 0 1 2 19.82V4.18A2.18 2.18 0 0 1 4.18 2z",
            "M7 2v20", "M17 2v20", "M2 12h20",
        )
    }
}
