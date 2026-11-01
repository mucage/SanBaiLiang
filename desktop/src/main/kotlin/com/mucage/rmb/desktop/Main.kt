package com.mucage.rmb.desktop

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.MergeResult
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncCategory
import com.mucage.sync.SyncCodec
import com.mucage.sync.SyncMerge
import com.mucage.sync.SyncPayload
import com.mucage.sync.SyncRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 支出红 / 收入绿，与安卓端保持一致 */
internal val ExpenseColor = Color(0xFFD94A4F)
internal val IncomeColor = Color(0xFF147D64)
internal val NahidaGreen = Color(0xFF3F7D4E)
internal val NahidaDeep = Color(0xFF2C5A38)
internal val NahidaSoft = Color(0xFFEAF3E4)
private val CanvasColor = Color(0xFFF4F1E9)

/** 板块（明细列表、统计卡片）的不透明度，可在「背景」设置里调整 */
internal val LocalCardAlpha = staticCompositionLocalOf { 0.92f }

private enum class TypeFilter(val label: String) { ALL("全部"), EXPENSE("支出"), INCOME("收入") }

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 启动入口：
 * - MucageLedger.exe --mcp            以 MCP stdio 服务方式运行（供 AI Agent 调用，不打开窗口）
 * - MucageLedger.exe "账本.json"       直接打开并合并指定账本文件
 */
fun main(args: Array<String>) {
    if (args.any { it == "--mcp" }) {
        com.mucage.rmb.desktop.mcp.McpServer.run()
        return
    }
    application {
        val initialFile = args.firstOrNull()?.let { path -> runCatching { File(path) }.getOrNull() }
        val (savedWidth, savedHeight) = DesktopPrefs.loadWindowSize()
        Window(
            onCloseRequest = ::exitApplication,
            title = "此地无银三百两 · 电脑端",
            state = rememberWindowState(width = savedWidth.dp, height = savedHeight.dp)
        ) {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = NahidaGreen,
                    secondary = Color(0xFF7E9E6A),
                    background = CanvasColor,
                    surface = Color.White
                )
            ) {
                LedgerApp(initialFile = initialFile, owner = window)
            }
            RememberWindowSize(window)
        }
    }
}

/** 记住窗口大小：拖动结束后写入本地设置，下次启动自动恢复 */
@Composable
private fun RememberWindowSize(window: java.awt.Window) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    DisposableEffect(window, density) {
        var saveJob: Job? = null
        val listener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                saveJob?.cancel()
                saveJob = scope.launch {
                    delay(500)
                    val width = (window.width / density).roundToInt().coerceIn(900, 2600)
                    val height = (window.height / density).roundToInt().coerceIn(640, 1600)
                    DesktopPrefs.saveWindowSize(width, height)
                }
            }
        }
        window.addComponentListener(listener)
        onDispose {
            saveJob?.cancel()
            window.removeComponentListener(listener)
        }
    }
}

