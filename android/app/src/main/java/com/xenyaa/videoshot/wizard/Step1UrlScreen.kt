package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
                    text = "最近取過的影片",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(top = AppTheme.spacing.s5, bottom = AppTheme.spacing.s2),
                )
                // 已在可捲動的父層裡，用一般 Column，不巢狀 LazyColumn
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    recent.forEach { video ->
                        RecentCard(video, onClick = { onOpenRecent(video.videoId) })
                    }
                }
            }
        }

        VsActionDock {
            VsButton("下一步", { onSubmit(input) }, Modifier.weight(1f), enabled = !loading)
        }
    }
}

/** 原型 `.wz-hist .hrow`：整張卡片都是點擊區 —— 點了**直接進第二步**，不是把網址填回輸入框（手冊 §四第一步）。 */
@Composable
private fun RecentCard(video: RecentVideo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        Icon(VsIcons.Film, null, tint = AppTheme.colors.textDim, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatDate(video.addedAt)} · 取了 ${video.shotCount} 張",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}

private fun formatDate(unixSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Date(unixSec * 1000))
