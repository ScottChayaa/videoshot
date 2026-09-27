package com.xenyaa.videoshot.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.similarity.FilterStrength
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

private const val GEMINI_KEYSTORE_ALIAS = "gemini_key_wrap"

/**
 * 裝置本地的設定值，**不進備份**（規格第四節）。
 * lastChangedAt 是自動備份的判斷依據之一：沒有變更就不要每天上傳一份一樣的檔案（第十節）。
 *
 * 實作 [ShellSettings]——`AppRoot` 只認得那個窄介面，測試才不必牽動這裡的 DataStore。
 */
class AppSettings(context: Context) : ShellSettings {

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
    override val filterStrength: Flow<FilterStrength> = store.data.map { prefs ->
        prefs[FILTER_STRENGTH]
            ?.let { name -> FilterStrength.entries.firstOrNull { it.name == name } }
            ?: FilterStrength.MEDIUM
    }

    suspend fun setFilterStrength(value: FilterStrength) {
        store.edit { it[FILTER_STRENGTH] = value.name }
    }

    /** 第二步「點一下收藏・長按看看那一段」這個一次性提示看過了沒（規格第五節）。 */
    override val gridHintSeen: Flow<Boolean> = store.data.map { it[GRID_HINT_SEEN] ?: false }

    override suspend fun markGridHintSeen() {
        store.edit { it[GRID_HINT_SEEN] = true }
    }

    /** Lightbox「左右滑動看上一張／下一張」這個一次性提示看過了沒（手冊 §三第三條）。 */
    override val lightboxHintSeen: Flow<Boolean> = store.data.map { it[LIGHTBOX_HINT_SEEN] ?: false }

    override suspend fun markLightboxHintSeen() {
        store.edit { it[LIGHTBOX_HINT_SEEN] = true }
    }

    /**
     * 分類清單頁的排序。存 id 字串而不是 ordinal —— 理由同 [filterStrength]：
     * enum 之後若調整順序，ordinal 會讓舊值指到別的排序。
     */
    override val folderSort: Flow<FolderSort> = store.data.map { FolderSort.byId(it[FOLDER_SORT]) }

    override suspend fun setFolderSort(value: FolderSort) {
        store.edit { it[FOLDER_SORT] = value.id }
    }

    /** 只給測試用：塞一個認不得的值，驗證讀取端會退回預設。 */
    @VisibleForTesting
    suspend fun writeRawFolderSortForTest(raw: String) {
        store.edit { it[FOLDER_SORT] = raw }
    }

    /**
     * 已連結的 Google 帳號（規格第九節 hero：「已連結 Google：名稱、Email、頭像字母」）。
     * 只存顯示用的兩個欄位——真正的授權狀態交給 `GoogleAuth`／Play Services 自己管，
     * 這裡不快取存取權杖。換裝置、清除 app 資料都會回到未連結，使用者要重新連結。
     */
    val linkedAccount: Flow<LinkedGoogleAccount?> = store.data.map { prefs ->
        val name = prefs[LINKED_ACCOUNT_NAME] ?: return@map null
        val email = prefs[LINKED_ACCOUNT_EMAIL] ?: return@map null
        LinkedGoogleAccount(name, email)
    }

    suspend fun setLinkedAccount(account: LinkedGoogleAccount) {
        store.edit {
            it[LINKED_ACCOUNT_NAME] = account.displayName
            it[LINKED_ACCOUNT_EMAIL] = account.email
        }
    }

    suspend fun clearLinkedAccount() {
        store.edit {
            it.remove(LINKED_ACCOUNT_NAME)
            it.remove(LINKED_ACCOUNT_EMAIL)
        }
    }

    /**
     * 全新安裝的第一個畫面（【從 Google Drive 還原】／【全新開始】）有沒有被回答過
     * （規格第十節「還原」入口）。回答過就不再問——即使之後圖庫又變空，也不會重新跳出來。
     */
    val restoreDecisionMade: Flow<Boolean> = store.data.map { it[RESTORE_DECISION_MADE] ?: false }

    suspend fun markRestoreDecisionMade() {
        store.edit { it[RESTORE_DECISION_MADE] = true }
    }

