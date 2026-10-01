package com.xenyaa.videoshot.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsTopBar

/** 帳號頁子畫面共用的返回列。外觀即全 app 共用頂欄（[VsTopBar]）。 */
@Composable
fun AccountSettingHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    VsTopBar(title, modifier, nav = TopBarNav.Back(onBack))
}