/** 主界面：本机账本自动保存 + 账目管理 + 统计 + 分类 + 设置 */
@Composable
private fun LedgerApp(initialFile: File?, owner: Frame) {
    val initialLedger = remember { DesktopLedgerStore.load() }
    var records by remember { mutableStateOf(initialLedger.records) }
    var balance by remember { mutableStateOf(initialLedger.balance) }
    var categories by remember { mutableStateOf(initialLedger.categories) }
    val ledgerFile = DesktopLedgerStore.file
    var banner by remember { mutableStateOf("账目会自动保存到本机账本，可随时导出给手机") }
    var bannerError by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var monthFilter by remember { mutableStateOf<String?>(null) }
    var statMonth by remember { mutableStateOf(YearMonth.from(LocalDate.now())) }
    var undoDeletes by remember { mutableStateOf<List<SyncRecord>?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editorTarget by remember { mutableStateOf<SyncRecord?>(null) }
    var showRecordEditor by remember { mutableStateOf(false) }
    var showBalanceEditor by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var lastSavedAt by remember { mutableStateOf<Long?>(null) }
    /** 导入后若本机存在对方文件没有的账目，在设置页持续提示用户反向导出 */
    var syncNotice by remember { mutableStateOf<String?>(null) }

    // 背景偏好只保存在本机：自定义图片路径 + 背景不透明度 + 板块不透明度
    val savedSettings = remember { DesktopPrefs.load() }
    var bgPath by remember { mutableStateOf(savedSettings.backgroundPath) }
    var bgOpacity by remember { mutableStateOf(savedSettings.backgroundOpacity) }
    var cardOpacity by remember { mutableStateOf(savedSettings.cardOpacity) }
    val bgBitmap by produceState<ImageBitmap?>(null, bgPath) {
        value = withContext(Dispatchers.IO) { DesktopImages.background(bgPath) }
    }
    LaunchedEffect(bgPath, bgOpacity, cardOpacity) {
        DesktopPrefs.save(DesktopPrefs.Settings(bgPath, bgOpacity, cardOpacity))
    }

    fun notify(message: String, error: Boolean = false) {
        banner = message
        bannerError = error
    }

    // 本机账本自动保存：账目、余额、分类任一变化后延迟写入，避免频繁写盘
    LaunchedEffect(records, balance, categories) {
        delay(700)
        val result = withContext(Dispatchers.IO) {
            DesktopLedgerStore.save(LedgerData(records, balance, categories))
        }
        result.onSuccess { lastSavedAt = System.currentTimeMillis() }
            .onFailure { notify("自动保存失败：${it.javaClass.simpleName}: ${it.message ?: "未知错误"}", true) }
    }

    LaunchedEffect(banner, undoDeletes) {
        if (banner.isNotEmpty()) {
            delay(if (undoDeletes != null) 15000 else 6000)
            banner = ""
            undoDeletes = null
        }
    }

    fun mergePayload(payload: SyncPayload): MergeResult {
        val merged = DesktopLedgerStore.merge(LedgerData(records, balance, categories), payload)
        records = merged.data.records
        balance = merged.data.balance
        categories = merged.data.categories
        return merged.result
    }

    fun loadFrom(file: File) {
        val payload = DesktopLedgerStore.readPayload(file)
        if (payload == null) {
            notify("「${file.name}」不是本应用导出的账本文件", true)
            return
        }
        val result = mergePayload(payload)
        syncNotice = if (result.localOnly > 0) {
            "电脑端有 ${result.localOnly} 笔账目不在「${file.name}」里，已全部保留。记得点「导出给手机」，让手机也同步到这些新记账。"
        } else {
            null
        }
        notify("已合并 ${file.name}：${result.summary()}（原文件未改动）")
    }

    LaunchedEffect(initialFile) {
        initialFile?.let { loadFrom(it) }
    }

    fun saveNow() {
        val result = DesktopLedgerStore.save(LedgerData(records, balance, categories))
        if (result.isSuccess) {
            lastSavedAt = System.currentTimeMillis()
            notify("已保存到本机账本 · ${records.count { !it.deleted }} 笔账目\n${ledgerFile.absolutePath}")
        } else {
            val error = result.exceptionOrNull()
            notify("保存失败：${error?.javaClass?.simpleName ?: "未知错误"}: ${error?.message ?: ""}", true)
        }
    }

    fun exportToPhone() {
        val chosen = chooseSaveFile(owner, SyncCodec.fileName(System.currentTimeMillis())) ?: return
        val target = if (chosen.name.lowercase().endsWith(".json")) chosen else File(chosen.parentFile, chosen.name + ".json")
        val result = DesktopLedgerStore.exportTo(target, LedgerData(records, balance, categories))
        if (result.isSuccess) {
            syncNotice = null
            notify("已导出 ${target.name}（${records.count { !it.deleted }} 笔账目），可在手机「设置 → 数据同步」导入\n${target.absolutePath}")
        } else {
            val error = result.exceptionOrNull()
            notify("导出失败：${error?.javaClass?.simpleName ?: "未知错误"}: ${error?.message ?: ""}", true)
        }
    }

    fun deleteRecords(targets: List<SyncRecord>) {
        if (targets.isEmpty()) return
        val now = System.currentTimeMillis()
        val ids = targets.map { it.syncId }.toSet()
        records = sortRecords(records.map { if (it.syncId in ids) it.copy(deleted = true, updatedAt = now) else it })
        undoDeletes = targets
        selectedIds = emptySet()
        notify("已删除 ${targets.size} 笔，15 秒内可撤销；导出给手机后会同步删除")
    }

    fun undoDelete() {
        val restore = undoDeletes ?: return
        val now = System.currentTimeMillis()
        val ids = restore.map { it.syncId }.toSet()
        records = sortRecords(
            records.map { if (it.syncId in ids) it.copy(deleted = false, updatedAt = now) else it }
        )
        undoDeletes = null
        notify("已撤销删除")
    }

    fun toggleSelect(syncId: String) {
        selectedIds = if (syncId in selectedIds) selectedIds - syncId else selectedIds + syncId
    }

    // 删除墓碑只存在于文件与内存中，界面与统计一律只看未删除的账目
    val visibleRecords = records.filterNot { it.deleted }
    val availableMonths = visibleRecords
        .map { YearMonth.from(it.day()) }
        .distinct()
        .sortedDescending()
        .take(8)

    val filtered = visibleRecords.filter { record ->
        val typeOk = when (typeFilter) {
            TypeFilter.ALL -> true
            TypeFilter.EXPENSE -> record.type == CategoryType.EXPENSE.code
            TypeFilter.INCOME -> record.type == CategoryType.INCOME.code
        }
        val monthOk = monthFilter?.let { record.monthKey() == it } ?: true
        typeOk && monthOk && record.matchesQuery(query.trim(), categories)
    }

    CompositionLocalProvider(LocalCardAlpha provides cardOpacity) {
    Box(Modifier.fillMaxSize().background(CanvasColor)) {
        if (bgBitmap != null && bgOpacity > 0f) {
            Image(
                bitmap = bgBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(bgOpacity)
            )
            // 极淡蒙层，仅用于压住图片亮度，内容卡片本身不透明
            Box(Modifier.fillMaxSize().background(CanvasColor.copy(alpha = 0.12f)))
        }

        Column(Modifier.fillMaxSize()) {
            Header(
                ledgerFile = ledgerFile,
                recordCount = visibleRecords.size,
                lastSavedAt = lastSavedAt,
                onSave = { saveNow() }
            )

            TabStrip(
                tab = tab,
                onTab = { tab = it },
                summary = "账本共 ${visibleRecords.size} 笔 · 当前显示 ${filtered.size} 笔"
            )

            if (tab == 0) {
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SummaryPanel(
                        modifier = Modifier.width(312.dp).fillMaxHeight(),
                        balance = balance,
                        records = visibleRecords,
                        onEditBalance = { showBalanceEditor = true }
                    )
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        FilterBar(
                            query = query,
                            onQuery = { query = it },
                            typeFilter = typeFilter,
                            onType = { typeFilter = it },
                            months = availableMonths,
                            monthFilter = monthFilter,
                            onMonth = { monthFilter = it },
                            count = filtered.size,
                            expense = filtered.filter { it.type == CategoryType.EXPENSE.code }.sumOf { it.amountCents },
                            income = filtered.filter { it.type == CategoryType.INCOME.code }.sumOf { it.amountCents }
                        )
                        Spacer(Modifier.height(10.dp))
                        if (selectedIds.isNotEmpty()) {
                            SelectionBar(
                                count = selectedIds.size,
                                allSelected = filtered.isNotEmpty() && selectedIds.size == filtered.size,
                                onSelectAll = { selectedIds = filtered.map { it.syncId }.toSet() },
                                onClear = { selectedIds = emptySet() },
                                onDelete = { deleteRecords(filtered.filter { it.syncId in selectedIds }) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = LocalCardAlpha.current)
                        ) {
                            if (filtered.isEmpty()) {
                                EmptyLedger(hasFilter = query.isNotBlank() || typeFilter != TypeFilter.ALL || monthFilter != null)
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 72.dp)
                                ) {
                                    items(filtered, key = { it.syncId }) { record ->
                                        val category = categories.firstOrNull { it.id == record.categoryId && it.type == record.type }
                                        RecordRow(
                                            record = record,
                                            categoryName = category?.name ?: "其他",
                                            categoryIcon = category?.icon ?: "more",
                                            selected = record.syncId in selectedIds,
                                            onToggleSelect = { toggleSelect(record.syncId) },
                                            onEdit = {
                                                editorTarget = record
                                                showRecordEditor = true
                                            },
                                            onDelete = { deleteRecords(listOf(record)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 固定在列表右下角的记账入口：任何窗口宽度下都可见
                    Button(
                        onClick = {
                            editorTarget = null
                            showRecordEditor = true
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("记一笔")
                    }
                    }
                }
            } else if (tab == 1) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StatsPage(
                        records = visibleRecords,
                        balance = balance,
                        categories = categories,
                        month = statMonth,
                        onMonthChange = { statMonth = it }
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    DesktopSettingsPage(
                        ledgerFile = ledgerFile,
                        recordCount = visibleRecords.size,
                        syncNotice = syncNotice,
                        onSaveNow = { saveNow() },
                        onImport = { chooseOpenFile(owner)?.let(::loadFrom) },
                        onExport = { exportToPhone() },
                        categories = categories,
                        onManageCategories = { showCategoryManager = true },
                        bgPath = bgPath,
                        bgOpacity = bgOpacity,
                        cardOpacity = cardOpacity,
                        onPickImage = {
                            chooseImageFile(owner)?.let { bgPath = it.absolutePath }
                        },
                        onResetImage = { bgPath = null },
                        onBgOpacity = { bgOpacity = it },
                        onCardOpacity = { cardOpacity = it }
                    )
                }
            }

            if (banner.isNotEmpty()) {
                Surface(color = if (bannerError) Color(0xFFFDECEC) else NahidaSoft, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (bannerError) Icons.Outlined.WarningAmber else Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (bannerError) ExpenseColor else NahidaGreen
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            banner,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bannerError) Color(0xFF7A2B2B) else Color(0xFF3A4A36)
                        )
                        if (undoDeletes != null) {
                            TextButton(onClick = ::undoDelete) { Text("撤销删除") }
                        }
                    }
                }
            }
        }
    }
    }

    if (showRecordEditor) {
        RecordEditorDialog(
            initial = editorTarget,
            categories = categories,
            onDismiss = { showRecordEditor = false },
            onSave = { saved ->
                val exists = records.any { it.syncId == saved.syncId }
                records = sortRecords(if (exists) records.map { if (it.syncId == saved.syncId) saved else it } else records + saved)
                showRecordEditor = false
                notify(if (exists) "已修改账目" else "已新增一笔账目")
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
                notify("余额已更新，导出给手机后即可同步")
            }
        )
    }

    if (showCategoryManager) {
        DesktopCategoryManagerDialog(
            categories = categories,
            onDismiss = { showCategoryManager = false },
            onAdd = { name, icon, type ->
                val next = DesktopLedgerStore.addCategory(categories, name, icon, type)
                if (next != null) {
                    categories = next
                    notify("已新增分类「${name.trim()}」")
                }
                next != null
            },
            onUpdate = { id, name, icon ->
                val next = DesktopLedgerStore.updateCategory(categories, id, name, icon)
                if (next != null) categories = next
                next != null
            },
            onDelete = { id ->
                val next = DesktopLedgerStore.deleteCategory(LedgerData(records, balance, categories), id)
                if (next == null) {
                    false
                } else {
                    records = next.first.records
                    categories = next.first.categories
                    notify(next.second)
                    true
                }
            }
        )
    }
}

@Composable
private fun Header(
    ledgerFile: File,
    recordCount: Int,
    lastSavedAt: Long?,
    onSave: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(NahidaGreen, Color(0xFF528C63))))
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Sticker(name = "nahida_hello", size = 48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "此地无银三百两",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "本机账本：" + ledgerFile.absolutePath + " · $recordCount 笔账目" +
                        (lastSavedAt?.let { " · 自动保存于 ${formatTimeOfDay(it)}" } ?: ""),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HeaderButton("保存", Icons.Outlined.Save, onSave, filled = true)
        }
    }
}

