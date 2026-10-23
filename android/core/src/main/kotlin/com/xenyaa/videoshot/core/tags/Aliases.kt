package com.xenyaa.videoshot.core.tags

/**
 * 標籤管理頁「別名」欄位的逗號分隔文字 → 正規化清單。
 *
 * **不排序**——跟 `core.details.normalizeTags` 不同：別名沒有「兩組別名算不算同一組」
 * 的比較需求，維持使用者輸入的先後順序，改一次別名不會讓整份清單洗牌。
 */
fun parseAliases(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
