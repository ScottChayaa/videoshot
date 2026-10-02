package com.xenyaa.videoshot.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.ChipSize
import com.xenyaa.videoshot.ui.common.PillStyle
import com.xenyaa.videoshot.ui.common.TextFieldSize
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.TopBarTitle
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsBottomNav
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsHintCard
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsStepIndicator
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.common.VsToolbarPill
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.common.VsUnderlineTabs
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.shell.Tab
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 元件總覽頁（開發測試版專用，計畫 15A Task 8）：每個共用元件的每種狀態各擺一份，
 * 實機上逐張對照原型樣式表（淺色＋深色）。用 `Column` ＋ `verticalScroll` 而不是 `LazyColumn`，
 * 讓每一區都一定被組合出來（測試要斷言每區標題都在）。
 * 互動範例各自帶本機狀態，可以在實機上點點看。
 */
@Composable
fun ComponentCatalog(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        VsTopBar("元件總覽")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(AppTheme.spacing.s4),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s4),
            ) {
                TopBarSection()
                BottomNavSection()
                ChipSection()
                ButtonSection()
                ListRowSection()
                EmptyStateSection()
                StepSection()
                TextFieldSection()
                TabsPillsHintSection()
            }
            DockSection()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textDim,
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun TopBarSection() = Section("頂欄") {
    VsTopBar("分類")
    VsTopBar(
        "加勒比海",
        nav = TopBarNav.Back(onClick = {}),
        actions = {
            TopBarIconButton(VsIcons.Edit, "編輯", onClick = {})
            TopBarIconButton(VsIcons.Trash, "刪除", onClick = {})
        },
    )
    VsTopBar("貼網址", nav = TopBarNav.Close(onClick = {}), titleStyle = TopBarTitle.Small)
}

@Composable
private fun BottomNavSection() = Section("底部導覽") {
    var home by remember { mutableStateOf(Tab.HOME) }
    VsBottomNav(current = home, onSelect = { home = it }, accountInitial = null)
    var account by remember { mutableStateOf(Tab.ACCOUNT) }
    VsBottomNav(current = account, onSelect = { account = it }, accountInitial = 'S')
}

@Composable
private fun ChipSection() = Section("標籤小膠囊") {
    val s2 = AppTheme.spacing.s2
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
        VsTagChip("加勒比海", ChipKind.PLACE)
        VsTagChip("阿明", ChipKind.PERSON)
        VsTagChip("海龜", ChipKind.PET)
        VsTagChip("夜潛", ChipKind.TOPIC)
        VsTagChip("龍蝦", ChipKind.OTHER)
    }
    var picked by remember { mutableStateOf(true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
        VsTagChip("夜潛", ChipKind.TOPIC, selected = picked, onClick = { picked = !picked })
        VsTagChip("加勒比海", ChipKind.PLACE, count = 12)
        VsTagChip("阿明", ChipKind.PERSON, selected = true, count = 3)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
        VsTagChip("加勒比海", ChipKind.PLACE, size = ChipSize.Mini)
        VsTagChip("阿明", ChipKind.PERSON, size = ChipSize.Mini)
        VsTagChip("海龜", ChipKind.PET, size = ChipSize.Mini)
        VsTagChip("夜潛", ChipKind.TOPIC, size = ChipSize.Mini)
        VsTagChip("龍蝦", ChipKind.OTHER, size = ChipSize.Mini)
    }
}

@Composable
private fun ButtonSection() = Section("按鈕") {
    VsButton("下一步", onClick = {}, variant = ButtonVariant.Primary)
    VsButton("取消", onClick = {}, variant = ButtonVariant.Secondary)
    VsButton("稍後再說", onClick = {}, variant = ButtonVariant.Quiet)
    VsButton("刪除這張收藏", onClick = {}, variant = ButtonVariant.Danger)
    VsButton("刪除這些收藏", onClick = {}, variant = ButtonVariant.DangerQuiet)
    VsButton("下一步", onClick = {}, enabled = false)
    VsButton("新增圖片", onClick = {}, icon = VsIcons.Plus)
}

@Composable
private fun ListRowSection() = Section("清單列") {
    var clicks by remember { mutableIntStateOf(0) }
    VsListRow(
        "備份（雲）",
        subtitle = if (clicks == 0) "上次備份：剛剛" else "點了 $clicks 次",
        icon = VsIcons.Cloud,
        onClick = { clicks++ },
    )
    VsListRow("刪除全部資料", icon = VsIcons.Trash, danger = true, onClick = {})
    VsListRow("版本", subtitle = "不可點的資訊列")
}

@Composable
private fun EmptyStateSection() = Section("空狀態") {
    VsEmptyState(
        "還沒有任何收藏，先貼一個 YouTube 網址開始挑畫面",
        icon = VsIcons.ImagePlus,
        actionText = "開始取圖",
        onAction = {},
    )
}

@Composable
private fun StepSection() = Section("步驟條") {
    val steps = listOf("貼網址", "挑畫面", "填資料") // 元件自己會加「1. 」編號
    VsStepIndicator(steps, current = 0)
    VsStepIndicator(steps, current = 1, onStepClick = {})
    VsStepIndicator(steps, current = 2, onStepClick = {})
    VsStepIndicator(steps, current = 0, furthest = 2, onStepClick = {}) // 已到達過第 3 步，回到第 1 步後可以往後跳
}

@Composable
private fun TextFieldSection() = Section("輸入欄") {
    var place by remember { mutableStateOf("") }
    VsTextField(
        place, { place = it }, label = "地點", placeholder = "例如：加勒比海",
        supporting = "留空就不寫入",
    )
    var dense by remember { mutableStateOf("加勒比海") }
    VsTextField(dense, { dense = it }, label = "地點", labelAccent = true, size = TextFieldSize.Dense)
    VsTextField(
        "", {}, label = "描述", placeholder = "〈多個值〉", mixedPlaceholder = true,
        size = TextFieldSize.Dense,
    )
    var bad by remember { mutableStateOf("12:99") }
    VsTextField(bad, { bad = it }, label = "時間", isError = true, supporting = "格式不對，請輸入 mm:ss")
}

@Composable
private fun TabsPillsHintSection() {
    Section("底線分頁") {
        var selected by remember { mutableIntStateOf(0) }
        VsUnderlineTabs(listOf("標籤與地點", "用文字描述"), selected, onSelect = { selected = it })
    }
    Section("工具列小按鈕") {
        val s2 = AppTheme.spacing.s2
        var on by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
            VsToolbarPill("全選", onClick = {})
            VsToolbarPill("隱藏相似", onClick = { on = !on }, icon = VsIcons.Filter, selected = on)
            VsToolbarPill("已選取", onClick = {}, selected = true)
            VsToolbarPill("清除", onClick = {}, style = PillStyle.Quiet)
            VsToolbarPill("停用", onClick = {}, enabled = false)
        }
    }
    Section("提示卡") {
        var shown by remember { mutableStateOf(true) }
        if (shown) {
            VsHintCard("點一下縮圖就能選取，長按可以預覽大圖", onDismiss = { shown = false })
        } else {
            VsButton("再顯示提示卡", onClick = { shown = true }, variant = ButtonVariant.Quiet)
        }
        VsHintCard("沒有關閉鈕的提示卡", icon = VsIcons.Sparkles)
    }
}

@Composable
private fun DockSection() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2)) { SectionTitle("底部動作列") }
        VsActionDock(status = { Text("已選 3 張") }) {
            VsButton("下一步", onClick = {})
        }
    }
}
