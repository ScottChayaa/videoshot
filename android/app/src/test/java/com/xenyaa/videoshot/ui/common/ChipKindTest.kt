package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.KindColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChipKindTest {
    @Test fun 標籤kind對應到小膠囊種類() {
        assertEquals(ChipKind.PERSON, ChipKind.ofTagKind("person"))
        assertEquals(ChipKind.PET, ChipKind.ofTagKind("pet"))
        assertEquals(ChipKind.TOPIC, ChipKind.ofTagKind("topic"))
        assertEquals(ChipKind.OTHER, ChipKind.ofTagKind("other"))
    }

    /** 未知值退回「其他」，跟 `TagKind.byId` 同一個規則。 */
    @Test fun 不認得的kind退回其他() {
        assertEquals(ChipKind.OTHER, ChipKind.ofTagKind("weird"))
    }

    /** 色值逐一對應原型 app.js 的 KIND。 */
    @Test fun 種類色對得上原型() {
        assertEquals(Color(0xFF0EA5E9), KindColors.place)
        assertEquals(Color(0xFF6366F1), KindColors.person)
        assertEquals(Color(0xFF8B5CF6), KindColors.topic)
        assertEquals(Color(0xFFF59E0B), KindColors.pet)
        assertEquals(Color(0xFF64748B), KindColors.other)
    }

    @Test fun 地點用地圖針圖示() {
        assertSame(VsIcons.MapPin, ChipKind.PLACE.icon)
        assertEquals(KindColors.place, ChipKind.PLACE.color)
    }
}
