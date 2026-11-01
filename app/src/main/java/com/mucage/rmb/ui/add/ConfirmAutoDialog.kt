package com.mucage.rmb.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mucage.rmb.data.AutoPendingEntity
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.components.categoryIcon
import com.mucage.rmb.ui.theme.ExpenseColor
import com.mucage.rmb.ui.theme.IncomeColor
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.centsToInput
import com.mucage.rmb.util.parseAmountToCents
import kotlinx.coroutines.launch

/** 确认自动记账弹窗：入账前可编辑金额、收支类型、用途与备注（账目统一计入总余额） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmAutoRecordDialog(pending: AutoPendingEntity, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    var type by rememberSaveable(pending.id) { mutableStateOf(pending.type) }
    var amountText by rememberSaveable(pending.id) { mutableStateOf(centsToInput(pending.amountCents)) }
    var categoryId by rememberSaveable(pending.id) { mutableLongStateOf(0L) }
    var note by rememberSaveable(pending.id) {
        mutableStateOf(pending.merchant.ifEmpty { pending.sourceLabel })
    }

    val categories by Repository.categoryDao.observeByType(type).collectAsState(initial = emptyList())

    // 默认选中分类：取该类型第一个
    LaunchedEffect(categories) {
        if (categoryId == 0L || categories.none { it.id == categoryId }) {
            categoryId = categories.firstOrNull()?.id ?: 0L
        }
    }

    val amountCents = parseAmountToCents(amountText)
    val canSave = amountCents != null && amountCents > 0 && categoryId != 0L
    val context = androidx.compose.ui.platform.LocalContext.current

    // 查重：账本或另一条待确认里 10 分钟内有同类型、同金额的记录时给出提醒
    var duplicateHints by remember(pending.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(pending.id) {
        duplicateHints = Repository.duplicateHints(listOf(pending))[pending.id].orEmpty()
    }

    fun save() {
        val cents = amountCents ?: return
        scope.launch {
            Repository.confirmAutoRecord(
                id = pending.id,
                amountCents = cents,
                type = type,
                categoryId = categoryId,
                note = note.trim(),
                date = pending.capturedAt
            )
            // 新账单入账后检测支出进度是否超过时间进度
            com.mucage.rmb.auto.SpendingPace.check(context)
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("确认这笔自动记账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${pending.sourceLabel} · ${DateUtils.formatFullDate(pending.capturedAt)} · ${pending.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (duplicateHints.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "疑似重复，请检查后再入账",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            duplicateHints.forEach { hint ->
                                Text(
                                    "· $hint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
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

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() || it == '.' }
                        val parts = filtered.split(".")
                        amountText = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else filtered
                    },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text("用途", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Icon(categoryIcon(category.icon), null, Modifier.size(16.dp))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = ::save,
                        enabled = canSave,
                        modifier = Modifier.weight(2f)
                    ) { Text("保存入账") }
                }
            }
        }
    }
}
