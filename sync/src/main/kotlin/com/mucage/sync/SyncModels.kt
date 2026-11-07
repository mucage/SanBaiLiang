package com.mucage.sync

/** 账本同步文件的数据模型（安卓端与桌面端共用） */
data class SyncRecord(
    val syncId: String,
    val amountCents: Long,
    val type: String,          // EXPENSE / INCOME
    val categoryId: Long,
    val note: String,
    val date: Long,
    val limitExempt: Int,
    val updatedAt: Long,
    /** 删除墓碑：为 true 时两端均隐藏该账目，但保留在同步文件中，保证删除也能跨端传播 */
    val deleted: Boolean = false,
    /**
     * 账面上的「攒着」划转：只改变攒着/可用的划分，不改变总余额。
     * 开启「视作支出」时会生成这种记录，用于计入限额与统计。
     */
    val savingsTransfer: Boolean = false
)

/** 分类（含用户自建分类）：id 为两端共用标识，updatedAt 用于「较新者胜出」的合并 */
data class SyncCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val type: String,          // EXPENSE / INCOME
    val sortOrder: Int,
    val updatedAt: Long = 0L,
    /** 删除墓碑：保留在同步文件中，避免另一端把它重新种回来 */
    val deleted: Boolean = false
)

data class SyncBalance(
    val balanceCents: Long,
    val anchoredAt: Long,
    val initialized: Boolean,
    val updatedAt: Long,
    /** 攒着：总余额中预留出来、仅供查看的部分（不涉及真实资金搬移） */
    val savedCents: Long = 0L,
    /** 自动攒着开关 */
    val autoSaveEnabled: Boolean = false,
    /** 结算周期：WEEKLY / MONTHLY / YEARLY */
    val autoSavePeriod: String = AutoSavePeriod.MONTHLY.code,
    /** 每期从可用转入攒着的固定金额 */
    val autoSaveAmountCents: Long = 0L,
    /** 转入是否同时生成一笔「攒着」支出，计入限额与统计 */
    val autoSaveCountAsExpense: Boolean = false,
    /** 上次结算到的周期起点；两端凭它防重复，并据此逐期补足 */
    val autoSaveLastAt: Long = 0L
)

data class SyncPayload(
    val exportedAt: Long,
    val records: List<SyncRecord>,
    val balance: SyncBalance,
    /** 分类定义（含自定义分类），导入时按 updatedAt 合并 */
    val categories: List<SyncCategory> = FixedCategories.defaultSyncCategories
)

/** 导入合并统计 */
data class MergeResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val balanceUpdated: Boolean,
    val categoriesChanged: Int = 0,
    /** 本机有、但导入文件里没有的账目数量：提示用户反向导出一次，才能两端一致 */
    val localOnly: Int = 0
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        parts += "新增 $inserted 笔"
        if (updated > 0) parts += "更新 $updated 笔"
        if (skipped > 0) parts += "跳过 $skipped 笔"
        if (categoriesChanged > 0) parts += "分类变更 $categoriesChanged 项"
        if (balanceUpdated) parts += "余额已同步"
        if (localOnly > 0) parts += "本机另有 $localOnly 笔未在对方文件中，请反向导出同步"
        return parts.joinToString("，")
    }
}
