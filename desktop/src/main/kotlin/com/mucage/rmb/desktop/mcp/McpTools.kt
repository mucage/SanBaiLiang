package com.mucage.rmb.desktop.mcp

import com.mucage.rmb.desktop.DesktopLedgerStore
import com.mucage.rmb.desktop.LedgerData
import com.mucage.rmb.desktop.sortRecords
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.JsonValue
import com.mucage.sync.SyncCategory
import com.mucage.sync.SyncRecord
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** 账本操作异常：以 isError=true 的文本结果返回给 Agent */
internal class McpToolException(message: String) : Exception(message)

/** 一个 MCP 工具的定义 */
internal class McpTool(
    val name: String,
    val description: String,
    val schema: JsonValue.Obj,
    val call: (JsonValue.Obj) -> JsonValue
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "name" to JsonValue.Str(name),
            "description" to JsonValue.Str(description),
            "inputSchema" to schema
        )
    )
}

/**
 * 桌面端账本 MCP 工具集：余额查询/设置、账目增删改查、分类管理、支出统计、账本导入导出。
 * 全部直接读写本机账本文件（%APPDATA%\MucageLedger\ledger.json），与桌面界面等价。
 */
internal object McpTools {

    private const val MAX_LIMIT = 1000

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "get_balance",
            description = "查询当前总余额（余额锚点 + 锚点后的收支净额）。返回是否已初始化、余额、锚点信息与账目数量。",
            schema = schema(emptyList()),
            call = { getBalance() }
        ),
        McpTool(
            name = "set_balance",
            description = "设置当前总余额（元）。与界面上的「设置余额」一致：以今天 0 点为锚点，之后的账目会在此基础上增减。",
            schema(
                required = listOf("amount"),
                props = mapOf("amount" to numberProp("新的总余额，单位元，可为小数"))
            ),
            call = { setBalance(it) }
        ),
        McpTool(
            name = "list_records",
            description = "查询账目列表，可按时间区间、收支类型、分类、关键词过滤；返回摘要与账目明细（含 id）。",
            schema(
                props = mapOf(
                    "start" to stringProp("开始日期或时间，如 2026-11-01 或 2026-11-01 08:00"),
                    "end" to stringProp("结束日期或时间，按日期时包含当天"),
                    "type" to stringProp("收支类型：支出 / 收入 / EXPENSE / INCOME"),
                    "category" to stringProp("分类名称或分类 id"),
                    "keyword" to stringProp("备注关键词"),
                    "limit" to numberProp("最多返回多少条，默认 100，最大 $MAX_LIMIT")
                )
            ),
            call = { listRecords(it) }
        ),
        McpTool(
            name = "add_record",
            description = "记一笔账。金额单位为元；分类可用名称，省略时归入同类型「其他」。",
            schema(
                required = listOf("amount", "type"),
                props = mapOf(
                    "amount" to numberProp("金额，单位元，必须大于 0"),
                    "type" to stringProp("支出 或 收入"),
                    "category" to stringProp("分类名称或分类 id，省略则用「其他」"),
                    "note" to stringProp("备注"),
                    "date" to stringProp("记账时间，如 2026-11-01 12:30；省略为当前时间"),
                    "limitExempt" to numberProp("限额豁免：0 不豁免，1 仅豁免日限额，2 全部豁免")
                )
            ),
            call = { addRecord(it) }
        ),
        McpTool(
            name = "update_record",
            description = "修改一笔账目（按 list_records 返回的 id）。只需传要改的字段。",
            schema(
                required = listOf("id"),
                props = mapOf(
                    "id" to stringProp("账目 id（syncId）"),
                    "amount" to numberProp("新金额，单位元"),
                    "type" to stringProp("新的收支类型：支出 / 收入"),
                    "category" to stringProp("新的分类名称或 id"),
                    "note" to stringProp("新备注"),
                    "date" to stringProp("新的记账时间"),
                    "limitExempt" to numberProp("限额豁免：0 / 1 / 2")
                )
            ),
            call = { updateRecord(it) }
        ),
        McpTool(
            name = "delete_record",
            description = "删除一笔账目（软删除，导出给手机后删除也会同步）。",
            schema(
                required = listOf("id"),
                props = mapOf("id" to stringProp("账目 id（syncId）"))
            ),
            call = { deleteRecord(it) }
        ),
        McpTool(
            name = "list_categories",
            description = "列出可用分类（支出用途 / 收入来源），含 id、名称、图标与排序。",
            schema(props = mapOf("type" to stringProp("可选：支出 / 收入；省略则返回全部")))
            ,
            call = { listCategories(it) }
        ),
        McpTool(
            name = "add_category",
            description = "新增分类。同类型下名称不能重复；图标可省略。",
            schema(
                required = listOf("name", "type"),
                props = mapOf(
                    "name" to stringProp("分类名称，最多 8 个字"),
                    "type" to stringProp("支出 或 收入"),
                    "icon" to stringProp("图标 key，如 restaurant、work、more，可省略")
                )
            ),
            call = { addCategory(it) }
        ),
        McpTool(
            name = "update_category",
            description = "修改分类名称或图标。",
            schema(
                required = listOf("id"),
                props = mapOf(
                    "id" to numberProp("分类 id"),
                    "name" to stringProp("新名称"),
                    "icon" to stringProp("新图标 key")
                )
            ),
            call = { updateCategory(it) }
        ),
        McpTool(
            name = "delete_category",
            description = "删除分类；该分类下的账目会归入同类型「其他」，「其他」本身不可删除。",
            schema(
                required = listOf("id"),
                props = mapOf("id" to numberProp("分类 id"))
            ),
            call = { deleteCategory(it) }
        ),
        McpTool(
            name = "get_stats",
            description = "读取支出统计图表数据：指定月份（默认本月）的支出/收入合计、分类占比、每日收支序列与最大几笔支出。",
            schema(
                props = mapOf(
                    "month" to stringProp("月份，格式 2026-11，默认本月"),
                    "start" to stringProp("自定义开始日期，可与 end 搭配使用"),
                    "end" to stringProp("自定义结束日期")
                )
            ),
            call = { getStats(it) }
        ),
        McpTool(
            name = "import_ledger",
            description = "导入手机或其他电脑导出的账本文件（mucage-ledger JSON），与本机账本合并：按 syncId 去重、updatedAt 较新者胜出；源文件不会被修改。",
            schema(
                required = listOf("path"),
                props = mapOf("path" to stringProp("要导入的账本文件完整路径，如 D:\\sync\\账本.json"))
            ),
            call = { importLedger(it) }
        ),
        McpTool(
            name = "export_ledger",
            description = "把本机账本导出为同步文件（mucage-ledger JSON），可直接传到手机导入；本机账本不会被修改。省略 path 时写入账本目录的 exports 文件夹。",
            schema(
                props = mapOf("path" to stringProp("导出文件完整路径；省略则在 %APPDATA%\\MucageLedger\\exports 下自动命名"))
            ),
            call = { exportLedger(it) }
        )
    )

    fun call(params: JsonValue.Obj): JsonValue {
        val name = params.fields["name"]?.str() ?: throw McpToolException("缺少工具名 name")
        val tool = tools.firstOrNull { it.name == name } ?: throw McpToolException("未知工具：$name")
        val args = params.fields["arguments"]?.obj() ?: JsonValue.Obj(emptyMap())
        val payload = tool.call(args)
        return jObj(
            "content" to JsonValue.Arr(
                listOf(jObj("type" to JsonValue.Str("text"), "text" to JsonValue.Str(pretty(payload)))
                )
            ),
            "isError" to JsonValue.Bool(false)
        )
    }

    fun error(message: String): JsonValue = jObj(
        "content" to JsonValue.Arr(listOf(jObj("type" to JsonValue.Str("text"), "text" to JsonValue.Str(message)))),
        "isError" to JsonValue.Bool(true)
    )

    // ---------------- 余额 ----------------

    private fun getBalance(): JsonValue {
        val data = DesktopLedgerStore.load()
        val balance = data.balance
        val current = currentBalance(data)
        return jObj(
            "initialized" to JsonValue.Bool(balance.initialized && balance.anchoredAt > 0),
            "balance" to amountNode(current ?: 0L),
            "balanceCents" to JsonValue.Num((current ?: 0L).toString()),
            "anchorBalance" to amountNode(balance.balanceCents),
            "anchoredAt" to JsonValue.Str(formatDateTime(balance.anchoredAt)),
            "recordCount" to JsonValue.Num(data.records.count { !it.deleted }.toString()),
            "hint" to JsonValue.Str(
                if (current == null) "尚未设置余额，可调用 set_balance 设置"
                else "余额 = 锚点余额 + 锚点之后的收入 - 支出"
            )
        )
    }

    private fun setBalance(args: JsonValue.Obj): JsonValue {
        val cents = args.amountCents() ?: throw McpToolException("参数 amount 必须是数字（单位元）")
        val data = DesktopLedgerStore.load()
        val anchor = startOfToday()
        val updated = data.copy(
            balance = data.balance.copy(
                balanceCents = cents,
                anchoredAt = anchor,
                initialized = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        persist(updated)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "balance" to amountNode(currentBalance(updated) ?: cents),
            "balanceCents" to JsonValue.Num((currentBalance(updated) ?: cents).toString()),
            "anchoredAt" to JsonValue.Str(formatDateTime(anchor)),
            "hint" to JsonValue.Str("已以今天 0 点为锚点设置余额")
        )
    }

    // ---------------- 账目 ----------------

    private fun listRecords(args: JsonValue.Obj): JsonValue {
        val data = DesktopLedgerStore.load()
        val start = parseDateTime(args.fields["start"]?.str(), endOfDay = false)
        val end = parseDateTime(args.fields["end"]?.str(), endOfDay = true)
        val rawType = args.fields["type"]?.str()?.takeIf { it.isNotBlank() }
        val type = rawType?.let { normalizeType(it) ?: throw McpToolException("无法识别的 type：$it（应为 支出/收入）") }
        val categoryRaw = args.fields["category"]?.str()
        val keyword = args.fields["keyword"]?.str()?.trim().orEmpty()
        val limit = (args.fields["limit"]?.long() ?: 100L).coerceIn(1, MAX_LIMIT.toLong()).toInt()

        val filtered = data.records.filter { record ->
            !record.deleted &&
                (type == null || record.type == type) &&
                (start == null || record.date >= start) &&
                (end == null || record.date <= end) &&
                (categoryRaw == null || matchesCategory(data.categories, record, categoryRaw)) &&
                (keyword.isEmpty() || record.note.contains(keyword, ignoreCase = true) ||
                    categoryName(data.categories, record).contains(keyword, ignoreCase = true))
        }.sortedByDescending { it.date }.take(limit)

        return jObj(
            "count" to JsonValue.Num(filtered.size.toString()),
            "totalExpense" to amountNode(filtered.filter { it.type == CategoryType.EXPENSE.code }.sumOf { it.amountCents }),
            "totalIncome" to amountNode(filtered.filter { it.type == CategoryType.INCOME.code }.sumOf { it.amountCents }),
            "records" to JsonValue.Arr(filtered.map { recordJson(it, data.categories) })
        )
    }

    private fun addRecord(args: JsonValue.Obj): JsonValue {
        val cents = args.amountCents() ?: throw McpToolException("参数 amount 必须是数字（单位元）")
        if (cents <= 0) throw McpToolException("金额必须大于 0")
        val type = normalizeType(args.fields["type"]?.str())
            ?: throw McpToolException("参数 type 必须是 支出 或 收入")
        val data = DesktopLedgerStore.load()
        val category = resolveCategory(data.categories, args.fields["category"]?.str(), type)
            ?: throw McpToolException(categoryError(data.categories, type))
        val date = parseDateTime(args.fields["date"]?.str(), endOfDay = false) ?: System.currentTimeMillis()
        val record = SyncRecord(
            syncId = UUID.randomUUID().toString(),
            amountCents = cents,
            type = type,
            categoryId = category.id,
            note = args.fields["note"]?.str().orEmpty().trim().take(60),
            date = date,
            limitExempt = (args.fields["limitExempt"]?.int() ?: 0).coerceIn(0, 2),
            updatedAt = System.currentTimeMillis()
        )
        val updated = data.copy(records = sortRecords(data.records + record))
        persist(updated)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "record" to recordJson(record, updated.categories),
            "balance" to amountNode(currentBalance(updated) ?: 0L)
        )
    }

    private fun updateRecord(args: JsonValue.Obj): JsonValue {
        val id = args.fields["id"]?.str()?.trim().orEmpty()
        if (id.isBlank()) throw McpToolException("参数 id 不能为空")
        val data = DesktopLedgerStore.load()
        val target = data.records.firstOrNull { it.syncId == id && !it.deleted }
            ?: throw McpToolException("找不到账目：$id")

        val type = args.fields["type"]?.str()?.let {
            normalizeType(it) ?: throw McpToolException("无法识别的 type：$it")
        } ?: target.type
        val cents = if (args.fields.containsKey("amount")) {
            args.amountCents() ?: throw McpToolException("参数 amount 必须是数字（单位元）")
        } else target.amountCents
        if (cents <= 0) throw McpToolException("金额必须大于 0")
        val date = if (args.fields.containsKey("date")) {
            parseDateTime(args.fields["date"]?.str(), endOfDay = false)
                ?: throw McpToolException("无法识别的 date：${args.fields["date"]}")
        } else target.date
        val category = when {
            args.fields.containsKey("category") ->
                resolveCategory(data.categories, args.fields["category"]?.str(), type)
                    ?: throw McpToolException(categoryError(data.categories, type))
            type != target.type -> resolveCategory(data.categories, null, type)
                ?: throw McpToolException(categoryError(data.categories, type))
            else -> data.categories.firstOrNull { it.id == target.categoryId && !it.deleted }
                ?: resolveCategory(data.categories, null, type)
                ?: throw McpToolException(categoryError(data.categories, type))
        }
        val updatedRecord = target.copy(
            amountCents = cents,
            type = type,
            categoryId = category.id,
            note = (args.fields["note"]?.str() ?: target.note).trim().take(60),
            date = date,
            limitExempt = (args.fields["limitExempt"]?.int() ?: target.limitExempt).coerceIn(0, 2),
            updatedAt = System.currentTimeMillis()
        )
        val updated = data.copy(records = sortRecords(data.records.map { if (it.syncId == id) updatedRecord else it }))
        persist(updated)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "record" to recordJson(updatedRecord, updated.categories)
        )
    }

    private fun deleteRecord(args: JsonValue.Obj): JsonValue {
        val id = args.fields["id"]?.str()?.trim().orEmpty()
        if (id.isBlank()) throw McpToolException("参数 id 不能为空")
        val data = DesktopLedgerStore.load()
        val target = data.records.firstOrNull { it.syncId == id && !it.deleted }
            ?: throw McpToolException("找不到账目：$id")
        val now = System.currentTimeMillis()
        val updated = data.copy(
            records = sortRecords(data.records.map {
                if (it.syncId == id) it.copy(deleted = true, updatedAt = now) else it
            })
        )
        persist(updated)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "id" to JsonValue.Str(id),
            "deletedRecord" to recordJson(target.copy(deleted = true, updatedAt = now), updated.categories)
        )
    }

    // ---------------- 分类 ----------------

    private fun listCategories(args: JsonValue.Obj): JsonValue {
        val data = DesktopLedgerStore.load()
        val type = args.fields["type"]?.str()?.takeIf { it.isNotBlank() }?.let {
            normalizeType(it) ?: throw McpToolException("无法识别的 type：$it")
        }
        val list = data.categories.filter { !it.deleted && (type == null || it.type == type) }
            .sortedWith(compareBy({ it.type }, { it.sortOrder }))
        return jObj(
            "count" to JsonValue.Num(list.size.toString()),
            "categories" to JsonValue.Arr(list.map { categoryJson(it) })
        )
    }

    private fun addCategory(args: JsonValue.Obj): JsonValue {
        val name = args.fields["name"]?.str()?.trim().orEmpty()
        if (name.isBlank()) throw McpToolException("参数 name 不能为空")
        if (name.length > 8) throw McpToolException("分类名称最多 8 个字")
        val type = normalizeType(args.fields["type"]?.str())
            ?: throw McpToolException("参数 type 必须是 支出 或 收入")
        val icon = args.fields["icon"]?.str()?.trim().orEmpty().ifBlank { "more" }
        val data = DesktopLedgerStore.load()
        val next = DesktopLedgerStore.addCategory(data.categories, name, icon, type)
            ?: throw McpToolException("新增失败：同类型下已存在同名分类，或名称为空")
        val created = next.firstOrNull { it.name == name && it.type == type }
        val updated = data.copy(categories = next)
        persist(updated)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "category" to (created?.let { categoryJson(it) } ?: JsonValue.Null)
        )
    }

    private fun updateCategory(args: JsonValue.Obj): JsonValue {
        val id = args.fields["id"]?.long() ?: throw McpToolException("参数 id 必须是分类数字 id")
        val data = DesktopLedgerStore.load()
        val target = data.categories.firstOrNull { it.id == id && !it.deleted }
            ?: throw McpToolException("找不到分类：$id")
        val name = args.fields["name"]?.str()?.trim()?.takeIf { it.isNotEmpty() } ?: target.name
        val icon = args.fields["icon"]?.str()?.trim()?.takeIf { it.isNotEmpty() } ?: target.icon
        val next = DesktopLedgerStore.updateCategory(data.categories, id, name, icon)
            ?: throw McpToolException("修改失败：同类型下已存在同名分类")
        val updated = data.copy(categories = next)
        persist(updated)
        val changed = next.firstOrNull { it.id == id }
        return jObj(
            "ok" to JsonValue.Bool(true),
            "category" to (changed?.let { categoryJson(it) } ?: JsonValue.Null)
        )
    }

    private fun deleteCategory(args: JsonValue.Obj): JsonValue {
        val id = args.fields["id"]?.long() ?: throw McpToolException("参数 id 必须是分类数字 id")
        val data = DesktopLedgerStore.load()
        val result = DesktopLedgerStore.deleteCategory(data, id)
            ?: throw McpToolException("删除失败：分类不存在，或为不可删除的「其他」")
        persist(result.first)
        return jObj(
            "ok" to JsonValue.Bool(true),
            "message" to JsonValue.Str(result.second)
        )
    }

    // ---------------- 统计 ----------------

    private fun getStats(args: JsonValue.Obj): JsonValue {
        val data = DesktopLedgerStore.load()
        val (start, end) = resolveRange(args)
        val records = data.records.filter { !it.deleted && it.date in start..end }
        val expenses = records.filter { it.type == CategoryType.EXPENSE.code }
        val incomes = records.filter { it.type == CategoryType.INCOME.code }

        val totalExpense = expenses.sumOf { it.amountCents }
        val totalIncome = incomes.sumOf { it.amountCents }

        val categoryTotals = expenses.groupBy { it.categoryId }
            .map { (categoryId, list) -> Triple(categoryId, list.sumOf { it.amountCents }, list.size) }
            .sortedByDescending { it.second }

        val byCategory = categoryTotals.map { (categoryId, amount, count) ->
            jObj(
                "categoryId" to JsonValue.Num(categoryId.toString()),
                "category" to JsonValue.Str(
                    categoryName(data.categories, expenses.first { it.categoryId == categoryId })
                ),
                "amount" to amountNode(amount),
                "count" to JsonValue.Num(count.toString()),
                "share" to JsonValue.Num(
                    String.format(Locale.US, "%.3f", share(amount, totalExpense))
                )
            )
        }

        val daily = mutableListOf<JsonValue>()
        var day = java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
        val lastDay = java.time.Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()
        while (!day.isAfter(lastDay)) {
            val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val dayExpense = expenses.filter { it.date in dayStart..dayEnd }.sumOf { it.amountCents }
            val dayIncome = incomes.filter { it.date in dayStart..dayEnd }.sumOf { it.amountCents }
            daily += jObj(
                "date" to JsonValue.Str(day.toString()),
                "expense" to amountNode(dayExpense),
                "income" to amountNode(dayIncome)
            )
            day = day.plusDays(1)
        }

        val top = expenses.sortedByDescending { it.amountCents }.take(5).map { recordJson(it, data.categories) }

        return jObj(
            "range" to jObj(
                "start" to JsonValue.Str(formatDateTime(start)),
                "end" to JsonValue.Str(formatDateTime(end))
            ),
            "expense" to jObj(
                "amount" to amountNode(totalExpense),
                "count" to JsonValue.Num(expenses.size.toString())
            ),
            "income" to jObj(
                "amount" to amountNode(totalIncome),
                "count" to JsonValue.Num(incomes.size.toString())
            ),
            "net" to amountNode(totalIncome - totalExpense),
            "byCategory" to JsonValue.Arr(byCategory),
            "daily" to JsonValue.Arr(daily),
            "topExpenses" to JsonValue.Arr(top)
        )
    }

    // ---------------- 账本导入导出 ----------------

    private fun importLedger(args: JsonValue.Obj): JsonValue {
        val raw = args.fields["path"]?.str()?.trim().orEmpty()
        if (raw.isBlank()) throw McpToolException("参数 path 不能为空（要导入的账本文件）")
        val source = File(raw)
        if (!source.isFile) throw McpToolException("找不到账本文件：${source.absolutePath}")
        if (sameFile(source, DesktopLedgerStore.file)) {
            throw McpToolException("不能导入本机账本自身：${DesktopLedgerStore.file.absolutePath}")
        }
        val payload = DesktopLedgerStore.readPayload(source)
            ?: throw McpToolException("无法解析账本文件，请确认是本应用导出的 JSON：${source.absolutePath}")
        val current = DesktopLedgerStore.load()
        val merged = DesktopLedgerStore.merge(current, payload)
        persist(merged.data)
        val result = merged.result
        return jObj(
            "ok" to JsonValue.Bool(true),
            "source" to JsonValue.Str(source.absolutePath),
            "inserted" to JsonValue.Num(result.inserted.toString()),
            "updated" to JsonValue.Num(result.updated.toString()),
            "skipped" to JsonValue.Num(result.skipped.toString()),
            "categoriesChanged" to JsonValue.Num(result.categoriesChanged.toString()),
            "balanceUpdated" to JsonValue.Bool(result.balanceUpdated),
            "localOnly" to JsonValue.Num(result.localOnly.toString()),
            "summary" to JsonValue.Str(result.summary()),
            "recordCount" to JsonValue.Num(merged.data.records.count { !it.deleted }.toString()),
            "balance" to amountNode(currentBalance(merged.data) ?: 0L),
            "hint" to JsonValue.Str(
                if (result.localOnly > 0) {
                    "本机另有 ${result.localOnly} 笔不在源文件中，可调用 export_ledger 导出后传回另一端"
                } else {
                    "已与源文件合并，源文件保持不变"
                }
            )
        )
    }

    private fun exportLedger(args: JsonValue.Obj): JsonValue {
        val data = DesktopLedgerStore.load()
        val raw = args.fields["path"]?.str()?.trim().orEmpty()
        val target = if (raw.isBlank()) defaultExportFile() else File(raw)
        if (target.isDirectory) throw McpToolException("导出路径是文件夹，请给出具体文件名：${target.absolutePath}")
        if (sameFile(target, DesktopLedgerStore.file)) {
            throw McpToolException("不能覆盖本机账本文件：${DesktopLedgerStore.file.absolutePath}")
        }
        val existed = target.exists()
        val result = DesktopLedgerStore.exportTo(target, data)
        result.exceptionOrNull()?.let {
            throw McpToolException("写入导出文件失败：${it.message ?: it.javaClass.simpleName}")
        }
        return jObj(
            "ok" to JsonValue.Bool(true),
            "path" to JsonValue.Str(target.absolutePath),
            "overwritten" to JsonValue.Bool(existed),
            "bytes" to JsonValue.Num(target.length().toString()),
            "recordCount" to JsonValue.Num(data.records.count { !it.deleted }.toString()),
            "tombstones" to JsonValue.Num(data.records.count { it.deleted }.toString()),
            "balance" to amountNode(currentBalance(data) ?: 0L),
            "exportedAt" to JsonValue.Str(formatDateTime(System.currentTimeMillis())),
            "hint" to JsonValue.Str("把该文件传到手机，在手机「设置 → 数据同步 → 从文件导入」即可合并；本机账本未被修改")
        )
    }

    private fun defaultExportFile(): File {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        return File(File(DesktopLedgerStore.file.parentFile, "exports"), "SanBaiLiang-ledger-$stamp.json")
    }

    private fun sameFile(a: File, b: File): Boolean =
        runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(false)

    // ---------------- 内部工具 ----------------

    private fun resolveRange(args: JsonValue.Obj): Pair<Long, Long> {
        val month = args.fields["month"]?.str()?.trim()
        val customStart = parseDateTime(args.fields["start"]?.str(), endOfDay = false)
        val customEnd = parseDateTime(args.fields["end"]?.str(), endOfDay = true)
        if (customStart != null && customEnd != null) return customStart to customEnd
        if (!month.isNullOrBlank()) {
            val parsed = runCatching { java.time.YearMonth.parse(month) }.getOrNull()
                ?: throw McpToolException("month 格式应为 YYYY-MM，如 2026-11")
            val start = parsed.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = parsed.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            return start to end
        }
        val now = LocalDate.now()
        val start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return start to end
    }

    private fun currentBalance(data: LedgerData): Long? {
        val balance = data.balance
        if (!balance.initialized || balance.anchoredAt <= 0) return null
        val flow = data.records
            .filter { !it.deleted && it.date >= balance.anchoredAt }
            .sumOf { if (it.type == CategoryType.INCOME.code) it.amountCents else -it.amountCents }
        return balance.balanceCents + flow
    }

    private fun persist(data: LedgerData) {
        val result = DesktopLedgerStore.save(data)
        result.exceptionOrNull()?.let { throw McpToolException("写入本机账本失败：${it.message ?: it.javaClass.simpleName}") }
    }

    private fun categoryName(categories: List<SyncCategory>, record: SyncRecord): String =
        categories.firstOrNull { it.id == record.categoryId && it.type == record.type }?.name ?: "其他"

    private fun matchesCategory(categories: List<SyncCategory>, record: SyncRecord, raw: String): Boolean {
        val value = raw.trim()
        if (value.isBlank()) return true
        value.toLongOrNull()?.let { return record.categoryId == it }
        return categoryName(categories, record) == value
    }

    private fun resolveCategory(categories: List<SyncCategory>, raw: String?, type: String): SyncCategory? {
        val visible = categories.filter { !it.deleted && it.type == type }
        if (raw.isNullOrBlank()) {
            return visible.firstOrNull { it.id == FixedCategories.otherIdFor(type) }
        }
        val value = raw.trim()
        value.toLongOrNull()?.let { id ->
            visible.firstOrNull { it.id == id }?.let { return it }
        }
        return visible.firstOrNull { it.name == value }
    }

    private fun categoryError(categories: List<SyncCategory>, type: String): String {
        val names = categories.filter { !it.deleted && it.type == type }.map { it.name }
        val typeText = if (type == CategoryType.INCOME.code) "收入" else "支出"
        return "分类不存在。可用的${typeText}分类：" + names.joinToString("、")
    }

    private fun recordJson(record: SyncRecord, categories: List<SyncCategory>): JsonValue.Obj = jObj(
        "id" to JsonValue.Str(record.syncId),
        "date" to JsonValue.Str(formatDateTime(record.date)),
        "dateMillis" to JsonValue.Num(record.date.toString()),
        "amount" to amountNode(record.amountCents),
        "amountCents" to JsonValue.Num(record.amountCents.toString()),
        "type" to JsonValue.Str(if (record.type == CategoryType.INCOME.code) "收入" else "支出"),
        "typeCode" to JsonValue.Str(record.type),
        "categoryId" to JsonValue.Num(record.categoryId.toString()),
        "category" to JsonValue.Str(categoryName(categories, record)),
        "note" to JsonValue.Str(record.note),
        "limitExempt" to JsonValue.Num(record.limitExempt.toString()),
        "deleted" to JsonValue.Bool(record.deleted)
    )

    private fun categoryJson(category: SyncCategory): JsonValue.Obj = jObj(
        "id" to JsonValue.Num(category.id.toString()),
        "name" to JsonValue.Str(category.name),
        "type" to JsonValue.Str(if (category.type == CategoryType.INCOME.code) "收入" else "支出"),
        "typeCode" to JsonValue.Str(category.type),
        "icon" to JsonValue.Str(category.icon),
        "sortOrder" to JsonValue.Num(category.sortOrder.toString())
    )

    private fun normalizeType(raw: String?): String? = when (raw?.trim()?.uppercase(Locale.US)) {
        "EXPENSE", "支出", "消费", "花销" -> CategoryType.EXPENSE.code
        "INCOME", "收入", "进账" -> CategoryType.INCOME.code
        else -> null
    }

    private fun JsonValue.Obj.amountCents(): Long? {
        val node = fields["amount"] ?: return null
        val amount = when (node) {
            is JsonValue.Num -> node.raw.toDoubleOrNull()
            is JsonValue.Str -> node.value.toDoubleOrNull()
            else -> null
        } ?: return null
        return Math.round(amount * 100)
    }

    private fun parseDateTime(raw: String?, endOfDay: Boolean): Long? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toLongOrNull()?.let { return it }
        val normalized = raw.trim().replace('/', '-')
        if (Regex("""\d{4}-\d{2}-\d{2}""").matches(normalized)) {
            val date = runCatching { LocalDate.parse(normalized) }.getOrNull() ?: return null
            val at = if (endOfDay) date.plusDays(1).atStartOfDay().minusNanos(1) else date.atStartOfDay()
            return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        for (pattern in onceFormats) {
            val dt = runCatching { LocalDateTime.parse(normalized.replace('T', ' '), pattern) }.getOrNull()
            if (dt != null) return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private val onceFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    )

    private fun formatDateTime(millis: Long): String {
        if (millis <= 0) return ""
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun share(amount: Long, total: Long): Double =
        if (total <= 0) 0.0 else Math.round(amount.toDouble() / total.toDouble() * 1000.0) / 1000.0

    private fun amountNode(cents: Long): JsonValue =
        JsonValue.Num(String.format(Locale.US, "%.2f", cents / 100.0))

    private fun pretty(value: JsonValue): String = com.mucage.sync.Json.write(value)

    private fun jObj(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
        JsonValue.Obj(linkedMapOf(*pairs))

    private fun schema(
        required: List<String> = emptyList(),
        props: Map<String, JsonValue> = emptyMap()
    ): JsonValue.Obj {
        val properties = linkedMapOf<String, JsonValue>()
        properties.putAll(props)
        return jObj(
            "type" to JsonValue.Str("object"),
            "properties" to JsonValue.Obj(properties),
            "required" to JsonValue.Arr(required.map { JsonValue.Str(it) })
        )
    }

    private fun stringProp(description: String): JsonValue.Obj = jObj(
        "type" to JsonValue.Str("string"),
        "description" to JsonValue.Str(description)
    )

    private fun numberProp(description: String): JsonValue.Obj = jObj(
        "type" to JsonValue.Str("number"),
        "description" to JsonValue.Str(description)
    )
}
