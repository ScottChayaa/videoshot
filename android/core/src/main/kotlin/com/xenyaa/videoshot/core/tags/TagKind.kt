package com.xenyaa.videoshot.core.tags

/**
 * 標籤管理頁的 kind 選項（規格第九節「帳號頁」；地點是 `shot.place`，不在這裡——
 * 規格第四節：`tag.kind` 不再有 `place`）。名稱與圖示語意對照見
 * `mockups/uiux-v2/app.js` 的 `KIND`（不含 `place`）。
 *
 * @param id 存進 `tag.kind` 欄位的值，**不可更動**——改了既有標籤的 kind 值會對不起來，
 *        `RoomLibraryRepo.commitPicks`／`patchShots` 目前用字面值 `"other"` 建新標籤，
 *        就是這個 enum 的 [OTHER] 分支
 */
enum class TagKind(val id: String, val label: String) {
    PERSON("person", "人物"),
    PET("pet", "動物"),
    TOPIC("topic", "主題"),
    OTHER("other", "其他");

    companion object {
        fun byId(id: String): TagKind = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * 標籤管理頁「別名」欄位的逗號分隔文字 → 正規化清單。
 *
 * **不排序**——跟 `core.details.normalizeTags` 不同：別名沒有「兩組別名算不算同一組」
 * 的比較需求，維持使用者輸入的先後順序，改一次別名不會讓整份清單洗牌。
 */
fun parseAliases(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
