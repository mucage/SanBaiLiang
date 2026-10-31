package com.mucage.rmb.util

import android.content.Context

/** 通用本地偏好设置：支出限额（月/日）、离日限额提醒、AI 配置 */
object SettingsPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_MONTHLY_LIMIT = "monthly_limit_cents"
    private const val KEY_DAILY_LIMIT = "daily_limit_cents"
    private const val KEY_DAILY_REMAINDER = "daily_remainder_cents"
    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_THINKING = "ai_thinking"
    private const val KEY_AI_PROMPT = "ai_prompt"
    private const val KEY_AI_COMMENT = "ai_comment"
    private const val KEY_AI_COMMENT_SIG = "ai_comment_signature"
    private const val KEY_BACKGROUND_URI = "background_uri"
    private const val KEY_BACKGROUND_ALPHA = "background_alpha"
    private const val KEY_BACKGROUND_ENABLED = "background_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 月度支出限额（分），0 表示未设置 */
    fun getMonthlyLimitCents(context: Context): Long =
        prefs(context).getLong(KEY_MONTHLY_LIMIT, 0L)

    fun setMonthlyLimitCents(context: Context, cents: Long) {
        prefs(context).edit().putLong(KEY_MONTHLY_LIMIT, cents).apply()
    }

    /** 每日支出限额（分），0 表示未设置 */
    fun getDailyLimitCents(context: Context): Long =
        prefs(context).getLong(KEY_DAILY_LIMIT, 0L)

    fun setDailyLimitCents(context: Context, cents: Long) {
        prefs(context).edit().putLong(KEY_DAILY_LIMIT, cents).apply()
    }

    /** 「离日限额还有多少元」提醒阈值（分），0 表示未设置 */
    fun getDailyReminderCents(context: Context): Long =
        prefs(context).getLong(KEY_DAILY_REMAINDER, 0L)

    fun setDailyReminderCents(context: Context, cents: Long) {
        prefs(context).edit().putLong(KEY_DAILY_REMAINDER, cents).apply()
    }

    /** DeepSeek API 密钥（仅保存在本机） */
    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_AI_API_KEY, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_AI_API_KEY, key.trim()).apply()
    }

    /** DeepSeek 模型名（用户手动填写，默认 deepseek-chat） */
    fun getModelName(context: Context): String =
        prefs(context).getString(KEY_AI_MODEL, "").orEmpty()

    fun setModelName(context: Context, model: String) {
        prefs(context).edit().putString(KEY_AI_MODEL, model.trim()).apply()
    }

    /** 是否启用深度思考（官方 thinking 参数；默认关闭以更快生成） */
    fun thinkingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AI_THINKING, false)

    fun setThinkingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI_THINKING, enabled).apply()
    }

    /** 发给 AI 的条件提示词（留空使用内置默认提示词） */
    fun getAiPrompt(context: Context): String =
        prefs(context).getString(KEY_AI_PROMPT, "").orEmpty()

    fun setAiPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_AI_PROMPT, prompt.trim()).apply()
    }

    /** 最近一次成功生成并保存的 AI 评价 */
    fun getSavedComment(context: Context): String =
        prefs(context).getString(KEY_AI_COMMENT, "").orEmpty()

    fun setSavedComment(context: Context, text: String) {
        prefs(context).edit().putString(KEY_AI_COMMENT, text).apply()
    }

    /** 生成该评价时的财务快照签名，用于判断余额是否变动 */
    fun getSavedCommentSignature(context: Context): String =
        prefs(context).getString(KEY_AI_COMMENT_SIG, "").orEmpty()

    fun setSavedCommentSignature(context: Context, signature: String) {
        prefs(context).edit().putString(KEY_AI_COMMENT_SIG, signature).apply()
    }

    /** 自定义界面背景图片的持久化 URI，null 表示使用内置背景 */
    fun getBackgroundUri(context: Context): String? =
        prefs(context).getString(KEY_BACKGROUND_URI, null)

    fun setBackgroundUri(context: Context, uri: String?) {
        val editor = prefs(context).edit()
        if (uri.isNullOrBlank()) editor.remove(KEY_BACKGROUND_URI) else editor.putString(KEY_BACKGROUND_URI, uri)
        editor.apply()
    }

    /** 背景不透明度（百分比），默认 50 */
    fun getBackgroundAlpha(context: Context): Int = prefs(context).getInt(KEY_BACKGROUND_ALPHA, 50)

    fun setBackgroundAlpha(context: Context, alpha: Int) {
        prefs(context).edit().putInt(KEY_BACKGROUND_ALPHA, alpha.coerceIn(0, 100)).apply()
    }

    /** 是否显示背景图，默认开启 */
    fun isBackgroundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND_ENABLED, true)

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_ENABLED, enabled).apply()
    }
}
