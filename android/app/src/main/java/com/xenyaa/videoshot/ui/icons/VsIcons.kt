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
/** Lucide `volume-2`／`volume-x` 共用的喇叭本體。 */
private const val SPEAKER_PATH =
    "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416" +
        "a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"

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

    /** Lucide `image`：照片分頁（底部導覽第一格）。 */
    val Image: ImageVector by lazy {
        strokeIcon(
            "image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
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

    /** 暫停（Lucide `pause`）。 */
    val Pause: ImageVector by lazy { strokeIcon("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z") }

    /** 全螢幕（Lucide `maximize`）。 */
    val Maximize: ImageVector by lazy {
        strokeIcon("maximize", "M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3")
    }

    /** 離開全螢幕（Lucide `minimize`）。 */
    val Minimize: ImageVector by lazy {
        strokeIcon("minimize", "M8 3v3a2 2 0 0 1-2 2H3", "M21 8h-3a2 2 0 0 1-2-2V3", "M3 16h3a2 2 0 0 1 2 2v3", "M16 21v-3a2 2 0 0 1 2-2h3")
    }

    /** 有聲音（Lucide `volume-2`）。 */
    val Volume: ImageVector by lazy {
        strokeIcon("volume", SPEAKER_PATH, "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728")
    }

    /** 靜音（Lucide `volume-x`）。 */
    val VolumeOff: ImageVector by lazy { strokeIcon("volume-off", SPEAKER_PATH, "M22 9l-6 6", "M16 9l6 6") }

    /** 三點連線（Lucide `share-2`，Android 慣用的分享圖示；原型是 iOS 式的方框加箭頭）。三個 r=3 的圓改寫成 path。 */
    val Share: ImageVector by lazy {
        strokeIcon(
            "share",
            "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "m8.59 13.51 6.83 3.98",
            "m15.41 6.51-6.82 3.98",
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

    /** 封存（Lucide `archive`）：相簿 ⋯ 選單與下拉進入封存相簿的提示。 */
    val Archive: ImageVector by lazy {
        strokeIcon(
            "archive",
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
            "M10 12h4",
        )
    }

    /** 取消封存（Lucide `archive-restore`）。 */
    val ArchiveRestore: ImageVector by lazy {
        strokeIcon(
            "archive-restore",
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M4 8v11a2 2 0 0 0 2 2h2",
            "M20 8v11a2 2 0 0 1-2 2h-2",
            "m9 15 3-3 3 3",
            "M12 12v9",
        )
    }

    /** 眼睛（原型 `ICONS.eye`；`<circle cx=12 cy=12 r=3>` 改寫成兩段弧線）。 */
    val Eye: ImageVector by lazy {
        strokeIcon(
            "eye",
            "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }

    val Check: ImageVector by lazy { strokeIcon("check", "M20 6 9 17l-5-5") }

    /** 全選（取圖第二步底部列）：前張打勾、後張只露左下邊的疊圖。改自 Lucide `images`（山與太陽換成勾），原型 `ICONS` 沒有這個 key。 */
    val ImagesCheck: ImageVector by lazy {
        strokeIcon(
            "images-check",
            "M8 2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
            "M18 22H4a2 2 0 0 1-2-2V6",
            "m10 10 3 3 5-5",
        )
    }

    /** 已收藏（取圖第二步縮圖格左上角）。Lucide `bookmark-check`，原型 `ICONS` 沒有這個 key。 */
    val BookmarkCheck: ImageVector by lazy {
        strokeIcon("bookmark-check", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2Z", "m9 10 2 2 4-4")
    }

    /** 截圖（取圖第二步底部列與手動格的時間標記）。Lucide `scissors`，原型 `ICONS` 沒有這個 key；圓形改寫成兩段弧線（同 [Search]）。 */
    val Scissors: ImageVector by lazy {
        strokeIcon(
            "scissors",
            "M3 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M8.12 8.12 12 12",
            "M20 4 8.12 15.88",
            "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M14.8 14.8 20 20",
        )
    }

    /**
     * 重來（原片播放 ⋯【重新取圖】）。Lucide `rotate-ccw`，原型 `ICONS` 沒有這個 key。
     * 不用雙箭頭的 `refresh-cw`：在播放器裡容易讀成「重新載入影片」，而這個動作會先刪掉整支收藏。
     */
    val RotateCcw: ImageVector by lazy {
        strokeIcon("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    }

    /**
     * 再加幾張（原片播放 ⋯【繼續取圖】）。Lucide `copy-plus`，原型 `ICONS` 沒有這個 key；
     * `<rect x=8 y=8 w=14 h=14 rx=2>` 改寫成 path。跟【重新取圖】的 [RotateCcw] 一方一圓，並排時分得開。
     */
    val CopyPlus: ImageVector by lazy {
        strokeIcon(
            "copy-plus",
            "M15 12v6",
            "M12 15h6",
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    /** 合併（標籤／地點編輯抽屜的【合併到…】）。Lucide `merge`，原型 `ICONS` 沒有這個 key。 */
    val Merge: ImageVector by lazy {
        strokeIcon("merge", "m8 6 4-4 4 4", "M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22", "m20 22-5-5")
    }

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
     * 篩選（首頁篩選鈕）。Lucide `list-filter`：置中、由長到短的三條橫線（2026-10-25 起，原本是漏斗 `filter`）。
     * 帳號頁「取圖」那一列 2026-10-09 起改用 [Sliders]，避免跟首頁篩選鈕同圖示不同語意。
     */
    val Filter: ImageVector by lazy {
        strokeIcon("list-filter", "M3 6h18", "M7 12h10", "M10 18h4")
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

    /** 描述（取圖第三步的欄位按鈕與縮圖圖示）。Lucide `text`：三行長短不一的字。 */
    val Notes: ImageVector by lazy { strokeIcon("notes", "M17 6.1H3", "M21 12.1H3", "M15.1 18H3") }

    /** 標籤管理選單列（規格第九節：標籤＝標籤管理）。原型 `tag`；小圓孔改寫成兩段弧線。 */
    val Tag: ImageVector by lazy {
        strokeIcon(
            "tag",
            "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
            "M6 7.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0",
        )
    }

    /** 標籤小膠囊的 `#`（標籤不分種類，一律用這個）。 */
    val Hash: ImageVector by lazy {
        strokeIcon("hash", "M4 9 20 9", "M4 15 20 15", "M10 3 8 21", "M16 3 14 21")
    }

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
