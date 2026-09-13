package com.xenyaa.videoshot.poc

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/** 規格第二節第 7 點：自帶 SQLite 是否含 FTS5 trigram、是否支援 VACUUM INTO。 */
object Fts5Probe {
    fun run(dir: File): String {
        val db = File(dir, "fts5-probe.db").apply { delete() }
        val snapshot = File(dir, "fts5-snapshot.db").apply { delete() }
        val conn = BundledSQLiteDriver().open(db.path)
        try {
            val version = conn.prepare("SELECT sqlite_version()").use { it.step(); it.getText(0) }
            conn.execSQL("CREATE VIRTUAL TABLE t USING fts5(body, tokenize='trigram')")
            conn.execSQL("INSERT INTO t(body) VALUES ('加勒比海夜潛看到的大蝦')")
            val three = conn.prepare("SELECT count(*) FROM t WHERE t MATCH '夜潛看'").use { it.step(); it.getLong(0) }
            val two = conn.prepare("SELECT count(*) FROM t WHERE t MATCH '大蝦'").use { it.step(); it.getLong(0) }
            conn.execSQL("VACUUM INTO '${snapshot.path}'")
            return "FTS5 sqlite=$version trigram：3 字命中=$three（預期 1）、2 字命中=$two（預期 0）；" +
                "VACUUM INTO 快照=${snapshot.length()}B"
        } finally {
            conn.close()
        }
    }
}
