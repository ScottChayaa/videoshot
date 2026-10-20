package com.xenyaa.videoshot.ui.common

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 「2026-08-03」→「2026年8月3日」；解析不了就原樣顯示，不為了格式讓整頁失敗。Lightbox 與相關畫面共用。 */
fun chineseDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
} catch (_: DateTimeParseException) {
    iso
}
