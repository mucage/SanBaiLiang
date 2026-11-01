package com.mucage.rmb.desktop.mcp

import com.mucage.rmb.desktop.DesktopLedgerStore
import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import kotlin.system.exitProcess

/**
 * MCP 模式下的系统托盘图标：让 Agent 启动的后台服务在通知区域可见，并可手动停止。
 * 不支持系统托盘的环境（如无桌面会话）会静默跳过，不影响 MCP 协议本身。
 */
internal object McpTray {

    fun install(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return runCatching {
            val tray = SystemTray.getSystemTray()
            val pid = ProcessHandle.current().pid()
            val icon = TrayIcon(createImage(), "此地无银三百两 · MCP 服务运行中（PID $pid）").apply {
                isImageAutoSize = true
            }
            icon.popupMenu = PopupMenu().apply {
                add(MenuItem("显示 MCP 状态").apply {
                    addActionListener { showStatus(icon, pid) }
                })
                addSeparator()
                add(MenuItem("停止 MCP 服务").apply {
                    addActionListener { exitProcess(0) }
                })
            }
            icon.addActionListener { showStatus(icon, pid) }
            tray.add(icon)
            icon
        }.getOrNull()
    }

    fun remove(icon: TrayIcon?) {
        if (icon == null) return
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    private fun showStatus(icon: TrayIcon, pid: Long) {
        runCatching {
            icon.displayMessage(
                "此地无银三百两 · MCP 服务",
                "PID $pid\n账本：${DesktopLedgerStore.file.absolutePath}",
                TrayIcon.MessageType.INFO
            )
        }
    }

    private fun createImage(): BufferedImage {
        val size = 32
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0x3F, 0x7D, 0x4E)
            g.fillRoundRect(1, 1, size - 2, size - 2, 12, 12)
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 19)
            val metrics = g.fontMetrics
            val text = "¥"
            g.drawString(text, (size - metrics.stringWidth(text)) / 2, (size - metrics.height) / 2 + metrics.ascent)
        } finally {
            g.dispose()
        }
        return image
    }
}
