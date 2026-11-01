package com.mucage.rmb.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mucage.sync.SyncCategory
import java.io.File
import kotlin.math.roundToInt

/** 桌面端设置页：账本文件、快速记账、分类管理、界面背景 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopSettingsPage(
    ledgerFile: File,
    recordCount: Int,
    syncNotice: String?,
    onSaveNow: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    categories: List<SyncCategory>,
    onManageCategories: () -> Unit,
    bgPath: String?,
    bgOpacity: Float,
    cardOpacity: Float,
    onPickImage: () -> Unit,
    onResetImage: () -> Unit,
    onBgOpacity: (Float) -> Unit,
    onCardOpacity: (Float) -> Unit
) {
    val expenseCats = categories.filter { it.type == "EXPENSE" && !it.deleted }.sortedBy { it.sortOrder }
    val incomeCats = categories.filter { it.type == "INCOME" && !it.deleted }.sortedBy { it.sortOrder }
    val preview = remember(bgPath) { DesktopImages.background(bgPath) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 本机账本与同步
        SettingsCard("本机账本与同步") {
            Text(
                ledgerFile.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "账目会自动保存到这份属于电脑自己的账本文件；「打开并合并」只会把手机导出的内容合并进来，不会改动对方文件；「导出给手机」会另存为一个新文件。共 $recordCount 笔账目。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (syncNotice != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF4E5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            null,
                            tint = Color(0xFFB36B00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            syncNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8A5300)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSaveNow) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存到本机")
                }
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开并合并手机账本")
                }
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出给手机")
                }
            }
        }

        // 分类管理
        SettingsCard("分类管理") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("支出 · 用途", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onManageCategories) { Text("管理分类") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                expenseCats.forEach { category ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text(category.name, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(desktopCategoryIcon(category.icon), null, Modifier.size(15.dp), tint = NahidaGreen)
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收入 · 来源", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                incomeCats.forEach { category ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text(category.name, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(desktopCategoryIcon(category.icon), null, Modifier.size(15.dp), tint = NahidaGreen)
                        }
                    )
                }
            }
        }

        // Agent / MCP 接口
        SettingsCard("Agent / MCP 接口") {
            Text(
                "允许支持 MCP 的 AI 客户端直接查询余额、设置余额、记账/改账/删账、管理分类，以及读取支出统计图表（仅电脑端）。使用前请先关闭本窗口，再让客户端用下面的命令启动。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF1F3EE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MucageLedger.exe --mcp",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NahidaDeep,
                        modifier = Modifier.weight(1f)
                    )
                    val clipboard = LocalClipboardManager.current
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString("MucageLedger.exe --mcp"))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "详细工具参数与客户端配置示例见发布包里的《MCP调用指南》。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 界面背景
        SettingsCard("界面背景") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F1E9)) {
                    Box(Modifier.width(160.dp).height(90.dp)) {
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (bgPath == null) "当前使用内置背景图" else "当前使用：${File(bgPath).name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onPickImage) {
                            Icon(Icons.Outlined.FileDownload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("选择图片")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onResetImage) { Text("恢复默认") }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("背景不透明度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Slider(value = bgOpacity, onValueChange = onBgOpacity, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${(bgOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = NahidaGreen, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("板块不透明度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Slider(value = cardOpacity, onValueChange = onCardOpacity, valueRange = 0.3f..1f, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${(cardOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = NahidaGreen, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "背景设置只保存在这台电脑，不随账本同步。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = LocalCardAlpha.current)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NahidaDeep)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
