package com.mucage.rmb.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mucage.rmb.data.LimitExempt
import com.mucage.rmb.data.RecordEntity
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.formatCentsWithSymbol
import com.mucage.rmb.util.todayStartMillis
import kotlinx.coroutines.launch

/** 明细页：本月收支摘要 + 按日分组的当月账目，支持长按批量管理 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (RecordEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories by Repository.categoryDao.observeAll().collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // 只加载当月账目，避免记录过多时整表加载导致的卡顿
    val monthStart = remember { DateUtils.startOfMonth(todayStartMillis()) }
    val monthEnd = remember { DateUtils.endOfDay(DateUtils.addMonths(monthStart, 1) - 1) }
    val monthRecords by Repository.recordDao.observeBetween(monthStart, monthEnd)
        .collectAsState(initial = emptyList())

    val monthExpense = monthRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amountCents }
    val monthIncome = monthRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents }

    // 批量管理：长按进入选择模式
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showLimitExemptDialog by remember { mutableStateOf(false) }

    val allSelected = monthRecords.isNotEmpty() && selectedIds.size == monthRecords.size

    fun toggleSelect(id: Long) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        MonthSummaryCard(
            monthExpense = monthExpense,
            monthIncome = monthIncome,
            monthLabel = DateUtils.formatMonth(monthStart),
            onAdd = onAdd
        )

        AiCommentCard()

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = ::exitSelection) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (allSelected) selectedIds.clear()
                        else {
                            selectedIds.clear()
                            selectedIds.addAll(monthRecords.map { it.id })
                        }
                    }) { Text(if (allSelected) "取消全选" else "全选") }
                    TextButton(onClick = { if (selectedIds.isNotEmpty()) showLimitExemptDialog = true }) {
                        Text("限额")
                    }
                    TextButton(onClick = { if (selectedIds.isNotEmpty()) showBatchDelete = true }) {
                        Text("删除 (${selectedIds.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (monthRecords.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "本月暂无账目记录\n点击右下角「+」记一笔吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val groups = remember(monthRecords) { monthRecords.groupBy { DateUtils.startOfDay(it.date) } }
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (day, dayRecords) ->
                    item(key = "day_$day") {
                        DayHeader(
                            day = day,
                            expense = dayRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amountCents },
                            income = dayRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents }
                        )
                    }
                    items(dayRecords, key = { it.id }) { record ->
                        RecordRow(
                            record = record,
                            categoryName = categoryMap[record.categoryId]?.name ?: "",
                            categoryIcon = categoryMap[record.categoryId]?.icon ?: "more",
                            selected = record.id in selectedIds,
                            selectionMode = selectionMode,
                            onToggleSelect = { toggleSelect(record.id) },
                            onClick = { onEdit(record) },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds.add(record.id)
                                } else {
                                    toggleSelect(record.id)
                                }
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // 批量删除确认
    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("删除所选 ${selectedIds.size} 笔账目？") },
            text = { Text("删除后无法恢复，并会随同步文件一同删除电脑端的对应账目。") },
            confirmButton = {
                TextButton(onClick = {
                    // 先快照待删除 id：rememberCoroutineScope 的协程体要等本次点击回调结束才执行，
                    // 若在协程内才读取 selectedIds，exitSelection() 已将其清空，会导致一笔也删不掉
                    val ids = monthRecords.filter { it.id in selectedIds }.map { it.id }
                    scope.launch {
                        // 软删除：保留墓碑并刷新修改时间，导入到电脑端时即可同步删除
                        Repository.recordDao.markDeleted(ids, System.currentTimeMillis())
                    }
                    showBatchDelete = false
                    exitSelection()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) { Text("取消") }
            }
        )
    }

    // 批量设置限额豁免
    if (showLimitExemptDialog) {
        AlertDialog(
            onDismissRequest = { showLimitExemptDialog = false },
            title = { Text("所选 ${selectedIds.size} 笔账目的限额计入") },
            text = {
                Column {
                    // 同删除：先快照 id 再启动协程，避免协程执行时 selectedIds 已被 exitSelection() 清空
                    TextButton(onClick = {
                        val ids = selectedIds.toList()
                        scope.launch {
                            Repository.recordDao.setLimitExempt(ids, LimitExempt.SKIP_DAILY, System.currentTimeMillis())
                        }
                        showLimitExemptDialog = false
                        exitSelection()
                    }) { Text("不计入当日限额（仍保留在月限额中）") }
                    TextButton(onClick = {
                        val ids = selectedIds.toList()
                        scope.launch {
                            Repository.recordDao.setLimitExempt(ids, LimitExempt.SKIP_ALL, System.currentTimeMillis())
                        }
                        showLimitExemptDialog = false
                        exitSelection()
                    }) { Text("不计入限额") }
                    TextButton(onClick = {
                        val ids = selectedIds.toList()
                        scope.launch {
                            Repository.recordDao.setLimitExempt(ids, LimitExempt.NONE, System.currentTimeMillis())
                        }
                        showLimitExemptDialog = false
                        exitSelection()
                    }) { Text("计入限额") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLimitExemptDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 本月收支摘要卡片 */
@Composable
private fun MonthSummaryCard(
    monthExpense: Long,
    monthIncome: Long,
    monthLabel: String,
    onAdd: () -> Unit
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = container
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "$monthLabel 收支",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                SummaryItem("支出", monthExpense, label = null, Modifier.weight(1f))
                SummaryItem("收入", monthIncome, label = null, Modifier.weight(1f))
                SummaryItem(
                    "结余",
                    monthIncome - monthExpense,
                    label = if (monthIncome - monthExpense >= 0) "盈余" else "超支",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(name: String, cents: Long, label: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(
            formatCentsWithSymbol(cents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** 按日分组的分组头 */
@Composable
private fun DayHeader(day: Long, expense: Long, income: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            DateUtils.formatDayWithWeek(day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (expense > 0) {
            Text(
                "支出 ${formatCentsWithSymbol(expense)}",
                style = MaterialTheme.typography.labelMedium,
                color = ExpenseColor
            )
        }
        if (income > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                "收入 ${formatCentsWithSymbol(income)}",
                style = MaterialTheme.typography.labelMedium,
                color = IncomeColor
            )
        }
    }
}

/** 单条账目：选择模式下显示复选框，长按进入选择模式 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    record: RecordEntity,
    categoryName: String,
    categoryIcon: String,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isExpense = record.type == RecordType.EXPENSE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    categoryIcon(categoryIcon),
                    contentDescription = categoryName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                categoryName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val exemptTag = if (isExpense) when (record.limitExempt) {
                LimitExempt.SKIP_DAILY -> "不计日限额"
                LimitExempt.SKIP_ALL -> "不计限额"
                else -> null
            } else null
            val sub = listOfNotNull(
                DateUtils.formatTime(record.date).takeIf { DateUtils.hasTimeOfDay(record.date) },
                record.note.takeIf { it.isNotBlank() },
                exemptTag
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = (if (isExpense) "-" else "+") + formatCentsWithSymbol(record.amountCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isExpense) ExpenseColor else IncomeColor
        )
    }
}
