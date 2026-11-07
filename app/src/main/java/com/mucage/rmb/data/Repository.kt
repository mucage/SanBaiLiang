package com.mucage.rmb.data

import android.content.Context
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.formatCents
import com.mucage.rmb.util.todayStartMillis
import com.mucage.sync.AutoSavePeriod
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.MergeResult
import com.mucage.sync.SavingsAuto
import com.mucage.sync.SavingsMath
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncCategory
import com.mucage.sync.SyncMerge
import com.mucage.sync.SyncPayload
import com.mucage.sync.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** 全局数据仓库，封装数据库访问入口 */
object Repository {
    private lateinit var database: AppDatabase

    fun init(context: Context) {
        if (!::database.isInitialized) {
            database = AppDatabase.get(context)
            repairLegacyCategoryIds()
            backfillSyncIds()
        }
    }

    /** 升级到 v8 后，为历史账目补发同步标识与修改时间 */
    private fun backfillSyncIds() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recordDao.getWithoutSyncId().forEach { record ->
                    recordDao.update(
                        record.copy(
                            syncId = java.util.UUID.randomUUID().toString(),
                            updatedAt = record.date
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 修复历史遗留问题：旧版本新建分类 id 固定为 0（无法记账且会主键冲突），重新分配唯一 id */
    private fun repairLegacyCategoryIds() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = categoryDao.getByType(RecordType.EXPENSE) + categoryDao.getByType(RecordType.INCOME)
                val broken = all.filter { it.id == 0L }
                if (broken.isEmpty()) return@launch
                var nextId = (all.maxOfOrNull { it.id } ?: 0L) + 1
                broken.forEach { category ->
                    categoryDao.update(category.copy(id = nextId))
                    nextId++
                }
            } catch (_: Exception) {
            }
        }
    }

    val recordDao: RecordDao get() = database.recordDao()
    val categoryDao: CategoryDao get() = database.categoryDao()
    val autoPendingDao: AutoPendingDao get() = database.autoPendingDao()
    val balanceDao: BalanceDao get() = database.balanceDao()

    /** 新增待确认自动记账；按去重键去重，返回是否插入成功 */
    suspend fun addAutoPending(entity: AutoPendingEntity): Boolean {
        if (autoPendingDao.countByDedupKey(entity.dedupKey) > 0) return false
        autoPendingDao.insert(entity)
        return true
    }

    /** 自动记账查重窗口：同类型、同金额在 10 分钟内视为疑似重复 */
    const val AUTO_DUPLICATE_WINDOW_MS = 10 * 60 * 1000L

    /**
     * 自动记账查重：同一类型、同等金额在 10 分钟内的账目或待确认记录视为疑似重复。
     */
    suspend fun duplicateHints(items: List<AutoPendingEntity>): Map<Long, List<String>> {
        if (items.isEmpty()) return emptyMap()
        val window = AUTO_DUPLICATE_WINDOW_MS
        val since = items.minOf { it.capturedAt } - window
        val records = runCatching { recordDao.getSince(since) }.getOrDefault(emptyList())
        return items.associate { item ->
            val hits = mutableListOf<String>()
            records.firstOrNull { record ->
                record.type == item.type && record.amountCents == item.amountCents &&
                    abs(record.date - item.capturedAt) <= window
            }?.let { hit ->
                hits += "账本里 ${DateUtils.formatTime(hit.date)} 已有一笔 ¥${formatCents(hit.amountCents)} 的账目" +
                    if (hit.note.isNotBlank()) "（${hit.note}）" else ""
            }
            items.firstOrNull { other ->
                other.id != item.id && other.status == AutoPendingStatus.PENDING &&
                    other.type == item.type && other.amountCents == item.amountCents &&
                    abs(other.capturedAt - item.capturedAt) <= window
            }?.let { other ->
                hits += "另一条待确认来自${other.sourceLabel}（${DateUtils.formatTime(other.capturedAt)}），金额相同"
            }
            item.id to hits
        }
    }

