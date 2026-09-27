package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 全新安裝的第一個畫面（規格第十節「還原」入口；手冊 §一：可選【從 Google Drive 還原】或
 * 【全新開始】）。
 *
 * @param linking 正在連結 Google 帳號（按了【從 Google Drive 還原】之後、還原清單出現之前）。
 *        全新安裝的裝置沒有任何既有授權，一定要先走一次連結／同意流程才讀得到 Drive 上的備份
 *        （見 `FirstRunGate` 的 KDoc）——這段時間可能要等系統畫面，按鈕要停用並說明在做什麼，
 *        不然看起來像按了沒反應。
 * @param error 連結失敗的訊息。顯示出來，讓使用者知道發生什麼事、可以再按一次同一顆按鈕重試
 *        （不另做一顆【重試】：這裡本來就只有兩顆鈕，重試就是再按一次【從 Google Drive 還原】）。
 */
@Composable
fun FirstRunChooserScreen(
    onRestoreClick: () -> Unit,
    onStartFreshClick: () -> Unit,
    modifier: Modifier = Modifier,
    linking: Boolean = false,
    error: String? = null,
) {
    Column(
        modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("歡迎使用 videoshot", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.text)
        Text(
            "如果你之前備份過圖庫，可以直接還原；也可以先略過，之後隨時能在帳號頁連結。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRestoreClick, enabled = !linking, modifier = Modifier.fillMaxWidth()) {
            Text(if (linking) "連結中…" else "從 Google Drive 還原")
        }
        TextButton(onClick = onStartFreshClick, enabled = !linking, modifier = Modifier.fillMaxWidth()) {
            Text("全新開始")
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.danger,
                textAlign = TextAlign.Center,
            )
        }
    }
}
