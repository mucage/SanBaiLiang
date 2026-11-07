package com.mucage.rmb.desktop

import com.mucage.sync.AutoSavePeriod
import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories
import com.mucage.sync.SavingsAuto
import com.mucage.sync.SavingsMath
import com.mucage.sync.SyncBalance
import com.mucage.sync.SyncRecord
import java.util.UUID

/**
 * 桌面端「总余额 / 攒着 / 可用」的账面计算与自动攒着补算。
 * 攒着只是账面预留，不改变总余额；开启「视作支出」时才额外生成一笔攒着支出记录。
 */
internal object DesktopSavings {

    internal data class ApplyResult(
        val data: LedgerData,
        val transferredCents: Long,
        val periods: Int,
        val changed: Boolean
    )

    fun totalBalance(data: LedgerData): Long = SavingsMath.totalBalance(data.balance, data.records)

    fun savedCents(data: LedgerData): Long =
        SavingsMath.clampSaved(data.balance.savedCents, totalBalance(data))

    fun availableCents(data: LedgerData): Long = totalBalance(data) - savedCents(data)

    /** 下次自动结算时间（未开启时返回 null） */
    fun nextAt(balance: SyncBalance, now: Long = System.currentTimeMillis()): Long? {
        if (!balance.autoSaveEnabled) return null
        val period = AutoSavePeriod.from(balance.autoSavePeriod)
        val lastAt = balance.autoSaveLastAt
        val anchor = if (lastAt in 1..now) lastAt else SavingsAuto.periodStart(period, now)
        return SavingsAuto.nextBoundary(period, anchor)
    }

    /** 手动调整攒着：总余额不变 */
    fun setSaved(data: LedgerData, cents: Long): LedgerData {
        val total = totalBalance(data)
        return data.copy(
            balance = data.balance.copy(
                savedCents = SavingsMath.clampSaved(cents, total),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 手动调整可用：总余额不变，攒着 = 总余额 - 可用 */
    fun setAvailable(data: LedgerData, cents: Long): LedgerData {
        val total = totalBalance(data)
        val available = cents.coerceIn(0L, total.coerceAtLeast(0L))
        return data.copy(
            balance = data.balance.copy(
                savedCents = SavingsMath.clampSaved(total - available, total),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 修改自动攒着配置；首次开启或切换周期时从当前周期起点重新计算 */
    fun updateAuto(
        data: LedgerData,
        enabled: Boolean? = null,
        period: String? = null,
        amountCents: Long? = null,
        countAsExpense: Boolean? = null,
        now: Long = System.currentTimeMillis()
    ): LedgerData {
        val balance = data.balance
        val nextEnabled = enabled ?: balance.autoSaveEnabled
        val nextPeriod = period?.let { AutoSavePeriod.from(it).code } ?: balance.autoSavePeriod
        val nextAmount = amountCents?.coerceAtLeast(0L) ?: balance.autoSaveAmountCents
        val nextCount = countAsExpense ?: balance.autoSaveCountAsExpense
        val turningOn = nextEnabled && !balance.autoSaveEnabled
        val periodChanged = nextPeriod != balance.autoSavePeriod
        var lastAt = balance.autoSaveLastAt
        if (nextEnabled && (turningOn || periodChanged || lastAt <= 0L)) {
            lastAt = SavingsAuto.periodStart(AutoSavePeriod.from(nextPeriod), now)
        }
        return data.copy(
            balance = balance.copy(
                autoSaveEnabled = nextEnabled,
                autoSavePeriod = nextPeriod,
                autoSaveAmountCents = nextAmount,
                autoSaveCountAsExpense = nextCount,
                autoSaveLastAt = lastAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 启动/MCP 调用前补算自动攒着：逐期检查，可用不足则跳过该期 */
    fun apply(data: LedgerData, now: Long = System.currentTimeMillis()): ApplyResult {
        val balance = data.balance
        if (!balance.initialized || !balance.autoSaveEnabled) {
            return ApplyResult(data, 0L, 0, false)
        }
        val total = totalBalance(data)
        val saved = SavingsMath.clampSaved(balance.savedCents, total)
        val plan = SavingsAuto.plan(
            lastAt = balance.autoSaveLastAt,
            periodCode = balance.autoSavePeriod,
            now = now,
            availableCents = total - saved,
            amountCents = balance.autoSaveAmountCents
        )
        if (plan.steps.isEmpty() && plan.anchorAt == balance.autoSaveLastAt) {
            return ApplyResult(data, 0L, 0, false)
        }
        val period = AutoSavePeriod.from(balance.autoSavePeriod)
        var newSaved = saved
        val added = mutableListOf<SyncRecord>()
        plan.steps.forEach { step ->
            newSaved += step.amountCents
            if (balance.autoSaveCountAsExpense) {
                added += SyncRecord(
                    syncId = UUID.randomUUID().toString(),
                    amountCents = step.amountCents,
                    type = CategoryType.EXPENSE.code,
                    categoryId = FixedCategories.EXPENSE_SAVINGS,
                    note = "自动攒着（${period.label}）",
                    date = step.boundaryAt,
                    limitExempt = 0,
                    updatedAt = now,
                    savingsTransfer = true
                )
            }
        }
        val newBalance = balance.copy(
            savedCents = newSaved,
            autoSaveLastAt = plan.anchorAt,
            updatedAt = System.currentTimeMillis()
        )
        return ApplyResult(
            data = data.copy(
                balance = newBalance,
                records = sortRecords(data.records + added)
            ),
            transferredCents = plan.steps.sumOf { it.amountCents },
            periods = plan.steps.size,
            changed = true
        )
    }

    /** 重设总余额后，把攒着夹到新的总范围内 */
    fun clampAfterTotalChange(data: LedgerData): LedgerData {
        val total = totalBalance(data)
        val saved = SavingsMath.clampSaved(data.balance.savedCents, total)
        if (saved == data.balance.savedCents) return data
        return data.copy(balance = data.balance.copy(savedCents = saved))
    }
}
