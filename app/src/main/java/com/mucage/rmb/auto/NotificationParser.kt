package com.mucage.rmb.auto

import com.mucage.rmb.data.RecordType

/** 通知解析结果 */
data class ParsedNotification(
    val amountCents: Long,
    val type: String,     // EXPENSE / INCOME
    val merchant: String
)

/**
 * 解析微信/支付宝/云闪付及银行App（招商银行、工商银行等）的支付通知，
 * 提取金额、收支方向与商户。全部在本地正则解析，不涉及任何网络请求。
 */
object NotificationParser {

    /** 金额提取模式（按优先级） */
    private val amountPatterns = listOf(
        // 1. ¥/￥ 符号，如「¥15.00」「￥100」
        Regex("""[¥￥]\s*(\d{1,8}(?:,\d{3})*(?:\.\d{1,2})?)"""),
        // 2. 动作词紧跟金额，如「支付15元」「付款15.00元」「收款100元」「到账0.1元」「扣款50元」
        //    含银行常用扣款动词：扣划/划扣/扣收（年费、管理费）、支取（ATM）、缴纳（水电燃气代扣）
        Regex(
            """(?:支付|付款|消费|支出|扣款|扣划|划扣|扣收|扣费|还款|转出|转入|转账|收款|到账|入账|收到|购买|代扣|退款|退回|存入|代发|取现|支取|缴纳|缴款)\s*[:：]?\s*[¥￥]?\s*(\d{1,8}(?:,\d{3})*(?:\.\d{1,2})?)\s*元"""
        ),
        // 3. 金额/人民币/实付前缀，如「金额：100.00」「人民币50元」「实付 ¥12.5」
        Regex("""(?:金额为|金额|人民币|实付金额|实付|付款金额|本次消费)\s*[:：]?\s*[¥￥]?\s*(\d{1,8}(?:,\d{3})*(?:\.\d{1,2})?)"""),
        // 4. 数字 + 元（兜底），如「15.00元」
        Regex("""(\d{1,8}(?:,\d{3})*(?:\.\d{1,2})?)\s*元""")
    )

    /** 收入关键词（优先匹配，如「收款」「退款」） */
    private val incomeKeywords = listOf("收款", "收到", "入账", "到账", "退款", "转入", "进账", "退还", "收钱", "代发", "存入", "收款成功", "已到账", "退回", "收入")

    /** 支出关键词（含银行扣款动词：扣划/划扣/扣收/支取/缴纳/缴款） */
    private val expenseKeywords = listOf("支付", "付款", "消费", "支出", "扣款", "扣划", "划扣", "扣收", "扣费", "转出", "还款", "代扣", "购买", "买单", "取现", "支取", "缴纳", "缴款", "下单", "代付", "支付成功", "付款成功", "交易成功")

    /** 干扰关键词：账单提醒、营销推广、贷款推销等通知直接跳过 */
    private val noiseKeywords = listOf(
        // 账单/提醒类。注意：不能整体过滤「提醒」二字——银行App的真实交易通知
        // 标题常为「动账提醒」「交易提醒」「余额变动提醒」，只过滤提醒类的具体场景
        "账单", "还款提醒", "缴费提醒", "待还", "还款日", "已生成", "月结单", "对账单",
        // 营销/优惠类
        "优惠", "立减", "折扣", "活动", "抽奖", "中奖", "积分", "会员日",
        "广告", "推广", "邀请", "额度", "专享", "福利", "免费", "退订",
        // 贷款/借款推销类
        "贷款", "借款", "可借", "备用金", "授信", "万元", "利率", "日息", "月息", "提额"
    )

    /** 商户提取模式 */
    private val merchantPatterns = listOf(
        Regex("""(?:向|给)\s*([\u4e00-\u9fa5A-Za-z0-9·&()（）\-]{2,24}?)\s*(?:付款|转账|支付|发红包)"""),
        Regex("""(?:转账|付款|支付)\s*(?:给|至)\s*([\u4e00-\u9fa5A-Za-z0-9·&()（）\-]{2,24})"""),
        Regex("""(?:商户|收款方|对方|店名|商家)\s*[:：]?\s*([\u4e00-\u9fa5A-Za-z0-9·&()（）\-]{2,24})"""),
        // 云闪付/银行：在某某商户消费
        Regex("""(?:在|于)\s*([\u4e00-\u9fa5A-Za-z0-9·&()（）\-]{2,24}?)\s*(?:消费|付款|刷卡|支付)""")
    )

    /** 解析通知标题与正文，无法识别时返回 null */
    fun parse(title: String, text: String): ParsedNotification? {
        val raw = (title + " " + text).replace(Regex("\\s+"), "")
        if (raw.isBlank()) return null
        if (noiseKeywords.any { raw.contains(it) }) return null

        // 剔除「余额」之后的余额数字，避免把余额当成交易金额
        val txPart = raw.substringBefore("余额")
        val amountCents = extractAmount(txPart) ?: return null
        val type = detectType(raw) ?: return null
        return ParsedNotification(amountCents, type, extractMerchant(raw))
    }

    private fun extractAmount(raw: String): Long? {
        for (pattern in amountPatterns) {
            val m = pattern.find(raw) ?: continue
            val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (value <= 0.0 || value > 100_000_000.0) continue
            // 四舍五入避免浮点截断，如 19.99 × 100 = 1998.999... → 1999
            return Math.round(value * 100)
        }
        return null
    }

    private fun detectType(raw: String): String? =
        if (incomeKeywords.any { raw.contains(it) }) RecordType.INCOME
        else if (expenseKeywords.any { raw.contains(it) }) RecordType.EXPENSE
        else null

    /** 日期/时间模式：银行通知常带「13日14:30」「09月13日」等，先剔除避免混入商户名 */
    private val dateTimePattern = Regex(
        """\d{1,4}年\d{1,2}月\d{1,2}日|\d{1,2}月\d{1,2}日|\d{1,2}日\d{1,2}[:：]\d{2}|\d{1,2}时\d{1,2}分|\d{1,2}[:：]\d{2}"""
    )

    private fun extractMerchant(raw: String): String {
        val cleaned = raw.replace(dateTimePattern, "")
        for (pattern in merchantPatterns) {
            val m = pattern.find(cleaned) ?: continue
            // 剔除日期后可能残留的前置介词（如「您于在星巴克消费」会提取出「在星巴克」）
            val name = m.groupValues[1].trim().trimStart('在', '于')
            if (name.isNotEmpty() && !name.contains("微信") && !name.contains("支付宝")) return name
        }
        return ""
    }
}
