package com.mucage.rmb.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 分 → 元字符串（保留两位小数），例如 12345 → "123.45" */
fun formatCents(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)

/** 分 → 带货币符号的字符串 */
fun formatCentsWithSymbol(cents: Long): String = "¥" + formatCents(cents)

/** 将输入的金额文本解析为「分」，非法输入返回 null */
fun parseAmountToCents(text: String): Long? {
    if (text.isBlank()) return null
    return try {
        BigDecimal(text)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    } catch (e: Exception) {
        null
    }
}

/** 将「分」转换为记账输入框显示的元文本，例如 12345 → "123.45" */
fun centsToInput(cents: Long): String {
    val yuan = cents / 100
    val frac = cents % 100
    return if (frac == 0L) yuan.toString() else "$yuan.${frac.toString().padStart(2, '0')}"
}

/** 当日 0 点（本地时区）的毫秒时间戳 */
fun todayStartMillis(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

object DateUtils {

    private fun calendarOf(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

    /** 当天 0 点 */
    fun startOfDay(ms: Long): Long {
        val c = calendarOf(ms)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 当天最后一毫秒 */
    fun endOfDay(ms: Long): Long {
        val c = calendarOf(startOfDay(ms))
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - 1
    }

    /** 本周周一 0 点（周一为一周的开始） */
    fun startOfWeek(ms: Long): Long {
        val c = calendarOf(startOfDay(ms))
        val dow = c.get(Calendar.DAY_OF_WEEK) // 1=周日
        val diff = (dow + 5) % 7               // 距周一的天数
        c.add(Calendar.DAY_OF_YEAR, -diff)
        return c.timeInMillis
    }

    /** 当月 1 号 0 点 */
    fun startOfMonth(ms: Long): Long {
        val c = calendarOf(startOfDay(ms))
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** 当年 1 月 1 日 0 点 */
    fun startOfYear(ms: Long): Long {
        val c = calendarOf(startOfDay(ms))
        c.set(Calendar.MONTH, Calendar.JANUARY)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun addDays(ms: Long, days: Int): Long {
        val c = calendarOf(ms)
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    fun addMonths(ms: Long, months: Int): Long {
        val c = calendarOf(ms)
        c.add(Calendar.MONTH, months)
        return c.timeInMillis
    }

    fun addYears(ms: Long, years: Int): Long {
        val c = calendarOf(ms)
        c.add(Calendar.YEAR, years)
        return c.timeInMillis
    }

    private fun fmt(pattern: String, ms: Long): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(ms))

    /** 例如：8月4日 */
    fun formatDay(ms: Long): String = fmt("M月d日", ms)

    /** 例如：14:30 */
    fun formatTime(ms: Long): String = fmt("HH:mm", ms)

    /** 例如：8月4日 周二 */
    fun formatDayWithWeek(ms: Long): String = fmt("M月d日 EEE", ms)

    /** 例如：2026年8月 */
    fun formatMonth(ms: Long): String = fmt("yyyy年M月", ms)

    /** 例如：2026年 */
    fun formatYear(ms: Long): String = fmt("yyyy年", ms)

    /** 例如：2026年8月4日 */
    fun formatFullDate(ms: Long): String = fmt("yyyy年M月d日", ms)

    /** 周区间标签，例如：8月3日 - 8月9日 */
    fun formatWeekRange(start: Long, end: Long): String =
        "${fmt("M月d日", start)} - ${fmt("M月d日", end)}"
}
