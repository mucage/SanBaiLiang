package com.mucage.rmb.ui.records

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mucage.rmb.ai.DeepSeekClient
import com.mucage.rmb.util.SettingsPrefs

/** AI 个性评价卡片：位于本月收支摘要正下方。余额等数据变动时才请求 DeepSeek，否则复用已保存内容 */
@Composable
fun AiCommentCard() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var noKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SettingsPrefs.getApiKey(context).isBlank()) {
            noKey = true
            loaded = true
            return@LaunchedEffect
        }
        val saved = SettingsPrefs.getSavedComment(context)
        val signature = DeepSeekClient.currentSignature()
        // 余额等财务数据未变动：不发送请求，直接复用已保存内容
        if (signature == SettingsPrefs.getSavedCommentSignature(context) && saved.isNotBlank()) {
            message = saved
            loaded = true
            return@LaunchedEffect
        }
        val result = DeepSeekClient.fetchComment(context)
        if (result != null) {
            SettingsPrefs.setSavedComment(context, result)
            SettingsPrefs.setSavedCommentSignature(context, signature)
            message = result
        } else {
            // 请求失败：保留上一次成功生成的内容，直到下一次成功
            message = saved.ifBlank { "获取失败，请检查网络或 API 密钥" }
        }
        loaded = true
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    !loaded -> "生成中..."
                    noKey -> "填写api秘钥来获得个性评价哦"
                    else -> message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (noKey) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
