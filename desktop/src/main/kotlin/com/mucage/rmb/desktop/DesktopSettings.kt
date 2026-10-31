package com.mucage.rmb.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.util.Properties

/** 桌面端本地偏好：背景图路径与不透明度，保存在 %APPDATA%\MucageLedger\settings.properties */
internal object DesktopPrefs {

    private val settingsFile: File by lazy {
        val root = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MucageLedger")
        root.mkdirs()
        File(root, "settings.properties")
    }

    data class Settings(
        val backgroundPath: String? = null,
        val backgroundOpacity: Float = 0.5f,
        /** 明细列表、统计卡片等白色板块的不透明度 */
        val cardOpacity: Float = 0.92f
    )

    fun load(): Settings {
        val props = loadProps()
        val path = props.getProperty("background.image")?.takeIf { it.isNotBlank() }
        val opacity = props.getProperty("background.opacity")?.toFloatOrNull()?.coerceIn(0.05f, 1f) ?: 0.5f
        val cardOpacity = props.getProperty("card.opacity")?.toFloatOrNull()?.coerceIn(0.3f, 1f) ?: 0.92f
        return Settings(path, opacity, cardOpacity)
    }

    fun save(settings: Settings) {
        runCatching {
            val props = loadProps()
            settings.backgroundPath?.takeIf { it.isNotBlank() }?.let { props.setProperty("background.image", it) }
            props.setProperty("background.opacity", settings.backgroundOpacity.toString())
            props.setProperty("card.opacity", settings.cardOpacity.toString())
            storeProps(props)
        }
    }

    /** 上次关闭时的窗口大小（dp） */
    fun loadWindowSize(): Pair<Int, Int> {
        val props = loadProps()
        val width = props.getProperty("window.width")?.toIntOrNull()?.coerceIn(900, 2600) ?: 1200
        val height = props.getProperty("window.height")?.toIntOrNull()?.coerceIn(640, 1600) ?: 840
        return width to height
    }

    fun saveWindowSize(width: Int, height: Int) {
        runCatching {
            val props = loadProps()
            props.setProperty("window.width", width.toString())
            props.setProperty("window.height", height.toString())
            storeProps(props)
        }
    }

    private fun loadProps(): Properties {
        val props = Properties()
        if (settingsFile.exists()) {
            runCatching { settingsFile.inputStream().use { props.load(it) } }
        }
        return props
    }

    private fun storeProps(props: Properties) {
        settingsFile.outputStream().use { props.store(it, "MucageLedger desktop settings") }
    }
}

/** 图片解码：默认背景随打包资源，用户自选时读取本地文件 */
internal object DesktopImages {

    private object Holder

    fun background(path: String?): ImageBitmap? {
        if (!path.isNullOrBlank()) {
            val fromFile = runCatching { decode(File(path).readBytes()) }.getOrNull()
            if (fromFile != null) return fromFile
        }
        return resource("bg_default.jpg")
    }

    fun resource(name: String): ImageBitmap? = runCatching {
        val stream = Holder::class.java.getResourceAsStream("/images/$name") ?: return@runCatching null
        stream.use { decode(it.readBytes()) }
    }.getOrNull()

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
