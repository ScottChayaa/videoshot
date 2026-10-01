package com.xenyaa.videoshot.debug.seed

import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot

/** 假資料 → 寫入計畫。純函式，不碰 DB（寫入在 [SeedImporter]）。 */
object SeedPlanner {
    fun plan(file: SeedFile, now: Long): SeedPlan {
        val byVideo = file.shots.groupBy { it.videoId }
        val batches = file.videos.mapNotNull { v ->
            val shots = byVideo[v.id] ?: return@mapNotNull null
            val level = v.sbSpec?.let(Storyboard::parse)?.let { Storyboard.pickLevel(it) }
            SeedBatch(
                video = VideoEntity(
                    id = v.id, title = v.title, channelTitle = v.channelTitle,
                    publishedAt = shots.minOf { it.eventDate }, durationSec = v.durationSec,
                    privacy = "public", sbSpec = v.sbSpec, addedAt = now,
                ),
                shotKeys = shots.map { it.key },
                picks = shots.map { s ->
                    NewShot(
                        atSec = s.atSec.toDouble(), source = "storyboard",
                        frameIndex = level?.let { Storyboard.frameIndexAt(it, s.atSec.toDouble()) },
                        sbLevel = level?.level,
                        eventDate = s.eventDate, place = s.place, description = s.description,
                        webp = null, tagNames = s.tags.map { it.name },
                    )
                },
            )
        }
        val kinds = linkedMapOf<String, String>()
        for (s in file.shots) for (t in s.tags) kinds.putIfAbsent(t.name, t.kind)
        return SeedPlan(batches, kinds, file.folders)
    }
}
