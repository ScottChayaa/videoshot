package com.xenyaa.videoshot.ui.common

import com.xenyaa.videoshot.data.repo.model.MonthFacet
import org.junit.Assert.assertEquals
import org.junit.Test

/** 月份標籤小膠囊的種類對應：`kind` 是 place／tag。 */
class ChipKindOfFacetTest {
    @Test fun 地點是地點種類() = assertEquals(ChipKind.PLACE, chipKindOf(MonthFacet("宜蘭", "place", 3)))

    @Test fun 標籤是標籤種類() = assertEquals(ChipKind.TAG, chipKindOf(MonthFacet("夜潛", "tag", 2)))
}
