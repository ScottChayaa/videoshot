package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.KindColors

/**
 * 標籤小膠囊的種類。比 [TagKind] 多一個 [PLACE]——地點是 `shot.place`，不是標籤
 * （規格第四節），但畫面上跟標籤混排在同一列，要用同一套圖示與顏色區分。
 */
enum class ChipKind(val color: Color, val icon: ImageVector) {
    PLACE(KindColors.place, VsIcons.MapPin),
    PERSON(KindColors.person, VsIcons.Person),
    PET(KindColors.pet, VsIcons.Pet),
    TOPIC(KindColors.topic, VsIcons.Topic),
    OTHER(KindColors.other, VsIcons.OtherKind);

    companion object {
        /** `tag.kind` 欄位值 → 種類；不認得的退回 [OTHER]（同 [TagKind.byId]）。 */
        fun ofTagKind(id: String): ChipKind = when (TagKind.byId(id)) {
            TagKind.PERSON -> PERSON
            TagKind.PET -> PET
            TagKind.TOPIC -> TOPIC
            TagKind.OTHER -> OTHER
        }
    }
}
