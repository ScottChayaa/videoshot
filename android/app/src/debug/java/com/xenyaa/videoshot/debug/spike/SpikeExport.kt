package com.xenyaa.videoshot.debug.spike

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xenyaa.videoshot.VideoshotApp
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.ui.clip.MimeFirstExtractorsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.ceil

/*
 * 階段 20 動工前的一次性實驗（不合併）：背景導出能不能照常跑、app 縮小或鎖屏時慢多少。
 * adb shell am broadcast -a com.xenyaa.videoshot.debug.SPIKE_EXPORT -n com.xenyaa.videoshot/.debug.spike.SpikeExportReceiver --es tag A --ei seconds 120
 * adb shell am broadcast -a com.xenyaa.videoshot.debug.SPIKE_CANCEL -n com.xenyaa.videoshot/.debug.spike.SpikeExportReceiver
 * 用手機上目前的剪輯草稿（不改它），時間軸重複接到 seconds 秒；成品在 files/spike/，記錄看 logcat 的 VsSpike。
 */

private const val TAG = "VsSpike"
private const val CHANNEL = "spike_export"
private const val NOTIFY_ID = 4201
private const val WORK = "spike-export"

class SpikeExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wm = WorkManager.getInstance(context)
        if (intent.action == "com.xenyaa.videoshot.debug.SPIKE_CANCEL") {
            wm.cancelUniqueWork(WORK)
            Log.i(TAG, "cancel requested")
            return
        }
        val data = Data.Builder()
            .putString("tag", intent.getStringExtra("tag") ?: "x")
            .putInt("seconds", intent.getIntExtra("seconds", 120))
            .putString("bg", intent.getStringExtra("bg") ?: "blur")
            .putInt("fps", intent.getIntExtra("fps", 0))
            .putInt("height", intent.getIntExtra("height", 1080))
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SpikeExportWorker>().setInputData(data).build())
        Log.i(TAG, "enqueued $data")
    }
}

@OptIn(UnstableApi::class)
class SpikeExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val tag = inputData.getString("tag") ?: "x"
        val seconds = inputData.getInt("seconds", 120)
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "實驗：導出", NotificationManager.IMPORTANCE_LOW))
        try {
            setForeground(info(app, "準備中", 0))
        } catch (e: Exception) {
            Log.e(TAG, "setForeground failed", e)
            return Result.failure()
        }
        val draft = (app as VideoshotApp).container.clipDeps.cacheRepo.currentClipDraft()
        if (draft == null) {
            Log.e(TAG, "no draft")
            return Result.failure()
        }
        val segs = draft.timeline.segments
        val times = ceil(seconds * 1000.0 / draft.timeline.durationMs).toInt().coerceAtLeast(1)
        val long = draft.copy(history = EditHistory(Timeline(List(times) { segs }.flatten())))
        val bg = inputData.getString("bg") ?: "blur"
        val fps = inputData.getInt("fps", 0)
        val height = inputData.getInt("height", 1080)
        val composition = spikeComposition(long, bg, fps, height)
        val out = File(app.filesDir, "spike/out-$tag.mp4").apply { parentFile?.mkdirs(); delete() }
        Log.i(TAG, "options tag=$tag bg=$bg fps=$fps height=$height tempStart=${batteryTemp(app)}")
        Log.i(TAG, "start tag=$tag timelineMs=${long.timeline.durationMs} segments=${long.timeline.segments.size} orientation=${draft.orientation}")
        return withContext(Dispatchers.Main) { export(app, nm, composition, out, tag) }
    }

    private suspend fun export(app: Context, nm: NotificationManager, composition: Composition, out: File, tag: String): Result =
        suspendCancellableCoroutine { cont ->
            val started = SystemClock.elapsedRealtime()
            val assets = DefaultAssetLoaderFactory(
                app,
                DefaultDecoderFactory.Builder(app).build(),
                Clock.DEFAULT,
                DefaultMediaSourceFactory(app, MimeFirstExtractorsFactory { app.contentResolver.getType(it) }),
                DataSourceBitmapLoader.Builder(app).build(),
            )
            lateinit var transformer: Transformer
            val poll = kotlinx.coroutines.MainScope().launch {
                val holder = ProgressHolder()
                val pm = app.getSystemService(PowerManager::class.java)
                while (true) {
                    delay(1000)
                    transformer.getProgress(holder)
                    val state = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
                    Log.i(TAG, "tag=$tag t=${SystemClock.elapsedRealtime() - started} progress=${holder.progress} screenOn=${pm.isInteractive} importance=${state.importance}")
                    nm.notify(NOTIFY_ID, notification(app, "導出中 ${holder.progress}%", holder.progress))
                }
            }
            transformer = Transformer.Builder(app)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setAssetLoaderFactory(assets)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        poll.cancel()
                        Log.i(TAG, "done tag=$tag tempEnd=${batteryTemp(app)} wallMs=${SystemClock.elapsedRealtime() - started} mediaMs=${exportResult.durationMs} bytes=${out.length()} size=${exportResult.width}x${exportResult.height} enc=${exportResult.videoEncoderName}")
                        if (cont.isActive) cont.resume(Result.success())
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        poll.cancel()
                        Log.e(TAG, "error tag=$tag wallMs=${SystemClock.elapsedRealtime() - started}", exportException)
                        if (cont.isActive) cont.resume(Result.failure())
                    }
                })
                .build()
            transformer.start(composition, out.absolutePath)
            cont.invokeOnCancellation {
                poll.cancel()
                kotlinx.coroutines.MainScope().launch { transformer.cancel() }
                Log.i(TAG, "cancelled tag=$tag wallMs=${SystemClock.elapsedRealtime() - started}")
            }
        }

    private fun notification(app: Context, text: String, progress: Int) =
        NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("實驗：背景導出")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

    private fun info(app: Context, text: String, progress: Int): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFY_ID, notification(app, text, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFY_ID, notification(app, text, progress))
        }
}

