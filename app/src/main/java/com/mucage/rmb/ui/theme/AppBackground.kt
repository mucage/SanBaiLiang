package com.mucage.rmb.ui.theme

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mucage.rmb.R
import com.mucage.rmb.util.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 界面背景偏好：默认使用内置图片，可换成相册图片；只保存在本机 */
object BackgroundState {
    var customUri by mutableStateOf<String?>(null)
        private set
    var alpha by mutableIntStateOf(50)
        private set
    var enabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        customUri = SettingsPrefs.getBackgroundUri(context)
        alpha = SettingsPrefs.getBackgroundAlpha(context)
        enabled = SettingsPrefs.isBackgroundEnabled(context)
    }

    fun pick(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        customUri = uri.toString()
        SettingsPrefs.setBackgroundUri(context, customUri)
    }

    fun reset(context: Context) {
        customUri = null
        SettingsPrefs.setBackgroundUri(context, null)
    }

    fun setAlpha(context: Context, value: Int) {
        alpha = value.coerceIn(0, 100)
        SettingsPrefs.setBackgroundAlpha(context, alpha)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        SettingsPrefs.setBackgroundEnabled(context, value)
    }
}

/** 铺满窗口的背景图 + 可读性蒙层，内容绘制在其上 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val enabled = BackgroundState.enabled
    val alpha = BackgroundState.alpha
    val uri = BackgroundState.customUri
    val image by produceState<ImageBitmap?>(initialValue = null, enabled, uri) {
        value = if (!enabled) null else withContext(Dispatchers.IO) { decode(context, uri) }
    }

    Box(Modifier.fillMaxSize()) {
        val bitmap = image
        if (enabled && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha / 100f)
            )
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.2f)))
        }
        content()
    }
}

/** 设置页里的小预览：跟随当前背景与不透明度 */
@Composable
fun BackgroundPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled = BackgroundState.enabled
    val alpha = BackgroundState.alpha
    val uri = BackgroundState.customUri
    val image by produceState<ImageBitmap?>(initialValue = null, enabled, uri) {
        value = if (!enabled) null else withContext(Dispatchers.IO) { decode(context, uri) }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha / 100f)
            )
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
    }
}

private fun decode(context: Context, uri: String?): ImageBitmap? = runCatching {
    val stream = if (uri == null) {
        context.resources.openRawResource(R.drawable.bg_default)
    } else {
        context.contentResolver.openInputStream(Uri.parse(uri))
    } ?: return@runCatching null
    stream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
}.getOrNull()
