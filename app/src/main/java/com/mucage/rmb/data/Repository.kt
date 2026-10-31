package com.mucage.rmb.data

import android.content.Context
import com.mucage.rmb.util.todayStartMillis
import com.mucage.sync.MergeResult
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncMerge
import com.mucage.sync.SyncPayload
import com.mucage.sync.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        balanceDao.upsert(
            BalanceEntity(
                id = 1,
                balanceCents = cents,
                anchoredAt = todayStartMillis(),
                initialized = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 当前总余额（锚点余额 + 锚点后净收支）；未完成首次输入时返回 null */
    suspend fun currentTotalBalance(): Long? {
        val balance = balanceDao.get() ?: return null
        if (!balance.initialized) return null
        return balance.balanceCents + recordDao.netFlowSince(balance.anchoredAt)
    }

    /** 组装导出用的同步数据（全部账目 + 余额锚点） */
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
                    deleted = record.deleted
                )
            },
            balance = SyncBalance(
                balanceCents = balance?.balanceCents ?: 0L,
                anchoredAt = balance?.anchoredAt ?: 0L,
                initialized = balance?.initialized ?: false,
                updatedAt = balance?.updatedAt ?: 0L
            )
        )
    }

    /**
     * 导入同步数据：以 syncId 去重，同一笔账目取 updatedAt 较新的版本；
     * 余额锚点在文件中版本较新时覆盖本地。
     */
    suspend fun importSync(payload: SyncPayload): MergeResult {
        var inserted = 0
        var updated = 0
        var skipped = 0

        payload.records.forEach { incoming ->
            val record = SyncMerge.normalize(incoming).copy(
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
                        deleted = record.deleted
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
                        deleted = record.deleted
                    )
                )
                updated++
            } else {
                skipped++
            }
        }

        val localBalance = balanceDao.get()?.let {
            SyncBalance(it.balanceCents, it.anchoredAt, it.initialized, it.updatedAt)
        }
        val balanceUpdated = if (SyncMerge.shouldTakeBalance(payload.balance, localBalance)) {
            balanceDao.upsert(
                BalanceEntity(
                    id = 1,
                    balanceCents = payload.balance.balanceCents,
                    anchoredAt = payload.balance.anchoredAt,
                    initialized = true,
                    updatedAt = payload.balance.updatedAt
                )
            )
            true
        } else {
            false
        }

        return MergeResult(inserted, updated, skipped, balanceUpdated)
    }
}
