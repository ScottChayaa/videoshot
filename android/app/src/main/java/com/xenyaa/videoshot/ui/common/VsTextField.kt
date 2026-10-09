package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/** [Regular]＝原型 `.wz-field`（精靈的單欄輸入）；[Dense]＝`.meta-drawer .field`（批次編輯的緊湊欄位）。 */
enum class TextFieldSize { Regular, Dense }

/**
 * 共用輸入欄（原型 `.wz-field`、`.meta-drawer .field`）。
 *
 * 標籤在欄位**上方**，不是 Material 的浮動標籤，所以不用 `OutlinedTextField`，改用 [BasicTextField]。
 * [labelAccent]＝「這一欄會被寫入」（批次編輯，原型 `.field.will-write`）：標籤與圓點換主色；
 * 有給值（true／false）時圓點位置永遠保留（不亮時透明），文字才不會位移；亮起時帶「將寫入」語意，輔助技術也分得出來。
 * 不給（null，預設）就不留圓點位置，標籤跟其他欄位（如 [VsSelectField]）的標題左緣對齊。
 * [mixedPlaceholder]＝多張的值不一致，佔位字樣用斜體 `textDim`（顯示〈多個值〉）。
 * [isError] 用 `warn` 色，不用紅（紅只留給破壞性動作）。
 * 整個欄位的 `contentDescription` 取 [semanticLabel]（預設同 [label]）——既有精靈測試用它定位欄位。
 * [leadingIcon]＝欄位內左側的圖示（查詢頁文字框的放大鏡，原型 `.search .ic`）：20dp、`textDim`，不進語意樹。
 * [trailing]＝欄位內右側的按鈕（篩選抽屜搜尋框的【✕】清除），跟欄位一樣高、在內距外面，觸控區撐得到 44dp。
 * [fieldModifier]＝套在輸入框本身（例如 `focusRequester`、`onFocusChanged`），[modifier] 套在整個欄位外框。
 * [visualTransformation]＝輸入內容的遮罩（Gemini 金鑰用 `PasswordVisualTransformation`，語意樹會標成密碼欄）。
 * 聚焦的 3dp `accentWeak` 外圈用 `drawBehind` 畫在框外（原型 `box-shadow` 不佔版面），聚焦與否不會讓版面位移。
 */
@Composable
fun VsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelAccent: Boolean? = null,
    placeholder: String? = null,
    supporting: String? = null,
    mixedPlaceholder: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
    size: TextFieldSize = TextFieldSize.Regular,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    semanticLabel: String? = label,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dense = size == TextFieldSize.Dense
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = (if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        .copy(color = colors.text)
    val line = when {
        isError -> colors.warn
        focused -> colors.accent
        dense -> colors.border
        else -> colors.borderStrong
    }
    val ring = colors.accentWeak
    val ringPx = with(LocalDensity.current) { 3.dp.toPx() }
    val radiusPx = with(LocalDensity.current) { AppTheme.radii.sm.toPx() }

    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        if (label != null) {
            Row(
                Modifier.padding(bottom = AppTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 會用到圓點的欄位才保留位置；不亮時透明且沒有語意
                if (labelAccent != null) {
                    Box(
                        Modifier.size(6.dp)
                            .background(if (labelAccent) colors.accent else Color.Transparent, CircleShape)
                            .then(if (labelAccent) Modifier.semantics { contentDescription = "將寫入" } else Modifier),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (labelAccent == true) colors.accent else colors.textDim,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .then(fieldModifier)
                .then(if (semanticLabel != null) Modifier.semantics { contentDescription = semanticLabel } else Modifier),
            enabled = enabled,
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth()
                        .drawBehind {
                            if (focused) {
                                // 外圈：框外再多 3dp（描邊中心線在框外 1.5dp 處）
                                val inset = -ringPx / 2
                                drawRoundRect(
                                    ring,
                                    topLeft = Offset(inset, inset),
                                    size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
                                    cornerRadius = CornerRadius(radiusPx + ringPx / 2),
                                    style = Stroke(ringPx),
                                )
                            }
                        }
                        .background(if (dense) colors.surface2 else colors.surface, shape)
                        .border(1.dp, line, shape)
                        // 觸控區至少 44dp（Dense 的內容只有 38dp 高）
                        .defaultMinSize(minHeight = AppTheme.spacing.tap),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 右側按鈕（[trailing]）在內距外面，跟欄位一樣高，觸控區才撐得到 44dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).padding(
                                start = AppTheme.spacing.s3,
                                end = if (trailing != null) 0.dp else AppTheme.spacing.s3,
                                top = if (dense) AppTheme.spacing.s2 else AppTheme.spacing.s3,
                                bottom = if (dense) AppTheme.spacing.s2 else AppTheme.spacing.s3,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                        ) {
                            if (leadingIcon != null) {
                                Icon(leadingIcon, null, tint = colors.textDim, modifier = Modifier.size(20.dp))
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty() && placeholder != null) {
                                    Text(
                                        placeholder,
                                        style = textStyle.copy(
                                            color = if (mixedPlaceholder) colors.textDim else colors.textFaint,
                                            fontStyle = if (mixedPlaceholder) FontStyle.Italic else FontStyle.Normal,
                                        ),
                                    )
                                }
                                inner()
                            }
                        }
                        trailing?.invoke()
                    }
                }
            },
        )
        if (supporting != null) {
            Text(
                supporting,
                Modifier.padding(top = AppTheme.spacing.s1),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim,
            )
        }
    }
}
