package com.mucage.rmb.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mucage.rmb.data.CategoryEntity
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.components.availableCategoryIcons
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.sync.FixedCategories
import kotlinx.coroutines.launch

/**
 * 分类管理：放在独立卡片里，进入、改名、删除都需要明确操作，
 * 避免在设置页误触直接改动分类。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryManagerDialog(onDismiss: () -> Unit) {
    val categories by Repository.categoryDao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(RecordType.EXPENSE) }
    var editorTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("分类管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "改名、换图标或删除都会随账本同步到另一端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == RecordType.EXPENSE,
                        onClick = { type = RecordType.EXPENSE },
                        label = { Text("支出用途") }
                    )
                    FilterChip(
                        selected = type == RecordType.INCOME,
                        onClick = { type = RecordType.INCOME },
                        label = { Text("收入来源") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 「攒着」是系统分类，不出现在分类管理里，避免被改名或删除
                    val list = categories.filter {
                        it.type == type && it.id != FixedCategories.EXPENSE_SAVINGS
                    }
                    items(list, key = { it.id }) { category ->
                        CategoryManageRow(
                            category = category,
                            locked = category.id == FixedCategories.EXPENSE_OTHER ||
                                category.id == FixedCategories.INCOME_OTHER,
                            onEdit = { editorTarget = category; creating = false },
                            onDelete = {
                                scope.launch {
                                    deleteCount = Repository.categoryDao.countRecordsByCategory(category.id)
                                    deleting = category
                                }
                            }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { editorTarget = null; creating = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("新增分类")
                        }
                    }
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (creating || editorTarget != null) {
        CategoryEditorCard(
            type = type,
            initial = editorTarget,
            onDismiss = { creating = false; editorTarget = null },
            onSave = { name, icon ->
                scope.launch {
                    val ok = if (editorTarget == null) {
                        Repository.addCategory(name, icon, type) != null
                    } else {
                        Repository.updateCategory(editorTarget!!.id, name, icon)
                    }
                    if (ok) {
                        status = if (editorTarget == null) "已新增「${name.trim()}」，导出后即可同步到电脑" else "已保存分类修改"
                        creating = false
                        editorTarget = null
                    } else {
                        status = "保存失败：名称不能为空，且同一类型下不能重名"
                    }
                }
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类「${target.name}」？") },
            text = {
                Text(
                    if (deleteCount > 0) {
                        "该分类下有 $deleteCount 笔账目，删除后这些账目会归入同类型「其他」。"
                    } else {
                        "删除后不会影响账目，但会同步到另一端的账本。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = Repository.deleteCategory(target.id)
                        status = if (ok) "已删除「${target.name}」" else "该分类无法删除"
                        deleting = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: CategoryEntity,
    locked: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon(category.icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (locked) {
                    Text(
                        "兜底分类，不可删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, "编辑", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, enabled = !locked, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    "删除",
                    Modifier.size(18.dp),
                    tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 新增/编辑分类的独立小卡片：名称 + 图标选择 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorCard(
    type: String,
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var icon by remember(initial) { mutableStateOf(initial?.icon ?: availableCategoryIcons.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) {
                        if (type == RecordType.INCOME) "新增收入来源" else "新增支出用途"
                    } else {
                        "编辑分类"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(8) },
                    label = { Text("分类名称（最多 8 字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("选择图标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCategoryIcons.forEach { key ->
                        val selected = icon == key
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.5.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                categoryIcon(key),
                                null,
                                Modifier.size(22.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(name, icon) }, enabled = name.isNotBlank()) { Text("保存") }
                }
            }
        }
    }
}
