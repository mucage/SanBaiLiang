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
    val deleted: Boolean = false
)

data class SyncBalance(
    val balanceCents: Long,
    val anchoredAt: Long,
    val initialized: Boolean,
    val updatedAt: Long
)

data class SyncPayload(
    val exportedAt: Long,
    val records: List<SyncRecord>,
    val balance: SyncBalance,
    /** 文件内附带的分类信息，仅用于展示与校验，两端始终使用内置固定分类 */
    val categories: List<FixedCategory> = FixedCategories.all
)

/** 导入合并统计 */
data class MergeResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val balanceUpdated: Boolean
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        parts += "新增 $inserted 笔"
        if (updated > 0) parts += "更新 $updated 笔"
        if (skipped > 0) parts += "跳过 $skipped 笔"
        if (balanceUpdated) parts += "余额已同步"
        return parts.joinToString("，")
    }
}
