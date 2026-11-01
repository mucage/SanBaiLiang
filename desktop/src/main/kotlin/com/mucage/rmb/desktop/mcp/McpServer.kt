package com.mucage.rmb.desktop.mcp

import com.mucage.sync.Json
import com.mucage.sync.JsonValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * 极简 MCP（Model Context Protocol）服务端，使用 stdio 传输。
 * 启动方式：MucageLedger.exe --mcp
 * 协议：JSON-RPC 2.0，每行一条消息；日志只写 stderr，stdout 仅输出协议消息。
 */
object McpServer {

    const val VERSION = "10.27.1"
    private const val PROTOCOL_VERSION = "2024-11-05"

    fun run() {
        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(System.out, StandardCharsets.UTF_8))
        System.err.println("[mucage-ledger-mcp] stdio server ready, version=$VERSION")

        while (true) {
            val line = reader.readLine() ?: break
            // 容忍 Windows 工具写入的 UTF-8 BOM 与行首空白
            val text = line.removePrefix("\uFEFF").trim()
            if (text.isBlank()) continue
            val request = runCatching { Json.parse(text) }.getOrNull()?.obj() ?: continue
            val id = request.fields["id"]
            val method = request.fields["method"]?.str() ?: continue
            val params = request.fields["params"]?.obj()

            try {
                when (method) {
                    "initialize" -> respond(writer, id, initializeResult())
                    "notifications/initialized", "notifications/cancelled" -> Unit
                    "ping" -> respond(writer, id, JsonValue.Obj(emptyMap()))
                    "tools/list" -> respond(
                        writer, id,
                        JsonValue.Obj(linkedMapOf("tools" to JsonValue.Arr(McpTools.tools.map { it.toJson() })))
                    )
                    "tools/call" -> {
                        val result = if (params == null) {
                            McpTools.error("缺少 params（应包含 name 与 arguments）")
                        } else {
                            runCatching { McpTools.call(params) }
                                .getOrElse { McpTools.error(it.message ?: it.javaClass.simpleName) }
                        }
                        respond(writer, id, result)
                    }
                    else -> if (id != null) {
                        respondError(writer, id, -32601, "Method not found: $method")
                    }
                }
            } catch (e: Exception) {
                System.err.println("[mucage-ledger-mcp] error: ${e.message}")
                if (id != null) respondError(writer, id, -32603, e.message ?: "Internal error")
            }
        }
    }

    private fun initializeResult(): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "protocolVersion" to JsonValue.Str(PROTOCOL_VERSION),
            "capabilities" to JsonValue.Obj(
                linkedMapOf("tools" to JsonValue.Obj(emptyMap()))
            ),
            "serverInfo" to JsonValue.Obj(
                linkedMapOf(
                    "name" to JsonValue.Str("mucage-ledger"),
                    "version" to JsonValue.Str(VERSION)
                )
            ),
            "instructions" to JsonValue.Str(
                "本地记账软件「此地无银三百两」电脑端账本工具。可查询/设置余额，记账、改账、删账，" +
                    "管理分类，读取支出统计图表。金额单位一律为元。调用前请先关闭桌面窗口，避免与界面同时写入。"
            )
        )
    )

    private fun respond(writer: BufferedWriter, id: JsonValue?, result: JsonValue) {
        if (id == null) return
        writeLine(
            writer,
            JsonValue.Obj(linkedMapOf("jsonrpc" to JsonValue.Str("2.0"), "id" to id, "result" to result))
        )
    }

    private fun respondError(writer: BufferedWriter, id: JsonValue?, code: Int, message: String) {
        if (id == null) return
        writeLine(
            writer,
            JsonValue.Obj(
                linkedMapOf(
                    "jsonrpc" to JsonValue.Str("2.0"),
                    "id" to id,
                    "error" to JsonValue.Obj(
                        linkedMapOf(
                            "code" to JsonValue.Num(code.toString()),
                            "message" to JsonValue.Str(message)
                        )
                    )
                )
            )
        )
    }

    private fun writeLine(writer: BufferedWriter, value: JsonValue) {
        writer.write(Json.writeCompact(value))
        writer.write("\n")
        writer.flush()
    }
}
