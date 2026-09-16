package com.xenyaa.videoshot.data

import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.settings.AppSettings
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

    /** DataStore 是 process 單例，測試之間會互相污染 —— 每次先歸零。 */
    @Before
    fun reset() = runBlocking {
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
}
