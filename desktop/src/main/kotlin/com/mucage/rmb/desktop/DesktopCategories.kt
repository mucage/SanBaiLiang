package com.mucage.rmb.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.SyncCategory

/** 桌面端可选的分类图标标识（与手机端保持一致） */
internal val desktopCategoryIconKeys: List<String> = listOf(
    "restaurant", "snack", "clothes", "entertainment", "education",
    "transport", "home", "medical", "telecom", "shopping",
    "travel", "pets", "sports", "beauty", "gift",
    "more", "salary", "bonus", "finance", "parttime", "redpacket"
)

internal fun desktopCategoryIcon(key: String): ImageVector = when (key) {
    "restaurant" -> Icons.Outlined.Restaurant
    "snack" -> Icons.Outlined.Icecream
    "clothes" -> Icons.Outlined.Checkroom
    "entertainment" -> Icons.Outlined.SportsEsports
    "education" -> Icons.Outlined.School
    "transport" -> Icons.Outlined.DirectionsBus
    "home" -> Icons.Outlined.Home
    "medical" -> Icons.Outlined.LocalHospital
    "telecom" -> Icons.Outlined.Smartphone
    "shopping" -> Icons.Outlined.ShoppingBag
    "travel" -> Icons.Outlined.Flight
    "pets" -> Icons.Outlined.Pets
    "sports" -> Icons.Outlined.FitnessCenter
    "beauty" -> Icons.Outlined.Spa
    "gift" -> Icons.Outlined.CardGiftcard
    "salary" -> Icons.Outlined.Work
    "bonus" -> Icons.Outlined.EmojiEvents
    "finance" -> Icons.Outlined.TrendingUp
    "parttime" -> Icons.Outlined.Luggage
    "redpacket" -> Icons.Outlined.Redeem
    "savings" -> Icons.Outlined.Savings
    else -> Icons.Outlined.MoreHoriz
}

/**
 * 分类管理卡片：独立弹层，进入后要点「编辑」才会改动，
 * 删除还要再确认一次，避免误触。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopCategoryManagerDialog(
    categories: List<SyncCategory>,
    onDismiss: () -> Unit,
    onAdd: (name: String, icon: String, type: String) -> Boolean,
    onUpdate: (id: Long, name: String, icon: String) -> Boolean,
    onDelete: (id: Long) -> Boolean
) {
    var type by remember { mutableStateOf(CategoryType.EXPENSE.code) }
    var editing by remember { mutableStateOf<SyncCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SyncCategory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 640.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("分类管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "只改这份本机账本；导出给手机后即可同步到另一端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭") }
                }
                Spacer(Modifier.height(12.dp))

                if (creating || editing != null) {
                    CategoryEditorPanel(
                        type = if (editing != null) editing!!.type else type,
                        initial = editing,
                        onCancel = { creating = false; editing = null; error = null },
                        onSave = { name, icon ->
                            val ok = if (editing == null) {
                                onAdd(name, icon, type)
                            } else {
                                onUpdate(editing!!.id, name, icon)
                            }
                            if (ok) {
                                status = if (editing == null) "已新增「${name.trim()}」" else "已保存分类修改"
                                error = null
                                creating = false
                                editing = null
                            } else {
                                error = "名称不能为空，且同一类型下不能重名"
                            }
                        }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == CategoryType.EXPENSE.code,
                            onClick = { type = CategoryType.EXPENSE.code },
                            label = { Text("支出用途") }
                        )
                        FilterChip(
                            selected = type == CategoryType.INCOME.code,
                            onClick = { type = CategoryType.INCOME.code },
                            label = { Text("收入来源") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 「攒着」是系统分类，不出现在分类管理里，避免被改名或删除
                    val list = categories.filter {
                        it.type == type && !it.deleted && it.id != FixedCategories.EXPENSE_SAVINGS
                    }.sortedBy { it.sortOrder }
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { category ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(34.dp).background(NahidaSoft, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(desktopCategoryIcon(category.icon), null, Modifier.size(18.dp), tint = NahidaGreen)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (category.id == FixedCategories.EXPENSE_OTHER || category.id == FixedCategories.INCOME_OTHER) {
                                        Text(
                                            "兜底分类，不可删除",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { editing = category; creating = false; error = null }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Outlined.Edit, "编辑", Modifier.size(17.dp), tint = NahidaGreen)
                                }
                                IconButton(
                                    onClick = { pendingDelete = category },
                                    enabled = category.id != FixedCategories.EXPENSE_OTHER &&
                                        category.id != FixedCategories.INCOME_OTHER,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        "删除",
                                        Modifier.size(17.dp),
                                        tint = if (category.id == FixedCategories.EXPENSE_OTHER ||
                                            category.id == FixedCategories.INCOME_OTHER
                                        ) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        else ExpenseColor
                                    )
                                }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { creating = true; editing = null; error = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("新增分类")
                            }
                        }
                    }
                }

                pendingDelete?.let { target ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFDECEC), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "删除「${target.name}」？该分类下的账目会归入同类型「其他」。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7A2B2B)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = {
                                    val ok = onDelete(target.id)
                                    status = if (ok) "已删除「${target.name}」" else "该分类无法删除"
                                    pendingDelete = null
                                }) { Text("确认删除") }
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ExpenseColor)
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NahidaGreen)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorPanel(
    type: String,
    initial: SyncCategory?,
    onCancel: () -> Unit,
    onSave: (name: String, icon: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var icon by remember(initial) { mutableStateOf(initial?.icon ?: desktopCategoryIconKeys.first()) }

    Column {
        Text(
            if (initial == null) {
                if (type == CategoryType.INCOME.code) "新增收入来源" else "新增支出用途"
            } else {
                "编辑「${initial.name}」"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(8) },
            label = { Text("分类名称（最多 8 字）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text("选择图标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            desktopCategoryIconKeys.forEach { key ->
                val selected = icon == key
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) NahidaSoft else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.5.dp,
                            if (selected) NahidaGreen else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        desktopCategoryIcon(key),
                        null,
                        Modifier.size(20.dp),
                        tint = if (selected) NahidaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(name, icon) }, enabled = name.isNotBlank()) { Text("保存") }
        }
    }
}