    /** 待确认列表里疑似重复的条数（用于通知文案） */
    suspend fun countSuspectedDuplicates(): Int =
        duplicateHints(autoPendingDao.getPending()).values.count { it.isNotEmpty() }

    /** 确认一条自动记账：按用户编辑后的字段写入 records 并标记已入账 */
    suspend fun confirmAutoRecord(
        id: Long,
        amountCents: Long,
        type: String,
        categoryId: Long,
        note: String,
        date: Long
    ) {
        recordDao.insert(
            RecordEntity(
                amountCents = amountCents,
                type = type,
                categoryId = categoryId,
                note = note,
                date = date
            )
        )
        autoPendingDao.updateStatus(id, AutoPendingStatus.CONFIRMED)
    }

    /** 忽略（丢弃）一条自动记账 */
    suspend fun discardAutoRecord(id: Long) {
        autoPendingDao.updateStatus(id, AutoPendingStatus.DISCARDED)
    }

    /**
     * 设置/重设总余额（首次输入初始余额与每月核对共用）。
     * 以「天」为粒度记录锚点（当天 00:00）：当天及之后的账目计入余额计算，之前的不计入。
     * 手工记账的日期即为当天 00:00，故不能用精确到毫秒的时刻，否则当天新账目会被排除。
     */
    suspend fun setTotalBalance(cents: Long) {
        val previous = balanceDao.get()
        val anchor = todayStartMillis()
        // 重设总额时保留攒着与自动攒着配置，只把攒着夹到新的总余额范围内
        val newTotal = cents + recordDao.netFlowSince(anchor)
        val saved = SavingsMath.clampSaved(previous?.savedCents ?: 0L, newTotal)
        balanceDao.upsert(
            (previous ?: BalanceEntity()).copy(
                id = 1,
                balanceCents = cents,
                anchoredAt = anchor,
                initialized = true,
                updatedAt = System.currentTimeMillis(),
                savedCents = saved
            )
        )
    }

