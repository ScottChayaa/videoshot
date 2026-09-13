package com.xenyaa.videoshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.di.AppContainer
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import java.io.File

/**
 * ⚠️ 臨時畫面：階段 2 的資料層冒煙測試。
 *
 * 真正的 App 外殼與首頁是**階段 7**，屆時整個 MainActivity 會被取代。
 * 這裡的用途只有一個：在實機上證明資料層真的活著 —— 檔案建在 filesDir、交易有效、
 * keyset 分頁不重不漏、刪除有連動。全部只透過 LibraryRepo，不碰 DAO（規格第三節模組邊界第 1 條）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoshotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SmokeScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun SmokeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(listOf("正在跑資料層…")) }

    LaunchedEffect(Unit) {
        val app = context.applicationContext as VideoshotApp
        lines = runSmoke(app.container, app.filesDir)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("videoshot · 資料層冒煙測試", style = MaterialTheme.typography.titleMedium)
        Text(
            "臨時畫面，階段 7 做真正的首頁時會整個換掉",
            style = MaterialTheme.typography.bodySmall,
        )
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** 每次開 app 都從乾淨狀態重跑一遍，結果才是可重現的。 */
private suspend fun runSmoke(container: AppContainer, filesDir: File): List<String> {
    val out = mutableListOf<String>()
    val repo: LibraryRepo = container.libraryRepo

    fun size(name: String): String {
        val f = File(filesDir, name)
        return if (f.exists()) "${f.length() / 1024} KB" else "（還沒建立）"
    }

    return try {
        // 先清掉上一輪，讓每次啟動的結果一致
        repo.deleteVideo("smoke-a")
        repo.deleteVideo("smoke-b")

        out += ""
        out += "── 資料庫檔案（filesDir，不是 cacheDir）──"
        out += "  library.db  ${size("library.db")}"
        out += "  cache.db    ${size("cache.db")}"

        val videoA = VideoEntity(
            "smoke-a", "潛水 Vlog", "OK哥", "2026-08-20T20:00:06-07:00", 1739, "public", null,
            System.currentTimeMillis() / 1000,
        )
        val videoB = VideoEntity(
            "smoke-b", "埃及市場", "烙賽瑞奇", "2026-03-01T09:00:00Z", 2350, "public", null,
            System.currentTimeMillis() / 1000,
        )
        fun pick(i: Int, date: String, place: String?, desc: String?) = NewShot(
            atSec = i.toDouble(), source = "storyboard", frameIndex = i, sbLevel = 3,
            eventDate = date, place = place, description = desc, webp = null,
        )

        val idsA = repo.commitPicks(
            videoA,
            listOf(
                pick(0, "2026-08-21", "加利福尼亞灣", "鬼頭刀"),
                pick(1, "2026-08-21", "加利福尼亞灣", null),
                pick(2, "2026-08-21", null, "花園鰻"),
            ),
        )
        val idsB = repo.commitPicks(
            videoB,
            listOf(pick(0, "2026-03-02", "開羅", "傳統市場"), pick(1, "2026-03-02", "開羅", null)),
        )

        out += ""
        out += "── 入庫（一個交易寫兩支影片、5 張圖）──"
        out += "  smoke-a → ${idsA.size} 張，id ${idsA.first()}～${idsA.last()}"
        out += "  smoke-b → ${idsB.size} 張，id ${idsB.first()}～${idsB.last()}"
        out += "  library.db 現在 ${size("library.db")}"

        val dup = runCatching {
            repo.commitPicks(videoA, listOf(pick(9, "2026-08-21", null, null), pick(0, "2026-08-21", null, null)))
        }
        out += ""
        out += "── 去重（同一格不能收藏兩次）──"
        out += "  重複入庫被擋：${dup.isFailure}（整批回滾，第 9 格也沒進去）"
        out += "  smoke-a 仍然是 ${repo.shotsOfVideo("smoke-a").size} 張"

        val page1 = repo.homeFeed(after = null, limit = 3)
        val page2 = repo.homeFeed(after = page1.next, limit = 3)
        out += ""
        out += "── 首頁 keyset 分頁（由新到舊）──"
        page1.items.forEach { out += "  第1頁 ${it.eventDate}  #${it.id}  ${it.place ?: "—"}" }
        page2.items.forEach { out += "  第2頁 ${it.eventDate}  #${it.id}  ${it.place ?: "—"}" }
        out += "  下一頁游標：${page2.next ?: "（到底了）"}"

        out += ""
        out += "── 月份統計（GROUP BY 即時算）──"
        repo.monthCounts().forEach { out += "  ${it.month}  ${it.count} 張" }

        repo.patchShots(listOf(idsA[1]), ShotPatch(null, "宜蘭", null, null))
        val patched = repo.shotById(idsA[1])!!
        out += ""
        out += "── 批次編輯（只套用動過的欄位）──"
        out += "  #${patched.id} place → ${patched.place}"
        out += "  #${patched.id} description 沒動過，仍是 ${patched.description ?: "null"}"

        repo.deleteShot(idsB[0])
        repo.deleteShot(idsB[1])
        out += ""
        out += "── 刪除連動（最後一張刪掉時順帶刪影片列）──"
        out += "  smoke-b 刪光後剩 ${repo.shotsOfVideo("smoke-b").size} 張"
        out += "  首頁現在共 ${repo.homeFeed(null, 100).items.size} 張"

        // 資料夾不像影片有 deleteVideo 可以清（repo 目前沒有刪資料夾），
        // 名稱帶上時間戳，才不會第二次開 app 就撞到上一輪留下的同名資料夾
        val folderName = "冒煙測試-${System.currentTimeMillis() % 100000}"
        val folder = repo.createFolder(null, folderName)
        val dupFolder = runCatching { repo.createFolder(null, folderName) }
        val deep = runCatching {
            var p: Long? = null
            repeat(6) { i -> p = repo.createFolder(p, "$folderName-L${i + 1}") }
        }
        out += ""
        out += "── 資料夾規則 ──"
        out += "  建立 #$folder（$folderName）"
        out += "  同層重名被擋：${dupFolder.isFailure}"
        out += "  超過 5 層被擋：${deep.isFailure}"

        // cache.db（不備份的那一個）
        val cache = container.cacheRepo
        cache.clearAll()
        cache.putThumbStates(
            listOf(
                ThumbStateEntity("smoke-a", 3, 0, "missing", 0, 0L, null),
                ThumbStateEntity("smoke-a", 3, 1, "ok", 0, 0L, null),
                ThumbStateEntity("smoke-a", 3, 2, "lost", 3, 0L, "retries_exhausted"),
            )
        )
        cache.saveDraft(DraftEntity("smoke-a", 2, "{\"picked\":[0,1,2]}", System.currentTimeMillis() / 1000))
        out += ""
        out += "── cache.db（裝置本地，不進備份）──"
        out += "  cache.db    ${size("cache.db")}"
        out += "  待回填的格子：${cache.thumbsDueForRetry(System.currentTimeMillis() / 1000, 10).size} 個"
        out += "  第 2 格狀態：${cache.thumbState("smoke-a", 3, 2)?.state}（${cache.thumbState("smoke-a", 3, 2)?.lostReason}）"
        out += "  草稿：${cache.currentDraft()?.videoId} 停在第 ${cache.currentDraft()?.step} 步"

        out += ""
        out += "✅ 資料層在這台裝置上正常運作"
        out
    } catch (e: Exception) {
        out += ""
        out += "❌ ${e::class.simpleName}: ${e.message}"
        out
    }
}
