package com.mucage.rmb.auto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.mucage.rmb.data.AutoPendingEntity
import com.mucage.rmb.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知监听自动记账服务。
 * 捕获微信/支付宝/云闪付/银行App（招商银行、工商银行等）及主流购物平台的支付通知，
 * 本地解析金额、收支方向与商户后，存入待确认列表，由用户在设置页确认后写入账本。全程本地处理。
 */
class AutoBookkeepingService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AutoBookkeeping"

        /**
         * 确保监听服务已绑定。vivo 等 ROM 在应用更新或进程被杀后会解绑服务且不自动重绑，
         * requestRebind 单独调用无效；这里先「禁用→启用组件」触发系统 PACKAGE_CHANGED
         * 促使重新绑定，再补一次官方 requestRebind。
         */
        fun ensureBound(context: Context) {
            if (!AutoPrefs.isEnabled(context)) return
            if (!NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            ) return
            val cn = ComponentName(context, AutoBookkeepingService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            NotificationListenerService.requestRebind(cn)
        }
    }

    /** 关注的通知来源（识别逻辑保持不变，账目统一计入总余额） */
    private val watchedPackages = setOf(
        // 支付平台
        "com.tencent.mm",                          // 微信
        "com.eg.android.AlipayGphone",             // 支付宝
        "com.unionpay",                            // 云闪付
        "com.ecny.ecnyapp",                        // 数字人民币
        // 银行手机客户端
        "cmb.pb",                                  // 招商银行
        "com.cmbchina.ccd.pluto.cmbActivity",      // 掌上生活（招商银行信用卡）
        "com.icbc",                                // 工商银行
        "com.icbc.im",                             // 工银e生活
        "com.chinamworld.main",                    // 建设银行
        "com.android.bankabc",                     // 农业银行
        "com.chinamworld.bocmbci",                 // 中国银行
        "com.bankcomm.Bankcomm",                   // 交通银行
        "com.yitong.mbank.psbc",                   // 邮储银行
        "com.ecitic.bank.mobile",                  // 中信银行
        "com.cebbank.mobile.cemb",                 // 光大银行
        "com.cmbc.cc.mbank",                       // 民生银行
        "com.spdbccc.app",                         // 浦发银行
        "com.pingan.paces.ccms",                   // 平安口袋银行
        "com.cib.xyk",                             // 兴业银行
        // 手机钱包与金融
        "com.huawei.wallet",                       // 华为钱包
        "com.mipay.wallet",                        // 小米钱包
        "com.coloros.wallet",                      // OPPO 钱包
        "com.vivo.wallet",                         // vivo 钱包
        "com.jd.jrapp",                            // 京东金融
        // 购物/生活平台
        "com.taobao.taobao",                       // 淘宝
        "com.alibaba.wireless",                    // 1688
        "com.jingdong.app.mall",                   // 京东
        "com.xunmeng.pinduoduo",                   // 拼多多
        "com.sankuai.meituan",                     // 美团
        "me.ele",                                  // 饿了么
        "com.ss.android.ugc.aweme",                // 抖音（商城/团购）
        "com.xingin.xhs",                          // 小红书
        "com.achievo.vipshop",                     // 唯品会
        "com.suning.mobile.ebuy",                  // 苏宁易购
        "com.netease.yanxuan"                      // 网易严选
    )

    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoPrefs.setServiceConnected(this, true)
        Log.i(TAG, "通知监听已连接，正在监听 ${watchedPackages.size} 个应用")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AutoPrefs.setServiceConnected(this, false)
        // 系统解绑（应用更新/ROM 清理）后主动请求重连，vivo 等 ROM 不会自动重绑
        requestRebind(android.content.ComponentName(this, javaClass))
    }

    override fun onDestroy() {
        super.onDestroy()
        AutoPrefs.setServiceConnected(this, false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!AutoPrefs.isEnabled(this)) return
        if (sbn.packageName !in watchedPackages) return
        val notification = sbn.notification ?: return

        val title = extractTitle(notification)
        val text = extractText(notification)
        // 记录最近捕获：证明监听服务在正常工作（区分「没收到通知」与「收到但解析失败」）
        AutoPrefs.setLastNotifAt(this, sbn.postTime)
        val parsed = NotificationParser.parse(title, text)
        if (parsed == null) {
            Log.i(TAG, "解析跳过 [${sbn.packageName}] title=$title text=$text")
            return
        }
        Log.i(TAG, "解析成功 [${sbn.packageName}] $parsed")

        val pending = AutoPendingEntity(
            dedupKey = "${sbn.key}:${parsed.amountCents}:${parsed.type}",
            sourcePackage = sbn.packageName,
            sourceLabel = sourceLabel(sbn.packageName),
            title = title,
            text = text,
            amountCents = parsed.amountCents,
            type = parsed.type,
            merchant = parsed.merchant,
            capturedAt = sbn.postTime,
            status = 0
        )

        serviceScope.launch {
            val inserted = Repository.addAutoPending(pending)
            if (inserted) NotificationHelper.refresh(this@AutoBookkeepingService)
        }
    }

    /** 来源包名 → 应用显示名 */
    private fun sourceLabel(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.unionpay" -> "云闪付"
        "com.ecny.ecnyapp" -> "数字人民币"
        "cmb.pb" -> "招商银行"
        "com.cmbchina.ccd.pluto.cmbActivity" -> "掌上生活"
        "com.icbc" -> "工商银行"
        "com.icbc.im" -> "工银e生活"
        "com.chinamworld.main" -> "建设银行"
        "com.android.bankabc" -> "农业银行"
        "com.chinamworld.bocmbci" -> "中国银行"
        "com.bankcomm.Bankcomm" -> "交通银行"
        "com.yitong.mbank.psbc" -> "邮储银行"
        "com.ecitic.bank.mobile" -> "中信银行"
        "com.cebbank.mobile.cemb" -> "光大银行"
        "com.cmbc.cc.mbank" -> "民生银行"
        "com.spdbccc.app" -> "浦发银行"
        "com.pingan.paces.ccms" -> "平安银行"
        "com.cib.xyk" -> "兴业银行"
        "com.huawei.wallet" -> "华为钱包"
        "com.mipay.wallet" -> "小米钱包"
        "com.coloros.wallet" -> "OPPO 钱包"
        "com.vivo.wallet" -> "vivo 钱包"
        "com.jd.jrapp" -> "京东金融"
        "com.taobao.taobao" -> "淘宝"
        "com.alibaba.wireless" -> "1688"
        "com.jingdong.app.mall" -> "京东"
        "com.xunmeng.pinduoduo" -> "拼多多"
        "com.sankuai.meituan" -> "美团"
        "me.ele" -> "饿了么"
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.xingin.xhs" -> "小红书"
        "com.achievo.vipshop" -> "唯品会"
        "com.suning.mobile.ebuy" -> "苏宁易购"
        "com.netease.yanxuan" -> "网易严选"
        else -> "支付"
    }

    private fun extractTitle(notification: Notification): String {
        val extras = notification.extras
        extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.let { if (it.isNotBlank()) return it.toString() }
        extras.getCharSequence(Notification.EXTRA_TITLE)?.let { if (it.isNotBlank()) return it.toString() }
        return ""
    }

    private fun extractText(notification: Notification): String {
        val extras = notification.extras
        // 注意：BigText/Text 在 Bundle 中存储为 CharSequence，必须用 getCharSequence 读取
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { if (it.isNotBlank()) return it.toString() }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { if (it.isNotBlank()) return it.toString() }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { if (it.isNotBlank()) return it.toString() }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) {
            return lines.joinToString(" ") { it?.toString().orEmpty() }
        }
        return ""
    }
}
