package com.xenyaa.videoshot.edit

import java.io.File

/**
 * 刪掉一個輸出任務在 app 裡的資料夾 `filesDir/edits/{taskId}/`（成品 `out.mp4` 與沒收乾淨的半成品）。
 * 存進相簿的那一份不在這裡，不受影響。資料夾不存在就什麼都不做。
 */
fun deleteEditTaskDir(filesDir: File, taskId: Long) {
    File(filesDir, "edits/$taskId").deleteRecursively()
}

/**
 * app 啟動時掃一次 `filesDir/edits/`，刪掉對不到任務的資料夾（取消或刪除時沒收乾淨的、被殺留下的半成品）。
 * [taskIds] 是現有任務的編號。
 *
 * 先列資料夾、再讀任務：資料夾是任務建好之後才由輸出流程建的，所以列得到的資料夾，它的任務一定在讀任務之前就有了；
 * 反過來的順序會把「讀完任務後才開始輸出」的新任務的資料夾刪掉。
 */
suspend fun deleteOrphanEditDirs(filesDir: File, taskIds: suspend () -> Set<Long>) {
    val dirs = File(filesDir, "edits").listFiles()?.takeIf { it.isNotEmpty() } ?: return
    val keep = taskIds()
    for (dir in dirs) {
        if (dir.name.toLongOrNull() !in keep) dir.deleteRecursively()
    }
}
