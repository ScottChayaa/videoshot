package com.xenyaa.videoshot.edit

import android.content.Context
import android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
import android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
import androidx.annotation.OptIn
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
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.ui.clip.MimeFirstExtractorsFactory
import com.xenyaa.videoshot.ui.clip.clipComposition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EncoderUtil
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 輸出的幀率上限：影片高於這個幀率才降（60fps 的素材保留 60fps）。 */
private const val MAX_FRAME_RATE = 60f

/**
 * 影像位元率：20 Mbps，1080p60 也接近原片（Media3 預設依解析度估，1080p30 只有約 8 Mbps）。
 * 手機的編碼器給不到時 Media3 會自動退到它能給的最高值。
 */
private const val VIDEO_BITRATE = 20_000_000

/**
 * 固定位元率：變動位元率下 20 Mbps 只是上限，實機量到只給 13～15 Mbps；固定位元率才穩定在 20 Mbps（2026-11-12）。
 * 手機的 H.264 編碼器都不支援固定位元率時改用變動位元率（Media3 不會自己退回）。
 */
@OptIn(UnstableApi::class)
private fun videoBitrateMode(): Int {
    val cbr = EncoderUtil.getSupportedEncoders(MimeTypes.VIDEO_H264)
        .any { EncoderUtil.isBitrateModeSupported(it, MimeTypes.VIDEO_H264, BITRATE_MODE_CBR) }
    return if (cbr) BITRATE_MODE_CBR else BITRATE_MODE_VBR
}

/** 輪詢輸出進度的間隔。 */
private const val PROGRESS_INTERVAL_MS = 1000L

/**
 * 用 Media3 Transformer 輸出 H.264／AAC mp4（1080p、最高 60 fps、影像 [VIDEO_BITRATE] 固定位元率，見 [clipComposition]）。
 * Transformer 要在同一條執行緒建立與呼叫，所以整段都在主執行緒（編碼本身在 Media3 自己的執行緒，不卡畫面）。
 */
@OptIn(UnstableApi::class)
class Media3ClipExporter(context: Context) : ClipExporter {
    private val context = context.applicationContext

    override suspend fun export(draft: ClipDraft, output: File, onProgress: (Int) -> Unit) {
        val composition = clipComposition(draft, MAX_FRAME_RATE)
            ?: throw ClipExportException("沒有可輸出的素材")
        withContext(Dispatchers.Main) {
            coroutineScope {
                var transformer: Transformer? = null
                var poll: Job? = null
                try {
                    suspendCancellableCoroutine { cont ->
                        val resolver = context.contentResolver
                        val assets = DefaultAssetLoaderFactory(
                            context,
                            DefaultDecoderFactory.Builder(context).build(),
                            Clock.DEFAULT,
                            DefaultMediaSourceFactory(context, MimeFirstExtractorsFactory { resolver.getType(it) }),
                            DataSourceBitmapLoader.Builder(context).build(),
                        )
                        val built = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        VideoEncoderSettings.Builder()
                                            .setBitrate(VIDEO_BITRATE)
                                            .setBitrateMode(videoBitrateMode())
                                            .build(),
                                    )
                                    .build(),
                            )
                            .setAssetLoaderFactory(assets)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (cont.isActive) {
                                        cont.resumeWithException(
                                            ClipExportException(exportException.message ?: "輸出失敗", exportException),
                                        )
                                    }
                                }
                            })
                            .build()
                        transformer = built
                        poll = launch {
                            val holder = ProgressHolder()
                            while (true) {
                                delay(PROGRESS_INTERVAL_MS)
                                if (built.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                    onProgress(holder.progress)
                                }
                            }
                        }
                        try {
                            built.start(composition, output.absolutePath)
                        } catch (e: RuntimeException) {
                            cont.resumeWithException(ClipExportException(e.message ?: "輸出失敗", e))
                        }
                    }
                    onProgress(100)
                } catch (e: CancellationException) {
                    // 取消時續行在主執行緒（withContext 的調度器），Transformer 要在這條執行緒上取消
                    transformer?.cancel()
                    throw e
                } finally {
                    // 輪詢是無窮迴圈，不取消的話 coroutineScope 永遠等不到它結束
                    poll?.cancel()
                }
            }
        }
    }
}
