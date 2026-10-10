package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 搜尋框裡的【✕】：跟欄位一樣高（44dp），寬 40dp，按了清空搜尋字（焦點留在框裡，可以接著打）。 */
@Composable
fun VsClearQueryButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = AppTheme.spacing.tap)
            .clickable(role = Role.Button, onClickLabel = "清除") { onClick() }
            .semantics { contentDescription = "清除搜尋文字" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(VsIcons.Close, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(18.dp))
    }
}

/**
 * 頂欄的搜尋模式：點了頂欄的〔搜尋〕圖示鈕之後，整條頂欄換成搜尋框＋【取消】。
 *
 * 搜尋框跟首頁篩選抽屜的一樣（Dense、左邊放大鏡、有字時右邊【✕】），一出現就聚焦（鍵盤跳出來）。
 * 高度與分隔線跟 [VsTopBar] 相同，切換時下面的內容不會上下跳。【取消】要清掉搜尋字並收回成原本的頂欄，
 * 由呼叫端的 [onCancel] 決定（系統返回鍵也該接到同一件事）。
 */
@Composable
fun VsSearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().background(AppTheme.colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.spacing.topBarHeight)
                .padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            VsTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = placeholder,
                size = TextFieldSize.Dense,
                semanticLabel = placeholder,
                leadingIcon = VsIcons.Search,
                fieldModifier = Modifier.focusRequester(focusRequester),
                trailing = if (query.isNotEmpty()) {
                    { VsClearQueryButton { onQuery("") } }
                } else {
                    null
                },
            )
            VsButton("取消", onCancel, variant = ButtonVariant.Quiet)
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