    /** 当前总余额（锚点余额 + 锚点后净收支）；未完成首次输入时返回 null */
    suspend fun currentTotalBalance(): Long? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        return balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
    }

    /** 余额三件套：总余额 / 攒着 / 可用 */
    data class BalanceSnapshot(
        val totalCents: Long,
        val savedCents: Long,
        val availableCents: Long,
        val balance: BalanceEntity
    )

    /** 自动攒着的执行结果 */
    data class AutoSavingsApplyResult(
        val transferredCents: Long,
        val periods: Int,
        val savedCents: Long
    )

    private suspend fun snapshotOf(balance: BalanceEntity): BalanceSnapshot {
        val total = balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
        val saved = SavingsMath.clampSaved(balance.savedCents, total)
        return BalanceSnapshot(total, saved, total - saved, balance)
    }

    /** 读取余额三件套（攒着会按总余额夹取，避免负数或超过总额） */
    suspend fun balanceSnapshot(): BalanceSnapshot? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        return snapshotOf(balance)
    }

    /** 手动调整「攒着」：总余额不变，只改攒着/可用的划分 */
    suspend fun setSavedBalance(cents: Long): BalanceSnapshot? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        val total = balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
        val saved = SavingsMath.clampSaved(cents, total)
        val updated = balance.copy(savedCents = saved, updatedAt = System.currentTimeMillis())
        balanceDao.upsert(updated)
        return snapshotOf(updated)
    }

    /** 手动调整「可用」：总余额不变，攒着 = 总余额 - 可用 */
    suspend fun setAvailableBalance(cents: Long): BalanceSnapshot? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        val total = balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
        val available = cents.coerceIn(0L, max(total, 0L))
        val saved = SavingsMath.clampSaved(total - available, total)
        val updated = balance.copy(savedCents = saved, updatedAt = System.currentTimeMillis())
        balanceDao.upsert(updated)
        return snapshotOf(updated)
    }

    /**
     * 更新自动攒着配置。
     * - 首次开启或切换周期时，把结算游标重置到当前周期起点，下一周期才开始转入；
     * - 关闭后再次开启同样重新开始，不会把关闭期间的周期一次性补上。
     */
    suspend fun updateAutoSavings(
        enabled: Boolean? = null,
        period: String? = null,
        amountCents: Long? = null,
        countAsExpense: Boolean? = null
    ): BalanceSnapshot? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        val nextEnabled = enabled ?: balance.autoSaveEnabled
        val nextPeriod = period?.let { AutoSavePeriod.from(it).code } ?: balance.autoSavePeriod
        val nextAmount = amountCents?.coerceAtLeast(0L) ?: balance.autoSaveAmountCents
        val nextCount = countAsExpense ?: balance.autoSaveCountAsExpense
        val turningOn = nextEnabled && !balance.autoSaveEnabled
        val periodChanged = nextPeriod != balance.autoSavePeriod
        var lastAt = balance.autoSaveLastAt
        if (nextEnabled && (turningOn || periodChanged || lastAt <= 0L)) {
            lastAt = SavingsAuto.periodStart(AutoSavePeriod.from(nextPeriod), System.currentTimeMillis())
        }
        val updated = balance.copy(
            autoSaveEnabled = nextEnabled,
            autoSavePeriod = nextPeriod,
            autoSaveAmountCents = nextAmount,
            autoSaveCountAsExpense = nextCount,
            autoSaveLastAt = lastAt,
            updatedAt = System.currentTimeMillis()
        )
        balanceDao.upsert(updated)
        return snapshotOf(updated)
    }

    /**
     * 自动攒着补算：从上次结算的周期起点逐期检查，可用足够才转入。
     * 开启「视作支出」时同时生成一笔「攒着」支出（计入限额与统计），否则只改账面金额。
     */
    suspend fun applyAutoSavings(now: Long = System.currentTimeMillis()): AutoSavingsApplyResult? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized || !balance.autoSaveEnabled) return null
        val total = balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
        val saved = SavingsMath.clampSaved(balance.savedCents, total)
        val available = total - saved
        val plan = SavingsAuto.plan(
            lastAt = balance.autoSaveLastAt,
            periodCode = balance.autoSavePeriod,
            now = now,
            availableCents = available,
            amountCents = balance.autoSaveAmountCents
        )
        if (plan.steps.isEmpty() && plan.anchorAt == balance.autoSaveLastAt) return null

        val period = AutoSavePeriod.from(balance.autoSavePeriod)
        var newSaved = saved
        plan.steps.forEach { step ->
            newSaved += step.amountCents
            if (balance.autoSaveCountAsExpense) {
                recordDao.insert(
                    RecordEntity(
                        amountCents = step.amountCents,
                        type = RecordType.EXPENSE,
                        categoryId = FixedCategories.EXPENSE_SAVINGS,
                        note = "自动攒着（${period.label}）",
                        date = step.boundaryAt,
                        limitExempt = LimitExempt.NONE,
                        updatedAt = System.currentTimeMillis(),
                        savingsTransfer = true
                    )
                )
            }
        }
        if (plan.steps.isNotEmpty() || plan.anchorAt != balance.autoSaveLastAt) {
            balanceDao.upsert(
                balance.copy(
                    savedCents = newSaved,
                    autoSaveLastAt = plan.anchorAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return AutoSavingsApplyResult(
            transferredCents = plan.steps.sumOf { it.amountCents },
            periods = plan.steps.size,
            savedCents = newSaved
        )
    }

    /** 组装导出用的同步数据（全部账目 + 余额锚点 + 分类定义） */
    suspend fun buildSyncPayload(): SyncPayload {
        // 含删除墓碑：让另一端也能学到「这笔已删除」
        val records = recordDao.getAll()
        val balance = balanceDao.get()
        return SyncPayload(
            exportedAt = System.currentTimeMillis(),
            records = records.map { record ->
                SyncRecord(
                    syncId = record.syncId,
                    amountCents = record.amountCents,
                    type = record.type,
                    categoryId = record.categoryId,
                    note = record.note,
                    date = record.date,
                    limitExempt = record.limitExempt,
                    updatedAt = record.updatedAt,
                    deleted = record.deleted,
                    savingsTransfer = record.savingsTransfer
                )
            },
            balance = SyncBalance(
                balanceCents = balance?.balanceCents ?: 0L,
                anchoredAt = balance?.anchoredAt ?: 0L,
                initialized = balance?.initialized ?: false,
                updatedAt = balance?.updatedAt ?: 0L,
                savedCents = balance?.savedCents ?: 0L,
                autoSaveEnabled = balance?.autoSaveEnabled ?: false,
                autoSavePeriod = balance?.autoSavePeriod ?: AutoSavePeriod.MONTHLY.code,
                autoSaveAmountCents = balance?.autoSaveAmountCents ?: 0L,
                autoSaveCountAsExpense = balance?.autoSaveCountAsExpense ?: false,
                autoSaveLastAt = balance?.autoSaveLastAt ?: 0L
            ),
            categories = categoryDao.getAll().map { it.toSyncCategory() }
        )
    }

    private fun CategoryEntity.toSyncCategory(): SyncCategory = SyncCategory(
        id = id,
        name = name,
        icon = icon,
        type = type,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
        deleted = deleted
    )

    /** 新增自定义分类；同类型下重名或名称为空时返回 null */
    suspend fun addCategory(name: String, icon: String, type: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val siblings = categoryDao.getByType(type)
        if (siblings.any { it.name == trimmed }) return null
        val now = System.currentTimeMillis()
        val id = FixedCategories.newCategoryId()
        categoryDao.insert(
            CategoryEntity(
                id = id,
                name = trimmed,
                icon = icon,
                type = type,
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                updatedAt = now,
                deleted = false
            )
        )
        return id
    }

    /** 修改分类名称/图标；同类型下重名时返回 false */
    suspend fun updateCategory(id: Long, name: String, icon: String): Boolean {
        // 「攒着」是系统分类，不允许改名或更换图标
        if (id == FixedCategories.EXPENSE_SAVINGS) return false
        val category = categoryDao.getById(id)?.takeIf { !it.deleted } ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val siblings = categoryDao.getByType(category.type)
        if (siblings.any { it.id != id && it.name == trimmed }) return false
        categoryDao.update(
            category.copy(name = trimmed, icon = icon, updatedAt = System.currentTimeMillis())
        )
        return true
    }

    /**
     * 删除分类：内置「其他」不可删除；分类下的账目会迁移到同类型「其他」，
     * 删除只盖墓碑，保证另一端不会把它重新种回来。
     */
    suspend fun deleteCategory(id: Long): Boolean {
        val category = categoryDao.getById(id)?.takeIf { !it.deleted } ?: return false
        if (id == FixedCategories.EXPENSE_OTHER || id == FixedCategories.INCOME_OTHER ||
            id == FixedCategories.EXPENSE_SAVINGS
        ) return false
        val now = System.currentTimeMillis()
        val fallback = FixedCategories.otherIdFor(category.type)
        if (id != fallback) {
            categoryDao.reassignCategory(id, fallback, now)
        }
        categoryDao.update(category.copy(deleted = true, updatedAt = now))
        return true
    }

    /**
     * 导入同步数据：先按 updatedAt 合并分类定义，再以 syncId 去重合并账目；
     * 账目引用了已删除/未知分类时归入同类型「其他」。余额锚点在文件版本较新时覆盖本地。
     */
    suspend fun importSync(payload: SyncPayload): MergeResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        var categoriesChanged = 0

        val localCategories = categoryDao.getAll().associateBy { it.id }.toMutableMap()
        payload.categories.forEach { incoming ->
            val local = localCategories[incoming.id]
            val target = CategoryEntity(
                id = incoming.id,
                name = incoming.name.trim(),
                icon = incoming.icon,
                type = incoming.type,
                sortOrder = incoming.sortOrder,
                updatedAt = incoming.updatedAt,
                // 「攒着」是系统分类：不接受删除墓碑，避免自动攒着记录失去分类
                deleted = if (incoming.id == FixedCategories.EXPENSE_SAVINGS) false else incoming.deleted
            )
            when {
                local == null -> {
                    if (target.name.isNotBlank()) {
                        categoryDao.insert(target)
                        localCategories[incoming.id] = target
                        categoriesChanged++
                    }
                }
                SyncMerge.shouldWriteCategory(incoming, local.updatedAt) -> {
                    categoryDao.update(
                        local.copy(
                            name = target.name,
                            icon = target.icon,
                            sortOrder = target.sortOrder,
                            updatedAt = target.updatedAt,
                            deleted = target.deleted
                        )
                    )
                    localCategories[incoming.id] = target
                    categoriesChanged++
                }
            }
        }

        val validExpenseIds = localCategories.values
            .filter { !it.deleted && it.type == RecordType.EXPENSE }
            .map { it.id }
            .toSet() + FixedCategories.EXPENSE_OTHER
        val validIncomeIds = localCategories.values
            .filter { !it.deleted && it.type == RecordType.INCOME }
            .map { it.id }
            .toSet() + FixedCategories.INCOME_OTHER

        payload.records.forEach { incoming ->
            val record = SyncMerge.normalize(incoming, validExpenseIds, validIncomeIds).copy(
                limitExempt = incoming.limitExempt.coerceIn(0, 2)
            )
            val local = recordDao.getBySyncId(record.syncId)
            if (local == null) {
                recordDao.insert(
                    RecordEntity(
                        amountCents = record.amountCents,
                        type = record.type,
                        categoryId = record.categoryId,
                        note = record.note,
                        date = record.date,
                        limitExempt = record.limitExempt,
                        syncId = record.syncId,
                        updatedAt = record.updatedAt,
                        deleted = record.deleted,
                        savingsTransfer = record.savingsTransfer
                    )
                )
                inserted++
            } else if (SyncMerge.shouldWrite(record, local.updatedAt)) {
                recordDao.update(
                    local.copy(
                        amountCents = record.amountCents,
                        type = record.type,
                        categoryId = record.categoryId,
                        note = record.note,
                        date = record.date,
                        limitExempt = record.limitExempt,
                        updatedAt = record.updatedAt,
                        deleted = record.deleted,
                        savingsTransfer = record.savingsTransfer
                    )
                )
                updated++
            } else {
                skipped++
            }
        }

        val localBalance = balanceDao.get()?.let {
            SyncBalance(
                it.balanceCents, it.anchoredAt, it.initialized, it.updatedAt,
                it.savedCents, it.autoSaveEnabled, it.autoSavePeriod,
                it.autoSaveAmountCents, it.autoSaveCountAsExpense, it.autoSaveLastAt
            )
        }
        val balanceUpdated = if (SyncMerge.shouldTakeBalance(payload.balance, localBalance)) {
            balanceDao.upsert(
                (balanceDao.get() ?: BalanceEntity()).copy(
                    balanceCents = payload.balance.balanceCents,
                    anchoredAt = payload.balance.anchoredAt,
                    initialized = true,
                    updatedAt = payload.balance.updatedAt,
                    savedCents = payload.balance.savedCents,
                    autoSaveEnabled = payload.balance.autoSaveEnabled,
                    autoSavePeriod = AutoSavePeriod.from(payload.balance.autoSavePeriod).code,
                    autoSaveAmountCents = payload.balance.autoSaveAmountCents,
                    autoSaveCountAsExpense = payload.balance.autoSaveCountAsExpense,
                    autoSaveLastAt = payload.balance.autoSaveLastAt
                )
            )
            true
        } else {
            false
        }

        // 本机已有、但对方文件里没有的账目：保留不动，并提示用户反向导出
        val incomingIds = payload.records.map { it.syncId }.toSet()
        val localOnly = recordDao.getAll().count { !it.deleted && it.syncId !in incomingIds }

        return MergeResult(inserted, updated, skipped, balanceUpdated, categoriesChanged, localOnly)
    }
}
