package com.mucage.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** 自动攒着的结算周期 */
enum class AutoSavePeriod(val code: String, val label: String) {
    WEEKLY("WEEKLY", "每周"),
    MONTHLY("MONTHLY", "每月"),
    YEARLY("YEARLY", "每年");

    companion object {
        fun from(code: String?): AutoSavePeriod =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: MONTHLY
    }
}

/** 某一期自动转入：boundaryAt 为该周期起点，amountCents 为转入「攒着」的金额 */
data class AutoSavingsStep(val boundaryAt: Long, val amountCents: Long)

/**
 * 自动攒着结算计划：
 * steps 为需要执行的各期转入；anchorAt 为推进后的结算游标（下次从它的下一周期开始算）。
 */
data class AutoSavingsPlan(val steps: List<AutoSavingsStep>, val anchorAt: Long)

/**
 * 自动攒着的周期计算与补算（两端共用）。
 * 周期边界按本地时间：每周一 00:00、每月 1 日 00:00、每年 1 月 1 日 00:00。
 */
object SavingsAuto {

    fun periodStart(
        period: AutoSavePeriod,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = when (period) {
            AutoSavePeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            AutoSavePeriod.MONTHLY -> date.withDayOfMonth(1)
            AutoSavePeriod.YEARLY -> date.withDayOfYear(1)
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun nextBoundary(
        period: AutoSavePeriod,
        boundary: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(boundary).atZone(zone).toLocalDate()
        val next: LocalDate = when (period) {
            AutoSavePeriod.WEEKLY -> date.plusWeeks(1)
            AutoSavePeriod.MONTHLY -> date.plusMonths(1).withDayOfMonth(1)
            AutoSavePeriod.YEARLY -> date.plusYears(1).withDayOfYear(1)
        }
        return next.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * 计算从 lastAt 之后到 now 之间需要补足的每一期。
     * 每期都先判断剩余可用是否足够，不足则跳过该期，但游标照常推进（不会无限重试）。
     *
     * @param availableCents 当前可用余额（总余额 - 攒着）
     * @param amountCents 每期转入金额
     */
    fun plan(
        lastAt: Long,
        periodCode: String,
        now: Long,
        availableCents: Long,
        amountCents: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): AutoSavingsPlan {
        val period = AutoSavePeriod.from(periodCode)
        var anchor = when {
            lastAt <= 0L -> periodStart(period, now, zone)
            lastAt > now -> periodStart(period, now, zone)
            else -> lastAt
        }
        var boundary = nextBoundary(period, anchor, zone)
        val steps = mutableListOf<AutoSavingsStep>()
        var remaining = availableCents.coerceAtLeast(0L)
        val amount = amountCents.coerceAtLeast(0L)
        var guard = 0
        while (boundary <= now && guard < 1000) {
            if (amount > 0L && remaining >= amount) {
                steps += AutoSavingsStep(boundary, amount)
                remaining -= amount
            }
            anchor = boundary
            boundary = nextBoundary(period, boundary, zone)
            guard++
        }
        return AutoSavingsPlan(steps, anchor)
    }
}

/** 总余额 / 攒着 / 可用的通用计算口径（账面搬移不计入总余额） */
object SavingsMath {

    fun totalBalance(balance: SyncBalance, records: List<SyncRecord>): Long {
        val flow = records
            .filter { !it.deleted && !it.savingsTransfer && it.date >= balance.anchoredAt }
            .sumOf { if (it.type == CategoryType.INCOME.code) it.amountCents else -it.amountCents }
        return balance.balanceCents + flow
    }

    fun clampSaved(savedCents: Long, totalCents: Long): Long =
        savedCents.coerceIn(0L, totalCents.coerceAtLeast(0L))

    fun availableCents(totalCents: Long, savedCents: Long): Long =
        totalCents - clampSaved(savedCents, totalCents)
}