@Composable
private fun HeaderButton(text: String, icon: ImageVector?, onClick: () -> Unit, filled: Boolean) {
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    if (filled) {
        Button(
            onClick = onClick,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = NahidaDeep)
        ) {
            icon?.let {
                Icon(it, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = contentPadding,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            icon?.let {
                Icon(it, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TabStrip(tab: Int, onTab: (Int) -> Unit, summary: String) {
    Surface(color = Color.White.copy(alpha = LocalCardAlpha.current), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabPill("明细", Icons.Outlined.ReceiptLong, tab == 0) { onTab(0) }
            Spacer(Modifier.width(6.dp))
            TabPill("统计", Icons.Outlined.PieChart, tab == 1) { onTab(1) }
            Spacer(Modifier.width(6.dp))
            TabPill("设置", Icons.Outlined.Settings, tab == 2) { onTab(2) }
            Spacer(Modifier.weight(1f))
            Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TabPill(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) NahidaGreen else Color.Transparent,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                Modifier.size(15.dp),
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryPanel(modifier: Modifier, balance: SyncBalance, records: List<SyncRecord>, onEditBalance: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val monthStart = remember { today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val nextMonthStart = remember { today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val todayStart = remember { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val tomorrowStart = remember { today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }

    val monthRecords = records.filter { it.date in monthStart until nextMonthStart }
    val monthExpense = monthRecords.filter { it.type == CategoryType.EXPENSE.code }.sumOf { it.amountCents }
    val monthIncome = monthRecords.filter { it.type == CategoryType.INCOME.code }.sumOf { it.amountCents }
    val todayExpense = records.filter { it.date in todayStart until tomorrowStart && it.type == CategoryType.EXPENSE.code }
        .sumOf { it.amountCents }
    val activeDays = monthRecords.map { it.day() }.distinct().size
    // 与安卓端口径一致：总余额 = 锚点余额 + 基准日之后（含当天）的净收支
    val flowSinceAnchor = records.filter { it.date >= balance.anchoredAt }.sumOf { record ->
        if (record.type == CategoryType.INCOME.code) record.amountCents else -record.amountCents
    }
    val totalBalance = balance.balanceCents + flowSinceAnchor

    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = LocalCardAlpha.current)) {
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
                Sticker(name = "nahida_cheer", size = 54.dp)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = NahidaGreen.copy(alpha = 0.16f))
            Spacer(Modifier.height(10.dp))
            Text("本月概览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            StatLine("本月支出", monthExpense, ExpenseColor)
            Spacer(Modifier.height(6.dp))
            StatLine("本月收入", monthIncome, IncomeColor)
            Spacer(Modifier.height(6.dp))
            StatLine("本月结余", monthIncome - monthExpense, NahidaGreen)
            Spacer(Modifier.height(6.dp))
            StatLine("今日支出", todayExpense, Color(0xFFB47A2E))
            Spacer(Modifier.height(6.dp))
            StatLine("记账天数", null, Color(0xFF4C7C9B), rawText = "$activeDays 天")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEditBalance, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("设置余额")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "导出账本文件后，在手机「设置 → 数据同步」导入即可合并；删除的账目也会同步。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatLine(label: String, cents: Long?, color: Color, rawText: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            rawText ?: "¥${formatCents(cents ?: 0L)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    query: String,
    onQuery: (String) -> Unit,
    typeFilter: TypeFilter,
    onType: (TypeFilter) -> Unit,
    months: List<YearMonth>,
    monthFilter: String?,
    onMonth: (String?) -> Unit,
    count: Int,
    expense: Long,
    income: Long
) {
    Surface(color = Color.White.copy(alpha = LocalCardAlpha.current), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(16.dp)) },
                placeholder = { Text("搜索备注、分类、金额或日期", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            // 筛选单独一行；新增记账按钮固定在列表右下角，窗口再窄也不会被挤掉
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeFilter.entries.forEach { item ->
                    FilterChip(
                        selected = typeFilter == item,
                        onClick = { onType(item) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) }
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$count 笔 · 支出 ¥${formatCents(expense)} · 收入 ¥${formatCents(income)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (months.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = monthFilter == null,
                        onClick = { onMonth(null) },
                        label = { Text("全部月份", style = MaterialTheme.typography.labelSmall) }
                    )
                    months.forEach { item ->
                        FilterChip(
                            selected = monthFilter == item.toString(),
                            onClick = { onMonth(item.toString()) },
                            label = { Text("${item.year} 年 ${item.monthValue} 月", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLedger(hasFilter: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Sticker(name = "nahida_think", size = 92.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasFilter) "没有符合条件的账目" else "还没有账目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasFilter) "换个关键词或月份试试" else "先「打开/合并」手机导出的账本文件，或点「记一笔」手动录入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = LocalCardAlpha.current)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已选 $count 笔",
                style = MaterialTheme.typography.labelLarge,
                color = NahidaDeep,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onSelectAll) { Text(if (allSelected) "取消全选" else "全选") }
            TextButton(onClick = onClear) { Text("清除选择") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor, contentColor = Color.White)
            ) { Text("删除所选") }
        }
    }
}

@Composable
private fun RecordRow(
    record: SyncRecord,
    categoryName: String,
    categoryIcon: String,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val income = record.type == CategoryType.INCOME.code
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = (if (selected) NahidaSoft else Color(0xFFFBFCF9)).copy(alpha = LocalCardAlpha.current)
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(34.dp)
            )
            Text(
                formatStamp(record.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(126.dp)
            )
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.6f)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(desktopCategoryIcon(categoryIcon), null, Modifier.size(14.dp), tint = NahidaGreen)
                    Spacer(Modifier.width(4.dp))
                    Text(categoryName, style = MaterialTheme.typography.labelMedium)
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
private fun RecordEditorDialog(
    initial: SyncRecord?,
    categories: List<SyncCategory>,
    onDismiss: () -> Unit,
    onSave: (SyncRecord) -> Unit
) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: CategoryType.EXPENSE.code) }
    var amountText by remember(initial) { mutableStateOf(initial?.let { centsToInput(it.amountCents) } ?: "") }
    var categoryId by remember(initial) {
        mutableStateOf(initial?.categoryId ?: FixedCategories.EXPENSE_FOOD)
    }
    var dateText by remember(initial) { mutableStateOf(formatDate(initial?.date ?: System.currentTimeMillis())) }
    var timeText by remember(initial) {
        mutableStateOf(formatTimeOfDay(initial?.date ?: System.currentTimeMillis()))
    }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }

    val typeCategories = DesktopLedgerStore.visibleCategories(categories, type)
    // 编辑老账目时若所选分类已被删除，回落到该类型的第一个可用分类
    if (categoryId !in typeCategories.map { it.id }) {
        categoryId = typeCategories.firstOrNull()?.id ?: FixedCategories.otherIdFor(type)
    }
    val amountCents = parseAmountToCents(amountText)
    val dateMillis = parseDateTime(dateText, timeText)
    val valid = amountCents != null && dateMillis != null && typeCategories.any { it.id == categoryId }

    EditorScrim(onDismiss) {
        Column(Modifier.padding(22.dp)) {
            Text(if (initial == null) "记一笔" else "编辑账目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == CategoryType.EXPENSE.code,
                    onClick = {
                        type = CategoryType.EXPENSE.code
                        categoryId = DesktopLedgerStore.visibleCategories(categories, type).firstOrNull()?.id
                            ?: FixedCategories.EXPENSE_OTHER
                    },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = type == CategoryType.INCOME.code,
                    onClick = {
                        type = CategoryType.INCOME.code
                        categoryId = DesktopLedgerStore.visibleCategories(categories, type).firstOrNull()?.id
                            ?: FixedCategories.INCOME_OTHER
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
                typeCategories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = { Icon(desktopCategoryIcon(category.icon), null, Modifier.size(15.dp)) }
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
                    modifier = Modifier.width(160.dp)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it.take(5) },
                    label = { Text("时间") },
                    singleLine = true,
                    modifier = Modifier.width(104.dp)
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
                Text("日期格式应为 2026-10-31，时间格式应为 14:30", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                .width(640.dp)
                .heightIn(max = 680.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) { content() }
    }
}

/** 纳西妲表情装饰：资源缺失时静默跳过，不影响功能 */
@Composable
internal fun Sticker(name: String, size: Dp) {
    val bitmap: ImageBitmap? = remember(name) { DesktopImages.resource("$name.jpg") }
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

internal fun sortRecords(list: List<SyncRecord>): List<SyncRecord> =
    list.sortedWith(compareByDescending<SyncRecord> { it.date }.thenByDescending { it.updatedAt })

private fun SyncRecord.matchesQuery(q: String, categories: List<SyncCategory>): Boolean {
    if (q.isBlank()) return true
    val categoryName = DesktopLedgerStore.categoryName(categories, categoryId, type)
    return note.contains(q, true) ||
        categoryName.contains(q, true) ||
        formatCents(amountCents).contains(q) ||
        amountCents.toString().contains(q) ||
        formatStamp(date).contains(q) ||
        formatDate(date) == q
}

internal fun parseAmountToCents(text: String): Long? {
    val value = text.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return (value * 100).roundToLong()
}

internal fun centsToInput(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString() else String.format("%.2f", cents / 100.0)

internal fun formatCents(cents: Long): String = String.format("%,.2f", cents / 100.0)

internal fun parseDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim(), dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

/** 日期 + 时间（HH:mm），按本地时区合成时间戳 */
private fun parseDateTime(dateText: String, timeText: String): Long? {
    val date = runCatching { LocalDate.parse(dateText.trim(), dateFormatter) }.getOrNull() ?: return null
    val time = runCatching { LocalTime.parse(timeText.trim(), timeFormatter) }.getOrNull() ?: return null
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun formatDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

internal fun formatTimeOfDay(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

/** 列表里展示的时间：老数据是当天 0 点，只显示日期；新数据带具体时间 */
internal fun formatStamp(ms: Long): String {
    val at = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val date = at.toLocalDate().format(dateFormatter)
    val time = at.toLocalTime()
    return if (time.hour == 0 && time.minute == 0) date else "$date ${time.format(timeFormatter)}"
}

private fun chooseOpenFile(owner: Frame): File? {
    val dialog = FileDialog(owner, "选择账本文件（合并到当前账本）", FileDialog.LOAD)
    dialog.file = "*.json"
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val file = File(directory, name)
    return file.takeIf { it.exists() }
}

private fun chooseSaveFile(owner: Frame, suggestedName: String): File? {
    val dialog = FileDialog(owner, "导出账本文件", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name)
}

private fun chooseImageFile(owner: Frame): File? {
    val dialog = FileDialog(owner, "选择背景图片", FileDialog.LOAD)
    dialog.file = "*.jpg"
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name).takeIf { it.exists() }
}
