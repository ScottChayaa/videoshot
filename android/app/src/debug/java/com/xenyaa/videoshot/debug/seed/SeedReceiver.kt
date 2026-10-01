package com.xenyaa.videoshot.debug.seed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xenyaa.videoshot.VideoshotApp
import com.xenyaa.videoshot.backfill.scheduleBackfill
import com.xenyaa.videoshot.backfill.scheduleBackfillScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 開發測試版專用：匯入原型假資料，**會取代現有圖庫**（計畫 15A Task 10）。
 * `adb shell am broadcast -a com.xenyaa.videoshot.debug.SEED -n com.xenyaa.videoshot/.debug.seed.SeedReceiver`
 *
 * 規格要求正式程式不寫 log；這支只在開發測試版，而且對照腳本靠 `VsSeed` 這行 log 判斷匯入完成，所以例外。
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = app.assets.open("seed/mock-seed.json").bufferedReader().use { it.readText() }
                val plan = SeedPlanner.plan(Json.decodeFromString<SeedFile>(text), System.currentTimeMillis())
                val container = (app as VideoshotApp).container
                val r = SeedImporter(container.libraryRepo, container.shotDeleter::deleteVideo).import(plan)
                // 假資料要立刻看到縮圖，不等 Wi-Fi
                scheduleBackfillScan(app)
                scheduleBackfill(app, allowMobileData = true, replace = true)
                Log.i("VsSeed", "done shots=${r.shots} folders=${r.folders}")
            } catch (e: Exception) {
                Log.e("VsSeed", "failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
