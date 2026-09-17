package com.xenyaa.videoshot.data.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xenyaa.videoshot.core.similarity.FilterStrength
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

    /**
     * 取圖第二步的「過濾相似強度」（規格第五節、帳號頁的設定項）。
     * 存字串而不是 ordinal —— enum 之後若調整順序，ordinal 會讓舊值指到別的強度。
     */
    val filterStrength: Flow<FilterStrength> = store.data.map { prefs ->
        prefs[FILTER_STRENGTH]
            ?.let { name -> FilterStrength.entries.firstOrNull { it.name == name } }
            ?: FilterStrength.MEDIUM
    }

    suspend fun setFilterStrength(value: FilterStrength) {
        store.edit { it[FILTER_STRENGTH] = value.name }
    }

    /** 第二步「點一下收藏・長按看看那一段」這個一次性提示看過了沒（規格第五節）。 */
    val gridHintSeen: Flow<Boolean> = store.data.map { it[GRID_HINT_SEEN] ?: false }

    suspend fun markGridHintSeen() {
        store.edit { it[GRID_HINT_SEEN] = true }
    }

    /** Lightbox「左右滑動看上一張／下一張」這個一次性提示看過了沒（手冊 §三第三條）。 */
    val lightboxHintSeen: Flow<Boolean> = store.data.map { it[LIGHTBOX_HINT_SEEN] ?: false }

    suspend fun markLightboxHintSeen() {
        store.edit { it[LIGHTBOX_HINT_SEEN] = true }
    }

    /**
     * 把所有設定值清空。**只給測試用**——DataStore 是裝置上的真實檔案，
     * 儀器測試跑在同一支手機、同一個已安裝的 app 上，不會像重灌一樣自動歸零，
     * 每個測試不各自清掉自己用到的值，上一輪留下的狀態就會讓下一輪的斷言失真。
     */
    @VisibleForTesting
    internal suspend fun resetAll() {
        store.edit { it.clear() }
    }

    private companion object {
        val LAST_CHANGED_AT = longPreferencesKey("last_changed_at")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val FILTER_STRENGTH = stringPreferencesKey("filter_strength")
        val GRID_HINT_SEEN = booleanPreferencesKey("grid_hint_seen")
        val LIGHTBOX_HINT_SEEN = booleanPreferencesKey("lightbox_hint_seen")
    }
}
