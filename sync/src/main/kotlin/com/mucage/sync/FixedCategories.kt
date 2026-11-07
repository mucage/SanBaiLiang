package com.mucage.sync

/**
 * 内置固定分类（两端共用，保证同步文件的分类 id 完全一致）。
 * 支出整合为「吃、用、玩、学、出行、其他」六类，不再支持自定义；收入为内置来源分类。
 */
enum class CategoryType(val code: String) {
    EXPENSE("EXPENSE"),
    INCOME("INCOME");

    companion object {
        fun from(code: String?): CategoryType = if (code == INCOME.code) INCOME else EXPENSE
    }
}

data class FixedCategory(val id: Long, val name: String, val icon: String, val type: CategoryType, val sortOrder: Int)

object FixedCategories {
    const val EXPENSE_FOOD = 101L
    const val EXPENSE_USE = 102L
    const val EXPENSE_FUN = 103L
    const val EXPENSE_STUDY = 104L
    const val EXPENSE_TRAVEL = 105L
    const val EXPENSE_OTHER = 116L

    /** 系统分类「攒着」：只用于自动/手动攒着的账面划转记录，不出现在手动记账的分类选择里 */
    const val EXPENSE_SAVINGS = 200L

    const val INCOME_SALARY = 201L
    const val INCOME_BONUS = 202L
    const val INCOME_FINANCE = 203L
    const val INCOME_PARTTIME = 204L
    const val INCOME_REDPACKET = 205L
    const val INCOME_OTHER = 206L

    val expense: List<FixedCategory> = listOf(
        FixedCategory(EXPENSE_FOOD, "吃", "restaurant", CategoryType.EXPENSE, 0),
        FixedCategory(EXPENSE_USE, "用", "shopping", CategoryType.EXPENSE, 1),
        FixedCategory(EXPENSE_FUN, "玩", "entertainment", CategoryType.EXPENSE, 2),
        FixedCategory(EXPENSE_STUDY, "学", "education", CategoryType.EXPENSE, 3),
        FixedCategory(EXPENSE_TRAVEL, "出行", "transport", CategoryType.EXPENSE, 4),
        FixedCategory(EXPENSE_OTHER, "其他", "more", CategoryType.EXPENSE, 5),
    )

    val income: List<FixedCategory> = listOf(
        FixedCategory(INCOME_SALARY, "工资", "salary", CategoryType.INCOME, 0),
        FixedCategory(INCOME_BONUS, "奖金", "bonus", CategoryType.INCOME, 1),
        FixedCategory(INCOME_FINANCE, "理财", "finance", CategoryType.INCOME, 2),
        FixedCategory(INCOME_PARTTIME, "兼职", "parttime", CategoryType.INCOME, 3),
        FixedCategory(INCOME_REDPACKET, "红包", "redpacket", CategoryType.INCOME, 4),
        FixedCategory(INCOME_OTHER, "其他", "more", CategoryType.INCOME, 5),
    )

    /** 账面攒着的系统分类（跟随账本同步，但不允许手动选作普通支出用途） */
    val savings: FixedCategory =
        FixedCategory(EXPENSE_SAVINGS, "攒着", "savings", CategoryType.EXPENSE, 6)

    val all: List<FixedCategory> = expense + income + listOf(savings)

    /** 内置分类转换为同步模型（内置分类的 updatedAt 为 0，用户修改后才会变新） */
    val defaultSyncCategories: List<SyncCategory> = all.map { category ->
        SyncCategory(
            id = category.id,
            name = category.name,
            icon = category.icon,
            type = category.type.code,
            sortOrder = category.sortOrder,
            updatedAt = 0L,
            deleted = false
        )
    }

    /** 新建自定义分类的 id：取毫秒时间戳，避免两端离线各自新增时发生主键冲突 */
    fun newCategoryId(): Long = System.currentTimeMillis()

    fun otherIdFor(type: String): Long =
        if (CategoryType.from(type) == CategoryType.INCOME) INCOME_OTHER else EXPENSE_OTHER

    /** 合法支出分类：包含「攒着」系统分类（用于同步文件的分类校验） */
    val expenseIds: Set<Long> = (expense + savings).map { it.id }.toSet()
    val incomeIds: Set<Long> = income.map { it.id }.toSet()

    /** 手动记账可选的支出分类（不含「攒着」） */
    val manualExpenseIds: Set<Long> = expense.map { it.id }.toSet()

    /** 常量引用的 id 集合（用于迁移脚本） */
    val expenseIdList: List<Long> = expense.map { it.id }

    /** 把任意分类 id 归一到合法值：找不到时归入同类型的「其他」 */
    fun normalizeCategoryId(type: String, categoryId: Long): Long {
        val ids = if (CategoryType.from(type) == CategoryType.INCOME) incomeIds else expenseIds
        if (categoryId in ids) return categoryId
        return otherIdFor(type)
    }

    fun nameOf(categoryId: Long): String =
        all.firstOrNull { it.id == categoryId }?.name ?: "其他"
}
