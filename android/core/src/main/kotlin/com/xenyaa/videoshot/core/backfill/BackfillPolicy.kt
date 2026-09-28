package com.xenyaa.videoshot.core.backfill

/**
 * 回填失敗時的重試排程（規格第十一節：「網路錯誤...以退避分數天重試，超過次數轉 lost」）。
 *
 * **這裡的數字是預先假設的預設值**——規格明講「參數待回填階段以 50 支以上真實回填量測訂定」，
 * 目前沒有真機的限流/退避量測數據。指數退避（1/2/4/8/16 天，共 5 次、累積約 31 天）是合理的
 * 起點，不是量測結果；有量測數據後只需要改這個檔案，呼叫端（`BackfillManager`）不必動。
 */
object BackfillPolicy {
    /** 第 6 次失敗（`attempts` 累加到超過這個值）就放棄，轉 `lost`（`retries_exhausted`）。 */
    const val MAX_ATTEMPTS = 5

    /** `attempts` 從 1 起算：1→1 天、2→2 天...5→16 天，之後（放棄前）不再增加。 */
    fun backoffSeconds(attempts: Int): Long {
        val days = 1L shl (attempts - 1).coerceIn(0, 4) // 1,2,4,8,16
        return days * 86_400L
    }

    fun nextTryAt(nowSec: Long, attempts: Int): Long = nowSec + backoffSeconds(attempts)

    fun shouldGiveUp(attempts: Int): Boolean = attempts > MAX_ATTEMPTS
}
