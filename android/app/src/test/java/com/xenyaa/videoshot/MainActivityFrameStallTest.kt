package com.xenyaa.videoshot

import android.view.Choreographer
import com.xenyaa.videoshot.ui.shell.FrameStallGuard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 釘住接線：`MainActivity` 取得焦點時一定要經過 [FrameStallGuard]。
 * 拿掉 `onWindowFocusChanged` 的覆寫，MIUI 13 上「開 App 白畫面」就會回來（見 FrameStallGuard 的 KDoc）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityFrameStallTest {

    @Test
    fun 取得焦點時經過FrameStallGuard補要一幀() {
        val posted = mutableListOf<Choreographer.FrameCallback>()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.frameStallGuard = FrameStallGuard(postFrame = { posted += it })

        activity.onWindowFocusChanged(true)

        assertEquals(1, posted.size)
    }
}
