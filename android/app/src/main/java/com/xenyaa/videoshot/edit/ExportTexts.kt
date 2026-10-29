package com.xenyaa.videoshot.edit

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.ui.clip.formatDuration
import java.util.Locale

/** 輸出失敗的原因（直接顯示給使用者，不可以放 Media3 的英文訊息）。 */
const val REASON_ALL_MISSING = "素材都已不存在"
const val REASON_CANNOT_READ = "讀不到相片和影片，請確認存取權"
const val REASON_EXPORT_FAILED = "輸出失敗"

private const val GB = 1_000_000_000L
private const val MB = 1_000_000L

/** 容量文字：1 GB 以上取一位小數（「1.2 GB」），不到 1 GB 取整數 MB（「350 MB」）；單位用 10 進位。 */
fun formatBytes(bytes: Long): String {
    val mb = Math.round(bytes.coerceAtLeast(0) / MB.toDouble())
    return if (mb >= 1000) {
        String.format(Locale.ROOT, "%.1f GB", bytes / GB.toDouble())
    } else {
        "$mb MB"
    }
}

/** 「空間不足，還需要 1.2 GB」。[missingBytes] 是還差的容量。 */
fun reasonNoSpace(missingBytes: Long): String = "空間不足，還需要 ${formatBytes(missingBytes)}"

/** 超過上限多少的文字：「48 秒」「1 分 12 秒」「2 分」；不足一秒進位成一秒（仍然是超過）。 */
fun overLimitText(overMs: Long): String {
    val totalSec = (overMs.coerceAtLeast(0) + 999) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return when {
        m == 0L -> "$s 秒"
        s == 0L -> "$m 分"
        else -> "$m 分 $s 秒"
    }
}

/**
 * 導出抽屜的摘要一行與「是否超過上限」：
 * 「0:48・直式・會成為 Shorts」「1:20・橫式・一般影片」「3:48・直式・超過上限 48 秒，請先刪掉一些段落」。
 *
 * 超過上限時總長跟差多少一樣進位到整秒（上限是整秒），兩個數字才對得起來：3:00.4 寫「3:01・…超過上限 1 秒」，
 * 不會寫成「3:00・…超過上限 1 秒」。沒超過時照常捨去。
 */
fun exportSummary(orientation: ClipOrientation, durationMs: Long): Pair<String, Boolean> {
    val label = if (orientation == ClipOrientation.PORTRAIT) "直式" else "橫式"
    val over = durationMs - orientation.maxDurationMs
    if (over > 0) {
        val time = formatDuration((durationMs + 999) / 1000 * 1000)
        return "$time・$label・超過上限 ${overLimitText(over)}，請先刪掉一些段落" to true
    }
    val time = formatDuration(durationMs)
    val kind = if (orientation == ClipOrientation.PORTRAIT) "會成為 Shorts" else "一般影片"
    return "$time・$label・$kind" to false
}

private fun skippedSuffix(skipped: Int): String = if (skipped > 0) "・有 $skipped 個素材已不存在，已略過" else ""

/** 輸出完成（停在輸出完成、等著檢查）的通知內文：「〈標題〉・點一下檢查成品」，有略過的素材再接說明。 */
fun doneNotificationText(title: String, skipped: Int): String = "$title・點一下檢查成品${skippedSuffix(skipped)}"

/** 已存進相簿的通知內文：「〈標題〉」，有略過的素材再接說明。 */
fun savedNotificationText(title: String, skipped: Int): String = "$title${skippedSuffix(skipped)}"

/** 失敗的標題（通知與導出中畫面共用），依失敗的步驟；舊資料沒記步驟當成輸出。 */
fun failedTitle(step: EditTaskStep?): String = when (step) {
    EditTaskStep.UPLOAD -> "影片上傳失敗"
    EditTaskStep.STORYBOARD -> "YouTube 處理失敗"
    EditTaskStep.RENDER, null -> "影片輸出失敗"
}
