package com.xenyaa.videoshot.ui.theme

import com.xenyaa.videoshot.ui.theme.palettes.Indigo

/**
 * 色系的**唯一註冊表**。新增一個色系＝在 `palettes/` 底下多一個檔案、在 ALL 裡多一行。
 * `PaletteTest` 會自動把新色系一起檢查對比，不合格就紅燈。
 */
object Palettes {
    val DEFAULT: ThemeSpec = Indigo

    val ALL: List<ThemeSpec> = listOf(Indigo)

    /** 認不得的 id（使用者降級、或色系被移除）一律退回預設，不當機。 */
    fun byId(id: String?): ThemeSpec = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
