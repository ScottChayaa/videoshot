package com.xenyaa.videoshot.core.timeline

import kotlinx.serialization.Serializable

/** 復原（與重做）紀錄各最多保留的步數。 */
const val MAX_HISTORY = 50

/**
 * 時間軸的編輯紀錄：[current] 是目前的時間軸，[undo] 由舊到新、[redo] 由遠到近（最後一個是下一個重做）。
 * 所有操作回傳新的紀錄。
 */
@Serializable
data class EditHistory(
    val current: Timeline,
    val undo: List<Timeline> = emptyList(),
    val redo: List<Timeline> = emptyList(),
) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** 以 [next] 當新的目前時間軸：舊的推進復原紀錄、重做清空；復原超過 [MAX_HISTORY] 丟最舊的。 */
    fun apply(next: Timeline): EditHistory =
        EditHistory(next, (undo + current).takeLast(MAX_HISTORY), emptyList())

    /** 退回上一步；沒有可復原的回 null。 */
    fun undo(): EditHistory? {
        if (undo.isEmpty()) return null
        return EditHistory(undo.last(), undo.dropLast(1), (redo + current).takeLast(MAX_HISTORY))
    }

    /** 重做被復原的一步；沒有可重做的回 null。 */
    fun redo(): EditHistory? {
        if (redo.isEmpty()) return null
        return EditHistory(redo.last(), (undo + current).takeLast(MAX_HISTORY), redo.dropLast(1))
    }
}
