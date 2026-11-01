package com.mucage.sync

/**
 * 极简 JSON 编解码（零依赖）。
 * 支持对象 / 数组 / 字符串 / 数字 / 布尔 / null，够用且解析严格。
 */
sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val raw: String) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    object Null : JsonValue()

    fun obj(): Obj? = this as? Obj
    fun arr(): Arr? = this as? Arr
    fun str(): String? = (this as? Str)?.value
    fun bool(): Boolean? = (this as? Bool)?.value
    fun long(default: Long = 0L): Long = (this as? Num)?.raw?.toDoubleOrNull()?.toLong() ?: default
    fun int(default: Int = 0): Int = (this as? Num)?.raw?.toDoubleOrNull()?.toInt() ?: default

    fun get(key: String): JsonValue? = obj()?.fields?.get(key)
}

object Json {

    /** 序列化为带缩进的 JSON 文本 */
    fun write(value: JsonValue): String {
        val sb = StringBuilder(4096)
        writeValue(value, sb, 0)
        return sb.toString()
    }

    /** 序列化为单行紧凑 JSON（MCP 等按行传输的场景使用） */
    fun writeCompact(value: JsonValue): String {
        val sb = StringBuilder(1024)
        writeCompactValue(value, sb)
        return sb.toString()
    }

    private fun writeCompactValue(value: JsonValue, sb: StringBuilder) {
        when (value) {
            is JsonValue.Obj -> {
                sb.append('{')
                value.fields.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) sb.append(',')
                    writeString(key, sb)
                    sb.append(':')
                    writeCompactValue(item, sb)
                }
                sb.append('}')
            }

            is JsonValue.Arr -> {
                sb.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    writeCompactValue(item, sb)
                }
                sb.append(']')
            }

            is JsonValue.Str -> writeString(value.value, sb)
            is JsonValue.Num -> sb.append(value.raw)
            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
            JsonValue.Null -> sb.append("null")
        }
    }

    private fun writeValue(value: JsonValue, sb: StringBuilder, depth: Int) {
        when (value) {
            is JsonValue.Obj -> {
                if (value.fields.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val indent = "  ".repeat(depth + 1)
                var first = true
                value.fields.forEach { (k, v) ->
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append(indent)
                    writeString(k, sb)
                    sb.append(": ")
                    writeValue(v, sb, depth + 1)
                }
                sb.append('\n').append("  ".repeat(depth)).append('}')
            }

            is JsonValue.Arr -> {
                if (value.items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                val indent = "  ".repeat(depth + 1)
                value.items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(",\n")
                    sb.append(indent)
                    writeValue(item, sb, depth + 1)
                }
                sb.append('\n').append("  ".repeat(depth)).append(']')
            }

            is JsonValue.Str -> writeString(value.value, sb)
            is JsonValue.Num -> sb.append(value.raw)
            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
            JsonValue.Null -> sb.append("null")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        s.forEach { ch ->
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u").append(String.format("%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    /** 解析 JSON 文本；格式非法时返回 null */
    fun parse(text: String): JsonValue? = try {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (parser.pos >= text.length) value else null
    } catch (_: Exception) {
        null
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (pos >= text.length) throw IllegalStateException("Unexpected end")
            return when (val ch = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> { expect("true"); JsonValue.Bool(true) }
                'f' -> { expect("false"); JsonValue.Bool(false) }
                'n' -> { expect("null"); JsonValue.Null }
                else -> if (ch == '-' || ch.isDigit()) parseNumber() else throw IllegalStateException("Bad char $ch")
            }
        }

        private fun expect(literal: String) {
            if (!text.startsWith(literal, pos)) throw IllegalStateException("Expect $literal")
            pos += literal.length
        }

        private fun parseObject(): JsonValue.Obj {
            pos++ // {
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') { pos++; return JsonValue.Obj(fields) }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (text[pos] != ':') throw IllegalStateException("Expect :")
                pos++
                fields[key] = parseValue()
                skipWhitespace()
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonValue.Obj(fields) }
                    else -> throw IllegalStateException("Expect , or }")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            pos++ // [
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') { pos++; return JsonValue.Arr(items) }
            while (true) {
                items += parseValue()
                skipWhitespace()
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonValue.Arr(items) }
                    else -> throw IllegalStateException("Expect , or ]")
                }
            }
        }

        private fun parseString(): String {
            if (text[pos] != '"') throw IllegalStateException("Expect string")
            pos++
            val sb = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos]
                when {
                    ch == '"' -> { pos++; return sb.toString() }
                    ch == '\\' -> {
                        pos++
                        when (val esc = text[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = text.substring(pos + 1, pos + 5)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalStateException("Bad escape $esc")
                        }
                        pos++
                    }
                    else -> { sb.append(ch); pos++ }
                }
            }
            throw IllegalStateException("Unterminated string")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' ||
                    text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')
            ) pos++
            return JsonValue.Num(text.substring(start, pos))
        }
    }
}
