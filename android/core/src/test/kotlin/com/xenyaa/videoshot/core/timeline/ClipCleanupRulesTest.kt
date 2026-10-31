package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipCleanupRulesTest {
    @Test fun 照片與影片() = assertEquals("要刪除這次用到的 3 張照片、2 支影片嗎？", clipDeletePrompt(3, 2))
    @Test fun 只有照片() = assertEquals("要刪除這次用到的 3 張照片嗎？", clipDeletePrompt(3, 0))
    @Test fun 只有影片() = assertEquals("要刪除這次用到的 2 支影片嗎？", clipDeletePrompt(0, 2))
    @Test fun 都不在了() = assertNull(clipDeletePrompt(0, 0))
}
