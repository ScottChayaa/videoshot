package com.xenyaa.videoshot.ui.common

import com.xenyaa.videoshot.data.repo.model.MonthFacet
import org.junit.Assert.assertEquals
import org.junit.Test

/** 月份標籤小膠囊的種類對應：`kind` 是 place／tag，標籤的種類看 `tagKind`。 */
class ChipKindOfFacetTest {
    @Test fun 地點是地點種類() = assertEquals(ChipKind.PLACE, chipKindOf(MonthFacet("宜蘭", "place", 3)))

    @Test fun 主題標籤是主題種類() = assertEquals(ChipKind.TOPIC, chipKindOf(MonthFacet("夜潛", "tag", 2, "topic")))

    @Test fun 人物標籤是人物種類() = assertEquals(ChipKind.PERSON, chipKindOf(MonthFacet("小明", "tag", 2, "person")))

    @Test fun 不認得的標籤種類退回其他() = assertEquals(ChipKind.OTHER, chipKindOf(MonthFacet("怪", "tag", 1, "unknown")))

    @Test fun 沒給標籤種類時預設是其他() = assertEquals(ChipKind.OTHER, chipKindOf(MonthFacet("龍蝦", "tag", 1)))
}
