package com.xenyaa.videoshot.data.library

/**
 * 統計表（規格第四節）：每月 × 地點／標籤 × 張數（`shot_stat`），以及每個地點／標籤的總數（`shot_stat_total`）。
 * 首頁月份標籤、月份選單、查詢頁候選清單、標籤管理的張數都讀這裡（階段 16B），
 * 100 萬張時不必每次把整個圖庫數一遍（實機量測：候選清單 9.2 秒 → 4 毫秒）。
 *
 * **由觸發器維護，app 的程式碼不必記得更新**：圖或標籤關聯一有變動，資料庫在同一個交易裡自動調整，
 * 跟 `shot_fts` 同一個做法（Room 不管這兩張表，用原生 SQL 建）。觸發器存在資料庫檔案裡，
 * 其他平台打開同一個檔案也一樣生效（規格第四節「跨平台的資料契約」）。
 *
 * 規則：
 * - 加一用 UPSERT（沒有就建、有就加）；減一用 UPDATE（沒有那一列就什麼都不做，永遠不會產生負數列）。
 * - 減到 0 的列不刪，**讀取端一律加 `cnt > 0`**。
 * - 刪圖用 `BEFORE DELETE`：那時圖的標籤關聯還在，才查得到要扣哪些標籤；之後外鍵連動刪掉關聯時，
 *   `shot_stat_tag_delete` 的 `WHEN EXISTS` 會發現圖已經不在而跳過，不會重複扣。
 * - `shot_stat_shot_move`（換了月份）與 `shot_stat_shot_place`（同月只換地點）用 WHEN 互斥，同一次 UPDATE 只會有一個生效。
 * - 刪除標籤／地點時把它的統計列整批刪掉（`*_gone`）。
 */
object StatKind {
    const val ALL = 0
    const val PLACE = 1
    const val TAG = 2
}

private const val M_NEW = "substr(new.event_date, 1, 7)"
private const val M_OLD = "substr(old.event_date, 1, 7)"

/** 加一。`notNullRef` 為 true 時 ref 是 NULL 就整句不做（沒有地點的圖）。INSERT…SELECT 接 UPSERT 一定要有 WHERE（SQLite 的語法限制）。 */
private fun inc(month: String, kind: Int, ref: String, notNullRef: Boolean = false): String {
    val where = if (notNullRef) "WHERE $ref IS NOT NULL" else "WHERE true"
    return """
        INSERT INTO shot_stat (month, kind, ref_id, cnt) SELECT $month, $kind, $ref, 1 $where
            ON CONFLICT (month, kind, ref_id) DO UPDATE SET cnt = cnt + 1;
        INSERT INTO shot_stat_total (kind, ref_id, cnt) SELECT $kind, $ref, 1 $where
            ON CONFLICT (kind, ref_id) DO UPDATE SET cnt = cnt + 1;
    """
}

/** 減一。ref 是 NULL 時 `ref_id = NULL` 不成立，自然什麼都不做。 */
private fun dec(month: String, kind: Int, ref: String): String = """
        UPDATE shot_stat SET cnt = cnt - 1 WHERE month = $month AND kind = $kind AND ref_id = $ref;
        UPDATE shot_stat_total SET cnt = cnt - 1 WHERE kind = $kind AND ref_id = $ref;
"""

/** 這張圖的每個標籤在某月加一（只動每月明細；換月份時標籤總數不變）。 */
private fun incTagsOf(month: String, shotId: String): String = """
        INSERT INTO shot_stat (month, kind, ref_id, cnt) SELECT $month, ${StatKind.TAG}, tag_id, 1 FROM shot_tag WHERE shot_id = $shotId
            ON CONFLICT (month, kind, ref_id) DO UPDATE SET cnt = cnt + 1;
"""

private fun decTagsOf(month: String, shotId: String): String = """
        UPDATE shot_stat SET cnt = cnt - 1
            WHERE month = $month AND kind = ${StatKind.TAG} AND ref_id IN (SELECT tag_id FROM shot_tag WHERE shot_id = $shotId);
"""

private fun decTagTotalsOf(shotId: String): String = """
        UPDATE shot_stat_total SET cnt = cnt - 1
            WHERE kind = ${StatKind.TAG} AND ref_id IN (SELECT tag_id FROM shot_tag WHERE shot_id = $shotId);
"""

/** 一筆標籤關聯（`r` 是 `new` 或 `old`）加一／減一；月份從圖上查。 */
private fun incLink(r: String): String = """
        INSERT INTO shot_stat (month, kind, ref_id, cnt)
            SELECT substr(event_date, 1, 7), ${StatKind.TAG}, $r.tag_id, 1 FROM shot WHERE id = $r.shot_id
            ON CONFLICT (month, kind, ref_id) DO UPDATE SET cnt = cnt + 1;
        INSERT INTO shot_stat_total (kind, ref_id, cnt) SELECT ${StatKind.TAG}, $r.tag_id, 1 WHERE true
            ON CONFLICT (kind, ref_id) DO UPDATE SET cnt = cnt + 1;
"""

private fun decLink(r: String): String = """
        UPDATE shot_stat SET cnt = cnt - 1
            WHERE kind = ${StatKind.TAG} AND ref_id = $r.tag_id
              AND month = (SELECT substr(event_date, 1, 7) FROM shot WHERE id = $r.shot_id);
        UPDATE shot_stat_total SET cnt = cnt - 1 WHERE kind = ${StatKind.TAG} AND ref_id = $r.tag_id;
"""

