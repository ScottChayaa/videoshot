package com.xenyaa.videoshot.edit

import kotlinx.coroutines.runBlocking
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

    @Test fun 啟動清理刪掉對不到任務的資料夾() = runBlocking {
        val root = tmp.newFolder("files")
        File(root, "edits/3").mkdirs()
        File(root, "edits/3/out.mp4").writeText("keep")
        File(root, "edits/5").mkdirs() // 取消後留下的空資料夾
        File(root, "edits/6").mkdirs()
        File(root, "edits/6/out.mp4").writeText("half") // 被殺留下的半成品，任務已刪
        File(root, "edits/junk").mkdirs()
        File(root, "keep.txt").writeText("x")

        deleteOrphanEditDirs(root) { setOf(3L, 4L) }

        assertEquals("keep", File(root, "edits/3/out.mp4").readText())
        assertFalse(File(root, "edits/5").exists())
        assertFalse(File(root, "edits/6").exists())
        assertFalse(File(root, "edits/junk").exists())
        assertTrue(File(root, "keep.txt").exists())
    }

    @Test fun 啟動清理先列資料夾再讀任務_清單之後才建的不會被刪() = runBlocking {
        val root = tmp.newFolder("files")
        File(root, "edits/1").mkdirs()

        // 讀任務的當下又有一個新任務開始輸出：它的資料夾是在列完之後才建的
        deleteOrphanEditDirs(root) {
            File(root, "edits/2").mkdirs()
            setOf(1L)
        }

        assertTrue(File(root, "edits/1").exists())
        assertTrue(File(root, "edits/2").exists())
    }

    @Test fun 啟動清理沒有edits資料夾時什麼都不做() = runBlocking {
        val root = tmp.newFolder("files")
        var asked = false
        deleteOrphanEditDirs(root) { asked = true; emptySet() }
        assertFalse(asked)
        assertFalse(File(root, "edits").exists())
    }
}
