package com.mucage.rmb.ai

import android.content.Context
import com.mucage.rmb.data.RecordType
import com.mucage.rmb.data.Repository
import com.mucage.rmb.util.DateUtils
import com.mucage.rmb.util.SettingsPrefs
import com.mucage.rmb.util.formatCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 当前财务快照：用于判断数据是否变动，以及生成 prompt */
data class FinancialSnapshot(
    val monthExpense: Long,
    val monthIncome: Long,
    val todayExpense: Long,
    val totalBalance: Long
)

/**
 * DeepSeek 个性化评价：根据日期、本月收支与结余生成一句俏皮文案。
 * 调用用户自己填写的 DeepSeek API 密钥；余额等数据未变动时不请求。
 */
object DeepSeekClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

    /** 默认模型（官方当前推荐，deepseek-chat 已停用） */
    const val DEFAULT_MODEL = "deepseek-v4-flash"

    /** 内置默认提示词（设置页展示，用户可自定义覆盖） */
    const val DEFAULT_PROMPT =
        "你是记账 App 里的一个毒舌又暖心的财务小助手。根据用户给出的日期、本月剩余天数、" +
            "本月支出、本月收入、今日支出和当前结余数据，写一句简短、俏皮、有生活气息的话（20 字以内），" +
            "例如调侃花钱太快、鼓励省钱、夸夸收入不错、提醒月末要省着点等。" +
            "语气轻松幽默，可以自嘲、可以吐槽，但不要说教、不要灌鸡汤、不要出现敏感或违规内容，" +
            "也不要提\"AI\"或\"助手\"这些词。只输出那一句话本身，不要任何解释、前缀或标点装饰。"

    /** 拉取一条个性化评价；未配置密钥或请求失败时返回 null */
    suspend fun fetchComment(context: Context): String? {
        val key = SettingsPrefs.getApiKey(context)
        if (key.isBlank()) return null
        // 模型由用户填写，默认使用官方当前推荐 deepseek-v4-flash
        val model = SettingsPrefs.getModelName(context).ifEmpty { DEFAULT_MODEL }
        // 深度思考：按官方 thinking 参数切换（默认 disabled 以更快生成）
        val thinkingType = if (SettingsPrefs.thinkingEnabled(context)) "enabled" else "disabled"
        // 条件提示词支持用户自定义，留空使用内置默认
        val systemPrompt = SettingsPrefs.getAiPrompt(context).ifBlank { DEFAULT_PROMPT }
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val prompt = buildUserPrompt()
                val payload = JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("temperature", 1.0)
                    .put("max_tokens", 80)
                    .put("thinking", JSONObject().put("type", thinkingType))
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", prompt)))

                conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }

                val code = conn.responseCode
                if (code !in 200..299) return@withContext null
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val content = JSONObject(resp)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                content.ifBlank { null }
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }

    /** 支出进度预警的内置提示词（独立于用户自定义的明细页提示词） */
    private const val OVERSPEND_PROMPT =
        "你是记账 App 里的毒舌财务小管家。用户本月支出进度超过了时间进度，花得太快了。" +
            "根据给出的数据写一句简短的提醒（25 字以内），吐槽开销过大、劝用户收敛一点。" +
            "语气俏皮有梗，不要说教、不要灌鸡汤，也不要提\"AI\"或\"助手\"。" +
            "只输出那一句话本身，不要任何解释或前缀。"

    /** 生成支出进度预警文案；未配置密钥或请求失败时返回 null */
    suspend fun fetchOverspendComment(
        context: Context,
        thresholdPercent: Int,
        spendPercent: Long,
        timePercent: Long,
        spentCents: Long,
        limitCents: Long
    ): String? {
        val key = SettingsPrefs.getApiKey(context)
        if (key.isBlank()) return null
        val model = SettingsPrefs.getModelName(context).ifEmpty { DEFAULT_MODEL }
        val thinkingType = if (SettingsPrefs.thinkingEnabled(context)) "enabled" else "disabled"
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val dateText = SimpleDateFormat("M月d日", Locale.CHINA).format(Date())
                val prompt = "今天是$dateText。本月支出限额 ${formatCents(limitCents)} 元，" +
                    "已支出 ${formatCents(spentCents)} 元，达到限额的 $spendPercent%，" +
                    "但本月时间才过了 $timePercent%（刚跨过 $thresholdPercent% 的预警线）。" +
                    "请写一句提醒用户开销过大的话。"
                val payload = JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("temperature", 1.0)
                    .put("max_tokens", 80)
                    .put("thinking", JSONObject().put("type", thinkingType))
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", OVERSPEND_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", prompt)))

                conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }

                val code = conn.responseCode
                if (code !in 200..299) return@withContext null
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(resp)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .ifBlank { null }
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }

    /** 组装用户数据 prompt */
    private suspend fun buildUserPrompt(): String {
        val s = currentSnapshot()
        val now = System.currentTimeMillis()
        val dateText = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(now))
        val cal = Calendar.getInstance()
        val remainingDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)

        return "今天是$dateText，本月还剩 $remainingDays 天。本月支出 ${formatCents(s.monthExpense)} 元，" +
            "本月收入 ${formatCents(s.monthIncome)} 元，今日支出 ${formatCents(s.todayExpense)} 元，" +
            "当前结余 ${formatCents(s.totalBalance)} 元。请根据这些数据写一句有个性、俏皮的话。"
    }

    /** 当前财务快照（本月收支、今日支出、总结余） */
    suspend fun currentSnapshot(): FinancialSnapshot {
        val now = System.currentTimeMillis()
        val monthStart = DateUtils.startOfMonth(now)
        val todayStart = DateUtils.startOfDay(now)

        val monthExpense = Repository.recordDao.sumBetween(RecordType.EXPENSE, monthStart, now)
        val monthIncome = Repository.recordDao.sumBetween(RecordType.INCOME, monthStart, now)
        val todayExpense = Repository.recordDao.sumBetween(RecordType.EXPENSE, todayStart, now)

        // 总余额 = 锚点余额 + 锚点后净收支；未完成首次输入时按 0 处理
        val balance = Repository.balanceDao.get()
        val totalBalance = if (balance != null && balance.initialized) {
            balance.balanceCents + Repository.recordDao.netFlowSince(balance.anchoredAt)
        } else 0L
        return FinancialSnapshot(monthExpense, monthIncome, todayExpense, totalBalance)
    }

    /** 财务快照签名：与上次一致时说明余额等数据未变动 */
    suspend fun currentSignature(): String {
        val s = currentSnapshot()
        return "${s.monthExpense}|${s.monthIncome}|${s.todayExpense}|${s.totalBalance}"
    }
}
