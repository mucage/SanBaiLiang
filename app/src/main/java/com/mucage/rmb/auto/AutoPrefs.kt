package com.mucage.rmb.auto

import android.content.Context

/** 自动记账偏好设置（仅保存在本机） */
object AutoPrefs {
    private const val PREFS_NAME = "auto_bookkeeping"
    private const val KEY_ENABLED = "enabled"          // 通知监听总开关
    private const val KEY_SMS_ENABLED = "sms_enabled"  // 短信识别开关
    private const val KEY_LAST_SMS_ID = "last_sms_id"  // 已处理的最大短信 id
    private const val KEY_NOTIF_REQUESTED = "notif_requested" // 是否已请求过通知权限
    private const val KEY_LAST_NOTIF_AT = "last_notif_at"     // 最近一次收到关注应用通知的时间
    private const val KEY_SERVICE_CONNECTED = "service_connected" // 监听服务当前是否已绑定

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 通知监听自动记账总开关（默认开启） */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 短信识别开关（默认关闭，需用户授权短信权限） */
    fun smsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SMS_ENABLED, false)

    fun setSmsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SMS_ENABLED, enabled).apply()
    }

    fun getLastSmsId(context: Context): Long = prefs(context).getLong(KEY_LAST_SMS_ID, 0L)

    fun setLastSmsId(context: Context, id: Long) {
        prefs(context).edit().putLong(KEY_LAST_SMS_ID, id).apply()
    }

    fun wasNotificationRequested(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIF_REQUESTED, false)

    fun markNotificationRequested(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTIF_REQUESTED, true).apply()
    }

    /** 最近一次收到关注应用通知的时间（0 表示从未收到过） */
    fun getLastNotifAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_NOTIF_AT, 0L)

    fun setLastNotifAt(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_NOTIF_AT, at).apply()
    }

    /** 监听服务是否已绑定（区分「已授权」与「服务真正在运行」） */
    fun isServiceConnected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_CONNECTED, false)

    fun setServiceConnected(context: Context, connected: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_CONNECTED, connected).apply()
    }
}
