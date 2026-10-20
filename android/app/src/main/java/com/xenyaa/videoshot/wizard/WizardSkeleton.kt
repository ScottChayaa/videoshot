package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.player.playerFrame
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsFieldButton
import com.xenyaa.videoshot.ui.common.VsSkeletonArea
import com.xenyaa.videoshot.ui.common.VsSkeletonBlock
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap

/**
 * 續做草稿時，資料還沒好之前的第二步／第三步（2026-10-18）。
 *
 * **形狀跟真的畫面一樣**：播放器框、狀態列、4 欄縮圖牆、底部列，
 * 換成真內容時版面不跳。底部列的按鈕先畫出來但停用。
 *
 * [error] 不是 null（沒網路、影片抓不到）時，縮圖牆那一塊換成錯誤訊息與【重試】，其餘照舊。
 */
@Composable
internal fun WizardSkeleton(
    step: WizardStep,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (step != WizardStep.DETAILS) {
            // 播放器框：真的播放器載入前也是黑底，所以這塊不閃
            Box(Modifier.playerFrame(fullscreen = false))
            // 狀態列（「N 張照片，隱藏 M 張相似畫面」）
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.accentWeak)
                    .defaultMinSize(minHeight = AppTheme.spacing.tap)
                    .padding(horizontal = AppTheme.spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (error == null) {
                    VsSkeletonArea { VsSkeletonBlock(Modifier.size(width = 180.dp, height = 12.dp)) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (error != null) {
                ResumeError(error, onRetry)
            } else {
                GridSkeleton()
            }
        }

        if (step == WizardStep.DETAILS) {
            VsActionDock {
                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                    VsFieldButton(VsIcons.ImagesCheck, "全選", onClick = {}, enabled = false)
                    DetailField.entries.forEach { VsFieldButton(it.icon, it.label, onClick = {}, enabled = false) }
                }
                VsButton("完成", onClick = {}, modifier = Modifier.weight(1f).align(Alignment.CenterVertically), enabled = false)
            }
        } else {
            VsActionDock {
                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                    VsFieldButton(VsIcons.ImagesCheck, "全選", onClick = {}, enabled = false)
                    VsFieldButton(VsIcons.Scissors, "截圖", onClick = {}, enabled = false)
                }
                VsButton("下一步", onClick = {}, modifier = Modifier.weight(1f).align(Alignment.CenterVertically), enabled = false)
            }
        }
    }
}

/** 4 欄正方形格子，間距同真的縮圖牆；排滿整塊高度（超出的被裁掉）。 */
@Composable
private fun GridSkeleton() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = (maxWidth - ThumbGridGap * 3) / 4
        val rows = (maxHeight / (cell + ThumbGridGap)).toInt() + 1
        VsSkeletonArea(Modifier.fillMaxSize(), description = "正在載入縮圖…") {
            Column(verticalArrangement = Arrangement.spacedBy(ThumbGridGap)) {
                repeat(rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ThumbGridGap)) {
                        repeat(4) { VsSkeletonBlock(Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape) }
                    }
                }
            }
        }
    }
}

/** 續做接不上：說原因，給【重試】（返回與 X 照常能用）。 */
@Composable
private fun ResumeError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(AppTheme.spacing.s3))
        VsButton("重試", onRetry, variant = ButtonVariant.Secondary, modifier = Modifier.width(120.dp))
    }
}
