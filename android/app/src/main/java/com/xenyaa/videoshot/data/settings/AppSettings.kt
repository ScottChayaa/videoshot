package com.xenyaa.videoshot.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 裝置本地的設定值，**不進備份**（規格第四節）。
 * lastChangedAt 是自動備份的判斷依據之一：沒有變更就不要每天上傳一份一樣的檔案（第十節）。
 */
class AppSettings(context: Context) {

    private val store = context.applicationContext.dataStore

    val lastChangedAt: Flow<Long> = store.data.map { it[LAST_CHANGED_AT] ?: 0L }
    val lastBackupAt: Flow<Long> = store.data.map { it[LAST_BACKUP_AT] ?: 0L }

    /** 每次寫入 library.db 之後呼叫。 */
    suspend fun markChanged(nowSec: Long = System.currentTimeMillis() / 1000) {
        store.edit { it[LAST_CHANGED_AT] = nowSec }
    }

    suspend fun markBackedUp(nowSec: Long = System.currentTimeMillis() / 1000) {
        store.edit { it[LAST_BACKUP_AT] = nowSec }
    }

    private companion object {
        val LAST_CHANGED_AT = longPreferencesKey("last_changed_at")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
