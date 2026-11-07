package com.mucage.rmb.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mucage.rmb.data.CategoryEntity
import com.mucage.rmb.data.LimitExempt
import com.mucage.rmb.data.RecordEntity
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.components.AmountKeypad
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.centsToInput
import com.mucage.rmb.util.parseAmountToCents
import com.mucage.rmb.util.todayStartMillis
import java.util.Calendar
import kotlinx.coroutines.launch

/** 记账全屏对话框：新建或编辑一笔账目 */
@Composable
fun AddRecordDialog(
    editing: RecordEntity?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AddRecordContent(editing = editing, onDismiss = onDismiss, onSaved = onSaved)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordContent(
    editing: RecordEntity?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var type by rememberSaveable(editing) {
        mutableStateOf(editing?.type ?: RecordType.EXPENSE)
    }
    var amountText by rememberSaveable(editing) {
        mutableStateOf(if (editing != null) centsToInput(editing.amountCents) else "")
    }
    var categoryId by rememberSaveable(editing) {
        mutableLongStateOf(editing?.categoryId ?: 0L)
    }
    var date by rememberSaveable(editing) { mutableLongStateOf(editing?.date ?: System.currentTimeMillis()) }
    var note by rememberSaveable(editing) { mutableStateOf(editing?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val allCategories by Repository.categoryDao.observeByType(type).collectAsState(initial = emptyList())
    // 「攒着」是系统分类，只用于自动攒着的账面划转：新建时不出现，编辑划转记录时只保留它
    val categories = if (editing?.savingsTransfer == true) {
        allCategories.filter { it.id == com.mucage.sync.FixedCategories.EXPENSE_SAVINGS }
    } else {
        allCategories.filter { it.id != com.mucage.sync.FixedCategories.EXPENSE_SAVINGS }
    }

    // 分类默认选中：编辑时保留原分类，否则取第一个；切换类型时自动落到新类型
    LaunchedEffect(categories) {
        if (categoryId == 0L || categories.none { it.id == categoryId }) {
            categoryId = categories.firstOrNull()?.id ?: 0L
        }
    }

    val amountCents = parseAmountToCents(amountText)
    val canSave = amountCents != null && amountCents > 0 && categoryId != 0L
    val context = androidx.compose.ui.platform.LocalContext.current

    fun save() {
        val cents = amountCents ?: return
        val record = RecordEntity(
            id = editing?.id ?: 0L,
            amountCents = cents,
            type = type,
            categoryId = categoryId,
            note = note.trim(),
            date = date,
            limitExempt = editing?.limitExempt ?: LimitExempt.NONE,
            // 编辑时保留同步标识，仅刷新修改时间；新建由实体默认值生成
            syncId = editing?.syncId ?: java.util.UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis(),
            deleted = editing?.deleted ?: false,
            savingsTransfer = editing?.savingsTransfer ?: false
        )
        scope.launch {
            if (editing == null) {
                Repository.recordDao.insert(record)
                // 新账单入账后检测支出进度是否超过时间进度
                com.mucage.rmb.auto.SpendingPace.check(context)
            } else {
                Repository.recordDao.update(record)
            }
            onSaved()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (editing == null) "记一笔" else "编辑账目",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭")
            }
        }

        // 支出 / 收入 切换
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = type == RecordType.EXPENSE,
                onClick = { type = RecordType.EXPENSE },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = ExpenseColor,
                    activeContentColor = Color.White
                )
            ) { Text("支出") }
            SegmentedButton(
                selected = type == RecordType.INCOME,
                onClick = { type = RecordType.INCOME },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = IncomeColor,
                    activeContentColor = Color.White
                )
            ) { Text("收入") }
        }

        // 金额显示
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "¥${amountText.ifEmpty { "0.00" }}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (type == RecordType.EXPENSE) ExpenseColor else IncomeColor
            )
        }

        // 分类网格
        Text("用途", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryCell(
                    category = category,
                    selected = category.id == categoryId,
                    onClick = { categoryId = category.id }
                )
            }
        }

        // 日期 + 时间 + 备注
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(DateUtils.formatFullDate(date))
            }
            TextButton(onClick = { showTimePicker = true }) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(DateUtils.formatTime(date))
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("备注（选填）") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // 数字键盘 + 保存
        AmountKeypad(
            onDigit = { d -> amountText = inputAmount(amountText, d) },
            onBackspace = { amountText = amountText.dropLast(1) }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = ::save,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (editing == null) "保存" else "保存修改",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { date = DateUtils.mergeDateAndTime(it, date) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val clock = remember(date) { Calendar.getInstance().apply { timeInMillis = date } }
        val timeState = rememberTimePickerState(
            initialHour = clock.get(Calendar.HOUR_OF_DAY),
            initialMinute = clock.get(Calendar.MINUTE),
            is24Hour = true
        )
        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                    Text("选择时间", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = timeState)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                        TextButton(onClick = {
                            val picked = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                            }.timeInMillis
                            date = DateUtils.mergeDateAndTime(date, picked)
                            showTimePicker = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}

/**
 * 金额输入规则：最多两位小数、长度上限、禁止多余前导零。
 * 返回新的金额文本。
 */
internal fun inputAmount(current: String, d: Char): String {
    if (current.length >= 9) return current
    return when {
        d == '.' && current.contains('.') -> current
        d == '.' && current.isEmpty() -> "0."
        d != '.' && current.contains('.') && current.substringAfter('.').length >= 2 -> current
        current == "0" && d != '.' -> d.toString()
        else -> current + d
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) bg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon(category.icon),
                contentDescription = category.name,
                tint = fg,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) fg else MaterialTheme.colorScheme.onSurface
        )
    }
}
