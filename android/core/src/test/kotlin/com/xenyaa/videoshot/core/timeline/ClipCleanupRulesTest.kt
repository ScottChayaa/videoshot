package com.xenyaa.videoshot.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipCleanupRulesTest {
    @Test fun 照片與影片合計() = assertEquals("要刪除已上傳完成的 5 個素材嗎？", clipDeletePrompt(3, 2))
    @Test fun 只有照片() = assertEquals("要刪除已上傳完成的 3 個素材嗎？", clipDeletePrompt(3, 0))
    @Test fun 只有影片() = assertEquals("要刪除已上傳完成的 2 個素材嗎？", clipDeletePrompt(0, 2))
    @Test fun 都不在了() = assertNull(clipDeletePrompt(0, 0))
}
