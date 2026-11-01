package com.mucage.rmb.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mucage.rmb.R
import com.mucage.rmb.auto.AutoBookkeepingService
import com.mucage.rmb.auto.AutoPrefs
import com.mucage.rmb.auto.NotificationHelper
import com.mucage.rmb.auto.SmsScanner
import com.mucage.rmb.ai.DeepSeekClient
import com.mucage.rmb.data.AutoPendingEntity
import com.mucage.rmb.data.CategoryEntity
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.add.ConfirmAutoRecordDialog
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.rmb.ui.theme.BackgroundPreview
import com.mucage.rmb.ui.theme.BackgroundState
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.SettingsPrefs
import com.mucage.rmb.util.centsToInput
import com.mucage.rmb.util.formatCents
import com.mucage.rmb.util.formatCentsWithSymbol
import com.mucage.rmb.util.parseAmountToCents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 设置页：资金用途（可自定义）、自动记账、限额、AI、数据同步与关于 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val categories by Repository.categoryDao.observeAll().collectAsState(initial = emptyList())
    var showCategoryManager by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // 纳西妲装饰横幅
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFEAF3E4)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.nahida_hello),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "小草神在此",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2F5D3A)
                    )
                    Text(
                        "每一笔都记清楚，钱包才不会偷偷瘦下去～",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4A6B4F)
                    )
                }
            }
        }

        // 资金用途与来源（可自定义；编辑入口在独立卡片里，避免误触直接改动）
        SectionTitle("资金用途")
        Text(
            "支出用途与收入来源都可以自定义，改名、换图标、新增或删除会随账本同步到电脑端",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        val expenseCats = categories.filter { it.type == RecordType.EXPENSE }
        val incomeCats = categories.filter { it.type == RecordType.INCOME }
        EditableCategoryRow(title = "支出 · 用途", categories = expenseCats, onManage = { showCategoryManager = true })
        Spacer(Modifier.height(8.dp))
        EditableCategoryRow(title = "收入 · 来源", categories = incomeCats, onManage = { showCategoryManager = true })

        Spacer(Modifier.height(16.dp))

        // 数据同步
        SectionTitle("数据同步")
        SyncSection()

        Spacer(Modifier.height(16.dp))

        // 自动记账
        SectionTitle("自动记账")
        AutoBookkeepingSection()

        Spacer(Modifier.height(16.dp))

        // 支出限额提醒
        SectionTitle("支出限额提醒")
        LimitSetting()

        Spacer(Modifier.height(16.dp))

        // AI 个性评价
        SectionTitle("AI 个性评价")
        AiSetting()

        Spacer(Modifier.height(16.dp))

        // 关于
        SectionTitle("关于")
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("此地无银三百两 v10.27.1", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Image(
                        painter = painterResource(R.drawable.nahida_cheer),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "所有账目、余额与分类数据均仅保存在本机，不上传任何服务器；与电脑同步需手动导出/导入文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 界面背景
        SectionTitle("界面背景")
        BackgroundSection()

        Spacer(Modifier.height(24.dp))
    }

    if (showCategoryManager) {
        CategoryManagerDialog(onDismiss = { showCategoryManager = false })
    }
}

/** 界面背景：内置图片为默认，可换成相册图片，并调节不透明度 */
@Composable
private fun BackgroundSection() {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) BackgroundState.pick(context, uri)
    }
    val alpha = BackgroundState.alpha
    val enabled = BackgroundState.enabled
    val hasCustom = BackgroundState.customUri != null

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackgroundPreview(Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("自定义背景图片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasCustom) "当前使用相册里的图片" else "当前使用内置背景图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = enabled, onCheckedChange = { BackgroundState.setEnabled(context, it) })
            }
            Spacer(Modifier.height(10.dp))
            Text("背景不透明度 $alpha%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = alpha.toFloat(),
                onValueChange = { BackgroundState.setAlpha(context, it.roundToInt()) },
                valueRange = 0f..100f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { pickImage.launch(arrayOf("image/*")) }) { Text("选择图片") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { BackgroundState.reset(context) }) { Text("恢复默认") }
            }
            Text(
                "背景图只保存在本机，不会随账本同步；默认不透明度 50%，可在 0%～100% 之间调整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 分类展示（只读）+ 独立的管理入口，避免在设置页误触改动分类 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableCategoryRow(title: String, categories: List<CategoryEntity>, onManage: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(bottom = 6.dp)
            )
            TextButton(onClick = onManage) { Text("管理分类") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            categoryIcon(category.icon),
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 检查本应用是否已在系统「通知使用权」中开启监听 */
private fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** 自动记账：通知监听 + 短信识别开关、授权入口、服务状态检查、待确认记录列表 */
@Composable
private fun AutoBookkeepingSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by Repository.autoPendingDao.observePending().collectAsState(initial = emptyList())

    var masterEnabled by remember { mutableStateOf(AutoPrefs.isEnabled(context)) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var smsEnabled by remember { mutableStateOf(AutoPrefs.smsEnabled(context)) }
    var confirmTarget by remember { mutableStateOf<AutoPendingEntity?>(null) }
    var serviceConnected by remember { mutableStateOf(AutoPrefs.isServiceConnected(context)) }
    var lastNotifAt by remember { mutableStateOf(AutoPrefs.getLastNotifAt(context)) }

    // 待确认列表的查重结果：id → 疑似重复说明（同类型、同金额、10 分钟内）
    var duplicateHints by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    LaunchedEffect(pending) {
        duplicateHints = Repository.duplicateHints(pending)
    }

    // 从系统设置返回时刷新监听授权与服务状态
    LifecycleResumeEffect(Unit) {
        listenerEnabled = isNotificationListenerEnabled(context)
        serviceConnected = AutoPrefs.isServiceConnected(context)
        lastNotifAt = AutoPrefs.getLastNotifAt(context)
        onPauseOrDispose { }
    }

    // 打开设置页时，若已授权短信则兜底扫描一次收件箱
    LaunchedEffect(Unit) {
        if (smsEnabled && SmsScanner.hasPermission(context)) {
            SmsScanner.scanInbox(context)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_SMS] == true &&
            grants[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            smsEnabled = true
            AutoPrefs.setSmsEnabled(context, true)
            scope.launch {
                SmsScanner.scanInbox(context)
                NotificationHelper.refresh(context)
            }
        } else {
            smsEnabled = false
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("自动捕获支付通知", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "微信/支付宝/云闪付、工商/建设/农业/中国/交通/邮储等银行App、手机钱包及购物平台的通知，确认后入账",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            masterEnabled = it
                            AutoPrefs.setEnabled(context, it)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!listenerEnabled) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开启通知使用权")
                    }
                    Text(
                        "需在系统设置中为本应用开启「通知使用权」，才能捕获微信/支付宝/云闪付、银行App及购物平台的支付通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (serviceConnected) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
                            null,
                            tint = if (serviceConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (serviceConnected) "监听服务运行中"
                            else "通知使用权已开，但监听服务未连接（可能被系统清理）",
                            color = if (serviceConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (lastNotifAt > 0) {
                                "最近收到关注应用的通知：${DateUtils.formatDay(lastNotifAt)} ${DateUtils.formatTime(lastNotifAt)}"
                            } else {
                                "尚未收到任何关注应用的通知（可先支付一笔小额试试）"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!serviceConnected) {
                            TextButton(onClick = {
                                AutoBookkeepingService.ensureBound(context)
                                scope.launch {
                                    delay(1200)
                                    serviceConnected = AutoPrefs.isServiceConnected(context)
                                }
                            }) { Text("重新绑定") }
                        } else {
                            TextButton(onClick = {
                                serviceConnected = AutoPrefs.isServiceConnected(context)
                                lastNotifAt = AutoPrefs.getLastNotifAt(context)
                            }) { Text("刷新状态") }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("短信识别（银行卡）", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (SmsScanner.hasPermission(context)) "解析银行/支付平台短信中的收支"
                            else "需授权短信权限，用于识别银行卡收支",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = smsEnabled,
                        onCheckedChange = { want ->
                            if (want) {
                                if (SmsScanner.hasPermission(context)) {
                                    smsEnabled = true
                                    AutoPrefs.setSmsEnabled(context, true)
                                    scope.launch {
                                        SmsScanner.scanInbox(context)
                                        NotificationHelper.refresh(context)
                                    }
                                } else {
                                    smsPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                                    )
                                }
                            } else {
                                smsEnabled = false
                                AutoPrefs.setSmsEnabled(context, false)
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val suspectedCount = duplicateHints.values.count { it.isNotEmpty() }
        Text(
            if (suspectedCount > 0) {
                "待确认的自动记账（${pending.size}）· 其中 $suspectedCount 笔疑似重复"
            } else {
                "待确认的自动记账（${pending.size}）"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (suspectedCount > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
        if (pending.isEmpty()) {
            Text(
                "暂无待确认记录。支付后识别结果会出现在这里，点击「入账」写入账本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else {
            pending.forEach { item ->
                PendingAutoRow(
                    item = item,
                    hints = duplicateHints[item.id].orEmpty(),
                    onConfirm = { confirmTarget = item },
                    onDiscard = { scope.launch { Repository.discardAutoRecord(item.id) } }
                )
            }
        }
    }

    confirmTarget?.let { target ->
        ConfirmAutoRecordDialog(pending = target, onDismiss = { confirmTarget = null })
    }
}

/** 单条待确认的自动记账；疑似重复时用红色标记并列出对比结果 */
@Composable
private fun PendingAutoRow(
    item: AutoPendingEntity,
    hints: List<String>,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    val isExpense = item.type == RecordType.EXPENSE
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (hints.isEmpty()) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (isExpense) "-" else "+") + formatCents(item.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (isExpense) ExpenseColor else IncomeColor
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    item.merchant.ifEmpty { item.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.sourceLabel} · ${DateUtils.formatDay(item.capturedAt)} ${DateUtils.formatTime(item.capturedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onConfirm) {
                    Text("入账", color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDiscard) {
                    Icon(
                        Icons.Outlined.Delete,
                        "忽略",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (hints.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "疑似重复，请检查后再入账",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                hints.forEach { hint ->
                    Text(
                        "· $hint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

/** 支出限额提醒设置：每月/每日支出限额，可自定义「离日限额还有多少元」时提醒 */
@Composable
private fun LimitSetting() {
    val context = LocalContext.current
    var monthLimitCents by remember { mutableStateOf(SettingsPrefs.getMonthlyLimitCents(context)) }
    var dayLimitCents by remember { mutableStateOf(SettingsPrefs.getDailyLimitCents(context)) }
    var dailyReminderCents by remember { mutableStateOf(SettingsPrefs.getDailyReminderCents(context)) }
    var monthLimitInput by remember { mutableStateOf(if (monthLimitCents > 0) centsToInput(monthLimitCents) else "") }
    var dayLimitInput by remember { mutableStateOf(if (dayLimitCents > 0) centsToInput(dayLimitCents) else "") }
    var reminderInput by remember {
        mutableStateOf(if (dailyReminderCents > 0) (dailyReminderCents / 100).toString() else "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var monthExpense by remember { mutableStateOf(0L) }
    var todayExpense by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        monthExpense = Repository.recordDao.sumBetweenForMonthly(
            RecordType.EXPENSE, DateUtils.startOfMonth(now), now
        )
        todayExpense = Repository.recordDao.sumBetweenForDaily(
            RecordType.EXPENSE, DateUtils.startOfDay(now), now
        )
    }

    fun saveMonthLimit() {
        parseAmountToCents(monthLimitInput)?.let { cents ->
            if (cents >= 0) {
                SettingsPrefs.setMonthlyLimitCents(context, cents)
                monthLimitCents = cents
                monthLimitInput = ""
                error = null
                return
            }
        }
        error = "请输入有效的月限额"
    }

    fun saveDayLimit() {
        parseAmountToCents(dayLimitInput)?.let { cents ->
            if (cents >= 0) {
                SettingsPrefs.setDailyLimitCents(context, cents)
                dayLimitCents = cents
                dayLimitInput = ""
                // 限额调低后，若提醒值超出「限额-1」，自动清除提醒
                if (dayLimitCents > 0 && (dailyReminderCents > dayLimitCents - 100)) {
                    SettingsPrefs.setDailyReminderCents(context, 0)
                    dailyReminderCents = 0
                    reminderInput = ""
                }
                error = null
                return
            }
        }
        error = "请输入有效的日限额"
    }

    fun saveReminder() {
        val raw = reminderInput.trim()
        if (raw.isEmpty()) {
            SettingsPrefs.setDailyReminderCents(context, 0)
            dailyReminderCents = 0
            error = null
            return
        }
        val value = raw.toLongOrNull()
        error = when {
            value == null -> "请输入整数"
            dayLimitCents <= 0 -> "请先设置每日支出限额"
            value <= 0 || value > dayLimitCents / 100 - 1 -> "请输入 1 到 ${dayLimitCents / 100 - 1} 之间的整数"
            else -> {
                SettingsPrefs.setDailyReminderCents(context, value * 100)
                dailyReminderCents = value * 100
                null
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = monthLimitInput,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() || it == '.' }
                        val parts = filtered.split(".")
                        monthLimitInput = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else filtered
                    },
                    label = { Text("每月支出限额（元）") },
                    singleLine = true,
                    placeholder = { Text("0 表示不限制") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = ::saveMonthLimit) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = dayLimitInput,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() || it == '.' }
                        val parts = filtered.split(".")
                        dayLimitInput = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else filtered
                    },
                    label = { Text("每日支出限额（元）") },
                    singleLine = true,
                    placeholder = { Text("0 表示不限制") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = ::saveDayLimit) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = reminderInput,
                    onValueChange = { reminderInput = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("离日限额还有（元）时提醒") },
                    singleLine = true,
                    placeholder = { Text("整数，不超过日限额-1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = ::saveReminder) { Text("保存") }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            val monthRemaining = monthLimitCents - monthExpense
            val dayRemaining = dayLimitCents - todayExpense
            Column {
                Text(
                    if (monthLimitCents <= 0) "本月已使用 ${formatCentsWithSymbol(monthExpense)}，未设置月限额"
                    else "本月已使用 ${formatCentsWithSymbol(monthExpense)} / ${formatCentsWithSymbol(monthLimitCents)}，" +
                        "离限额还有 ${formatCentsWithSymbol(if (monthRemaining > 0) monthRemaining else 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        dayLimitCents <= 0 -> "今日已使用 ${formatCentsWithSymbol(todayExpense)}，未设置日限额"
                        dailyReminderCents > 0 -> "今日已使用 ${formatCentsWithSymbol(todayExpense)} / ${formatCentsWithSymbol(dayLimitCents)}，" +
                            "离日限额还有 ${formatCentsWithSymbol(if (dayRemaining > 0) dayRemaining else 0)}，低于 ${formatCentsWithSymbol(dailyReminderCents)} 时提醒"
                        else -> "今日已使用 ${formatCentsWithSymbol(todayExpense)} / ${formatCentsWithSymbol(dayLimitCents)}，" +
                            "离日限额还有 ${formatCentsWithSymbol(if (dayRemaining > 0) dayRemaining else 0)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** AI 个性评价设置：API 密钥、模型名、深度思考开关与自定义提示词 */
@Composable
private fun AiSetting() {
    val context = LocalContext.current
    var key by remember { mutableStateOf(SettingsPrefs.getApiKey(context)) }
    var model by remember { mutableStateOf(SettingsPrefs.getModelName(context).ifEmpty { DeepSeekClient.DEFAULT_MODEL }) }
    var thinking by remember { mutableStateOf(SettingsPrefs.thinkingEnabled(context)) }
    // 未自定义时输入框直接预填内置默认提示词，改完保存即生效
    var prompt by remember {
        mutableStateOf(SettingsPrefs.getAiPrompt(context).ifEmpty { DeepSeekClient.DEFAULT_PROMPT })
    }
    var saved by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "填写自备的 DeepSeek API 密钥与模型名，将在「明细」页生成个性化点评，配置仅保存在本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; saved = false },
                    label = { Text("DeepSeek API 密钥") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = {
                    SettingsPrefs.setApiKey(context, key)
                    saved = true
                }) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = { Text("模型名") },
                    singleLine = true,
                    placeholder = { Text("如 ${DeepSeekClient.DEFAULT_MODEL}") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = {
                    SettingsPrefs.setModelName(context, model)
                    saved = true
                }) { Text("保存") }
            }
            Text(
                "默认 ${DeepSeekClient.DEFAULT_MODEL}（deepseek-chat/reasoner 已停用）；v4-flash / v4-pro 均支持思考开关",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("深度思考", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (thinking) "已开启：先推理再回答，较慢" else "已关闭：直接生成，更快",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = thinking,
                    onCheckedChange = {
                        SettingsPrefs.setThinkingEnabled(context, it)
                        thinking = it
                        saved = true
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; saved = false },
                label = { Text("发给 AI 的条件提示词") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    SettingsPrefs.setAiPrompt(context, prompt)
                    saved = true
                }) { Text("保存提示词") }
            }
            if (saved) {
                Spacer(Modifier.height(4.dp))
                Text("已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
