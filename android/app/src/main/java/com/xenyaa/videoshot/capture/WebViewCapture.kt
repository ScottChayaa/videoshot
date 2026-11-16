package com.xenyaa.videoshot.capture

import android.graphics.Bitmap
import android.util.Base64
import com.xenyaa.videoshot.core.capture.isBlackFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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

/**
 * 先切到這支影片的最高畫質、等那一格換好，再截原尺寸 JPEG（Lightbox 與原片播放的【截圖】，規格第六節）。
 * 網路慢、[MAX_QUALITY_WAIT_MS] 內等不到就截當下的畫質；截不到回 null。影片要先停在那一格。
 *
 * 換畫質時影片會黑約半秒，所以要換之前先截一張當下畫質的交給 [onSwitching]，讓畫面拿它蓋住影片，
 * 它回來之後才真的換。已經是最高畫質時不會呼叫。
 */
suspend fun captureMaxQualityJpeg(
    eval: suspend (String) -> String,
    onSwitching: suspend (ByteArray) -> Unit = {},
): ByteArray? {
    capturedJpegOf(eval(SNAPSHOT_BEFORE_MAX_QUALITY_JS))?.let { onSwitching(it) }
    if (eval(MAX_QUALITY_JS).contains("switching")) {
        withTimeoutOrNull(MAX_QUALITY_WAIT_MS) {
            while (!eval(MAX_QUALITY_READY_JS).contains("1")) delay(100)
        }
    }
    return capturedJpegOf(eval(CAPTURE_JS))
}

/**
 * [CAPTURE_JS] 的回應取出原尺寸 JPEG（Lightbox【截圖】存到手機相簿用，規格第六節）；截不到回 null。
 * 不縮圖、不判黑畫面——Lightbox 只在影片畫面已經停在那一格時才截。
 */
fun capturedJpegOf(raw: String): ByteArray? {
    val json = unwrap(raw)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    if (!json.optBoolean("ok")) return null
    return base64Of(json.optString("data"))
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

/** 【截圖】等最高畫質最多等多久（2026-11-16 實機：1080p 半秒內就換好）。 */
private const val MAX_QUALITY_WAIT_MS = 3_000L

/** YouTube 播放器物件（有 `getAvailableQualityLevels` 等方法的那個元素）。 */
private const val YT_PLAYER = "(document.getElementById('movie_player')||document.querySelector('.html5-video-player'))"

/**
 * 【截圖】前把畫質固定在這支影片的最高一級（2026-11-16 scott）：YouTube 自動挑的畫質看播放器大小，
 * Lightbox 的直片只給到 720p，來源明明有 1080p。固定後跳回同一個時間點，讓那一格重新以高畫質解出來。
 * 回 `ready`（已經是最高）、`switching`（換畫質中，要等 [MAX_QUALITY_READY_JS]）或 `none`（拿不到播放器）。
 */
internal const val MAX_QUALITY_JS = """
(function(){
  var p = $YT_PLAYER, v = document.querySelector('video');
  if (!p || !v || !p.getAvailableQualityLevels || !p.setPlaybackQualityRange) return 'none';
  var q = p.getAvailableQualityLevels().filter(function(l){ return l !== 'auto'; })[0];
  if (!q) return 'none';
  if (p.getPlaybackQuality() === q) return 'ready';
  window.__vsMaxQ = {q: q, side: Math.min(v.videoWidth, v.videoHeight)};
  var t = v.currentTime;
  p.setPlaybackQualityRange(q, q);
  v.currentTime = t;
  return 'switching';
})()
"""

/** 還不是最高畫質時，先用 [CAPTURE_JS] 截一張當下的（換畫質前蓋住影片用）；已經是最高回空字串。 */
internal const val SNAPSHOT_BEFORE_MAX_QUALITY_JS = """
(function(){
  var p = $YT_PLAYER;
  if (!p || !p.getAvailableQualityLevels || !p.getPlaybackQuality) return '';
  var q = p.getAvailableQualityLevels().filter(function(l){ return l !== 'auto'; })[0];
  if (!q || p.getPlaybackQuality() === q) return '';
  var shot = $CAPTURE_JS;
  return shot;
})()
"""

/** 換好了沒：畫質是目標、那一格解好了，而且畫面尺寸真的變了（只看畫質標籤會截到還沒換的舊畫面）。好了回 `1`。 */
internal const val MAX_QUALITY_READY_JS = """
(function(){
  var p = $YT_PLAYER, v = document.querySelector('video'), s = window.__vsMaxQ;
  if (!p || !v || !s) return '1';
  var ok = p.getPlaybackQuality() === s.q && v.readyState >= 2 && !v.seeking
    && Math.min(v.videoWidth, v.videoHeight) !== s.side;
  return ok ? '1' : '0';
})()
"""
