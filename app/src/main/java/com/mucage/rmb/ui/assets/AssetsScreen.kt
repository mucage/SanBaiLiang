package com.mucage.rmb.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.centsToInput
import com.mucage.rmb.util.formatCents
import com.mucage.rmb.util.formatCentsWithSymbol
import com.mucage.rmb.util.parseAmountToCents
import com.mucage.rmb.util.todayStartMillis
import com.mucage.sync.AutoSavePeriod
import com.mucage.sync.SavingsAuto
import com.mucage.sync.SavingsMath
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 资产页的两个小板块 */
private enum class BalanceBucket { SAVED, AVAILABLE }

/**
 * 资产页：总余额（大板块）+ 攒着 / 可用（小板块）。
 * 攒着只是账面预留，不涉及真实资金搬移；两者都可手动调整，总余额保持不变。
 */
@Composable
fun AssetsScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val balance by Repository.balanceDao.observe().collectAsState(initial = null)
    val anchoredAt = balance?.anchoredAt ?: 0L
    val netFlow by remember(anchoredAt) {
        if (balance == null) flowOf(0L)
        else Repository.recordDao.observeNetFlowSince(anchoredAt)
    }.collectAsState(initial = 0L)

    val b = balance
    val initialized = b != null && b.initialized
    val total = if (initialized) b.balanceCents + netFlow else 0L
    val saved = b?.let { SavingsMath.clampSaved(it.savedCents, total) } ?: 0L
    val available = total - saved

    var showReset by remember { mutableStateOf(false) }
    var editBucket by remember { mutableStateOf<BalanceBucket?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // 大板块：总余额
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text(
                    "总余额",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatCentsWithSymbol(total),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "总余额 = 攒着 + 可用 · 两者只是账面划分，不涉及真实资金搬动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                )
            }
        }

        // 小板块：攒着 / 可用
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BucketCard(
                title = "攒着",
                amountCents = saved,
                hint = "点击调整",
                accent = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer,
                icon = true,
                modifier = Modifier.weight(1f),
                onClick = { editBucket = BalanceBucket.SAVED }
            )
            BucketCard(
                title = "可用",
                amountCents = available,
                hint = "点击调整",
                accent = MaterialTheme.colorScheme.onSurface,
                container = MaterialTheme.colorScheme.surfaceVariant,
                icon = false,
                modifier = Modifier.weight(1f),
                onClick = { editBucket = BalanceBucket.AVAILABLE }
            )
        }

        Spacer(Modifier.height(16.dp))
        AutoSavingsCard(
            enabled = b?.autoSaveEnabled ?: false,
            periodCode = b?.autoSavePeriod ?: AutoSavePeriod.MONTHLY.code,
            amountCents = b?.autoSaveAmountCents ?: 0L,
            countAsExpense = b?.autoSaveCountAsExpense ?: false,
            lastAt = b?.autoSaveLastAt ?: 0L,
            onEnabled = { value -> scope.launch { Repository.updateAutoSavings(enabled = value) } },
            onPeriod = { code -> scope.launch { Repository.updateAutoSavings(period = code) } },
            onAmount = { cents -> scope.launch { Repository.updateAutoSavings(amountCents = cents) } },
            onCountAsExpense = { value ->
                scope.launch { Repository.updateAutoSavings(countAsExpense = value) }
            }
        )

        Spacer(Modifier.height(16.dp))

        // 余额构成
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "上次确认余额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatCentsWithSymbol(b?.balanceCents ?: 0L),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "确认后收支",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        (if (netFlow >= 0) "+" else "") + formatCents(netFlow),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (netFlow >= 0) IncomeColor else ExpenseColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "攒着 + 可用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${formatCents(saved)} + ${formatCents(available)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "所有账目都会影响总余额；自动攒着生成的「攒着」记录只是账面搬移，不会重复扣减总余额。每月 1 日打开时会提示核对一次。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        MonthFlowCard()

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showReset = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("重设当前余额")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showReset && initialized) {
        SetTotalBalanceDialog(
            currentCents = total,
            onDismiss = { showReset = false },
            onConfirm = { cents ->
                scope.launch { Repository.setTotalBalance(cents) }
                showReset = false
            }
        )
    }

    if (editBucket != null && initialized) {
        val isSaved = editBucket == BalanceBucket.SAVED
        SetBucketBalanceDialog(
            title = if (isSaved) "调整攒着" else "调整可用",
            hint = if (isSaved) {
                "总余额 ${formatCentsWithSymbol(total)} 不变，只改变账面划分。当前攒着："
            } else {
                "总余额 ${formatCentsWithSymbol(total)} 不变，只改变账面划分。当前可用："
            },
            currentCents = if (isSaved) saved else available,
            onDismiss = { editBucket = null },
            onConfirm = { cents ->
                scope.launch {
                    if (isSaved) Repository.setSavedBalance(cents)
                    else Repository.setAvailableBalance(cents)
                }
                editBucket = null
            }
        )
    }
}

