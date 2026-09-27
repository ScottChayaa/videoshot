package com.xenyaa.videoshot.core.backup

import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 備份檔案的編碼格式：`library.db` 的 `VACUUM INTO` 快照先 gzip 壓縮，
 * SHA-256 算在**壓縮後**的位元組上（規格第十節「VACUUM INTO 產生快照 → gzip → 算 SHA-256」；
 * 還原流程圖也是先驗 SHA-256 再解壓——雜湊要對應到真正上傳／下載的那份位元組）。
 */
object BackupCodec {

    /** 壓縮 [source] 寫到 [dest]，回傳壓縮後檔案的 SHA-256（小寫十六進位）。 */
    fun gzipWithSha256(source: File, dest: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        dest.outputStream().use { fileOut ->
            DigestOutputStream(fileOut, digest).use { digestOut ->
                GZIPOutputStream(digestOut).use { gz ->
                    source.inputStream().use { input -> input.copyTo(gz) }
                }
            }
        }
        return digest.digest().toHexString()
    }

    /** [gzFile] 的 SHA-256 是否等於 [expectedHex]（不分大小寫）。 */
    fun verifySha256(gzFile: File, expectedHex: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        gzFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHexString().equals(expectedHex, ignoreCase = true)
    }

    /** 解壓 [source] 寫到 [dest]。呼叫端要自己先用 [verifySha256] 驗過。 */
    fun gunzip(source: File, dest: File) {
        GZIPInputStream(source.inputStream()).use { gz ->
            dest.outputStream().use { out -> gz.copyTo(out) }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
