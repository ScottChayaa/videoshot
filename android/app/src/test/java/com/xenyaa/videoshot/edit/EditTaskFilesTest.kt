package com.xenyaa.videoshot.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditTaskFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun 刪掉這個任務的整個資料夾_別的任務不動() {
        val root = tmp.newFolder("files")
        File(root, "edits/3").mkdirs()
        File(root, "edits/3/out.mp4").writeText("video")
        File(root, "edits/3/out.tmp").writeText("partial")
        File(root, "edits/4").mkdirs()
        File(root, "edits/4/out.mp4").writeText("other")

        deleteEditTaskDir(root, 3)

        assertFalse(File(root, "edits/3").exists())
        assertEquals("other", File(root, "edits/4/out.mp4").readText())
    }

    @Test fun 資料夾不存在時什麼都不做() {
        val root = tmp.newFolder("files")
        deleteEditTaskDir(root, 9)
        assertTrue(root.exists())
    }

    @Test fun 只刪edits底下的子資料夾() {
        val root = tmp.newFolder("files")
        File(root, "edits").mkdirs()
        File(root, "keep.txt").writeText("x")
        deleteEditTaskDir(root, 0)
        assertTrue(File(root, "keep.txt").exists())
        assertTrue(File(root, "edits").exists())
    }
}
