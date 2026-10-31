package com.mucage.rmb.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategory
import com.mucage.sync.FixedCategories
import com.mucage.sync.MergeResult
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncCodec
import com.mucage.sync.SyncMerge
import com.mucage.sync.SyncPayload
import com.mucage.sync.SyncRecord
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToLong

/** 支出红 / 收入绿，与安卓端保持一致的配色 */
private val ExpenseColor = Color(0xFFE5484D)
private val IncomeColor = Color(0xFF147D64)
private val NahidaGreen = Color(0xFF3F7D4E)
private val NahidaSoft = Color(0xFFEAF3E4)

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 支持以命令行参数直接打开账本文件：MucageLedger.exe "此地无银三百两-账本-xxx.json" */
fun main(args: Array<String>) = application {
    val initialFile = args.firstOrNull()?.let { path -> runCatching { File(path) }.getOrNull() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "此地无银三百两 · 电脑端",
        state = rememberWindowState(width = 1120.dp, height = 780.dp)
    ) {
        MaterialTheme(colorScheme = lightColorScheme(primary = NahidaGreen, secondary = Color(0xFF7E9E6A))) {
            LedgerApp(initialFile = initialFile)
        }
    }
}

/** 主界面：账本文件读写 + 账目管理 + 余额同步 */
@Composable
private fun LedgerApp(initialFile: File? = null) {
    var records by remember { mutableStateOf<List<SyncRecord>>(emptyList()) }
    var balance by remember { mutableStateOf(SyncBalance(0L, 0L, false, 0L)) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var banner by remember { mutableStateOf("打开或新建一份账本文件，即可与手机互传账目") }
    var editorTarget by remember { mutableStateOf<SyncRecord?>(null) }
    var showRecordEditor by remember { mutableStateOf(false) }
    var showBalanceEditor by remember { mutableStateOf(false) }

    LaunchedEffect(banner) {
        delay(5000)
        if (banner.isNotEmpty()) banner = ""
    }

    fun mergePayload(payload: SyncPayload): MergeResult {
        val byId = records.associateBy { it.syncId }.toMutableMap()
        var inserted = 0
        var updated = 0
        var skipped = 0
        payload.records.forEach { raw ->
            val incoming = SyncMerge.normalize(raw)
            val existing = byId[incoming.syncId]
            when {
                existing == null -> {
                    byId[incoming.syncId] = incoming
                    inserted++
                }
                SyncMerge.shouldWrite(incoming, existing.updatedAt) -> {
                    byId[incoming.syncId] = incoming
                    updated++
                }
                else -> skipped++
            }
        }
        var balanceUpdated = false
        if (SyncMerge.shouldTakeBalance(payload.balance, balance)) {
            balance = payload.balance
            balanceUpdated = true
        }
        records = sortRecords(byId.values.toList())
        return MergeResult(inserted, updated, skipped, balanceUpdated)
    }

    fun loadFrom(file: File) {
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null) {
            banner = "读取失败：${file.name}"
            return
        }
        val payload = SyncCodec.decode(text)
        if (payload == null) {
            banner = "「${file.name}」不是本应用导出的账本文件"
            return
        }
        currentFile = file
        banner = "已合并 ${file.name}：${mergePayload(payload).summary()}"
    }

    // 启动时若带了文件参数（例如从手机导出的账本），直接合并显示
    LaunchedEffect(initialFile) {
        initialFile?.let { loadFrom(it) }
    }

    fun openFile() {
        val chooser = JFileChooser().apply {
            dialogTitle = "选择账本文件（合并到当前账本）"
            fileFilter = FileNameExtensionFilter("账本 JSON 文件 (*.json)", "json")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        loadFrom(chooser.selectedFile)
    }

    fun saveFile(saveAs: Boolean) {
        var target = if (saveAs) null else currentFile
        if (target == null) {
            val chooser = JFileChooser().apply {
                dialogTitle = "保存账本文件"
                selectedFile = File(SyncCodec.fileName(System.currentTimeMillis()))
                fileFilter = FileNameExtensionFilter("账本 JSON 文件 (*.json)", "json")
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
            val chosen = chooser.selectedFile
            target = if (chosen.name.lowercase().endsWith(".json")) chosen else File(chosen.parentFile, chosen.name + ".json")
        }
        val payload = SyncPayload(System.currentTimeMillis(), records, balance)
        if (runCatching { target!!.writeText(SyncCodec.encode(payload)) }.isSuccess) {
            currentFile = target
            banner = "已导出 ${target!!.name}，可发送到手机导入"
        } else {
            banner = "保存失败：${target!!.name}"
        }
    }

    // 删除墓碑只存在于文件与内存中，界面与统计一律只看未删除的账目
    val visibleRecords = records.filterNot { it.deleted }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F5EF))) {
        Header(
            currentFile = currentFile,
            recordCount = visibleRecords.size,
            onOpen = ::openFile,
            onSave = { saveFile(false) },
            onSaveAs = { saveFile(true) },
            onAdd = {
                editorTarget = null
                showRecordEditor = true
            },
            onResetBalance = { showBalanceEditor = true }
        )

        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
            SummaryPanel(
                modifier = Modifier.width(300.dp),
                balance = balance,
                records = visibleRecords,
                onEditBalance = { showBalanceEditor = true }
            )
            Spacer(Modifier.width(14.dp))
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (visibleRecords.isEmpty()) {
                    EmptyLedger()
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(10.dp)) {
                        items(visibleRecords, key = { it.syncId }) { record ->
                            RecordRow(
                                record = record,
                                onEdit = {
                                    editorTarget = record
                                    showRecordEditor = true
                                },
                                onDelete = {
                                    // 软删除：留墓碑并刷新时间，导出后手机导入即可同步删除
                                    val tombstone = record.copy(deleted = true, updatedAt = System.currentTimeMillis())
                                    records = sortRecords(records.map { if (it.syncId == record.syncId) tombstone else it })
                                    banner = "已删除这笔账目，导出账本后手机端导入即可同步删除"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (banner.isNotEmpty()) {
            Surface(color = NahidaSoft, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(15.dp), tint = NahidaGreen)
                    Spacer(Modifier.width(6.dp))
                    Text(banner, style = MaterialTheme.typography.bodySmall, color = Color(0xFF3A4A36))
                }
            }
        }
    }

    if (showRecordEditor) {
        RecordEditorDialog(
            initial = editorTarget,
            onDismiss = { showRecordEditor = false },
            onSave = { saved ->
                val exists = records.any { it.syncId == saved.syncId }
                records = sortRecords(if (exists) records.map { if (it.syncId == saved.syncId) saved else it } else records + saved)
                showRecordEditor = false
                banner = if (exists) "已修改账目" else "已新增一笔账目"
            }
        )
    }

    if (showBalanceEditor) {
        BalanceEditorDialog(
            current = balance,
            onDismiss = { showBalanceEditor = false },
            onSave = {
                balance = it
                showBalanceEditor = false
                banner = "余额已更新，导出后手机导入即可同步"
            }
        )
    }
}

@Composable
private fun Header(
    currentFile: File?,
    recordCount: Int,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onAdd: () -> Unit,
    onResetBalance: () -> Unit
) {
    Surface(color = NahidaGreen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Sticker(name = "nahida_hello", size = 46.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("此地无银三百两", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = (currentFile?.absolutePath ?: "尚未关联文件") + " · $recordCount 笔账目",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onOpen) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("打开/合并")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave) {
                Icon(Icons.Outlined.Save, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("导出")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSaveAs) { Text("另存为") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("记一笔")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onResetBalance) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("余额")
            }
        }
    }
}

