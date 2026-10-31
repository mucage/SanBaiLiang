package com.mucage.rmb.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.formatCents
import com.mucage.rmb.util.formatCentsWithSymbol
import com.mucage.rmb.util.todayStartMillis
import kotlin.math.roundToInt

/** 统计周期类型 */
private enum class PeriodType { DAY, WEEK, MONTH, YEAR, CUSTOM }

/** 分类配色盘 */
private val Palette = listOf(
    Color(0xFF147D64), Color(0xFFF5B301), Color(0xFFE5484D), Color(0xFF3B82F6), Color(0xFF8B5CF6),
    Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF97316), Color(0xFF64748B), Color(0xFF84CC16)
)

/** 自定义区间最大天数 */
private const val MAX_CUSTOM_DAYS = 366L

/** 统计页：日 / 周 / 月 / 年 / 自定义区间的总支出、总收入与分类占比 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(contentPadding: PaddingValues) {
    var periodType by rememberSaveable { mutableStateOf(PeriodType.MONTH) }
    var anchor by rememberSaveable { mutableLongStateOf(todayStartMillis()) }
    var customStart by rememberSaveable { mutableLongStateOf(todayStartMillis()) }
    var customEnd by rememberSaveable { mutableLongStateOf(todayStartMillis()) }
    var rangeTooLong by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 当前展示类型：支出分布 / 收入分布
    var breakdownType by rememberSaveable { mutableStateOf(RecordType.EXPENSE) }

    val start: Long
    val end: Long
    when (periodType) {
        PeriodType.DAY -> {
            start = DateUtils.startOfDay(anchor); end = DateUtils.endOfDay(anchor)
        }
        PeriodType.WEEK -> {
            start = DateUtils.startOfWeek(anchor); end = DateUtils.endOfDay(DateUtils.addDays(start, 6))
        }
        PeriodType.MONTH -> {
            start = DateUtils.startOfMonth(anchor); end = DateUtils.endOfDay(DateUtils.addMonths(start, 1) - 1)
        }
        PeriodType.YEAR -> {
            start = DateUtils.startOfYear(anchor); end = DateUtils.endOfDay(DateUtils.addYears(start, 1) - 1)
        }
        PeriodType.CUSTOM -> {
            start = customStart; end = DateUtils.endOfDay(customEnd)
            if (end - start > MAX_CUSTOM_DAYS * 86_400_000L) {
                rangeTooLong = true
            } else {
                rangeTooLong = false
            }
        }
    }

    val records by remember(start, end) {
        Repository.recordDao.observeBetween(start, end)
    }.collectAsState(initial = emptyList())
    val categories by Repository.categoryDao.observeAll().collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val expense = records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amountCents }
    val income = records.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents }

    // 分类金额汇总（降序）
    val breakdown = remember(records, breakdownType, categoryMap) {
        records.filter { it.type == breakdownType }
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                CategoryAmount(
                    categoryId = catId,
                    name = categoryMap[catId]?.name ?: "未知",
                    icon = categoryMap[catId]?.icon ?: "more",
                    amount = list.sumOf { it.amountCents }
                )
            }
            .sortedByDescending { it.amount }
    }
    val breakdownTotal = breakdown.sumOf { it.amount }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // 周期切换
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PeriodType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = periodType == type,
                    onClick = { periodType = type },
                    shape = SegmentedButtonDefaults.itemShape(index, PeriodType.entries.size)
                ) {
                    Text(
                        when (type) {
                            PeriodType.DAY -> "日"
                            PeriodType.WEEK -> "周"
                            PeriodType.MONTH -> "月"
                            PeriodType.YEAR -> "年"
                            PeriodType.CUSTOM -> "自定义"
                        }
                    )
                }
            }
        }

        // 区间导航（自定义区间显示起止日期按钮）
        if (periodType == PeriodType.CUSTOM) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateRangeButton(DateUtils.formatFullDate(customStart), Modifier.weight(1f)) {
                    showStartPicker = true
                }
                Text("至", modifier = Modifier.padding(horizontal = 8.dp))
                DateRangeButton(DateUtils.formatFullDate(customEnd), Modifier.weight(1f)) {
                    showEndPicker = true
                }
            }
            if (rangeTooLong) {
                Text(
                    "自定义区间不能超过 366 天，请调整起止日期",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { anchor = shiftAnchor(periodType, anchor, -1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一周期")
                }
                Text(
                    periodLabel(periodType, anchor, start, end),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { anchor = shiftAnchor(periodType, anchor, 1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一周期")
                }
            }
        }

        // 汇总卡片
        SummaryCards(expense, income)
        Spacer(Modifier.height(8.dp))

        // 分类占比
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "分类占比",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = breakdownType == RecordType.EXPENSE,
                onClick = { breakdownType = RecordType.EXPENSE },
                label = { Text("支出") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = breakdownType == RecordType.INCOME,
                onClick = { breakdownType = RecordType.INCOME },
                label = { Text("收入") }
            )
        }

        if (breakdown.isEmpty()) {
            Text(
                "该区间暂无${if (breakdownType == RecordType.EXPENSE) "支出" else "收入"}记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )
        } else {
            // 环形图 + 分类列表
            DonutChartWithCenter(
                segments = breakdown.mapIndexed { index, item ->
                    DonutSegment(item.amount, Palette[index % Palette.size])
                },
                centerText = formatCents(breakdownTotal),
                centerLabel = if (breakdownType == RecordType.EXPENSE) "总支出" else "总收入",
                modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
            breakdown.forEachIndexed { index, item ->
                CategoryBreakdownRow(
                    item = item,
                    color = Palette[index % Palette.size],
                    total = breakdownTotal
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // 自定义区间的起止日期选择
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStart)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customStart = it }
                    showStartPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("取消") }
            }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEnd)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customEnd = it }
                    showEndPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("取消") }
            }
        ) { DatePicker(state = state) }
    }
}

private data class CategoryAmount(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val amount: Long
)

private data class DonutSegment(val value: Long, val color: Color)

/** 区间标签 */
private fun periodLabel(type: PeriodType, anchor: Long, start: Long, end: Long): String =
    when (type) {
        PeriodType.DAY -> DateUtils.formatDayWithWeek(anchor)
        PeriodType.WEEK -> DateUtils.formatWeekRange(start, end)
        PeriodType.MONTH -> DateUtils.formatMonth(anchor)
        PeriodType.YEAR -> DateUtils.formatYear(anchor)
        PeriodType.CUSTOM -> ""
    }

