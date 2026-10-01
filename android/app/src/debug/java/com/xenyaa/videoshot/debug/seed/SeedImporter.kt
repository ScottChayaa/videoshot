package com.xenyaa.videoshot.debug.seed

import com.xenyaa.videoshot.data.repo.LibraryRepo

data class SeedResult(val shots: Int, val folders: Int)

/**
 * 把 [SeedPlan] 寫進圖庫，**會取代現有圖庫**（計畫 15A Task 10）。
 * 所有寫入都走 [LibraryRepo]；刪影片走 [deleteVideo]（正式環境接 `ShotDeleter`，縮圖檔與快取一起清）。
 */
class SeedImporter(
    private val library: LibraryRepo,
    private val deleteVideo: suspend (String) -> Unit,
) {
    suspend fun import(plan: SeedPlan): SeedResult {
        // 1. 清空：影片逐支刪；資料夾只刪根層，子層跟著刪
        for (v in library.recentVideos(Int.MAX_VALUE)) deleteVideo(v.videoId)
        for (f in library.folderTree()) if (f.parentId == null) library.deleteFolder(f.id)

        // 2. 寫入；commitPicks 回傳的 id 依序對應 shotKeys
        val shotIds = HashMap<String, Long>()
        for (b in plan.batches) {
            val ids = library.commitPicks(b.video, b.picks)
            b.shotKeys.forEachIndexed { i, key -> shotIds[key] = ids[i] }
        }

        // 3. 標籤 kind：只改 kind，名稱與別名不變
        for (t in library.allTagsWithUsage()) {
            val kind = plan.tagKinds[t.name] ?: continue
            if (kind != t.kind) library.renameTag(t.id, t.name, kind, t.aliases)
        }

        // 4. 資料夾
        for (f in plan.folders) {
            val folderId = library.createFolder(null, f.name)
            for (key in f.shotKeys) shotIds[key]?.let { library.addShotToFolder(it, folderId) }
        }
        return SeedResult(shots = shotIds.size, folders = plan.folders.size)
    }
}
