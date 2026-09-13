package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 第一步：貼網址或點「最近取過的影片」。
 *
 * 所有錯誤都**就地顯示、不換頁**（規格第五節第一步的表）——
 * 換頁去顯示一句錯誤訊息，等於把使用者剛貼好的網址丟掉。
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

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("貼上 YouTube 網址") },
            singleLine = true,
            enabled = !loading,
            isError = status is Step1Status.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        if (status is Step1Status.Error) {
            Text(
                text = status.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { onSubmit(input) },
            enabled = !loading,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("下一步")
        }

        if (recent.isNotEmpty()) {
            Text(
                text = "最近取過的影片",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            LazyColumn {
                items(recent, key = { it.videoId }) { video ->
                    RecentRow(video, onClick = { onOpenRecent(video.videoId) })
                }
            }
        }
    }
}

/** 一整列都是點擊區 —— 點了**直接進第二步**，不是把網址填回輸入框（手冊 §四第一步）。 */
@Composable
private fun RecentRow(video: RecentVideo, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(video.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${formatDate(video.addedAt)} · ${video.shotCount} 張",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(unixSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Date(unixSec * 1000))
