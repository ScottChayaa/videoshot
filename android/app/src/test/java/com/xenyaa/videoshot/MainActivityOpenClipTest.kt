package com.xenyaa.videoshot

import android.content.Intent
import com.xenyaa.videoshot.edit.EXTRA_OPEN_CLIP
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 點輸出通知會帶 [EXTRA_OPEN_CLIP] 進 `MainActivity`：冷啟動（onCreate）與已開著（onNewIntent）都要換成「開剪輯頁」的訊號。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityOpenClipTest {

    private fun openClipIntent() = Intent().putExtra(EXTRA_OPEN_CLIP, true)

    @Test fun 一般啟動沒有開剪輯的訊號() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(0, activity.openClipSignal)
    }

    @Test fun 冷啟動帶extra就有訊號() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, openClipIntent()).setup().get()
        assertEquals(1, activity.openClipSignal)
    }

    @Test fun 已開著時onNewIntent每次點通知都再發一次訊號() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        controller.newIntent(openClipIntent())
        assertEquals(1, controller.get().openClipSignal)
        controller.newIntent(openClipIntent())
        assertEquals(2, controller.get().openClipSignal)
    }

    @Test fun onNewIntent沒帶extra不發訊號() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.newIntent(Intent())
        assertEquals(0, controller.get().openClipSignal)
    }
}
