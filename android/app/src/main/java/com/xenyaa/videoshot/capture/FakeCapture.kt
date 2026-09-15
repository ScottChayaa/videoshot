package com.xenyaa.videoshot.capture

/**
 * 測試用的截圖器。精靈的測試不該為了驗一條規則去啟動 WebView ——
 * 與 `FakePlayer`、`FakeFrameSource` 是同一個取捨。
 */
class FakeCapture(private val atSec: Double = 0.0) : Capture {

    /** 下一次 [capture] 要回什麼。null 代表回一張成功的假圖。 */
    var nextResult: CaptureResult? = null

    var calls: Int = 0
        private set

    override suspend fun capture(): CaptureResult {
        calls++
        return nextResult ?: CaptureResult.Success(byteArrayOf(1, 2, 3), atSec)
    }
}
