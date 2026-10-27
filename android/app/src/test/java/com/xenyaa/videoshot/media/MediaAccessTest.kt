package com.xenyaa.videoshot.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {

    private val IMAGES = "android.permission.READ_MEDIA_IMAGES"
    private val VIDEO = "android.permission.READ_MEDIA_VIDEO"
    private val USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private val EXTERNAL = "android.permission.READ_EXTERNAL_STORAGE"

    @Test fun 安卓14兩個都有是完整() =
        assertEquals(MediaAccess.FULL, mediaAccessOf(34, setOf(IMAGES, VIDEO, USER_SELECTED)))

    @Test fun 安卓14只有部分選取是部分() =
        assertEquals(MediaAccess.PARTIAL, mediaAccessOf(34, setOf(USER_SELECTED)))

    @Test fun 安卓14什麼都沒有是拒絕() =
        assertEquals(MediaAccess.DENIED, mediaAccessOf(34, emptySet()))

    @Test fun 安卓13只有照片沒有影片是拒絕() =
        assertEquals(MediaAccess.DENIED, mediaAccessOf(33, setOf(IMAGES)))

    @Test fun 安卓13兩個都有是完整() =
        assertEquals(MediaAccess.FULL, mediaAccessOf(33, setOf(IMAGES, VIDEO)))

    @Test fun 安卓13的部分選取權限不算數() =
        assertEquals(MediaAccess.DENIED, mediaAccessOf(33, setOf(USER_SELECTED)))

    @Test fun 安卓12看外部儲存() {
        assertEquals(MediaAccess.FULL, mediaAccessOf(32, setOf(EXTERNAL)))
        assertEquals(MediaAccess.DENIED, mediaAccessOf(32, emptySet()))
    }

    @Test fun 各版本請求的權限() {
        assertEquals(listOf(EXTERNAL, "android.permission.WRITE_EXTERNAL_STORAGE"), mediaPermissionsFor(28))
        assertEquals(listOf(EXTERNAL), mediaPermissionsFor(29))
        assertEquals(listOf(EXTERNAL), mediaPermissionsFor(32))
        assertEquals(listOf(IMAGES, VIDEO), mediaPermissionsFor(33))
        assertEquals(listOf(IMAGES, VIDEO, USER_SELECTED), mediaPermissionsFor(34))
    }
}
