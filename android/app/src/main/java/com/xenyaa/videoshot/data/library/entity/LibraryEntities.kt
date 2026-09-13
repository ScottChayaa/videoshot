package com.xenyaa.videoshot.data.library.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 被取過圖的 YouTube 影片（不一定屬於使用者）。欄位對應規格第四節 library.db 的 video。
 * publishedAt 是 watch page 的 ISO 8601 原字串；addedAt 是 Unix 秒。
 */
@Entity(tableName = "video")
data class VideoEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "channel_title") val channelTitle: String,
    @ColumnInfo(name = "published_at") val publishedAt: String,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "privacy") val privacy: String,
    @ColumnInfo(name = "sb_spec") val sbSpec: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * 使用者從影片挑出的單一畫面 —— 本系統的第一級公民（規格第四節）。
 * atSec 是唯一的時間軸欄位；eventDate 是首頁年月分組的依據，預設取 video.publishedAt 的日期部分。
 * **不存任何檔案路徑**：縮圖由 (videoId, sbLevel, frameIndex) 推導。
 */
@Entity(
    tableName = "shot",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["event_date", "id"]),
        Index(value = ["video_id"]),
        Index(value = ["place"]),
        Index(value = ["video_id", "frame_index"], unique = true),
    ],
)
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "at_sec") val atSec: Double,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "frame_index") val frameIndex: Int?,
    @ColumnInfo(name = "sb_level") val sbLevel: Int?,
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "place") val place: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "ai_transcript") val aiTranscript: String?,
    @ColumnInfo(name = "ai_visual_desc") val aiVisualDesc: String?,
    @ColumnInfo(name = "ai_raw") val aiRaw: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 手動補圖的圖片本體。獨立成表，讓 shot 每列保持小，首頁掃描不被 6 KB 的 BLOB 拖慢。 */
@Entity(
    tableName = "shot_image",
    foreignKeys = [
        ForeignKey(
            entity = ShotEntity::class,
            parentColumns = ["id"],
            childColumns = ["shot_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ShotImageEntity(
    @PrimaryKey @ColumnInfo(name = "shot_id") val shotId: Long,
    @ColumnInfo(name = "webp", typeAffinity = ColumnInfo.BLOB) val webp: ByteArray,
) {
    // ByteArray 的預設 equals 比參考，資料類別的語意會出錯 —— 覆寫成比內容
    override fun equals(other: Any?): Boolean =
        this === other || (other is ShotImageEntity && shotId == other.shotId && webp.contentEquals(other.webp))

    override fun hashCode(): Int = 31 * shotId.hashCode() + webp.contentHashCode()
}

/** 標籤與暱稱（人／動物／主題）。地點是 shot.place，不在這裡。 */
@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    /** 別名的 JSON 陣列字串，如 ["我家的貓","橘貓"]；檢索時視同 name */
    @ColumnInfo(name = "aliases") val aliases: String,
)

@Entity(
    tableName = "shot_tag",
    primaryKeys = ["shot_id", "tag_id"],
    foreignKeys = [
        ForeignKey(ShotEntity::class, ["id"], ["shot_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"]), Index(value = ["shot_id"])],
)
data class ShotTagEntity(
    @ColumnInfo(name = "shot_id") val shotId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "source") val source: String,
)

/** 使用者自訂的分類資料夾（樹狀；深度上限 5、同層不重名都在 repo 擋）。 */
@Entity(
    tableName = "folder",
    foreignKeys = [
        ForeignKey(FolderEntity::class, ["id"], ["parent_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["parent_id"])],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "shot_folder",
    primaryKeys = ["shot_id", "folder_id"],
    foreignKeys = [
        ForeignKey(ShotEntity::class, ["id"], ["shot_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(FolderEntity::class, ["id"], ["folder_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["folder_id", "added_at"]), Index(value = ["shot_id"])],
)
data class ShotFolderEntity(
    @ColumnInfo(name = "shot_id") val shotId: Long,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