@Composable
private fun SummaryPanel(
    modifier: Modifier,
    balance: SyncBalance,
    records: List<SyncRecord>,
    onEditBalance: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val nextMonthStart = remember { LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthRecords = records.filter { it.date in monthStart until nextMonthStart }
    val monthExpense = monthRecords.filter { it.type == CategoryType.EXPENSE.code }.sumOf { it.amountCents }
    val monthIncome = monthRecords.filter { it.type == CategoryType.INCOME.code }.sumOf { it.amountCents }
    // 与安卓端口径一致：总余额 = 锚点余额 + 基准日之后（含当天）的净收支
    val flowSinceAnchor = records.filter { it.date >= balance.anchoredAt }.sumOf { record ->
        if (record.type == CategoryType.INCOME.code) record.amountCents else -record.amountCents
    }
    val totalBalance = balance.balanceCents + flowSinceAnchor

    Surface(modifier = modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前总余额", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (balance.initialized) "¥${formatCents(totalBalance)}" else "未设置",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = NahidaGreen
                    )
                    if (balance.initialized) {
                        Text(
                            "基准日 ${formatDate(balance.anchoredAt)} · 基准 ¥${formatCents(balance.balanceCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Sticker(name = "nahida_cheer", size = 58.dp)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text("本月收支", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            StatLine("本月支出", monthExpense, ExpenseColor)
            Spacer(Modifier.height(6.dp))
            StatLine("本月收入", monthIncome, IncomeColor)
            Spacer(Modifier.height(6.dp))
            StatLine("本月结余", monthIncome - monthExpense, NahidaGreen)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEditBalance, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("设置余额")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, cents: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("¥${formatCents(cents)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun EmptyLedger() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Sticker(name = "nahida_think", size = 96.dp)
        Spacer(Modifier.height(12.dp))
        Text("还没有账目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "先「打开/合并」手机导出的账本文件，或点「记一笔」手动录入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordRow(record: SyncRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val income = record.type == CategoryType.INCOME.code
    val category = FixedCategories.all.firstOrNull { it.id == record.categoryId && it.type.code == record.type }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFBFCF9)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDate(record.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(92.dp)
            )
            Surface(shape = RoundedCornerShape(8.dp), color = NahidaSoft) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFor(category?.icon ?: "more"), null, Modifier.size(14.dp), tint = NahidaGreen)
                    Spacer(Modifier.width(4.dp))
                    Text(category?.name ?: "其他", style = MaterialTheme.typography.labelMedium)
                }
            }
            val exemptText = when (record.limitExempt) {
                1 -> "不计当日"
                2 -> "不计限额"
                else -> ""
            }
            if (exemptText.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(exemptText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                record.note.ifBlank { "—" },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = (if (income) "+" else "-") + "¥" + formatCents(record.amountCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (income) IncomeColor else ExpenseColor
            )
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Edit, "编辑", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordEditorDialog(initial: SyncRecord?, onDismiss: () -> Unit, onSave: (SyncRecord) -> Unit) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: CategoryType.EXPENSE.code) }
    var amountText by remember(initial) { mutableStateOf(initial?.let { centsToInput(it.amountCents) } ?: "") }
    var categoryId by remember(initial) {
        mutableStateOf(
            initial?.categoryId
                ?: if (type == CategoryType.INCOME.code) FixedCategories.INCOME_OTHER else FixedCategories.EXPENSE_FOOD
        )
    }
    var dateText by remember(initial) { mutableStateOf(formatDate(initial?.date ?: System.currentTimeMillis())) }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }

    val categories = if (type == CategoryType.INCOME.code) FixedCategories.income else FixedCategories.expense
    val amountCents = parseAmountToCents(amountText)
    val dateMillis = parseDate(dateText)
    val valid = amountCents != null && dateMillis != null && categories.any { it.id == categoryId }

    EditorScrim(onDismiss) {
        Column(Modifier.padding(22.dp)) {
            Text(if (initial == null) "记一笔" else "编辑账目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == CategoryType.EXPENSE.code,
                    onClick = {
                        type = CategoryType.EXPENSE.code
                        categoryId = FixedCategories.EXPENSE_FOOD
                    },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = type == CategoryType.INCOME.code,
                    onClick = {
                        type = CategoryType.INCOME.code
                        categoryId = FixedCategories.INCOME_SALARY
                    },
                    label = { Text("收入") }
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                label = { Text("金额（元）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text("用途", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = {
                            Icon(iconFor(category.icon), null, Modifier.size(15.dp))
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    label = { Text("日期") },
                    singleLine = true,
                    modifier = Modifier.width(180.dp)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (dateMillis == null) {
                Spacer(Modifier.height(6.dp))
                Text("日期格式应为 2026-10-31", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        val cents = amountCents ?: return@Button
                        val date = dateMillis ?: return@Button
                        onSave(
                            SyncRecord(
                                syncId = initial?.syncId ?: UUID.randomUUID().toString(),
                                amountCents = cents,
                                type = type,
                                categoryId = categoryId,
                                note = note.trim(),
                                date = date,
                                limitExempt = initial?.limitExempt ?: 0,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun BalanceEditorDialog(current: SyncBalance, onDismiss: () -> Unit, onSave: (SyncBalance) -> Unit) {
    var amountText by remember { mutableStateOf(if (current.initialized) centsToInput(current.balanceCents) else "") }
    var dateText by remember { mutableStateOf(formatDate(if (current.anchoredAt > 0) current.anchoredAt else System.currentTimeMillis())) }
    val cents = parseAmountToCents(amountText.replace("-", ""))
    val dateMillis = parseDate(dateText)
    val negative = amountText.trim().startsWith("-")

    EditorScrim(onDismiss) {
        Column(Modifier.padding(22.dp)) {
            Text("设置总余额", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "余额以基准日为准，之后新增的账目会自动在其上增减。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(13) },
                label = { Text("余额（元，可填负数）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it.take(10) },
                label = { Text("基准日期") },
                singleLine = true,
                modifier = Modifier.width(200.dp)
            )
            if (dateMillis == null) {
                Spacer(Modifier.height(6.dp))
                Text("日期格式应为 2026-10-31", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = cents != null && dateMillis != null,
                    onClick = {
                        val value = cents ?: return@Button
                        val date = dateMillis ?: return@Button
                        onSave(
                            SyncBalance(
                                balanceCents = if (negative) -value else value,
                                anchoredAt = date,
                                initialized = true,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                ) { Text("保存") }
            }
        }
    }
}

/** 居中弹层：点击遮罩关闭 */
@Composable
private fun EditorScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) { content() }
    }
}

/** 纳西妲表情装饰：资源缺失时静默跳过，不影响功能 */
@Composable
private fun Sticker(name: String, size: Dp) {
    val bitmap: ImageBitmap? = remember(name) { loadSticker(name) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/** 图片通过类加载器读取，打包成 app-image 后依然可用 */
private object StickerHolder

private fun loadSticker(name: String): ImageBitmap? = runCatching {
    val stream = StickerHolder::class.java.getResourceAsStream("/images/$name.jpg")
        ?: return@runCatching null
    stream.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
}.getOrNull()

private fun sortRecords(list: List<SyncRecord>): List<SyncRecord> =
    list.sortedWith(compareByDescending<SyncRecord> { it.date }.thenByDescending { it.updatedAt })

private fun iconFor(icon: String): ImageVector = when (icon) {
    "restaurant" -> Icons.Outlined.Restaurant
    "shopping" -> Icons.Outlined.ShoppingBag
    "entertainment" -> Icons.Outlined.SportsEsports
    "education" -> Icons.Outlined.School
    "transport" -> Icons.Outlined.DirectionsBus
    "salary" -> Icons.Outlined.Work
    "bonus" -> Icons.Outlined.EmojiEvents
    "finance" -> Icons.Outlined.TrendingUp
    "parttime" -> Icons.Outlined.Schedule
    "redpacket" -> Icons.Outlined.Redeem
    else -> Icons.Outlined.MoreHoriz
}

private fun parseAmountToCents(text: String): Long? {
    val value = text.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return (value * 100).roundToLong()
}

private fun centsToInput(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString() else String.format("%.2f", cents / 100.0)

private fun formatCents(cents: Long): String = String.format("%,.2f", cents / 100.0)

private fun parseDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim(), dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private fun formatDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
