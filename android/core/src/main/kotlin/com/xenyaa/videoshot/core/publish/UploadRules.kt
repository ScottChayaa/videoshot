package com.xenyaa.videoshot.core.publish

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId

/** 上傳分段大小：8 MiB（YouTube 要求 256 KiB 的倍數）。 */
const val UPLOAD_CHUNK_BYTES = 8L * 1024 * 1024

/** 上傳時斷網，在工作裡最多等網路回來的時間：10 分鐘。 */
const val NETWORK_WAIT_MS = 600_000L

private const val TITLE_MAX_CODE_POINTS = 100
private const val TITLE_FALLBACK = "videoshot"

/**
 * 轉成 YouTube 收得下的標題：`<`、`>` 換成全形（YouTube 不收角括號），去頭尾空白，
 * 超過 100 個字（code point，表情符號不切半）截斷，空的用「videoshot」。
 */
fun youtubeTitle(title: String): String {
    val cleaned = title.replace('<', '＜').replace('>', '＞').trim()
    if (cleaned.isEmpty()) return TITLE_FALLBACK
    if (cleaned.codePointCount(0, cleaned.length) <= TITLE_MAX_CODE_POINTS) return cleaned
    val end = cleaned.offsetByCodePoints(0, TITLE_MAX_CODE_POINTS)
    // 截斷後尾端可能剛好是空白，再去一次
    return cleaned.substring(0, end).trimEnd().ifEmpty { TITLE_FALLBACK }
}

enum class UploadErrorKind { AUTH, NO_CHANNEL, QUOTA, CHANNEL_LIMIT, SESSION_GONE, TRANSIENT, FATAL }

/**
 * 先看 body 的 `error.errors[0].reason`（不論狀態碼），沒對到再看狀態碼。body 不是 JSON 不丟例外。
 */
fun classifyUploadError(httpCode: Int, body: String?): UploadErrorKind {
    when (firstReason(body)) {
        "youtubeSignupRequired" -> return UploadErrorKind.NO_CHANNEL
        "quotaExceeded", "dailyLimitExceeded" -> return UploadErrorKind.QUOTA
        "uploadLimitExceeded" -> return UploadErrorKind.CHANNEL_LIMIT
    }
    return when (httpCode) {
        401 -> UploadErrorKind.AUTH
        404, 410 -> UploadErrorKind.SESSION_GONE
        408, 429, in 500..599 -> UploadErrorKind.TRANSIENT
        else -> UploadErrorKind.FATAL
    }
}

private fun firstReason(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val error = (Json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
        val first = (error?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject
        (first?.get("reason") as? JsonPrimitive)?.content
    }.getOrNull()
}

/**
 * 等 YouTube 產生 storyboard 的輪詢間隔：前 5 分鐘每 30 秒、之後每 2 分鐘、滿 60 分鐘回 null（逾時）。
 */
fun storyboardPollDelayMs(elapsedMs: Long): Long? = when {
    elapsedMs < 300_000L -> 30_000L
    elapsedMs < 3_600_000L -> 120_000L
    else -> null
}

/** YouTube Data API 的每日額度在美國太平洋時間午夜重置（頻道的每日上限也是隔天恢復）。 */
private val QUOTA_RESET_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

/** 重置後多等一下再接著傳，免得剛好卡在重置前一刻又撞上限：5 分鐘。 */
private const val QUOTA_RESET_GRACE_MS = 300_000L

/**
 * 撞到每日上傳上限後，什麼時候可以接著傳：[nowMs] 之後的下一個美國太平洋時間午夜，再加 5 分鐘。
 * 剛好是午夜或剛過午夜時是隔天的午夜（今天的額度剛重置又用完了）；夏令時間切換那天照當地日曆算。
 */
fun nextQuotaResetMs(nowMs: Long): Long {
    val today = Instant.ofEpochMilli(nowMs).atZone(QUOTA_RESET_ZONE).toLocalDate()
    return today.plusDays(1).atStartOfDay(QUOTA_RESET_ZONE).toInstant().toEpochMilli() + QUOTA_RESET_GRACE_MS
}
