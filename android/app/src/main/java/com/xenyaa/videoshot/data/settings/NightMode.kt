package com.xenyaa.videoshot.data.settings

/**
 * 淺色／深色要聽誰的。
 *
 * 預設 [SYSTEM]（跟著手機的顯示設定），另外兩個是 app 內的手動覆寫 ——
 * 使用者選了節慶主題卻只在白天好看時，至少能自己鎖成淺色。
 *
 * @param id 存進 DataStore 的值，**不可更動**（改了使用者選過的設定會失效）。
 *        存字串而不是 ordinal，理由同 `FilterStrength`／`FolderSort`：
 *        enum 之後若調整順序，ordinal 會讓舊值指到別的模式。
 */
enum class NightMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun byId(id: String?): NightMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * 這個模式下要不要用深色。
 *
 * @param systemDark 手機目前是不是深色（`isSystemInDarkTheme()`）—— 只有 [NightMode.SYSTEM] 會看它
 */
fun NightMode.isDark(systemDark: Boolean): Boolean = when (this) {
    NightMode.SYSTEM -> systemDark
    NightMode.LIGHT -> false
    NightMode.DARK -> true
}