private fun trigger(name: String, on: String, body: String) =
    "CREATE TRIGGER IF NOT EXISTS $name $on BEGIN\n$body\nEND"

private val ALL = StatKind.ALL
private val PLACE = StatKind.PLACE

/** 統計表、索引、觸發器。新安裝（`LibrarySchemaCallback.onCreate`）與 v1→v2 遷移共用。每個元素是一句完整的 SQL。 */
val STATS_SETUP_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS shot_stat (month TEXT NOT NULL, kind INTEGER NOT NULL, ref_id INTEGER NOT NULL, " +
        "cnt INTEGER NOT NULL, PRIMARY KEY (month, kind, ref_id)) WITHOUT ROWID",
    // 月份選單（kind=0 的每一個月）與「某個地點／標籤每個月幾張」用
    "CREATE INDEX IF NOT EXISTS shot_stat_by_ref ON shot_stat (kind, ref_id, month)",
    "CREATE TABLE IF NOT EXISTS shot_stat_total (kind INTEGER NOT NULL, ref_id INTEGER NOT NULL, " +
        "cnt INTEGER NOT NULL, PRIMARY KEY (kind, ref_id)) WITHOUT ROWID",
    trigger(
        "shot_stat_shot_insert", "AFTER INSERT ON shot",
        inc(M_NEW, ALL, "0") + inc(M_NEW, PLACE, "new.place_id", notNullRef = true),
    ),
    trigger(
        "shot_stat_shot_delete", "BEFORE DELETE ON shot",
        dec(M_OLD, ALL, "0") + dec(M_OLD, PLACE, "old.place_id") + decTagsOf(M_OLD, "old.id") + decTagTotalsOf("old.id"),
    ),
    trigger(
        "shot_stat_shot_move", "AFTER UPDATE OF event_date, place_id ON shot WHEN $M_OLD <> $M_NEW",
        dec(M_OLD, ALL, "0") + dec(M_OLD, PLACE, "old.place_id") + decTagsOf(M_OLD, "old.id") +
            inc(M_NEW, ALL, "0") + inc(M_NEW, PLACE, "new.place_id", notNullRef = true) + incTagsOf(M_NEW, "new.id"),
    ),
    trigger(
        "shot_stat_shot_place", "AFTER UPDATE OF place_id ON shot WHEN $M_OLD = $M_NEW AND old.place_id IS NOT new.place_id",
        dec(M_OLD, PLACE, "old.place_id") + inc(M_NEW, PLACE, "new.place_id", notNullRef = true),
    ),
    trigger("shot_stat_tag_insert", "AFTER INSERT ON shot_tag", incLink("new")),
    trigger(
        "shot_stat_tag_delete", "AFTER DELETE ON shot_tag WHEN EXISTS (SELECT 1 FROM shot WHERE id = old.shot_id)",
        decLink("old"),
    ),
    trigger("shot_stat_tag_update", "AFTER UPDATE OF shot_id, tag_id ON shot_tag", decLink("old") + incLink("new")),
    trigger(
        "shot_stat_tag_gone", "AFTER DELETE ON tag",
        "DELETE FROM shot_stat WHERE kind = ${StatKind.TAG} AND ref_id = old.id;\n" +
            "DELETE FROM shot_stat_total WHERE kind = ${StatKind.TAG} AND ref_id = old.id;",
    ),
    trigger(
        "shot_stat_place_gone", "AFTER DELETE ON place",
        "DELETE FROM shot_stat WHERE kind = ${StatKind.PLACE} AND ref_id = old.id;\n" +
            "DELETE FROM shot_stat_total WHERE kind = ${StatKind.PLACE} AND ref_id = old.id;",
    ),
)

/** 量測程式造大量資料時先拿掉觸發器、最後重算再建回來用。 */
val STATS_TRIGGER_NAMES: List<String> = listOf(
    "shot_stat_shot_insert", "shot_stat_shot_delete", "shot_stat_shot_move", "shot_stat_shot_place",
    "shot_stat_tag_insert", "shot_stat_tag_delete", "shot_stat_tag_update", "shot_stat_tag_gone", "shot_stat_place_gone",
)

/** 從圖資整張重算（v1→v2 遷移用；之後由觸發器維護，正常情況不需要再跑）。 */
val STATS_REBUILD_SQL: List<String> = listOf(
    "DELETE FROM shot_stat",
    "DELETE FROM shot_stat_total",
    "INSERT INTO shot_stat (month, kind, ref_id, cnt) " +
        "SELECT substr(event_date, 1, 7), ${StatKind.ALL}, 0, COUNT(*) FROM shot GROUP BY substr(event_date, 1, 7)",
    "INSERT INTO shot_stat (month, kind, ref_id, cnt) " +
        "SELECT substr(event_date, 1, 7), ${StatKind.PLACE}, place_id, COUNT(*) FROM shot WHERE place_id IS NOT NULL " +
        "GROUP BY substr(event_date, 1, 7), place_id",
    "INSERT INTO shot_stat (month, kind, ref_id, cnt) " +
        "SELECT substr(s.event_date, 1, 7), ${StatKind.TAG}, st.tag_id, COUNT(*) FROM shot_tag st JOIN shot s ON s.id = st.shot_id " +
        "GROUP BY substr(s.event_date, 1, 7), st.tag_id",
    "INSERT INTO shot_stat_total (kind, ref_id, cnt) SELECT kind, ref_id, SUM(cnt) FROM shot_stat GROUP BY kind, ref_id",
)
