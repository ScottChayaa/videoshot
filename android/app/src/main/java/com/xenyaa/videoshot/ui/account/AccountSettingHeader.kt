package com.xenyaa.videoshot.ui.account

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
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 帳號頁六個子畫面共用的返回列（比照 `DetailScreen` 的頂列樣式，見它的 KDoc 對 inset 的說明）。 */
@Composable
fun AccountSettingHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
            Icon(VsIcons.Back, contentDescription = "返回", tint = AppTheme.colors.textDim)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.text,
            modifier = Modifier.padding(start = AppTheme.spacing.s2),
        )
    }
}
