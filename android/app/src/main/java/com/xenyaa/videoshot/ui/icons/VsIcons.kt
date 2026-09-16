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
}
