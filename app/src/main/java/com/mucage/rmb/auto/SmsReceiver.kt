package com.mucage.rmb.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 接收新短信广播，解析银行/支付短信并写入待确认列表 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AutoPrefs.smsEnabled(context)) return
        if (!SmsScanner.hasPermission(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (message in messages) {
                    val body = message.displayMessageBody ?: continue
                    SmsScanner.insertSms(
                        context,
                        message.originatingAddress ?: "",
                        body,
                        message.timestampMillis
                    )
                }
                // 兜底补齐广播可能遗漏的短信
                SmsScanner.scanInbox(context)
                NotificationHelper.refresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
