package com.mucage.rmb.auto

import android.content.Context
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 月度支出限额提醒：达到限额时发送系统通知（每月提醒一次） */
object MonthlyLimit {
    private const val PREFS = "monthly_limit"
    private const val KEY_NOTIFIED = "notified"

    suspend fun check(context: Context) {
        val limit = SettingsPrefs.getMonthlyLimitCents(context)
        if (limit <= 0) return
        if (!NotificationHelper.canNotify(context)) return

        val now = System.currentTimeMillis()
        val month = DateUtils.formatMonth(now)
        val start = DateUtils.startOfMonth(now)
        val spent = withContext(Dispatchers.IO) {
            Repository.recordDao.sumBetweenForMonthly(RecordType.EXPENSE, start, now)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (spent >= limit) {
            val key = "over:$month:$limit"
            if (prefs.getString(KEY_NOTIFIED, null) == key) return
            prefs.edit().putString(KEY_NOTIFIED, key).apply()
            NotificationHelper.postMonthlyLimit(context, spent, limit)
        }
    }
}
