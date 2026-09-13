package com.xenyaa.videoshot.poc

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/** 階段 0 的一次性 POC：驗證截圖、watch page、InnerTube、FTS5。驗證完即刪除。 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var idInput: EditText
    private lateinit var preview: ImageView
    private lateinit var logView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        val width = resources.displayMetrics.widthPixels
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        idInput = EditText(this).apply {
            setText("aqz-KE-bpKQ")
            hint = "videoId"
        }
        root.addView(idInput)
        root.addView(
            buttonRow(
                "Embed" to ::loadEmbed,
                "Mobile" to ::loadMobile,
                "播放" to { js("document.querySelector('video').play()") },
                "跳到10s" to { js("var v=document.querySelector('video');v.currentTime=10;v.pause()") },
            )
        )
        root.addView(
            buttonRow(
                "JS截圖" to ::captureJs,
                "PixelCopy" to ::capturePixelCopy,
                "WatchPage" to { probe { WatchPageProbe.run(videoId()) } },
                "InnerTube" to { probe { InnerTubeProbe.run(videoId()) } },
                "FTS5" to { probe { Fts5Probe.run(cacheDir) } },
            )
        )
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        root.addView(webView, LinearLayout.LayoutParams(width, width * 9 / 16))
        preview = ImageView(this)
        root.addView(preview, LinearLayout.LayoutParams(width / 2, width * 9 / 32))
        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(
            ScrollView(this).apply { addView(logView) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
    }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>): HorizontalScrollView {
        val row = LinearLayout(this)
        for ((label, action) in buttons) {
            row.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            })
        }
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun videoId(): String = idInput.text.toString().trim()

    private fun log(message: String) = runOnUiThread { logView.append(message + "\n") }

    private fun js(code: String) = webView.evaluateJavascript(code, null)

    private fun probe(block: () -> String) {
        thread {
            val out = try {
                block()
            } catch (e: Exception) {
                "ERROR ${e::class.simpleName}: ${e.message}"
            }
            log(out)
        }
    }

    // ---- 載入方式（P-1 要比較這兩種）----

    private fun loadEmbed() {
        val url = "https://www.youtube.com/embed/${videoId()}?playsinline=1&controls=0&cc_load_policy=0&rel=0"
        webView.loadUrl(url, mapOf("Referer" to "https://$packageName"))
        log("載入 embed：$url")
    }

    private fun loadMobile() {
        webView.loadUrl("https://m.youtube.com/watch?v=${videoId()}")
        log("載入 m.youtube.com")
    }

    // ---- P-1：JS canvas ----

    private val captureScript = """
        (function(){
          var v = document.querySelector('video');
          if (!v) return JSON.stringify({error: 'no video element'});
          v.pause();
          var ad = !!document.querySelector('.ad-showing');
          var c = document.createElement('canvas');
          c.width = v.videoWidth; c.height = v.videoHeight;
          try {
            c.getContext('2d').drawImage(v, 0, 0, c.width, c.height);
            return JSON.stringify({t: v.currentTime, ad: ad, w: c.width, h: c.height,
              data: c.toDataURL('image/jpeg', 0.92)});
          } catch (e) {
            return JSON.stringify({error: String(e), t: v.currentTime, ad: ad, w: c.width, h: c.height});
          }
        })()
    """.trimIndent()

    private fun captureJs() {
        val started = System.nanoTime()
        webView.evaluateJavascript(captureScript) { raw ->
            val ms = (System.nanoTime() - started) / 1_000_000
            val value = JSONTokener(raw).nextValue()
            if (value !is String) {
                log("JS截圖：腳本回傳 $raw")
                return@evaluateJavascript
            }
            val json = JSONObject(value)
            if (json.has("error")) {
                log("JS截圖失敗：$json")
                return@evaluateJavascript
            }
            val data = json.getString("data")
            val bytes = Base64.decode(data.substringAfter(","), Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                // toDataURL 失敗時 Chromium 會回 "data:," 之類解不開的內容，不走 catch。照實記錄，不要當機。
                log(
                    "JS截圖：dataURL 解不出 bitmap —— 影片 ${json.optInt("w")}x${json.optInt("h")} " +
                        "dataURL 長度=${data.length} 前綴=${data.take(30)} base64 位元組=${bytes.size} " +
                        "t=${"%.3f".format(json.getDouble("t"))} 耗時=${ms}ms"
                )
                return@evaluateJavascript
            }
            report("JS", bmp, json.getDouble("t"), json.getBoolean("ad"), ms)
        }
    }

    // ---- P-2：PixelCopy ----

    private val rectScript = """
        (function(){
          var v = document.querySelector('video');
          if (!v) return JSON.stringify({error: 'no video element'});
          v.pause();
          var r = v.getBoundingClientRect();
          return JSON.stringify({x: r.left, y: r.top, w: r.width, h: r.height,
            dpr: window.devicePixelRatio, t: v.currentTime, ad: !!document.querySelector('.ad-showing')});
        })()
    """.trimIndent()

    private fun capturePixelCopy() {
        webView.evaluateJavascript(rectScript) { raw ->
            val value = JSONTokener(raw).nextValue()
            if (value !is String) {
                log("PixelCopy：腳本回傳 $raw")
                return@evaluateJavascript
            }
            val j = JSONObject(value)
            if (j.has("error")) {
                log("PixelCopy：$j")
                return@evaluateJavascript
            }
            val dpr = j.getDouble("dpr")
            val loc = IntArray(2).also { webView.getLocationInWindow(it) }
            val left = loc[0] + (j.getDouble("x") * dpr).toInt()
            val top = loc[1] + (j.getDouble("y") * dpr).toInt()
            val src = Rect(left, top, left + (j.getDouble("w") * dpr).toInt(), top + (j.getDouble("h") * dpr).toInt())
            // 影片可能超出 WebView 可見範圍，裁到 WebView 內
            if (!src.intersect(Rect(loc[0], loc[1], loc[0] + webView.width, loc[1] + webView.height))) {
                log("PixelCopy：影片不在可見範圍")
                return@evaluateJavascript
            }
            val bmp = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
            val started = System.nanoTime()
            PixelCopy.request(window, src, bmp, { result ->
                val ms = (System.nanoTime() - started) / 1_000_000
                if (result == PixelCopy.SUCCESS) {
                    report("PixelCopy", bmp, j.getDouble("t"), j.getBoolean("ad"), ms)
                } else {
                    log("PixelCopy 失敗 code=$result")
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    // ---- 共用：縮成 320×180 WebP、統計亮度 ----

    private fun report(kind: String, bmp: Bitmap, t: Double, ad: Boolean, ms: Long) {
        val stats = FrameCheck.luma(bmp)
        val thumb = Bitmap.createScaledBitmap(bmp, 320, 180, true)
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        thumb.compress(format, 75, out)
        preview.setImageBitmap(thumb)
        log(
            "$kind ${bmp.width}x${bmp.height} t=${"%.3f".format(t)} ad=$ad " +
                "亮度平均=${"%.1f".format(stats.mean)} 標準差=${"%.1f".format(stats.std)} 黑畫面=${stats.isBlack} " +
                "webp=${out.size()}B 耗時=${ms}ms"
        )
    }
}
