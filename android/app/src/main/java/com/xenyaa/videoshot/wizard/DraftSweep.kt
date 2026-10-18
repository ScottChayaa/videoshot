package com.xenyaa.videoshot.wizard

import java.io.File

/**
 * 清掉草稿資料夾（`drafts/`）裡用不到的影片目錄，回傳刪了幾個。
 *
 * 草稿只記最近一支影片，但每支進過第二步的影片都會在 `drafts/{videoId}/` 留下 sheet（與手動圖）。
 * 換影片、app 當掉、儀器測試都可能留下沒有草稿指著的目錄，這裡統一收掉（2026-10-18）。
 *
 * 不刪的：
 * - [keep] 裡的影片（目前的草稿、正在開的影片）。**每個目錄刪之前都重新問一次** ——
 *   清的同時使用者可能剛開了一支影片。
 * - 最後修改時間不早於 [olderThanMillis] 的目錄：這次 app 啟動之後才建或才動過的，
 *   可能正在用，留給下次啟動再判斷。
 */
fun sweepDraftDirs(root: File, keep: () -> Set<String>, olderThanMillis: Long): Int {
    val dirs = root.listFiles()?.filter { it.isDirectory } ?: return 0
    var deleted = 0
    for (dir in dirs) {
        if (dir.name in keep()) continue
        if (dir.lastModified() >= olderThanMillis) continue
        if (dir.deleteRecursively()) deleted++
    }
    return deleted
}
