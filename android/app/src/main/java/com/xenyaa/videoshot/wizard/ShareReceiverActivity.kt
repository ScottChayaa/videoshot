package com.xenyaa.videoshot.wizard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.xenyaa.videoshot.MainActivity
import com.xenyaa.videoshot.core.url.parseVideoId

/** 分享進來的影片 ID（[MainActivity] 收到後交給取圖精靈）。 */
const val EXTRA_SHARED_VIDEO_ID = "com.xenyaa.videoshot.SHARED_VIDEO_ID"

/**
 * 「分享」清單裡的 videoshot（YouTube 的【分享】→ videoshot）：解出影片 ID，交給 [MainActivity] 開取圖第二步。
 *
 * 不直接讓 [MainActivity] 接分享：分享會把它開在 YouTube 的工作裡，變成第二份 app、第二個精靈，
 * 兩邊搶同一份草稿。這裡用 `NEW_TASK｜CLEAR_TOP｜SINGLE_TOP` 轉給 app 自己的那一份（已開著就走 onNewIntent），
 * 自己不畫任何東西就結束。
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent?.getStringExtra(Intent.EXTRA_TEXT)?.let(::parseVideoId)
        if (videoId == null) {
            Toast.makeText(this, "這不是 YouTube 影片網址", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(EXTRA_SHARED_VIDEO_ID, videoId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}

/** 一次分享。不是 data class：同一支影片分享兩次是兩個不同的值，畫面才會再處理一次。 */
class SharedVideo(val videoId: String)
