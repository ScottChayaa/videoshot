package com.xenyaa.videoshot.core.similarity

/**
 * 過濾相似畫面的強度（規格第五節第二步，值取自帳號頁的「取圖 › 過濾相似強度」）。
 * threshold 是**漢明距離的上限**：距離 ≤ threshold 視為相似。
 * 門檻越大越容易判定相似 → 藏得越多，所以 HIGH 的數值最大。
 */
enum class FilterStrength(val threshold: Int) {
    HIGH(10),
    MEDIUM(6),
    LOW(3),
}

/** 一格的指紋。frameIndex 是 storyboard 的格號，不是清單位置。 */
data class Fingerprint(val frameIndex: Int, val hash: Long)

/** @param kept 保留下來的格號，維持時間順序 @param hiddenCount 被藏起來的張數 */
data class ConvergeResult(val kept: List<Int>, val hiddenCount: Int)

/**
 * 依時間順序掃描，把連續相似的畫面收斂成一張 —— 每組保留**最早**的那一張（規格第五節第二步）。
 *
 * 比較的對象是「最近一張**被保留的**」，不是「前一張」。
 * 若跟前一張比，畫面緩慢變化時每一張都跟前一張很像，會一路藏到底，
 * 最後只剩第一張 —— 那不是收斂，是把整支影片吃掉。
 *
 * 這是純計算，呼叫端要放在 `Dispatchers.Default`（規格第三節設計原則第 5 條）。
 */
fun converge(fingerprints: List<Fingerprint>, strength: FilterStrength): ConvergeResult {
    if (fingerprints.isEmpty()) return ConvergeResult(emptyList(), 0)

    val kept = mutableListOf<Int>()
    var lastKeptHash = 0L
    var first = true

    for (fp in fingerprints) {
        if (first || hammingDistance(lastKeptHash, fp.hash) > strength.threshold) {
            kept += fp.frameIndex
            lastKeptHash = fp.hash
            first = false
        }
    }
    return ConvergeResult(kept, fingerprints.size - kept.size)
}
