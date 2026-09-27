package com.xenyaa.videoshot.core.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCodecTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun 壓縮再解壓拿回原始內容() {
        val source = tmp.newFile("library.db").apply { writeBytes("hello-videoshot".toByteArray()) }
        val gz = tmp.newFile("library.db.gz")
        BackupCodec.gzipWithSha256(source, gz)

        val restored = tmp.newFile("restored.db")
        BackupCodec.gunzip(gz, restored)

        assertEquals("hello-videoshot", restored.readText())
    }

    @Test
    fun 雜湊算在壓縮後的位元組上_同輸入永遠同雜湊() {
        val source = tmp.newFile("a.db").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val gz1 = tmp.newFile("a1.db.gz")
        val gz2 = tmp.newFile("a2.db.gz")
        val hash1 = BackupCodec.gzipWithSha256(source, gz1)
        val hash2 = BackupCodec.gzipWithSha256(source, gz2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 十六進位字串長度
    }

    @Test
    fun 雜湊比對通過() {
        val source = tmp.newFile("b.db").apply { writeBytes("abc".toByteArray()) }
        val gz = tmp.newFile("b.db.gz")
        val hash = BackupCodec.gzipWithSha256(source, gz)

        assertTrue(BackupCodec.verifySha256(gz, hash))
        assertTrue(BackupCodec.verifySha256(gz, hash.uppercase())) // 大小寫不敏感
    }

    @Test
    fun 雜湊不符會被抓出來() {
        val source = tmp.newFile("c.db").apply { writeBytes("original".toByteArray()) }
        val gz = tmp.newFile("c.db.gz")
        BackupCodec.gzipWithSha256(source, gz)

        assertFalse(BackupCodec.verifySha256(gz, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
