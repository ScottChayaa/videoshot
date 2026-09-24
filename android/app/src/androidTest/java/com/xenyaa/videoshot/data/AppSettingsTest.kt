package com.xenyaa.videoshot.data

import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.settings.AppSettings
import com.xenyaa.videoshot.data.settings.NightMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DataStore 是真的檔案 I/O，所以這一組跑在實機而不是 Robolectric
 * —— 與資料層其他測試同一個取捨。
 */
class AppSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = AppSettings(context)

    /**
     * DataStore 是 process 單例、又是裝置上的真實檔案，測試之間、甚至同一支測試
     * 跑兩次之間都會互相污染 —— 每次先把整份設定清空，不是只清自己這個案例用到的欄位。
     */
    @Before
    fun reset() = runBlocking {
        settings.resetAll()
        settings.setFilterStrength(FilterStrength.MEDIUM)
    }

    @Test
    fun 過濾強度預設是中() = runBlocking {
        assertEquals(FilterStrength.MEDIUM, settings.filterStrength.first())
    }

    @Test
    fun 過濾強度存得住() = runBlocking {
        settings.setFilterStrength(FilterStrength.HIGH)
        assertEquals(FilterStrength.HIGH, settings.filterStrength.first())

        settings.setFilterStrength(FilterStrength.LOW)
        assertEquals(FilterStrength.LOW, settings.filterStrength.first())
    }

    @Test
    fun 一次性提示標記過就不會再回到未看過() = runBlocking {
        settings.markGridHintSeen()
        assertEquals(true, settings.gridHintSeen.first())
    }

    @Test
    fun Lightbox_的一次性提示記得住() = runTest {
        assertFalse(settings.lightboxHintSeen.first())
        settings.markLightboxHintSeen()
        assertTrue(settings.lightboxHintSeen.first())
    }

    @Test
    fun 資料夾排序存得住() = runTest {
        assertEquals("預設是名稱升冪", FolderSort.NAME_ASC, settings.folderSort.first())

        settings.setFolderSort(FolderSort.RECENT)

        assertEquals(FolderSort.RECENT, settings.folderSort.first())
    }

    /** 存的是 id 字串不是 ordinal —— 之後 enum 調順序，舊值不能指到別的排序。 */
    @Test
    fun 認不得的舊值退回預設() = runTest {
        settings.setFolderSort(FolderSort.COUNT_DESC)
        settings.writeRawFolderSortForTest("這個排序已經不存在了")

        assertEquals(FolderSort.NAME_ASC, settings.folderSort.first())
    }

    /**
     * 主題（配色）的偏好。存的是色系 id 字串 —— 之後多做幾個節慶主題時，
     * 這個值就是使用者在帳號頁選過的那一個（階段 11 才有 UI，管線先通）。
     */
    @Test
    fun 主題_id_存得住_預設是空的代表跟著預設色系() = runTest {
        assertEquals(null, settings.themeId.first())

        settings.setThemeId("christmas")

        assertEquals("christmas", settings.themeId.first())
    }

    @Test
    fun 淺深模式預設跟隨系統且存得住() = runTest {
        assertEquals(NightMode.SYSTEM, settings.nightMode.first())

        settings.setNightMode(NightMode.DARK)
        assertEquals(NightMode.DARK, settings.nightMode.first())

        settings.setNightMode(NightMode.LIGHT)
        assertEquals(NightMode.LIGHT, settings.nightMode.first())
    }

    /** 存 id 字串不是 ordinal —— 認不得的舊值要退回跟隨系統，不是當機。 */
    @Test
    fun 認不得的淺深模式退回跟隨系統() = runTest {
        settings.setNightMode(NightMode.DARK)
        settings.writeRawNightModeForTest("這個模式已經不存在了")

        assertEquals(NightMode.SYSTEM, settings.nightMode.first())
    }
}
