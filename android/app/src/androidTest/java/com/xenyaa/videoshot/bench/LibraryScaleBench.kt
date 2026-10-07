package com.xenyaa.videoshot.bench

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.Transactor
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.data.library.FTS_SETUP_SQL
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.library.STATS_REBUILD_SQL
import com.xenyaa.videoshot.data.library.STATS_SETUP_SQL
import com.xenyaa.videoshot.data.library.STATS_TRIGGER_NAMES
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * 百萬張規模的效能量測（不是正確性測試）。在 app 的 files 目錄另開 `bench-library.db`，
 * **不碰使用者的 library.db**。用 app 實際的 Room 設定（BundledSQLiteDriver、FTS 觸發器）。
 *
 * 平常跑 androidTest 會自動跳過，要加參數才會執行：
 * ```
 * adb shell am instrument -w -e class com.xenyaa.videoshot.bench.LibraryScaleBench -e bench true \
 *   com.xenyaa.videoshot.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * 可選參數：`-e benchShots 1000000`（張數）、`-e benchRebuild true`（強制重造資料）。
 * 結果印在 logcat：`adb logcat -s VsBench`。資料庫檔留著，下次直接重用（造一次要幾分鐘）；
 * Gradle 跑儀器測試會解除安裝 app，檔案會跟著消失。
 */
@RunWith(AndroidJUnit4::class)
class LibraryScaleBench {

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shots = args.getString("benchShots")?.toLong() ?: 1_000_000L
    private val dbFile = File(context.filesDir, "bench-library.db")

    private fun open(): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, dbFile.path)
            .setDriver(BundledSQLiteDriver())
            .addCallback(LibrarySchemaCallback)
            .addMigrations(*LIBRARY_MIGRATIONS)
            .build()

    @Test
    fun bench() = runBlocking {
        assumeTrue(args.getString("bench") == "true")
        if (args.getString("benchRebuild") == "true") deleteDb()
        if (dbFile.exists()) {
            val version = BundledSQLiteDriver().open(dbFile.path).let { c ->
                try { c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) } } finally { c.close() }
            }
            if (version == 1L) {
                val ms = timed { open().also { it.text("SELECT COUNT(*) FROM shot") }.close() }
                log("開檔（含 v1→v2 升級，100 萬張）：%.1f 秒".format(ms / 1000))
            }
        }
        if (!dbFile.exists()) {
            open().also { it.text("SELECT COUNT(*) FROM shot") }.close() // 讓 Room 建好 schema
            val ms = timed { generate() }
            log("造資料 %,d 張：%.1f 秒（檔案 %.0f MB）".format(shots, ms / 1000, dbFile.length() / 1e6))
        }
        // 重開一次，SQLite 自己的頁面快取清空（作業系統的檔案快取清不掉，要 root）
        val db = open()
        val repo = RoomLibraryRepo(db, Dispatchers.IO)
        log("===== 現行做法（app 實際的程式碼）=====")
        measure("首頁第一頁 50 張") { repo.homeFeed(null, 50).items.size }
        val deep = db.text("SELECT event_date || '|' || id FROM shot ORDER BY event_date DESC, id DESC LIMIT 1 OFFSET 10000")
            .split('|').let { ShotCursor(it[0], it[1].toLong()) }
        measure("首頁往下滑（第 200 頁）") { repo.homeFeed(deep, 50).items.size }
        measure("首頁共 M 張（每批都算）") { repo.shotCount(null) }
        measure("月份選單") { repo.monthCounts().size }
        measure("首頁某月的標籤小膠囊") { repo.monthFacets("2025-03").size }
        measure("查詢頁候選清單") { repo.searchFacets(null, 31).size }
        measure("查詢結果：常見地點 第一頁") { repo.searchByFacets(setOf("宜蘭2"), emptySet(), null, null, 50).items.size }
        measure("查詢結果：常見地點 N 張") { repo.searchByFacetsCount(setOf("宜蘭2"), emptySet(), null) }
        measure("查詢結果：大標籤 第一頁") { repo.searchByFacets(emptySet(), setOf("標籤1"), null, null, 50).items.size }
        measure("查詢結果：冷門標籤 第一頁") { repo.searchByFacets(emptySet(), setOf("標籤500"), null, null, 50).items.size }
        measure("查詢結果：冷門標籤 N 張") { repo.searchByFacetsCount(emptySet(), setOf("標籤500"), null) }
        measure("地點輸入提示") { repo.distinctPlaces().size }
        measure("標籤管理（每個標籤張數）") { repo.allTagsWithUsage().size }
        measure("帳號頁統計") { repo.accountStats("2025-03").totalShots }
        measure("描述查詢：3 字常見詞") {
            runCatching { repo.searchByQuery(ParsedQuery(keywords = listOf("一一丁")), null, null, 50).items.size }
                .fold({ it }, { "失敗：${it.javaClass.simpleName} ${it.message?.take(60)}" })
        }
        measure("描述查詢：3 字常見詞 N 張") {
            runCatching { repo.searchByQueryCount(ParsedQuery(keywords = listOf("一一丁")), null) }
                .fold({ it }, { "失敗：${it.javaClass.simpleName} ${it.message?.take(60)}" })
        }
        measure("描述查詢：2 字詞") {
            runCatching { repo.searchByQuery(ParsedQuery(keywords = listOf("丁七")), null, null, 50).items.size }
                .fold({ it }, { "失敗：${it.javaClass.simpleName} ${it.message?.take(60)}" })
        }
        measure("描述查詢：地點＋標籤＋3字詞") {
            runCatching {
                repo.searchByQuery(ParsedQuery(places = listOf("宜蘭2"), tags = listOf("標籤1"), keywords = listOf("一丁丂")), null, null, 50).items.size
            }.fold({ it }, { "失敗：${it.javaClass.simpleName} ${it.message?.take(60)}" })
        }

        log("===== 統計表（16A 的觸發器維護，16B 會改讀這裡）=====")
        measure("統計表：某月標籤") { db.rows("SELECT kind, ref_id, cnt FROM shot_stat WHERE month = '2025-03' AND kind != 0 AND cnt > 0 ORDER BY cnt DESC") }
        measure("統計表：月份選單") { db.rows("SELECT month, cnt FROM shot_stat WHERE kind = 0 AND ref_id = 0 AND cnt > 0 ORDER BY month DESC") }
        measure("統計表：候選清單（全部時間）") { db.rows("SELECT kind, ref_id FROM shot_stat_total WHERE kind != 0 AND cnt > 0 ORDER BY cnt DESC LIMIT 31") }
        measure("統計表：候選清單（2020-01 以前，月加總）") {
            db.rows("SELECT kind, ref_id, SUM(cnt) n FROM shot_stat WHERE month < '2020-01' AND kind != 0 GROUP BY kind, ref_id HAVING n > 0 ORDER BY n DESC LIMIT 31")
        }
        measure("統計表：候選清單（2020-01 以前，總數減掉之後）") {
            db.rows(
                """
                SELECT t.kind, t.ref_id, t.cnt - COALESCE(r.n, 0) AS n FROM shot_stat_total t
                LEFT JOIN (SELECT kind, ref_id, SUM(cnt) n FROM shot_stat WHERE month >= '2020-01' AND kind != 0 GROUP BY kind, ref_id) r
                  ON r.kind = t.kind AND r.ref_id = t.ref_id
                WHERE t.kind != 0 AND t.cnt - COALESCE(r.n, 0) > 0 ORDER BY n DESC LIMIT 31
                """
            )
        }
        log("===== 寫入成本（觸發器開著）=====")
        measure("取圖 100 張（每張 0～4 個標籤）然後刪整支", repeat = 2) {
            val picks = (0 until 100).map { i ->
                NewShot(i.toDouble(), "storyboard", 9_000_000 + i, 3, "2025-03-15", "宜蘭2", null, null, listOf("標籤${1 + i % 7}", "標籤${100 + i % 3}"))
            }
            val ms = timed { repo.commitPicks(VideoEntity("bench-v", "t", "c", "2025-03-15T00:00:00Z", 600, "public", null, 0), picks) }
            repo.deleteVideo("bench-v")
            "只算入庫 %.1f ms".format(ms)
        }
        val firstId = db.text("SELECT MIN(id) FROM shot")
        measure("編輯一張：改地點＋改到別的月份", repeat = 2) {
            db.write(rollback = true) { exec("UPDATE shot SET place_id = 3, event_date = '2001-01-01' WHERE id = $firstId") }
        }
        val bigTag = db.text("SELECT id FROM tag WHERE name = '標籤1'")
        val to = db.text("SELECT id FROM tag WHERE name = '標籤2'")
        measure("合併大標籤（標籤1→標籤2）", repeat = 1) {
            db.write(rollback = true) {
                exec("UPDATE OR IGNORE shot_tag SET tag_id = $to WHERE tag_id = $bigTag")
                exec("DELETE FROM tag WHERE id = $bigTag")
            }
            "已還原"
        }
        measure("合併地點（宜蘭2 的 10 萬張 → 地點0）", repeat = 1) {
            db.write(rollback = true) {
                exec("UPDATE shot SET place_id = 2 WHERE place_id = 1")
                exec("DELETE FROM place WHERE id = 1")
            }
            "已還原"
        }
        measure("地點改名（宜蘭2→宜蘭）") {
            db.write(rollback = true) { exec("UPDATE place SET name = '宜蘭' WHERE id = 1") }
        }
        measure("統計正確性抽查（總數＝實際張數）") {
            db.text("SELECT (SELECT cnt FROM shot_stat_total WHERE kind = 0) = (SELECT COUNT(*) FROM shot)")
        }
        db.close()
        log("===== 完成 =====")
    }

    // ---------- 造資料 ----------

    /**
     * 造資料直接用 driver 的連線：Room 的 `usePrepared` 區塊不能再呼叫 suspend 函式，兩個語句沒辦法巢狀。
     * schema 由前面那次 Room 開檔建好（含 FTS 表與觸發器）。
     */
    private fun generate() {
        val rnd = Random(1)
        val chars = 3000
        val cdf = DoubleArray(chars).also { a -> var s = 0.0; for (i in 0 until chars) { s += 1.0 / (i + 1); a[i] = s } }
        fun zipfChar(): Char {
            val x = rnd.nextDouble() * cdf.last()
            var lo = 0; var hi = chars - 1
            while (lo < hi) { val mid = (lo + hi) / 2; if (cdf[mid] < x) lo = mid + 1 else hi = mid }
            return (0x4E00 + lo).toChar()
        }
        val kinds = listOf("person", "pet", "topic", "other")
        val conn = BundledSQLiteDriver().open(dbFile.path)
        try {
            conn.execSQL("PRAGMA journal_mode = WAL")
            conn.execSQL("BEGIN IMMEDIATE")
            // 先拿掉插入觸發器，最後一次重建全文索引——比每列觸發快得多，結果相同
            conn.execSQL("DROP TRIGGER IF EXISTS shot_fts_after_insert")
            STATS_TRIGGER_NAMES.forEach { conn.execSQL("DROP TRIGGER IF EXISTS $it") }
            conn.prepare("INSERT INTO place(id, name, aliases) VALUES (?, ?, '[]')").use { st ->
                st.bindLong(1, 1); st.bindText(2, "宜蘭2"); st.step(); st.reset()
                for (p in 0 until 2000) { st.bindLong(1, 2L + p); st.bindText(2, "地點$p"); st.step(); st.reset() }
            }
            conn.prepare("INSERT INTO video VALUES (?, 't', 'c', '2020-01-01T00:00:00Z', 600, 'public', NULL, 0)").use { st ->
                for (v in 0 until 50_000) { st.bindText(1, "v$v"); st.step(); st.reset() }
            }
            conn.prepare("INSERT INTO tag(id, name, kind, aliases) VALUES (?, ?, ?, '[]')").use { st ->
                for (t in 1..500) { st.bindLong(1, t.toLong()); st.bindText(2, "標籤$t"); st.bindText(3, kinds[t % 4]); st.step(); st.reset() }
            }
            conn.execSQL("COMMIT")
            val base = java.time.LocalDate.of(2006, 1, 1)
            val st = conn.prepare(
                "INSERT INTO shot(id, video_id, at_sec, source, frame_index, sb_level, event_date, place_id, description, created_at) " +
                    "VALUES (?, ?, 1.0, 'storyboard', ?, 3, ?, ?, ?, 0)"
            )
            val tg = conn.prepare("INSERT OR IGNORE INTO shot_tag(shot_id, tag_id, source) VALUES (?, ?, 'human')")
            var id = 0L
            while (id < shots) {
                conn.execSQL("BEGIN IMMEDIATE")
                repeat(minOf(100_000L, shots - id).toInt()) {
                    id++
                    st.bindLong(1, id)
                    st.bindText(2, "v${rnd.nextInt(50_000)}")
                    st.bindLong(3, id)
                    st.bindText(4, base.plusDays(rnd.nextInt(7300).toLong()).toString())
                    when {
                        id % 10 == 0L -> st.bindLong(5, 1)
                        id % 4 == 1L -> st.bindNull(5)
                        else -> st.bindLong(5, 2L + rnd.nextInt(2000))
                    }
                    st.bindText(6, String(CharArray(20) { zipfChar() }))
                    st.step(); st.reset()
                    // 每張 0～4 個標籤（平均 2），偏斜分布：少數標籤很大、多數很小
                    repeat(rnd.nextInt(5)) {
                        val r = rnd.nextDouble()
                        tg.bindLong(1, id)
                        tg.bindLong(2, (500 * r * r * r).toLong() + 1)
                        tg.step(); tg.reset()
                    }
                }
                conn.execSQL("COMMIT")
                log("  已造 %,d 張".format(id))
            }
            st.close(); tg.close()
            conn.execSQL("BEGIN IMMEDIATE")
            conn.execSQL("INSERT INTO shot_fts(shot_fts) VALUES ('rebuild')")
            STATS_REBUILD_SQL.forEach { conn.execSQL(it) }
            STATS_SETUP_SQL.forEach { conn.execSQL(it) }
            conn.execSQL(FTS_SETUP_SQL[1])
            conn.execSQL("COMMIT")
            conn.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { it.step() }
        } finally {
            conn.close()
        }
    }

    private fun deleteDb() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    // ---------- 小工具 ----------

    private suspend fun LibraryDatabase.write(rollback: Boolean = false, block: suspend Transactor.() -> Unit) {
        useWriterConnection { t ->
            t.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                t.block()
                if (rollback) rollback(Unit)
            }
        }
    }

    private suspend fun Transactor.exec(sql: String) = usePrepared(sql) { it.step() }

    private suspend fun LibraryDatabase.exec(sql: String) = useWriterConnection { it.usePrepared(sql) { st -> st.step() } }

    private suspend fun LibraryDatabase.text(sql: String): String =
        useReaderConnection { it.usePrepared(sql) { st -> st.step(); st.getText(0) } }

    private suspend fun LibraryDatabase.rows(sql: String): String =
        useReaderConnection { it.usePrepared(sql) { st -> var n = 0; while (st.step()) n++; "$n 列" } }

    private inline fun timed(block: () -> Unit): Double {
        val t0 = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
    }

    /** 第一次（資料還沒進 SQLite 快取）＋之後幾次的中位數。 */
    private suspend fun measure(name: String, repeat: Int = 3, block: suspend () -> Any?) {
        var result: Any? = null
        val first = timedSuspend { result = block() }
        val rest = (1..repeat).map { timedSuspend { block() } }.sorted()
        val after = if (rest.isEmpty()) "" else "  之後 %8.1f ms".format(rest[rest.size / 2])
        log("%-24s 第一次 %8.1f ms%s  （%s）".format(name, first, after, result))
    }

    private suspend fun timedSuspend(block: suspend () -> Unit): Double {
        val t0 = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
    }

    private fun log(msg: String) { Log.i("VsBench", msg) }
}
