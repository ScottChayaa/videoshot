package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.KindColors

/**
 * 小膠囊的種類：地點或標籤。地點是 `place` 表，不是標籤（規格第四節），
 * 但畫面上跟標籤混排在同一列，用圖示與顏色區分。標籤不分種類，一律是 `#`。
 */
enum class ChipKind(val color: Color, val icon: ImageVector) {
    PLACE(KindColors.place, VsIcons.MapPin),
    TAG(KindColors.tag, VsIcons.Hash),
}

/** 月份／查詢標籤雲小膠囊的種類（首頁與查詢頁共用）。 */
fun chipKindOf(facet: MonthFacet): ChipKind =
    if (facet.kind == "place") ChipKind.PLACE else ChipKind.TAG
