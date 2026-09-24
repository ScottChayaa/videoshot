package com.xenyaa.videoshot.ui.thumb

import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow

/**
 * 縮圖唸出來是什麼。有描述就唸描述，沒有就唸中性字樣加秒數（手冊 §零「輔助操作」）。
 * 首頁與資料夾頁共用 —— 兩邊各寫一份的話，只會有一邊被改到。
 */
fun labelOf(shot: ShotRow): String =
    shot.description?.takeIf { it.isNotBlank() } ?: "片段縮圖 ${formatClock(shot.atSec)}"
