package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 帳號頁「AI 分析」子畫面：往前／往後秒數（規格第九節；顯示但第四步上線後才生效）。
 * `onChange` 一次帶完整組值——`AccountViewModel.setAiRange` 只有一個方法同時存兩個數字。
 */
@Composable
fun AiRangeScreen(
    beforeSec: Int,
    afterSec: Int,
    onBack: () -> Unit,
    onChange: (beforeSec: Int, afterSec: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("AI 分析", onBack)
        Stepper("往前秒數", "送 AI 分析時，從時間點往前涵蓋的秒數", beforeSec, "往前秒數") { onChange(it, afterSec) }
        Stepper("往後秒數", "往後涵蓋的秒數", afterSec, "往後秒數") { onChange(beforeSec, it) }
        Text(
            "AI 補充功能還沒上線，這裡的設定會先存著，上線後才會送出分析。",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textDim,
            modifier = Modifier.padding(AppTheme.spacing.s4),
        )
    }
}

/**
 * 一列「名稱／說明 ＋ 減／值／加」。**沒有現成的「減號」圖示**（`VsIcons` 目前最接近的是
 * `Close`，語意是關閉不是減少，用了會誤導）——減號用 `Text("－")` 畫，跟 `VsIcons.Plus`
 * 配一對，兩邊按鈕大小、可點擊範圍一致（`Modifier.size(AppTheme.spacing.tap)`）。
 */
@Composable
private fun Stepper(label: String, note: String, value: Int, contentDescriptionPrefix: String, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(note, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
        }
        IconButton(
            onClick = { if (value > 0) onChange(value - 1) },
            modifier = Modifier
                .size(AppTheme.spacing.tap)
                .focusRing(CircleShape)
                .semantics { contentDescription = "${contentDescriptionPrefix}減少" },
        ) {
            Text("－", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textDim)
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
        IconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier
                .size(AppTheme.spacing.tap)
                .focusRing(CircleShape)
                .semantics { contentDescription = "${contentDescriptionPrefix}增加" },
        ) {
            Icon(VsIcons.Plus, contentDescription = null, tint = AppTheme.colors.textDim)
        }
    }
}
