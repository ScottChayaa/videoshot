package com.xenyaa.videoshot.debug.seed

import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
import kotlinx.serialization.Serializable

// ── 匯入檔（assets/seed/mock-seed.json，由 `pnpm seed` 從原型假資料產生）──

@Serializable
data class SeedFile(val videos: List<SeedVideo>, val shots: List<SeedShot>, val folders: List<SeedFolder>)

@Serializable
data class SeedVideo(val id: String, val title: String, val channelTitle: String, val durationSec: Int, val sbSpec: String?)

@Serializable
data class SeedTag(val name: String, val kind: String)

@Serializable
data class SeedShot(
    val key: String,
    val videoId: String,
    val atSec: Int,
    val eventDate: String,
    val place: String?,
    val description: String,
    val tags: List<SeedTag>,
)

@Serializable
data class SeedFolder(val key: String, val name: String, val shotKeys: List<String>)

// ── 寫入計畫（[SeedPlanner] 的輸出）──

/** 一支影片與它底下要寫入的格子；[shotKeys] 與 [picks] 一一對應，供資料夾用 key 找回 shot。 */
data class SeedBatch(val video: VideoEntity, val shotKeys: List<String>, val picks: List<NewShot>)

data class SeedPlan(val batches: List<SeedBatch>, val tagKinds: Map<String, String>, val folders: List<SeedFolder>)
