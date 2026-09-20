package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.core.folders.compareNaturally
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 父在前、子緊跟在自己的父層底下、同層照 [compareNaturally] 排序，供畫面縮排用。
 *
 * 同層排序不重寫比較邏輯 —— `:core` 的 `FolderSort.NAME_ASC` 要吃 `SortableFolder`，
 * `FolderNode` 沒有實作它（也不該為了排序多背 `shotCount`／`lastActivityAt`），
 * 所以直接用 `compareNaturally` 本體。
 */
fun flattenTree(nodes: List<FolderNode>): List<FolderNode> {
    val byParent: Map<Long?, List<FolderNode>> = nodes.groupBy { it.parentId }
    val result = mutableListOf<FolderNode>()
    fun visit(parentId: Long?) {
        val siblings = byParent[parentId].orEmpty().sortedWith { a, b -> compareNaturally(a.name, b.name) }
        for (node in siblings) {
            result += node
            visit(node.id)
        }
    }
    visit(null)
    return result
}

/**
 * Lightbox 動作列的【加入分類】（手冊 §三第四條）。
 *
 * **勾一下就寫一次**（本階段決定 3）：每一次 [onToggle] 由呼叫端立刻 insert／delete
 * `shot_folder`，這張 sheet 純粹呈現目前狀態，關掉不需要做任何事、中途被系統殺掉也不會遺失。
 * 因此這裡不自己碰 repo —— 接線是呼叫端的事。
 *
 * 【＋新增資料夾】開的是 [FolderNameDialog]，新資料夾一律建在根層
 * （呼叫端把 [onCreate] 接到 `repo.createFolder(null, name)`）——
 * 這裡再挑父層等於把整棵樹的操作搬進 Lightbox，不值得。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToFolderSheet(
    tree: List<FolderNode>,
    checked: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null＝沒有在新增；有值＝正在輸入新資料夾的名字。跟 FolderNameDialog 自己的 ModalBottomSheet
    // 疊在這張 sheet 上面（兩個都是 sheet，實測不會卡死 —— 跟 FoldersScreen 排序抽屜 ＋
    // 改名對話框同一種「同一層兩個 sibling」寫法，只是這裡兩者可能同時顯示）。
    var creatingName by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            Text(
                "加入分類",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
            )

            val flat = flattenTree(tree)
            if (flat.isEmpty()) {
                Text(
                    "還沒有任何分類",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
                )
            } else {
                for (node in flat) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("folderRow")
                            .focusRing()
                            .toggleable(
                                value = node.id in checked,
                                onValueChange = { onToggle(node.id, it) },
                            )
                            .padding(
                                start = AppTheme.spacing.s4 + AppTheme.spacing.s4 * (node.depth - 1),
                                top = AppTheme.spacing.s3,
                                bottom = AppTheme.spacing.s3,
                                end = AppTheme.spacing.s4,
                            )
                            .semantics { contentDescription = node.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = node.id in checked, onCheckedChange = null)
                        Text(
                            node.name,
                            modifier = Modifier.padding(start = AppTheme.spacing.s2),
                            color = AppTheme.colors.text,
                        )
                    }
                }
            }

            TextButton(
                onClick = { creatingName = "" },
                modifier = Modifier.focusRing().padding(horizontal = AppTheme.spacing.s2),
            ) {
                Text("新增資料夾")
            }
        }
    }

    creatingName?.let { name ->
        FolderNameDialog(
            editor = FolderEditor(target = null, name = name),
            onName = { creatingName = it },
            onConfirm = {
                onCreate(name.trim())
                creatingName = null
            },
            onDismiss = { creatingName = null },
        )
    }
}
