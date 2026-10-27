package com.xenyaa.videoshot.ui.clip

import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MimeFirstExtractorsFactoryTest {
    private val photo = Uri.parse("content://media/external/images/media/1")

    private fun firstOf(mime: String?) =
        MimeFirstExtractorsFactory { mime }.createExtractors(photo, emptyMap()).first().javaClass.simpleName

    @Test fun 照片先試JPEG辨識器_不會先輪到MP3() = assertEquals("JpegExtractor", firstOf("image/jpeg"))

    // MP4 類型先排分段 MP4、再排一般 MP4，都是 MP4 家族
    @Test fun 影片先試MP4辨識器() = assertTrue(firstOf("video/mp4").endsWith("Mp4Extractor"))

    @Test fun 查不到類型時照預設順序() = assertEquals(
        DefaultExtractorsFactory().createExtractors(photo, emptyMap()).map { it.javaClass },
        MimeFirstExtractorsFactory { null }.createExtractors(photo, emptyMap()).map { it.javaClass },
    )

    @Test fun 查類型丟例外時照預設順序() = assertEquals(
        DefaultExtractorsFactory().createExtractors(photo, emptyMap()).map { it.javaClass },
        MimeFirstExtractorsFactory { error("provider 掛了") }.createExtractors(photo, emptyMap()).map { it.javaClass },
    )
}
