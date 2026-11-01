package com.mucage.rmb.desktop

import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.MergeResult
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncCategory
import com.mucage.sync.SyncCodec
import com.mucage.sync.SyncMerge
import com.mucage.sync.SyncPayload
import com.mucage.sync.SyncRecord
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** 桌面端账本的全部内容：账目 + 余额 + 分类定义 */
internal data class LedgerData(
    val records: List<SyncRecord> = emptyList(),
    val balance: SyncBalance = SyncBalance(0L, 0L, false, 0L),
    val categories: List<SyncCategory> = FixedCategories.defaultSyncCategories
)

/** 合并结果 */
internal data class LedgerMerge(
    val data: LedgerData,
    val result: MergeResult
)

/**
 * 桌面端本机账本文件（%APPDATA%\MucageLedger\ledger.json）。
 * 桌面端只维护这一份自己的文件：导入手机导出的文件只做合并、绝不改写它；
 * 导出给手机时会另存为新文件，也不动本机账本。
 */
internal object DesktopLedgerStore {

    private val root: File by lazy {
        // MUCAGE_LEDGER_HOME 可指定账本目录（便携使用或 MCP 联调测试时使用），默认仍为 %APPDATA%\MucageLedger
        val custom = System.getenv("MUCAGE_LEDGER_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        val dir = if (custom != null) File(custom) else File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MucageLedger")
        dir.also { it.mkdirs() }
    }

    val file: File get() = File(root, "ledger.json")

