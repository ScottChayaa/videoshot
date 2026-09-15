package com.xenyaa.videoshot.capture

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebViewCaptureTest {

    /** `evaluateJavascript` 的回傳值是 **JSON 值**，字串會多一層引號與跳脫。 */
    private fun jsReturn(json: String) = org.json.JSONObject.quote(json)

    private fun filled(color: Int, w: Int = 64, h: Int = 36) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun capture(response: String, decoded: Bitmap? = filled(0xFFFFFFFF.toInt())) =
        WebViewCapture(eval = { response }, decode = { decoded })

    @Test
    fun 成功時回傳秒數與圖() = runTest {
        val r = capture(jsReturn("""{"ok":true,"t":12.5,"data":"data:image/jpeg;base64,AAAA"}""")).capture()
        assertTrue(r.toString(), r is CaptureResult.Success)
        assertEquals(12.5, (r as CaptureResult.Success).atSec, 0.0)
    }

    @Test
    fun 廣告播放中回AdPlaying() = runTest {
        assertEquals(
            CaptureResult.AdPlaying,
            capture(jsReturn("""{"ok":false,"reason":"ad"}""")).capture(),
        )
    }

    @Test
    fun 播放器還沒就緒回NotReady() = runTest {
        assertEquals(
            CaptureResult.NotReady,
            capture(jsReturn("""{"ok":false,"reason":"not-ready"}""")).capture(),
        )
    }

    @Test
    fun 解不出bitmap回NotDecodable() = runTest {
        // POC 實測：影片沒載入時 toDataURL 回 "data:," 而且**不拋例外**，
        // 解出來的 bitmap 是 null。這是一種失敗型態，不是當機的理由
        val r = capture(jsReturn("""{"ok":true,"t":1.0,"data":"data:,"}"""), decoded = null).capture()
        assertEquals(CaptureResult.NotDecodable, r)
    }

    @Test
    fun 資料看起來正常但解碼失敗也回NotDecodable() {
        // 與上一條不同：這次 data URL 是好的，base64 也解得開，
        // 是 BitmapFactory 那一步回 null。兩條路徑都要走到，
        // 不然「解不出圖」其實只測到了空的 data URL
        kotlinx.coroutines.test.runTest {
            val r = capture(
                jsReturn("""{"ok":true,"t":1.0,"data":"data:image/jpeg;base64,AAAA"}"""),
                decoded = null,
            ).capture()
            assertEquals(CaptureResult.NotDecodable, r)
        }
    }

    @Test
    fun 截到全黑的圖回BlackFrame() = runTest {
        val r = capture(
            jsReturn("""{"ok":true,"t":1.0,"data":"data:image/jpeg;base64,AAAA"}"""),
            decoded = filled(0xFF000000.toInt()),
        ).capture()
        assertEquals(CaptureResult.BlackFrame, r)
    }

    @Test
    fun JS回了看不懂的東西也不當機() = runTest {
        assertEquals(CaptureResult.NotReady, capture("null").capture())
        assertEquals(CaptureResult.NotReady, capture("").capture())
        assertEquals(CaptureResult.NotReady, capture(jsReturn("不是 JSON")).capture())
    }

    @Test
    fun 截圖的JS要在同一次呼叫裡讀秒數() {
        // 圖與秒數必須是同一瞬間 —— 分兩次 eval 就會對不上（規格第五節）
        assertTrue(CAPTURE_JS, CAPTURE_JS.contains("currentTime"))
        assertTrue(CAPTURE_JS, CAPTURE_JS.contains("toDataURL"))
    }

    @Test
    fun 截圖的JS要擋掉廣告() {
        assertTrue(CAPTURE_JS, CAPTURE_JS.contains("ad-showing"))
    }
}
