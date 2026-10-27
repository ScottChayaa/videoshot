package com.xenyaa.videoshot.edit

import com.xenyaa.videoshot.core.timeline.ClipDraft
import java.io.File

/** 輸出失敗。[message] 是給紀錄看的原因，畫面上的說明由呼叫端另外轉成人話。 */
class ClipExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 把剪輯草稿的時間軸輸出成一支 mp4。 */
interface ClipExporter {
    /**
     * 輸出到 [output]，進度約每秒一次以 0～100 回報給 [onProgress]（在主執行緒呼叫）。
     * 失敗丟 [ClipExportException]；coroutine 被取消時停下輸出並把取消丟出去。
     * 不負責刪檔：失敗或取消留下的半成品由呼叫端清掉。
     */
    suspend fun export(draft: ClipDraft, output: File, onProgress: (Int) -> Unit)
}