/** 電池溫度（0.1°C）。 */
private fun batteryTemp(app: Context): Int =
    app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

/** 實驗用的合成：bg＝blur（正式做法）或 black（黑邊）；fps＞0 時影片降到這個幀率；height＝輸出的短邊（1080 或 720）。 */
@OptIn(UnstableApi::class)
private fun spikeComposition(draft: com.xenyaa.videoshot.core.timeline.ClipDraft, bg: String, fps: Int, height: Int): Composition {
    val k = height / 1080f
    val outW = (draft.orientation.width * k).toInt() / 2 * 2
    val outH = (draft.orientation.height * k).toInt() / 2 * 2
    val items = com.xenyaa.videoshot.ui.clip.usableSegments(draft).map { (seg, source) ->
        val uri = android.net.Uri.parse(source.uri)
        val builder = when (source.kind) {
            com.xenyaa.videoshot.core.timeline.SourceKind.VIDEO -> androidx.media3.transformer.EditedMediaItem.Builder(
                androidx.media3.common.MediaItem.Builder().setUri(uri).setClippingConfiguration(
                    androidx.media3.common.MediaItem.ClippingConfiguration.Builder().setStartPositionMs(seg.startMs).setEndPositionMs(seg.endMs).build(),
                ).build(),
            ).also { b -> source.durationMs?.takeIf { it > 0 }?.let { b.setDurationUs(it * 1000) } }
            com.xenyaa.videoshot.core.timeline.SourceKind.PHOTO -> androidx.media3.transformer.EditedMediaItem.Builder(
                androidx.media3.common.MediaItem.fromUri(uri).buildUpon().setImageDurationMs(seg.lengthMs).build(),
            ).setDurationUs(seg.lengthMs * 1000).setFrameRate(30)
        }
        val scale = com.xenyaa.videoshot.ui.clip.fitScale(source.width, source.height, source.rotation, outW, outH)
        val effects = mutableListOf<androidx.media3.common.Effect>()
        if (fps > 0 && source.kind == com.xenyaa.videoshot.core.timeline.SourceKind.VIDEO) {
            effects += androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat())
        }
        if (bg == "blur" && com.xenyaa.videoshot.ui.clip.needsBlurBackground(scale)) {
            val (sw, sh) = com.xenyaa.videoshot.ui.clip.sharpSize(scale, outW, outH)
            effects += androidx.media3.effect.Presentation.createForWidthAndHeight(sw, sh, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            effects += com.xenyaa.videoshot.ui.clip.BlurBackgroundEffect(outW, outH)
        } else {
            effects += androidx.media3.effect.Presentation.createForWidthAndHeight(outW, outH, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT)
        }
        builder.setEffects(androidx.media3.transformer.Effects(emptyList(), effects)).build()
    }
    return Composition.Builder(androidx.media3.transformer.EditedMediaItemSequence.withAudioAndVideoFrom(items)).build()
}
