package com.mucage.rmb.auto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.mucage.rmb.data.AutoPendingEntity
import com.mucage.rmb.data.Repository

/** 短信自动记账：解析银行/支付平台短信并写入待确认列表 */
object SmsScanner {

    private val SMS_URI = Uri.parse("content://sms")

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** 解析并写入一条短信，返回是否新增了待确认记录 */
    suspend fun insertSms(context: Context, sender: String, body: String, timestamp: Long): Boolean {
        // 剔除「余额」之后的余额部分，避免把余额数字误当成交易金额
        val text = body.substringBefore("余额")
        val parsed = NotificationParser.parse(sender, text) ?: return false
        val pending = AutoPendingEntity(
            dedupKey = "sms:$sender:${parsed.amountCents}:${parsed.type}:${body.hashCode()}",
            sourcePackage = "sms",
            sourceLabel = "短信",
            title = sender,
            text = body,
            amountCents = parsed.amountCents,
            type = parsed.type,
            merchant = parsed.merchant,
            capturedAt = timestamp,
            status = 0
        )
        return Repository.addAutoPending(pending)
    }

    /** 扫描收件箱中尚未处理的新短信（兜底，覆盖广播未收到的场景） */
    suspend fun scanInbox(context: Context) {
        if (!hasPermission(context)) return
        var lastId = AutoPrefs.getLastSmsId(context)
        val processed = try {
            context.contentResolver.query(
                SMS_URI,
                arrayOf("_id", "address", "body", "date"),
                "_id > ?",
                arrayOf(lastId.toString()),
                "_id ASC"
            )?.use { cursor ->
                var maxId = lastId
                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(0)
                    val sender = cursor.getString(1) ?: ""
                    val body = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    insertSms(context, sender, body, date)
                    maxId = maxOf(maxId, id)
                    count++
                }
                maxId
            } ?: lastId
        } catch (e: Exception) {
            return
        }
        if (processed > lastId) AutoPrefs.setLastSmsId(context, processed)
    }
}
