package com.xenyaa.videoshot.capture

/**
 * 截一張圖的結果。
 *
 * 失敗分成四種而不是一個 boolean —— 畫面上要講得出**為什麼**截不到。
 * 廣告播放中是「等一下再試」，黑畫面是「這一格截不到、改用相簿」，
 * 兩者的處置完全不同，講成同一句話會讓使用者做錯事（規格第五節）。
 */
sealed interface CaptureResult {

    /**
     * @param webp 已經縮成 320×180 的 WebP
     * @param atSec 截下那一瞬間的播放秒數 —— **和圖是同一個瞬間取的**，所以不開放微調
     */
    data class Success(val webp: ByteArray, val atSec: Double) : CaptureResult {
        override fun equals(other: Any?): Boolean =
            other is Success && atSec == other.atSec && webp.contentEquals(other.webp)

        override fun hashCode(): Int = 31 * webp.contentHashCode() + atSec.hashCode()
    }

    /** 截到了，但是一張全黑的圖（規格第五節的兩條件判定）。 */
    data object BlackFrame : CaptureResult

    /** `toDataURL` 回了東西但解不出 bitmap —— POC 實測它在影片沒載入時回 `"data:,"` 且不拋例外。 */
    data object NotDecodable : CaptureResult

    /** 廣告播放中。截了也是廣告畫面，不如直接擋下並說明。 */
    data object AdPlaying : CaptureResult

    /** 播放器還沒就緒（沒有 `<video>`、`videoWidth` 是 0）。 */
    data object NotReady : CaptureResult
}

/**
 * 截圖實作藏在這個介面後面（規格第三節邊界 4）。
 *
 * 呼叫端**不負責**暫停與續播以外的事：秒數由實作在截圖的同一瞬間讀出來，
 * 一起放進 [CaptureResult.Success]。
 */
interface Capture {
    suspend fun capture(): CaptureResult
}