/** 攒着 / 可用的小板块卡片 */
@Composable
private fun BucketCard(
    title: String,
    amountCents: Long,
    hint: String,
    accent: Color,
    container: Color,
    icon: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = container
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon) {
                    Icon(
                        Icons.Outlined.Savings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accent
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(title, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatCentsWithSymbol(amountCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 自动攒着设置卡片 */
@Composable
private fun AutoSavingsCard(
    enabled: Boolean,
    periodCode: String,
    amountCents: Long,
    countAsExpense: Boolean,
    lastAt: Long,
    onEnabled: (Boolean) -> Unit,
    onPeriod: (String) -> Unit,
    onAmount: (Long) -> Unit,
    onCountAsExpense: (Boolean) -> Unit
) {
    val period = AutoSavePeriod.from(periodCode)
    var amountText by remember { mutableStateOf(centsToInput(amountCents)) }
    LaunchedEffect(amountCents) { amountText = centsToInput(amountCents) }
    val parsedAmount = parseAmountToCents(amountText)
    val nextAt = remember(enabled, periodCode, lastAt) {
        if (!enabled) {
            null
        } else {
            val now = System.currentTimeMillis()
            val anchor = if (lastAt in 1..now) lastAt else SavingsAuto.periodStart(period, now)
            SavingsAuto.nextBoundary(period, anchor)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "自动攒着",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "按周 / 月 / 年把「可用」的一部分转为「攒着」，可用不足时跳过该期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }

            if (enabled) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Text(
                    "结算周期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSavePeriod.entries.forEach { item ->
                        FilterChip(
                            selected = period == item,
                            onClick = { onPeriod(item.code) },
                            label = { Text(item.label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { new ->
                            val filtered = new.filter { it.isDigit() || it == '.' }
                            val parts = filtered.split(".")
                            amountText = if (parts.size > 1) {
                                parts[0] + "." + parts[1].take(2)
                            } else {
                                filtered
                            }
                        },
                        label = { Text("每期转入（元）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { parsedAmount?.let(onAmount) },
                        enabled = parsedAmount != null
                    ) {
                        Text("保存")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("视作支出", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后会生成一笔「攒着」支出，计入日 / 月限额与统计；关闭则只做账面划分",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = countAsExpense, onCheckedChange = onCountAsExpense)
                }

                if (nextAt != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "上次结算：" + (if (lastAt > 0) DateUtils.formatFullDate(lastAt) else "尚未开始") +
                            " · 下次结算：" + DateUtils.formatFullDate(nextAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "两端都会按同步的结算时间自动执行；逐期补足，可用不足的那一期直接跳过。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 本月收支卡片 */
@Composable
private fun MonthFlowCard() {
    val monthStart = remember { DateUtils.startOfMonth(todayStartMillis()) }
    val monthEnd = remember { DateUtils.endOfDay(DateUtils.addMonths(monthStart, 1) - 1) }
    val records by Repository.recordDao.observeBetween(monthStart, monthEnd)
        .collectAsState(initial = emptyList())
    val expense = records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amountCents }
    val income = records.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(Modifier.padding(vertical = 18.dp)) {
            FlowCell("本月支出", expense, ExpenseColor, Modifier.weight(1f))
            FlowCell("本月收入", income, IncomeColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlowCell(name: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
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

/**
 * 首次启动：输入各账户当前余额，每个余额一行，求和作为初始总余额。
 * 完成前阻断使用（无退出入口）。
 */
@Composable
fun InitialBalanceScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }

    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val parsed = lines.map { parseAmountToCents(it) }
    val allValid = lines.isNotEmpty() && parsed.all { it != null }
    val total = parsed.filterNotNull().sum()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "欢迎使用",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "请输入各账户的当前余额，每个余额一行（如微信钱包、支付宝、银行卡等），" +
                "确认后合计将作为初始总余额，之后所有账目只对总余额加减。攒着与可用可在资产页随时调整。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("各账户余额（元，每行一个）") },
            placeholder = { Text("例如：\n520.00\n1280.50\n3620.00") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("合计（初始总余额）", style = MaterialTheme.typography.labelLarge)
            Text(
                formatCentsWithSymbol(total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (total >= 0) IncomeColor else ExpenseColor
            )
        }
        if (lines.isNotEmpty() && !allValid) {
            Spacer(Modifier.height(6.dp))
            Text(
                "有无法识别的行，请检查格式：每行只填一个金额，如 520.00",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (!allValid) return@Button
                val cents = total
                scope.launch {
                    Repository.setTotalBalance(cents)
                    onDone()
                }
            },
            enabled = allValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("开始使用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** 余额核对：首次启动及每月 1 日（即月份变化时）提示一次 */
object BalanceCheck {
    private const val PREFS = "balance_check"
    private const val KEY_MONTH = "month"

    private fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date())

    fun shouldShow(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_MONTH, null) != currentMonth()
    }

    fun markShown(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY_MONTH, currentMonth()).apply()
    }
}

/** 核对当前总余额弹窗：每月 1 日（月份变化后首次打开）提示一次 */
@Composable
fun BalanceCheckDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current by produceState<Long?>(initialValue = null) {
        value = Repository.currentTotalBalance()
    }
    val cents = current ?: return
    SetTotalBalanceDialog(
        currentCents = cents,
        onDismiss = onDismiss,
        onConfirm = { newCents ->
            scope.launch { Repository.setTotalBalance(newCents) }
            onDismiss()
        },
        title = "核对当前余额",
        hint = "本月首次打开，请核对总余额。当前按账目计算的余额如下，如与实际不符请修改：",
        confirmText = "保存",
        dismissText = "下次再说"
    )
}

/** 设置总余额对话框（预填当前计算值，保存后以今天为锚点重新计算） */
@Composable
fun SetTotalBalanceDialog(
    currentCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    title: String = "重设当前余额",
    hint: String = "当前按账目自动计算的总余额：",
    confirmText: String = "保存",
    dismissText: String = "取消"
) {
    var text by rememberSaveable { mutableStateOf(centsToInput(currentCents)) }
    val cents = parseAmountToCents(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "$hint${formatCentsWithSymbol(currentCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() || it == '.' || it == '-' }
                        val parts = filtered.split(".")
                        text = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else filtered
                    },
                    label = { Text("实际总余额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "保存后会保留当前攒着，并自动夹到新的总余额范围内。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { cents?.let(onConfirm) }, enabled = cents != null) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}

/** 调整攒着 / 可用的对话框：只改账面划分，总余额不变 */
@Composable
private fun SetBucketBalanceDialog(
    title: String,
    hint: String,
    currentCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(centsToInput(currentCents)) }
    val cents = parseAmountToCents(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "$hint${formatCentsWithSymbol(currentCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() || it == '.' }
                        val parts = filtered.split(".")
                        text = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else filtered
                    },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "金额会自动限制在 0 到总余额之间；保存后另一端同步即可看到。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { cents?.let(onConfirm) }, enabled = cents != null) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
