package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.KindColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChipKindTest {
    @Test fun 種類色() {
        assertEquals(Color(0xFF0EA5B7), KindColors.place)
        assertEquals(Color(0xFF8C6D46), KindColors.tag)
    }

    @Test fun 地點用地圖針圖示() {
        assertSame(VsIcons.MapPin, ChipKind.PLACE.icon)
        assertEquals(KindColors.place, ChipKind.PLACE.color)
    }

    /** 標籤不分種類，一律是 `#`。 */
    @Test fun 標籤用井字號圖示() {
        assertSame(VsIcons.Hash, ChipKind.TAG.icon)
        assertEquals(KindColors.tag, ChipKind.TAG.color)
    }
}