/** 切换周期 */
private fun shiftAnchor(type: PeriodType, anchor: Long, delta: Int): Long =
    when (type) {
        PeriodType.DAY -> DateUtils.addDays(anchor, delta)
        PeriodType.WEEK -> DateUtils.addDays(anchor, delta * 7)
        PeriodType.MONTH -> DateUtils.addMonths(anchor, delta)
        PeriodType.YEAR -> DateUtils.addYears(anchor, delta)
        PeriodType.CUSTOM -> anchor
    }

@Composable
private fun DateRangeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 汇总卡片：总支出 / 总收入 / 结余 */
@Composable
private fun SummaryCards(expense: Long, income: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(Modifier.padding(vertical = 20.dp)) {
            SummaryCell("总支出", expense, ExpenseColor, Modifier.weight(1f))
            SummaryCell("总收入", income, IncomeColor, Modifier.weight(1f))
            val balance = income - expense
            SummaryCell(
                "结余",
                balance,
                if (balance >= 0) IncomeColor else ExpenseColor,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(name: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(6.dp))
        Text(
            formatCentsWithSymbol(cents),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** 分类占比行：色点 + 名称 + 进度条 + 金额 + 占比 */
@Composable
private fun CategoryBreakdownRow(item: CategoryAmount, color: Color, total: Long) {
    val fraction = if (total > 0) item.amount.toFloat() / total else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatCentsWithSymbol(item.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/** 环形图，中心显示总额 */
@Composable
private fun DonutChartWithCenter(
    segments: List<DonutSegment>,
    centerText: String,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = segments.sumOf { it.value }
            if (total <= 0) return@Canvas
            val strokeWidth = size.minDimension * 0.13f
            var startAngle = -90f
            segments.filter { it.value > 0 }.forEach { segment ->
                val sweep = segment.value.toFloat() / total * 360f
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                centerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
