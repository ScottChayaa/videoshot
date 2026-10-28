package com.xenyaa.videoshot.edit

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTextsTest {

    @Test fun `超過上限的文字秒數分鐘與整分`() {
        assertEquals("48 秒", overLimitText(48_000))
        assertEquals("1 分 12 秒", overLimitText(72_000))
        assertEquals("2 分", overLimitText(120_000))
        assertEquals("1 分", overLimitText(60_000))
    }

    @Test fun `超過上限不足一秒也算一秒`() {
        assertEquals("1 秒", overLimitText(1))
        assertEquals("49 秒", overLimitText(48_500))
    }

    @Test fun `摘要直式三分鐘內會成為 Shorts`() {
        assertEquals("0:48・直式・會成為 Shorts" to false, exportSummary(ClipOrientation.PORTRAIT, 48_000))
        // 剛好 3 分鐘還在上限內
        assertEquals("3:00・直式・會成為 Shorts" to false, exportSummary(ClipOrientation.PORTRAIT, 180_000))
    }

    @Test fun `摘要橫式是一般影片`() {
        assertEquals("1:20・橫式・一般影片" to false, exportSummary(ClipOrientation.LANDSCAPE, 80_000))
    }

    @Test fun `摘要超過上限寫差多少且不說 Shorts`() {
        assertEquals(
            "3:48・直式・超過上限 48 秒，請先刪掉一些段落" to true,
            exportSummary(ClipOrientation.PORTRAIT, 228_000),
        )
        assertEquals(
            "12:00・橫式・超過上限 2 分，請先刪掉一些段落" to true,
            exportSummary(ClipOrientation.LANDSCAPE, 720_000),
        )
    }

    @Test fun `摘要超過上限時總長進位_不跟差多少矛盾`() {
        // 3:00.4：差 0.4 秒進位成 1 秒，總長也要進位成 3:01，不能寫「3:00・…超過上限 1 秒」
        assertEquals(
            "3:01・直式・超過上限 1 秒，請先刪掉一些段落" to true,
            exportSummary(ClipOrientation.PORTRAIT, 180_400),
        )
        assertEquals(
            "3:49・直式・超過上限 49 秒，請先刪掉一些段落" to true,
            exportSummary(ClipOrientation.PORTRAIT, 228_500),
        )
        // 沒超過時照常捨去
        assertEquals("2:59・直式・會成為 Shorts" to false, exportSummary(ClipOrientation.PORTRAIT, 179_900))
    }

    @Test fun `formatBytes 的 GB 取一位小數 MB 取整數`() {
        assertEquals("1.2 GB", formatBytes(1_200_000_000))
        assertEquals("350 MB", formatBytes(350_000_000))
        assertEquals("500 MB", formatBytes(500_000_000))
        assertEquals("1.0 GB", formatBytes(1_000_000_000))
        // 四捨五入進位到 1000 MB 時改寫成 GB
        assertEquals("1.0 GB", formatBytes(999_600_000))
        assertEquals("12.5 GB", formatBytes(12_500_000_000))
    }

    @Test fun `空間不足的原因文字`() {
        assertEquals("空間不足，還需要 1.2 GB", reasonNoSpace(1_200_000_000))
        assertEquals("空間不足，還需要 350 MB", reasonNoSpace(350_000_000))
    }

    @Test fun `失敗原因的固定文字`() {
        assertEquals("素材都已不存在", REASON_ALL_MISSING)
        assertEquals("讀不到相片和影片，請確認存取權", REASON_CANNOT_READ)
        assertEquals("輸出失敗", REASON_EXPORT_FAILED)
    }

    @Test fun `完成通知內文沒略過就只有標題與提示`() {
        assertEquals("videoshot 2026/10/20～10/27・點一下檢查成品", doneNotificationText("videoshot 2026/10/20～10/27", 0))
    }

    @Test fun `完成通知內文有略過的素材要接說明`() {
        assertEquals("旅行・點一下檢查成品・有 2 個素材已不存在，已略過", doneNotificationText("旅行", 2))
    }

    @Test fun `存相簿通知內文是標題有略過才接說明`() {
        assertEquals("旅行", savedNotificationText("旅行", 0))
        assertEquals("旅行・有 1 個素材已不存在，已略過", savedNotificationText("旅行", 1))
    }
}
