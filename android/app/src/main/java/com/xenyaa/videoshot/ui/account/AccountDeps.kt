package com.xenyaa.videoshot.ui.account

import android.app.Activity
import android.content.Intent
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.flow.Flow

/**
 * 帳號頁用得到的組裝點。窄介面的理由同 `WizardData`／`ShellSettings`：帳號頁要用到的設定值
 * （Gemini 金鑰、AI 分析區間、儲存用量、標籤管理）比 `ShellSettings` 給 `AppRoot` 其餘畫面的
 * 還多，但那些不是別的畫面用得到的東西，不該塞進 `ShellSettings` 讓它變成什麼都放的抽屜
 * （`ShellSettings` 的 KDoc 原本就把這些留給階段 11 另開一個介面）。
 */
interface AccountDeps {
    val filterStrength: Flow<FilterStrength>
    suspend fun setFilterStrength(value: FilterStrength)

    val aiRangeBeforeSec: Flow<Int>
    val aiRangeAfterSec: Flow<Int>
    suspend fun setAiRange(beforeSec: Int, afterSec: Int)

    /** 金鑰**存在與否**，不是金鑰本身——畫面不需要、也不該把明文金鑰握在 Compose 狀態裡。 */
    val geminiKeySet: Flow<Boolean>
    suspend fun setGeminiKey(plain: String)
    suspend fun clearGeminiKey()

    /** @param thisMonth `YYYY-MM` */
    suspend fun stats(thisMonth: String): AccountStats
    suspend fun tags(): List<TagUsage>
    suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>)
    suspend fun deleteTag(id: Long)

    /** 縮圖牆手機寬度每列張數（2／3／4）。 */
    val thumbColumns: Flow<Int>
    suspend fun setThumbColumns(value: Int)

    /** `thumbs/` 目錄 ＋ `library.db` 的位元組數（規格附錄 A-8：只顯示用量，不設上限）。 */
    suspend fun storageUsageBytes(): Long

    val linkedAccount: Flow<LinkedGoogleAccount?>
    /** 前景才能呼叫（會跳出系統畫面）。回傳值只是通知呼叫端「需不需要再跳一個 IntentSender」——
     * 真的連結完成是靠 [linkedAccount] 這個 Flow 自己更新畫面。 */
    suspend fun beginLink(activity: Activity): LinkOutcome
    suspend fun finishLink(data: Intent): LinkedGoogleAccount
    /** 未連結時是無害的 no-op——沒有帳號可斷。 */
    suspend fun unlink()

    val lastBackupAtEpochSec: Flow<Long>
    /** @return true 代表真的執行了一次上傳；false 代表（理論上不會發生，因為帳號頁的呼叫永遠是 force=true）什麼都沒做。 */
    suspend fun backupNow(): Boolean

    /** 回填進度（規格第十一節；手冊 §一「回填看得到進度」）。 */
    suspend fun backfillProgress(): com.xenyaa.videoshot.backfill.BackfillProgress

    /** 【稍後重試】：全部 lost 的格子重設成 missing，立刻排進下一次回填。 */
    suspend fun retryLostThumbs()

    /** 【刪除這些收藏】：刪掉全部 lost 對應的 shot。 */
    suspend fun deleteLostThumbs()

    /** 【用行動網路繼續】：這次允許用行動網路,不持久化（規格第四節）。 */
    suspend fun continueBackfillOnMobileData()
}
