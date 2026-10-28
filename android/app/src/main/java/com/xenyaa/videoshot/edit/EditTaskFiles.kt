package com.xenyaa.videoshot.edit

import java.io.File

/**
 * 刪掉一個輸出任務在 app 裡的資料夾 `filesDir/edits/{taskId}/`（成品 `out.mp4` 與沒收乾淨的半成品）。
 * 存進相簿的那一份不在這裡，不受影響。資料夾不存在就什麼都不做。
 */
fun deleteEditTaskDir(filesDir: File, taskId: Long) {
    File(filesDir, "edits/$taskId").deleteRecursively()
}
