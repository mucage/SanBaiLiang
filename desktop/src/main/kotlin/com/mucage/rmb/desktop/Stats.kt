package com.mucage.rmb.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mucage.sync.CategoryType
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncCategory
import com.mucage.sync.SyncRecord
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

private val chartPalette = listOf(
    Color(0xFF3F7D4E),
    Color(0xFFE0A33E),
    Color(0xFF6B8F7A),
    Color(0xFFB45A5A),
    Color(0xFF4C7C9B),
    Color(0xFF8A7BA8)
)

private fun freeColor(index: Int): Color = chartPalette[index % chartPalette.size]

internal fun SyncRecord.day(): LocalDate =
    Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun SyncRecord.monthKey(): String = YearMonth.from(day()).toString()

private fun startOfMonth(month: YearMonth): Long =
    month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun monthRange(month: YearMonth): LongRange =
    startOfMonth(month) until startOfMonth(month.plusMonths(1))

/** 统计页：月度概览、分类占比、近半年趋势、消费日历与本地规则点评 */
@Composable
internal fun StatsPage(
    records: List<SyncRecord>,
    balance: SyncBalance,
    categories: List<SyncCategory>,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    val today = LocalDate.now()
    val monthRecords = records.filter { monthRange(month).contains(it.date) }
    val expenses = monthRecords.filter { it.type == CategoryType.EXPENSE.code }
    val incomes = monthRecords.filter { it.type == CategoryType.INCOME.code }
    val expenseTotal = expenses.sumOf { it.amountCents }
    val incomeTotal = incomes.sumOf { it.amountCents }
    val net = incomeTotal - expenseTotal

    val daysInMonth = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    val dailyAverage = if (daysInMonth > 0) expenseTotal / daysInMonth else 0L
    val activeDays = monthRecords.map { it.day() }.distinct().size
    val largest = expenses.maxByOrNull { it.amountCents }

    val slices = DesktopLedgerStore
        .visibleCategories(categories, CategoryType.EXPENSE.code)
        .mapIndexed { index, category ->
            Triple(category.name, freeColor(index), expenses.filter { it.categoryId == category.id }.sumOf { it.amountCents })
        }.filter { it.third > 0 }.sortedByDescending { it.third }

    val previousExpense = records
        .filter { monthRange(month.minusMonths(1)).contains(it.date) && it.type == CategoryType.EXPENSE.code }
        .sumOf { it.amountCents }
    val commentary = remember(records, categories, month, expenseTotal, incomeTotal, slices) {
        buildCommentary(
            month, records, expenseTotal, incomeTotal, previousExpense, activeDays, slices,
            largest?.let { record ->
                record.copy(note = record.note.ifBlank {
                    DesktopLedgerStore.categoryName(categories, record.categoryId, record.type)
                })
            }
        )
    }

    val canGoNext = month < YearMonth.from(today)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "本月 ${monthRecords.size} 笔账目 · 记账 $activeDays 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, "上个月")
                }
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = canGoNext) {
                    Icon(Icons.Outlined.ChevronRight, "下个月")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("本月支出", "¥${formatCents(expenseTotal)}", "日均 ¥${formatCents(dailyAverage)}", ExpenseColor, Modifier.weight(1f))
                StatTile("本月收入", "¥${formatCents(incomeTotal)}", if (incomeTotal > 0L) "共 ${incomes.size} 笔" else "暂无收入", IncomeColor, Modifier.weight(1f))
                StatTile("本月结余", "¥${formatCents(net)}", if (net >= 0) "收支为正" else "支出超出收入", if (net >= 0) NahidaGreen else ExpenseColor, Modifier.weight(1f))
                StatTile("记账天数", "$activeDays 天", if (month == YearMonth.from(today)) "本月已过 $daysInMonth 天" else "整月", Color(0xFF4C7C9B), Modifier.weight(1f))
                StatTile("最大单笔", largest?.let { "¥${formatCents(it.amountCents)}" } ?: "—", largest?.note?.ifBlank { "无备注" } ?: "本月没有支出", Color(0xFF8A7BA8), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CategoryCard(slices, expenseTotal, Modifier.weight(1.05f))
                TrendCard(records, month, Modifier.weight(0.95f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HeatmapCard(expenses, month, Modifier.weight(1.05f))
                CommentaryCard(commentary, Modifier.weight(0.95f))
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, hint: String, accent: Color, modifier: Modifier) {
    Surface(modifier = modifier.height(96.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = LocalCardAlpha.current)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryCard(slices: List<Triple<String, Color, Long>>, total: Long, modifier: Modifier) {
    Surface(modifier = modifier.heightIn(min = 250.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = LocalCardAlpha.current)) {
        Column(Modifier.padding(16.dp)) {
            Text("支出构成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (slices.isEmpty()) {
                Text("本月还没有支出记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(128.dp)) {
                        val stroke = 18.dp.toPx()
                        var start = -90f
                        slices.forEach { (_, color, cents) ->
                            val sweep = if (total > 0) (cents.toFloat() / total) * 360f else 0f
                            drawArc(
                                color = color,
                                startAngle = start,
                                sweepAngle = max(sweep - 1.5f, 0.5f),
                                useCenter = false,
                                style = Stroke(width = stroke)
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("合计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${formatCents(total)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slices.forEach { (name, color, cents) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(38.dp))
                            Text(
                                "${((cents.toFloat() / total) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(38.dp)
                            )
                            Text("¥${formatCents(cents)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(records: List<SyncRecord>, month: YearMonth, modifier: Modifier) {
    val months = remember(month) { (0 until 6).map { month.minusMonths((5 - it).toLong()) } }
    val data = remember(records, month) {
        months.map { item ->
            val range = monthRange(item)
            val expense = records.filter { it.date in range && it.type == CategoryType.EXPENSE.code }.sumOf { it.amountCents }
            val income = records.filter { it.date in range && it.type == CategoryType.INCOME.code }.sumOf { it.amountCents }
            Triple(item, expense, income)
        }
    }
    val peak = max(data.maxOfOrNull { max(it.second, it.third) } ?: 0L, 1L)

    Surface(modifier = modifier.heightIn(min = 250.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = LocalCardAlpha.current)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("近 6 个月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                LegendDot(ExpenseColor, "支出")
                Spacer(Modifier.width(10.dp))
                LegendDot(IncomeColor, "收入")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
                data.forEach { (item, expense, income) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TrendBar(expense, peak, ExpenseColor)
                            TrendBar(income, peak, IncomeColor)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${item.monthValue}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item == month) NahidaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (item == month) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendBar(cents: Long, peak: Long, color: Color) {
    val height = if (cents <= 0) 2.dp else (max(4f, (cents.toFloat() / peak) * 96f)).dp
    Box(
        Modifier
            .width(9.dp)
            .height(height)
            .background(if (cents <= 0) color.copy(alpha = 0.15f) else color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatmapCard(expenses: List<SyncRecord>, month: YearMonth, modifier: Modifier) {
    val byDay = remember(expenses) { expenses.groupBy { it.day().dayOfMonth }.mapValues { entry -> entry.value.sumOf { it.amountCents } } }
    val peak = max(byDay.values.maxOrNull() ?: 0L, 1L)
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val dayCount = month.lengthOfMonth()

    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = LocalCardAlpha.current)) {
        Column(Modifier.padding(16.dp)) {
            Text("消费日历", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val cells = firstOffset + dayCount
            val rows = (cells + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val index = row * 7 + col
                        val day = index - firstOffset + 1
                        if (day in 1..dayCount) {
                            val cents = byDay[day] ?: 0L
                            val ratio = if (cents > 0) cents.toFloat() / peak else 0f
                            val cellColor = if (cents > 0) {
                                NahidaGreen.copy(alpha = 0.18f + 0.62f * ratio)
                            } else {
                                Color(0xFFF1F3F0)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .padding(2.dp)
                                    .background(cellColor, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ratio > 0.45f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (cents > 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        } else {
                            Box(Modifier.weight(1f).height(34.dp).padding(2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "颜色越深表示当天支出越多，最深一天 ¥${formatCents(peak)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentaryCard(lines: List<String>, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color(0xFFF1F6EC).copy(alpha = LocalCardAlpha.current)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sticker("nahida_think", 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("纳西妲的账本点评", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("根据本机账目自动生成，不会联网", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = NahidaGreen.copy(alpha = 0.18f))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                lines.forEach { line ->
                    Row {
                        Box(Modifier.padding(top = 5.dp).size(6.dp).background(NahidaGreen, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall, color = Color(0xFF33422F))
                    }
                }
            }
        }
    }
}

private fun buildCommentary(
    month: YearMonth,
    records: List<SyncRecord>,
    expense: Long,
    income: Long,
    previousExpense: Long,
    activeDays: Int,
    slices: List<Triple<String, Color, Long>>,
    largest: SyncRecord?
): List<String> {
    val lines = mutableListOf<String>()
    if (expense == 0L && income == 0L) {
        return listOf("这个月还没有任何账目，先把每一笔都记下来，月底才有得看。")
    }
    if (previousExpense > 0L) {
        val diff = expense - previousExpense
        val ratio = (diff.toDouble() / previousExpense * 100).toInt()
        lines += when {
            diff > 0 -> "本月支出比上月多出 ${ratio}%，多了 ¥${formatCents(diff)}，可以看看是哪一类在悄悄涨。"
            diff < 0 -> "本月支出比上月少了 ${-ratio}%，省下 ¥${formatCents(-diff)}，保持住。"
            else -> "本月支出和上月刚好持平，都是 ¥${formatCents(expense)}。"
        }
    } else {
        lines += "上月没有可对比的支出记录，这个月的数字就是新的基线。"
    }
    slices.firstOrNull()?.let { (name, _, cents) ->
        val share = (cents.toDouble() / expense * 100).toInt()
        lines += "花得最多的是「$name」，共 ¥${formatCents(cents)}，占本月支出的 $share%。"
    }
    largest?.let {
        val note = it.note.ifBlank { "未填写备注" }
        lines += "最大的一笔是「$note」¥${formatCents(it.amountCents)}，记得确认它是不是必要开销。"
    }
    if (income > 0L) {
        val net = income - expense
        lines += "本月收入 ¥${formatCents(income)}，收支${if (net >= 0) "结余 ¥${formatCents(net)}" else "缺口 ¥${formatCents(-net)}"}。"
    } else {
        lines += "本月还没有记录收入，别忘了把工资或红包也记上。"
    }
    if (activeDays > 0) {
        val avg = expense / if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
        lines += "记账 $activeDays 天，日均支出 ¥${formatCents(avg)}。"
    }
    return lines
}
