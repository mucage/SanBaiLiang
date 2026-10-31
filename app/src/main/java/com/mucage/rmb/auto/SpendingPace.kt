package com.mucage.rmb.auto

import android.content.Context
import com.mucage.rmb.ai.DeepSeekClient
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 支出进度预警：每月支出达到月限额的 25%/50%/75% 时，核对当前日期是否达到本月的相应进度，
 * 若时间进度落后于支出进度（开销过大），发送提醒。每个阈值每月最多提醒一次，
 * 每当添加新账单时检测；文案可借助 AI 生成（未配置密钥时用内置文案）。
 */
object SpendingPace {
    private const val PREFS = "spending_pace"
    private const val KEY_NOTIFIED = "notified_thresholds"
    private val THRESHOLDS = intArrayOf(25, 50, 75)

    // 通知（含 AI 请求）后台发出，不阻塞记账保存流程
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun check(context: Context) {
        val limit = SettingsPrefs.getMonthlyLimitCents(context)
        if (limit <= 0) return

        val now = System.currentTimeMillis()
        val spent = withContext(Dispatchers.IO) {
            val start = DateUtils.startOfMonth(now)
            Repository.recordDao.sumBetweenForMonthly(RecordType.EXPENSE, start, now)
        }

        // 支出进度（百分比，向上取整保证「已达到」判断）
        val spendPercent = (spent * 100 + limit - 1) / limit
        // 时间进度：今天在本月的位置（当天计为已过）
        val cal = Calendar.getInstance()
        val timePercent = cal.get(Calendar.DAY_OF_MONTH) * 100L /
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 支出已达、未提醒过、且时间进度落后的最高阈值
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val month = DateUtils.formatMonth(now)
        val notified = prefs.getString(KEY_NOTIFIED, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val hit = THRESHOLDS
            .filter { spendPercent >= it && !notified.contains("$month:$it") && timePercent < it }
            .maxOrNull() ?: return

        // 标记本阈值本月已提醒，避免重复
        prefs.edit()
            .putString(KEY_NOTIFIED, (notified + "$month:$hit").joinToString(","))
            .apply()

        // 借助 AI 生成文案后发通知（后台执行，未配置密钥或失败时用内置文案）
        notifyScope.launch {
            val aiMessage = DeepSeekClient.fetchOverspendComment(
                context, hit, spendPercent, timePercent, spent, limit
            )
            NotificationHelper.postPaceWarning(
                context,
                spentCents = spent,
                limitCents = limit,
                spendPercent = spendPercent.toInt(),
                timePercent = timePercent.toInt(),
                message = aiMessage ?: "本月支出已达限额的 $hit%（时间才过了 $timePercent%），钱包在哭，悠着点花～"
            )
        }
    }
}
