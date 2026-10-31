package com.mucage.rmb.auto

import android.content.Context
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 每日支出限额提醒：达到限额或剩余额度不足提醒值时发送系统通知（每天各提醒一次） */
object DailyLimit {
    private const val PREFS = "daily_limit"
    private const val KEY_NOTIFIED = "notified"

    suspend fun check(context: Context) {
        val limit = SettingsPrefs.getDailyLimitCents(context)
        if (limit <= 0) return
        if (!NotificationHelper.canNotify(context)) return

        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(now))
        val start = DateUtils.startOfDay(now)
        val spent = withContext(Dispatchers.IO) {
            Repository.recordDao.sumBetweenForDaily(RecordType.EXPENSE, start, now)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (spent >= limit) {
            // 已达日限额
            val key = "over:$day:$limit"
            if (prefs.getString(KEY_NOTIFIED, null) == key) return
            prefs.edit().putString(KEY_NOTIFIED, key).apply()
            NotificationHelper.postDailyLimit(context, spent, limit)
            return
        }

        // 离日限额不足提醒阈值（通知使用精确剩余数据）
        val threshold = SettingsPrefs.getDailyReminderCents(context)
        if (threshold > 0 && limit - spent <= threshold) {
            val key = "near:$day:$limit:$threshold"
            if (prefs.getString(KEY_NOTIFIED, null) == key) return
            prefs.edit().putString(KEY_NOTIFIED, key).apply()
            NotificationHelper.postNearDailyLimit(context, limit - spent, limit)
        }
    }
}
