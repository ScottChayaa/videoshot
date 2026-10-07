package com.xenyaa.videoshot.data.library.dao

/**
 * 讀圖查詢共用的欄位清單，對應 [ShotRowProjection]。**`shot` 一律別名 `s`、`place` 一律別名 `p`，
 * 而且要 `LEFT JOIN place p ON p.id = s.place_id`**——地點名稱只存在 `place` 表（規格第四節）。
 * LEFT JOIN 讓 `shot` 維持外層迴圈，`ORDER BY s.event_date DESC, s.id DESC` 照樣走 `index_shot_event_date_id`。
 */
internal const val SHOT_ROW_COLUMNS =
    "s.id AS id, s.video_id AS video_id, s.at_sec AS at_sec, s.source AS source, " +
        "s.frame_index AS frame_index, s.sb_level AS sb_level, s.event_date AS event_date, " +
        "p.name AS place, s.description AS description"

internal const val JOIN_PLACE = "LEFT JOIN place p ON p.id = s.place_id"
