package com.xenyaa.videoshot.ui.account

import android.app.Activity
import android.content.Intent
import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.PlaceUsage
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 測試用的假帳號頁組裝點，每個方法都有「什麼都沒有」的預設值 —— 理由同
 * `data.repo.FakeLibraryRepo`：不測帳號頁本身的測試（AppRoot 其他分頁的整合測試）
 * 只需要一個能編過的 `AccountDeps`，不必各自重寫一整份。
 */
open class FakeAccountDeps : AccountDeps {
    override val filterStrength = MutableStateFlow(FilterStrength.MEDIUM)
    override suspend fun setFilterStrength(value: FilterStrength) = Unit

    override val aiRangeBeforeSec = MutableStateFlow(10)
    override val aiRangeAfterSec = MutableStateFlow(20)
    override suspend fun setAiRange(beforeSec: Int, afterSec: Int) = Unit

    override val geminiKeySet = MutableStateFlow(false)
    override suspend fun setGeminiKey(plain: String) = Unit
    override suspend fun clearGeminiKey() = Unit

    override suspend fun stats(thisMonth: String): AccountStats = AccountStats(0, 0, 0)
    override suspend fun tags(): List<TagUsage> = emptyList()
    override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>) = Unit
    override suspend fun deleteTag(id: Long) = Unit
    override suspend fun places(): List<PlaceUsage> = emptyList()
    override suspend fun renamePlace(id: Long, name: String, aliases: List<String>) = Unit
    override suspend fun mergePlace(fromId: Long, toId: Long) = Unit
    override suspend fun deletePlace(id: Long) = Unit
    override suspend fun mergeTag(fromId: Long, toId: Long) = Unit

    override suspend fun storageUsageBytes(): Long = 0L

    override val thumbColumns = MutableStateFlow(4)
    override suspend fun setThumbColumns(value: Int) { thumbColumns.value = value }

    override val linkedAccount = MutableStateFlow<LinkedGoogleAccount?>(null)
    override suspend fun beginLink(activity: Activity): LinkOutcome =
        LinkOutcome.Linked(LinkedGoogleAccount("", ""))
    override suspend fun finishLink(data: Intent): LinkedGoogleAccount = LinkedGoogleAccount("", "")
    override suspend fun unlink() = Unit

    override val lastBackupAtEpochSec = MutableStateFlow(0L)
    override suspend fun backupNow(): Boolean = true

    override suspend fun backfillProgress(): BackfillProgress = BackfillProgress(0, 0, 0)
    override suspend fun retryLostThumbs() = Unit
    override suspend fun deleteLostThumbs() = Unit
    override suspend fun continueBackfillOnMobileData() = Unit
}
