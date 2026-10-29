package com.xenyaa.videoshot.core.publish

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadRulesTest {
    @Test fun 角括號換成全形() = assertEquals("a＜b＞c", youtubeTitle("a<b>c"))
    @Test fun 超過100字截斷() = youtubeTitle("字".repeat(150)).let { assertEquals(100, it.codePointCount(0, it.length)) }
    @Test fun 表情符號不被切半() = assertEquals(100, youtubeTitle("😀".repeat(120)).let { it.codePointCount(0, it.length) })
    @Test fun 全空白用videoshot() = assertEquals("videoshot", youtubeTitle("   "))
    @Test fun 去頭尾空白() = assertEquals("abc", youtubeTitle("  abc \n"))
    @Test fun 沒頻道看reason不看碼() = assertEquals(UploadErrorKind.NO_CHANNEL,
        classifyUploadError(401, """{"error":{"errors":[{"reason":"youtubeSignupRequired"}]}}"""))
    @Test fun 專案額度() = assertEquals(UploadErrorKind.QUOTA, classifyUploadError(403, """{"error":{"errors":[{"reason":"quotaExceeded"}]}}"""))
    @Test fun 每日額度() = assertEquals(UploadErrorKind.QUOTA, classifyUploadError(403, """{"error":{"errors":[{"reason":"dailyLimitExceeded"}]}}"""))
    @Test fun 頻道上限() = assertEquals(UploadErrorKind.CHANNEL_LIMIT, classifyUploadError(400, """{"error":{"errors":[{"reason":"uploadLimitExceeded"}]}}"""))
    @Test fun 一般401是授權() = assertEquals(UploadErrorKind.AUTH, classifyUploadError(401, "not json"))
    @Test fun 網址失效() {
        assertEquals(UploadErrorKind.SESSION_GONE, classifyUploadError(404, null))
        assertEquals(UploadErrorKind.SESSION_GONE, classifyUploadError(410, null))
    }
    @Test fun 伺服器暫時錯誤() = listOf(408, 429, 500, 503).forEach { assertEquals(UploadErrorKind.TRANSIENT, classifyUploadError(it, null)) }
    @Test fun 其他是致命() = assertEquals(UploadErrorKind.FATAL, classifyUploadError(400, """{"error":{"errors":[{"reason":"invalidTitle"}]}}"""))
    @Test fun body不是預期結構不丟例外() {
        assertEquals(UploadErrorKind.FATAL, classifyUploadError(403, """{"error":"oops"}"""))
        assertEquals(UploadErrorKind.FATAL, classifyUploadError(403, """{"error":{"errors":[]}}"""))
    }
    @Test fun 輪詢間隔() {
        assertEquals(30_000L, storyboardPollDelayMs(0)); assertEquals(30_000L, storyboardPollDelayMs(299_999))
        assertEquals(120_000L, storyboardPollDelayMs(300_000)); assertEquals(120_000L, storyboardPollDelayMs(3_599_999))
        assertEquals(null, storyboardPollDelayMs(3_600_000))
    }
    @Test fun 分段是256KiB的倍數() = assertEquals(0L, UPLOAD_CHUNK_BYTES % (256 * 1024))
}
