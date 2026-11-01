package com.mucage.rmb.auto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mucage.rmb.MainActivity
import com.mucage.rmb.R
import com.mucage.rmb.data.Repository

/** 待确认记账的系统通知 */
object NotificationHelper {
    const val NOTIF_ID = 1001
    const val LIMIT_NOTIF_ID = 1003
    const val DAILY_LIMIT_NOTIF_ID = 1005
    const val NEAR_DAILY_NOTIF_ID = 1006
    const val PACE_NOTIF_ID = 1007
    const val EXTRA_OPEN_TAB = "open_tab"
    private const val CHANNEL_ID = "auto_pending"
    private const val CHANNEL_NAME = "待确认记账"
    private const val CHANNEL_LIMIT_ID = "monthly_limit"
    private const val CHANNEL_LIMIT_NAME = "支出限额"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (nm.getNotificationChannel(CHANNEL_LIMIT_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LIMIT_ID, CHANNEL_LIMIT_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 有待确认记账时发通知，无则取消已有通知；会提示其中疑似重复的条数 */
    suspend fun refresh(context: Context) {
        val count = Repository.autoPendingDao.countPending()
        if (count > 0) {
            val suspected = runCatching { Repository.countSuspectedDuplicates() }.getOrDefault(0)
            post(context, count, suspected)
        } else {
            cancel(context)
        }
    }

    fun post(context: Context, count: Int, suspected: Int = 0) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, 3)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (suspected > 0) {
            "共 $count 笔待确认，其中 $suspected 笔疑似重复，点击检查"
        } else {
            "共 $count 笔待确认，点击查看"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pending)
            .setContentTitle("有待确认的记账")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    /** 月度支出达到限额的通知 */
    fun postMonthlyLimit(context: Context, usedCents: Long, limitCents: Long) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMIT_ID)
            .setSmallIcon(R.drawable.ic_stat_pending)
            .setContentTitle("本月支出已达限额")
            .setContentText("已使用 ${formatCents(usedCents)} / ${formatCents(limitCents)} 元，点击查看明细")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(LIMIT_NOTIF_ID, notification)
    }

    /** 支出进度超过时间进度的预警通知（文案可由 AI 生成） */
    fun postPaceWarning(
        context: Context,
        spentCents: Long,
        limitCents: Long,
        spendPercent: Int,
        timePercent: Int,
        message: String
    ) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 5, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMIT_ID)
            .setSmallIcon(R.drawable.ic_stat_pending)
            .setContentTitle("开销过大：支出 $spendPercent%，时间才过 $timePercent%")
            .setContentText("$message（${formatCents(spentCents)} / ${formatCents(limitCents)} 元）")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message（${formatCents(spentCents)} / ${formatCents(limitCents)} 元）")
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(PACE_NOTIF_ID, notification)
    }

    /** 每日支出达到限额的通知 */
    fun postDailyLimit(context: Context, usedCents: Long, limitCents: Long) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMIT_ID)
            .setSmallIcon(R.drawable.ic_stat_pending)
            .setContentTitle("今日支出已达限额")
            .setContentText("已使用 ${formatCents(usedCents)} / ${formatCents(limitCents)} 元，点击查看明细")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(DAILY_LIMIT_NOTIF_ID, notification)
    }

    /** 离日限额剩余额度不足时提醒（剩余额度用精确数据） */
    fun postNearDailyLimit(context: Context, remainingCents: Long, limitCents: Long) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMIT_ID)
            .setSmallIcon(R.drawable.ic_stat_pending)
            .setContentTitle("离日限额还有 ${formatCents(remainingCents)} 元")
            .setContentText("今日支出限额 ${formatCents(limitCents)} 元，点击查看明细")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NEAR_DAILY_NOTIF_ID, notification)
    }

    private fun formatCents(cents: Long): String = String.format(java.util.Locale.US, "%.2f", cents / 100.0)
}
