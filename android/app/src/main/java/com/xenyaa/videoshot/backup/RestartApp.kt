package com.xenyaa.videoshot.backup

import android.content.Context
import android.content.Intent
import com.xenyaa.videoshot.MainActivity

/**
 * 還原成功後重啟整個 app（見 `RestoreManager` 的 KDoc：換檔後要讓 `AppContainer` 用全新的
 * Room 實例打開新檔，不嘗試在執行中置換既有實例）。`Runtime.getRuntime().exit(0)` 保證
 * 舊的 process 徹底結束，不會有殘留的協程／ViewModel 還握著已經關閉的舊 `LibraryDatabase`。
 */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