    fun load(): LedgerData {
        val target = file
        if (!target.exists()) return LedgerData()
        val text = runCatching { target.readText() }.getOrNull() ?: return LedgerData()
        val payload = runCatching { SyncCodec.decode(text) }.getOrNull()
        if (payload == null) {
            // 文件损坏时不静默覆盖：先留一份备份，再从空账本开始
            runCatching {
                val backup = File(root, "ledger-broken-${System.currentTimeMillis()}.json")
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return LedgerData()
        }
        return LedgerData(
            records = payload.records,
            balance = payload.balance,
            categories = ensureFallbacks(payload.categories)
        )
    }

    /** 写入本机账本：先写临时文件再替换，避免写入中断导致账本损坏 */
    fun save(data: LedgerData): Result<Unit> = runCatching {
        val text = SyncCodec.encode(
            SyncPayload(
                exportedAt = System.currentTimeMillis(),
                records = data.records,
                balance = data.balance,
                categories = data.categories
            )
        )
        val target = file
        val tmp = File(root, "ledger.tmp")
        Files.writeString(
            tmp.toPath(),
            text,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    /** 导出给手机：写入指定路径，不影响本机账本 */
    fun exportTo(target: File, data: LedgerData): Result<Unit> = runCatching {
        val payload = SyncPayload(
            exportedAt = System.currentTimeMillis(),
            records = data.records,
            balance = data.balance,
            categories = data.categories
        )
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        Files.writeString(
            target.toPath(),
            SyncCodec.encode(payload),
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    /** 读取手机导出的账本（只读，不改动源文件） */
    fun readPayload(source: File): SyncPayload? = runCatching {
        SyncCodec.decode(source.readText())
    }.getOrNull()

    /** 把导入文件合并进本机账本；源文件保持原样 */
    fun merge(current: LedgerData, payload: SyncPayload): LedgerMerge {
        var categoriesChanged = 0
        val categoryById = current.categories.associateBy { it.id }.toMutableMap()
        payload.categories.forEach { incoming ->
            val local = categoryById[incoming.id]
            when {
                local == null -> {
                    if (incoming.name.isNotBlank()) {
                        categoryById[incoming.id] = incoming
                        categoriesChanged++
                    }
                }
                SyncMerge.shouldWriteCategory(incoming, local.updatedAt) -> {
                    categoryById[incoming.id] = incoming
                    categoriesChanged++
                }
            }
        }
        val mergedCategories = ensureFallbacks(categoryById.values.toList())

        val validExpenseIds = mergedCategories
            .filter { !it.deleted && it.type == CategoryType.EXPENSE.code }
            .map { it.id }
            .toSet() + FixedCategories.EXPENSE_OTHER
        val validIncomeIds = mergedCategories
            .filter { !it.deleted && it.type == CategoryType.INCOME.code }
            .map { it.id }
            .toSet() + FixedCategories.INCOME_OTHER

        val recordById = current.records.associateBy { it.syncId }.toMutableMap()
        var inserted = 0
        var updated = 0
        var skipped = 0
        payload.records.forEach { raw ->
            val incoming = SyncMerge.normalize(raw, validExpenseIds, validIncomeIds)
            val local = recordById[incoming.syncId]
            when {
                local == null -> {
                    recordById[incoming.syncId] = incoming
                    inserted++
                }
                SyncMerge.shouldWrite(incoming, local.updatedAt) -> {
                    recordById[incoming.syncId] = incoming
                    updated++
                }
                else -> skipped++
            }
        }

        val balanceUpdated = SyncMerge.shouldTakeBalance(payload.balance, current.balance)
        // 本机有、但对方文件里没有的账目：提示用户反向导出一次
        val incomingIds = payload.records.map { it.syncId }.toSet()
        val localOnly = current.records.count { !it.deleted && it.syncId !in incomingIds }
        return LedgerMerge(
            data = LedgerData(
                records = sortRecords(recordById.values.toList()),
                balance = if (balanceUpdated) payload.balance else current.balance,
                categories = mergedCategories.sortedWith(compareBy({ it.type }, { it.sortOrder }))
            ),
            result = MergeResult(
                inserted = inserted,
                updated = updated,
                skipped = skipped,
                balanceUpdated = balanceUpdated,
                categoriesChanged = categoriesChanged,
                localOnly = localOnly
            )
        )
    }

    /** 保证每种类型都有可兜底的「其他」分类（即使另一端把它删掉了） */
    fun ensureFallbacks(categories: List<SyncCategory>): List<SyncCategory> {
        val result = categories.toMutableList()
        listOf(
            FixedCategories.EXPENSE_OTHER to CategoryType.EXPENSE.code,
            FixedCategories.INCOME_OTHER to CategoryType.INCOME.code
        ).forEach { (id, type) ->
            val existing = result.firstOrNull { it.id == id }
            if (existing == null) {
                result += FixedCategories.defaultSyncCategories.first { it.id == id }
            } else if (existing.deleted) {
                // 兜底分类被同步成碑时在本机恢复（否则账目无处归类）
                result[result.indexOf(existing)] = existing.copy(deleted = false, updatedAt = 0)
            }
        }
        return result.sortedWith(compareBy({ it.type }, { it.sortOrder }))
    }

    /** 新建分类（本机账本里的分类定义） */
    fun addCategory(categories: List<SyncCategory>, name: String, icon: String, type: String): List<SyncCategory>? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val siblings = categories.filter { it.type == type && !it.deleted }
        if (siblings.any { it.name == trimmed }) return null
        val newCategory = SyncCategory(
            id = FixedCategories.newCategoryId(),
            name = trimmed,
            icon = icon,
            type = type,
            sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            updatedAt = System.currentTimeMillis()
        )
        return (categories + newCategory).sortedWith(compareBy({ it.type }, { it.sortOrder }))
    }

    /** 修改分类名称/图标 */
    fun updateCategory(
        categories: List<SyncCategory>,
        id: Long,
        name: String,
        icon: String
    ): List<SyncCategory>? {
        val target = categories.firstOrNull { it.id == id && !it.deleted } ?: return null
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        if (categories.any { it.type == target.type && !it.deleted && it.id != id && it.name == trimmed }) return null
        return categories.map {
            if (it.id == id) it.copy(name = trimmed, icon = icon, updatedAt = System.currentTimeMillis()) else it
        }
    }

    /** 删除分类：其他分类禁删；该分类下的账目归入同类型「其他」 */
    fun deleteCategory(
        data: LedgerData,
        id: Long
    ): Pair<LedgerData, String>? {
        val target = data.categories.firstOrNull { it.id == id && !it.deleted } ?: return null
        if (id == FixedCategories.EXPENSE_OTHER || id == FixedCategories.INCOME_OTHER) return null
        val now = System.currentTimeMillis()
        val fallback = FixedCategories.otherIdFor(target.type)
        val categories = data.categories.map {
            if (it.id == id) it.copy(deleted = true, updatedAt = now) else it
        }
        val records = data.records.map { record ->
            if (record.categoryId == id) record.copy(categoryId = fallback, updatedAt = now) else record
        }
        val moved = data.records.count { it.categoryId == id && !it.deleted }
        return (data.copy(records = sortRecords(records), categories = categories)) to
            "已删除「${target.name}」" + if (moved > 0) "，$moved 笔账目已归入「其他」" else ""
    }

    fun categoryName(categories: List<SyncCategory>, categoryId: Long, type: String): String =
        categories.firstOrNull { it.id == categoryId && it.type == type }?.name ?: "其他"

    fun visibleCategories(categories: List<SyncCategory>, type: String): List<SyncCategory> =
        categories.filter { it.type == type && !it.deleted }.sortedBy { it.sortOrder }
}
