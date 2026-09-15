package com.xenyaa.videoshot.capture

import android.graphics.Bitmap
import android.util.Base64
import com.xenyaa.videoshot.core.capture.isBlackFrame
import org.json.JSONObject

/**
 * 截圖用的 JS。**圖與秒數在同一次呼叫裡取得** —— 分兩次讀就對不上了（規格第五節）。
 *
 * 廣告偵測用 `.ad-showing`。⏳ 這個選擇器在 app 的 WebView 裡**尚未驗證有效**
 * （POC 八支影片十七次截圖都沒觸發過廣告，規格第十二節）。所以它只是「偵測得到就擋」，
 * **偵測不到不能擋住流程** —— 真的截到廣告畫面時使用者看得出來，重截一次即可。
 *
 * `toDataURL` 在影片還沒載入（`videoWidth=0`）時會回 `"data:,"` 而**不拋例外**，
 * 所以這裡先擋掉 `videoWidth` 是 0 的情形；解不出 bitmap 的情形由 [WebViewCapture] 再擋一次。
 */
internal const val CAPTURE_JS = """
(function(){
  var v = document.querySelector('video');
  if (!v || !v.videoWidth) return JSON.stringify({ok:false, reason:'not-ready'});
  var player = document.querySelector('.html5-video-player');
  if (player && player.classList.contains('ad-showing')) {
    return JSON.stringify({ok:false, reason:'ad'});
  }
  var c = document.createElement('canvas');
  c.width = v.videoWidth;
  c.height = v.videoHeight;
  try {
    c.getContext('2d').drawImage(v, 0, 0, c.width, c.height);
    return JSON.stringify({ok:true, t:v.currentTime, data:c.toDataURL('image/jpeg', 0.92)});
  } catch (e) {
    return JSON.stringify({ok:false, reason:'draw-failed'});
  }
})()
"""

/**
 * JS canvas 截圖（POC P-1 選定的實作，規格第二節第 5 點）。
 *
 * **建構子收 lambda 而不是 `WebView`**：`evaluateJavascript` 是回呼式又綁 UI 執行緒，
 * 測試裡沒辦法餵假回應。收一個 `suspend (String) -> String` 之後，這支類別的全部邏輯
 * 都在 JVM 上測得完，只剩「把 lambda 接到真 WebView」那一行需要實機。
 *
 * @param decode 把 JPEG bytes 解成 Bitmap。抽成參數同理 —— Robolectric 解不出真的 JPEG
 */
class WebViewCapture(
    private val eval: suspend (String) -> String,
    private val decode: (ByteArray) -> Bitmap?,
) : Capture {

    override suspend fun capture(): CaptureResult {
        val payload = unwrap(eval(CAPTURE_JS)) ?: return CaptureResult.NotReady
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return CaptureResult.NotReady

        if (!json.optBoolean("ok")) {
            return if (json.optString("reason") == "ad") CaptureResult.AdPlaying else CaptureResult.NotReady
        }

        val bytes = base64Of(json.optString("data")) ?: return CaptureResult.NotDecodable
        val bitmap = runCatching { decode(bytes) }.getOrNull() ?: return CaptureResult.NotDecodable
        if (isBlackFrame(sampleGray(bitmap))) return CaptureResult.BlackFrame

        val webp = encodeManualWebp(bitmap) ?: return CaptureResult.NotDecodable
        return CaptureResult.Success(webp, json.optDouble("t", 0.0))
    }

    /** `evaluateJavascript` 回來的是 JSON 值，字串會多一層引號與跳脫。 */
    private fun unwrap(raw: String): String? {
        if (raw.isBlank() || raw == "null") return null
        return runCatching { JSONObject("""{"v":$raw}""").getString("v") }.getOrNull()
    }

    private fun base64Of(dataUrl: String): ByteArray? {
        val comma = dataUrl.indexOf(',')
        // "data:," 就是 POC 遇到的那種：有回應，但逗號後面什麼都沒有
        if (comma < 0 || comma == dataUrl.lastIndex) return null
        return runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
    }

    /**
     * 取一片灰階樣本給黑畫面判定用。
     *
     * **逐點取樣，不做區域平均** —— 這裡要的正是「黑底白字」那種高標準差，
     * 平均掉就跟全黑分不出來了。與 dHash 的取樣要求正好相反（見 `grayscale9x8`）。
     */
    private fun sampleGray(bitmap: Bitmap, cols: Int = 32, rows: Int = 18): IntArray {
        val out = IntArray(cols * rows)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val px = bitmap.getPixel(
                    (bitmap.width * x / cols).coerceIn(0, bitmap.width - 1),
                    (bitmap.height * y / rows).coerceIn(0, bitmap.height - 1),
                )
                out[y * cols + x] =
                    (0.299 * ((px shr 16) and 0xFF) + 0.587 * ((px shr 8) and 0xFF) + 0.114 * (px and 0xFF)).toInt()
            }
        }
        return out
    }
}
