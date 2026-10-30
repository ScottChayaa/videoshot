package com.xenyaa.videoshot.core.publish

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

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
    // ---- 每日額度重置時間（美國太平洋時間午夜＋5 分鐘） ----

    private fun ms(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test fun 台灣下午兩點是當天太平洋午夜加五分() =
        // 台灣 10/30 14:00＝太平洋 10/29 23:00（夏令時間）→ 10/30 00:00 PDT＝台灣 15:00
        assertEquals(ms("2026-10-30T15:05+08:00"), nextQuotaResetMs(ms("2026-10-30T14:00+08:00")))

    @Test fun 剛過午夜等到隔天() {
        // 太平洋 10/30 00:02：剛重置過，撞到上限就是今天的也用完了
        assertEquals(ms("2026-10-31T00:05-07:00"), nextQuotaResetMs(ms("2026-10-30T00:02-07:00")))
        assertEquals(ms("2026-10-31T00:05-07:00"), nextQuotaResetMs(ms("2026-10-30T00:00-07:00")))
    }

    @Test fun 夏令時間結束那天() {
        // 2026-11-01 02:00 PDT 撥回 01:00 PST：那天有 25 小時
        assertEquals(ms("2026-11-02T00:05-08:00"), nextQuotaResetMs(ms("2026-11-01T00:30-07:00")))
        // 結束後台灣下午兩點＝太平洋前一天 22:00 PST → 午夜變成台灣 16:00
        assertEquals(ms("2026-11-02T16:05+08:00"), nextQuotaResetMs(ms("2026-11-02T14:00+08:00")))
    }

    @Test fun 夏令時間開始那天() {
        // 2026-03-08 02:00 PST 撥快到 03:00 PDT：那天只有 23 小時
        assertEquals(ms("2026-03-09T00:05-07:00"), nextQuotaResetMs(ms("2026-03-08T00:30-08:00")))
        assertEquals(ms("2026-03-08T00:05-08:00"), nextQuotaResetMs(ms("2026-03-07T23:00-08:00")))
    }
}
