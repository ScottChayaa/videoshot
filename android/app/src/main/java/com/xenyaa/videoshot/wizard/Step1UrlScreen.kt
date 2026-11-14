package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 第一步：貼網址或點「最近取過的影片」（原型 `capture.html` 第一步）。
 *
 * 所有錯誤都**就地顯示、不換頁**（規格第五節第一步的表）——
 * 換頁去顯示一句錯誤訊息，等於把使用者剛貼好的網址丟掉。
 *
 * 版面：上半可捲動（輸入欄、錯誤、歷史），【下一步】固定在底部動作列。
 * 外層精靈殼不補底部 inset，由 [VsActionDock] 自己處理導覽列與鍵盤，鍵盤打開時按鈕浮在鍵盤上方。
 */
@Composable
fun Step1UrlScreen(
    status: Step1Status,
    recent: List<RecentVideo>,
    onSubmit: (String) -> Unit,
    onOpenRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChannel: (String) -> Unit = {},
    loadCover: suspend (String) -> ImageBitmap? = { null },
) {
    var input by remember { mutableStateOf("") }
    val loading = status is Step1Status.Loading

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.s4),
        ) {
            VsTextField(
                value = input,
                onValueChange = { input = it },
                label = "貼上 YouTube 網址或 videoId",
                placeholder = "https://youtu.be/…",
                semanticLabel = "貼上 YouTube 網址",
                isError = status is Step1Status.Error,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { onSubmit(input) }),
            )

            if (status is Step1Status.Error) {
                // 原型 .wz-err：提示框。顏色用 warn（紅只留給破壞性動作），不用原型的 danger
                Text(
                    text = status.message,
                    color = AppTheme.colors.warn,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = AppTheme.spacing.s2)
                        .fillMaxWidth()
                        .background(AppTheme.colors.warnWeak, RoundedCornerShape(AppTheme.radii.sm))
                        .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
                )
            }

            if (recent.isNotEmpty()) {
                Text(
                    text = "歷史紀錄",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(top = AppTheme.spacing.s5, bottom = AppTheme.spacing.s2),
                )
                // 已在可捲動的父層裡，用一般 Column，不巢狀 LazyColumn
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    recent.forEach { video ->
                        RecentCard(
                            video,
                            onClick = { onOpenRecent(video.videoId) },
                            onOpenChannel = { onOpenChannel(video.videoId) },
                            loadCover = loadCover,
                        )
                    }
                }
            }
        }

        VsActionDock {
            VsButton("下一步", { onSubmit(input) }, Modifier.weight(1f), enabled = !loading)
        }
    }
}

/**
 * 原型 `.wz-hist .hrow`：整張卡片都是點擊區 —— 點了**直接進第二步**，不是把網址填回輸入框（手冊 §四第一步）。
 * 只有頻道名稱例外：點了開 YouTube 上的頻道（2026-11-14）。
 * 封面裁成正方形、邊長跟右邊兩行字一樣高，列高跟沒有封面時一樣；抓不到時退回影片圖示。
 */
@Composable
private fun RecentCard(
    video: RecentVideo,
    onClick: () -> Unit,
    onOpenChannel: () -> Unit,
    loadCover: suspend (String) -> ImageBitmap?,
) {
    val cover by produceState<ImageBitmap?>(null, video.videoId) { value = loadCover(video.videoId) }
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.border, shape)
            .clip(shape) // 波紋要裁在圓角內
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s2, vertical = AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        // 邊長＝右邊兩行字的行高＋行距：跟著字級縮放，列高不因封面變高
        val typography = MaterialTheme.typography
        val lineGap = AppTheme.spacing.s1
        val side = with(LocalDensity.current) {
            typography.bodyMedium.lineHeight.toDp() + lineGap + typography.bodySmall.lineHeight.toDp()
        }
        Box(Modifier.size(side).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            val image = cover
            if (image != null) {
                Canvas(Modifier.fillMaxSize()) { drawCoverSquare(image) }
            } else {
                Icon(VsIcons.Film, null, tint = AppTheme.colors.textDim, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(lineGap)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
            ) {
                if (video.channelTitle.isNotBlank()) {
                    // 小字的點擊區比字小；Compose 會把它的觸控範圍補到最小點擊尺寸，不必撐高這一列
                    Row(
                        Modifier.weight(1f, fill = false).clickable(role = Role.Button, onClick = onOpenChannel),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            video.channelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(VsIcons.ExternalLink, null, tint = AppTheme.colors.accent, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    formatDate(video.addedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    maxLines = 1,
                )
                Text(
                    "取圖 ${video.shotCount} 張",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * YouTube 的 hqdefault 是 4:3，16:9 的畫面上下各有黑邊：取 16:9 那塊的正中間正方形（邊長＝寬 × 9/16），
 * 黑邊就不會進來。直式的 Shorts 左右還是會看到一點底色。
 */
private fun DrawScope.drawCoverSquare(image: ImageBitmap) {
    val srcSide = minOf(image.width * 9 / 16, image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - srcSide) / 2, (image.height - srcSide) / 2),
        srcSize = IntSize(srcSide, srcSide),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

private fun formatDate(unixSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Date(unixSec * 1000))