    /**
     * 使用者選的色系 id（`Palettes.ALL` 裡那個 `ThemeSpec.id`）。
     *
     * null＝沒選過，用預設色系。**這裡刻意不回傳 `ThemeSpec`** —— 那是 UI 層的型別，
     * 資料層不該認得它；由套用主題的地方（`MainActivity`）用 `Palettes.byId(id)` 解析，
     * 認不得的 id（主題被移除、使用者降級）就自動退回預設。
     */
    val themeId: Flow<String?> = store.data.map { it[THEME_ID] }

    suspend fun setThemeId(id: String) {
        store.edit { it[THEME_ID] = id }
    }

    /** 淺色／深色要聽誰的（預設跟隨系統）。 */
    val nightMode: Flow<NightMode> = store.data.map { NightMode.byId(it[NIGHT_MODE]) }

    suspend fun setNightMode(value: NightMode) {
        store.edit { it[NIGHT_MODE] = value.id }
    }

    /** 只給測試用：塞一個認不得的值，驗證讀取端會退回跟隨系統。 */
    @VisibleForTesting
    suspend fun writeRawNightModeForTest(raw: String) {
        store.edit { it[NIGHT_MODE] = raw }
    }

    /**
     * Gemini 金鑰（規格第四節：「以 Android Keystore 的金鑰加密後存 DataStore」）。
     * 換裝置、清除 app 資料、或 Keystore 本身被系統清掉的話，舊密文解不開——
     * 讀取失敗一律當成「沒有金鑰」，不拋例外（這一格本來就允許沒有值，找不到就是找不到）。
     *
     * 這裡只做存取層；輸入這個值的畫面是階段 11 帳號頁 T11.3 的範圍（見 Task 7 的 KDoc）。
     */
    val geminiKey: Flow<String?> = store.data.map { prefs ->
        val raw = prefs[GEMINI_KEY] ?: return@map null
        decryptGeminiKey(raw)
    }

    suspend fun setGeminiKey(plain: String) {
        store.edit { it[GEMINI_KEY] = encryptGeminiKey(plain) }
    }

    suspend fun clearGeminiKey() {
        store.edit { it.remove(GEMINI_KEY) }
    }

    /**
     * AI 分析區間：送 Gemini 分析時，從 `shot.at_sec` 往前／往後涵蓋的秒數（規格第九節、
     * 第十三節「shot 只有 at_sec，送 Gemini 時由帳號頁的兩個數字推導」）。
     * **顯示但第四步（AI 補充）還沒上線前不生效**——上線後才會真的拿這兩個值切分析區間。
     */
    val aiRangeBeforeSec: Flow<Int> = store.data.map { it[AI_RANGE_BEFORE_SEC] ?: 10 }
    val aiRangeAfterSec: Flow<Int> = store.data.map { it[AI_RANGE_AFTER_SEC] ?: 20 }

    suspend fun setAiRange(beforeSec: Int, afterSec: Int) {
        store.edit {
            it[AI_RANGE_BEFORE_SEC] = beforeSec.coerceAtLeast(0)
            it[AI_RANGE_AFTER_SEC] = afterSec.coerceAtLeast(0)
        }
    }

    private fun geminiSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(GEMINI_KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                GEMINI_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 密文格式：`base64(iv) + ":" + base64(密文)`——DataStore 只存字串，GCM 的 iv 要跟密文一起存。 */
    private fun encryptGeminiKey(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, geminiSecretKey())
        }
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decryptGeminiKey(encoded: String): String? = runCatching {
        val (ivB64, bodyB64) = encoded.split(":", limit = 2)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(bodyB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, geminiSecretKey(), GCMParameterSpec(128, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

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
        val FOLDER_SORT = stringPreferencesKey("folder_sort")
        val THEME_ID = stringPreferencesKey("theme_id")
        val NIGHT_MODE = stringPreferencesKey("night_mode")
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val AI_RANGE_BEFORE_SEC = intPreferencesKey("ai_range_before_sec")
        val AI_RANGE_AFTER_SEC = intPreferencesKey("ai_range_after_sec")
        val LINKED_ACCOUNT_NAME = stringPreferencesKey("linked_account_name")
        val LINKED_ACCOUNT_EMAIL = stringPreferencesKey("linked_account_email")
        val RESTORE_DECISION_MADE = booleanPreferencesKey("restore_decision_made")
    }
}
