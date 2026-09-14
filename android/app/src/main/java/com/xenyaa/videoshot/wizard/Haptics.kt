package com.xenyaa.videoshot.wizard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 長按跳播的觸覺回饋。
 *
 * 為什麼不用 `LocalHapticFeedback`：規格第五節指定 **15 ms**，
 * 而 `HapticFeedbackType` 只有語意化的幾種，指定不了時長。
 *
 * 抽成介面的理由與 [com.xenyaa.videoshot.player.Player] 相同 —— 測試裡數得出次數。
 */
interface Haptics {
    fun tick()
}

/** 測試用。放在 main 是因為 `test` 與 `androidTest` 兩個 source set 不能互相引用。 */
class FakeHaptics : Haptics {
    var ticks: Int = 0
        private set

    override fun tick() { ticks++ }
}

/** 需要 `android.permission.VIBRATE`。裝置沒有震動器時安靜地什麼都不做，不當機。 */
class SystemHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun tick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
