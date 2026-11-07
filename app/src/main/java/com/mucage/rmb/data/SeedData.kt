package com.mucage.rmb.data

import com.mucage.sync.CategoryType
import com.mucage.sync.FixedCategories

/**
 * 内置固定分类：支出整合为「吃、用、玩、学、出行、其他」六类，不再支持自定义；
 * 收入保持内置来源分类，同样不支持自定义。分类定义与桌面端共用 :sync 模块。
 */
object SeedData {

    const val EXPENSE_FOOD = FixedCategories.EXPENSE_FOOD
    const val EXPENSE_USE = FixedCategories.EXPENSE_USE
    const val EXPENSE_FUN = FixedCategories.EXPENSE_FUN
    const val EXPENSE_STUDY = FixedCategories.EXPENSE_STUDY
    const val EXPENSE_TRAVEL = FixedCategories.EXPENSE_TRAVEL
    const val EXPENSE_OTHER = FixedCategories.EXPENSE_OTHER

    /** 支出分类（资金用途）：固定六类 */
    val expenseCategories: List<CategoryEntity> = FixedCategories.expense.map { it.toEntity() }

    /** 收入分类（资金来源）：固定内置 */
    val incomeCategories: List<CategoryEntity> = FixedCategories.income.map { it.toEntity() }

    /** 系统分类「攒着」：用于自动攒着生成的账面划转记录 */
    val savingsCategory: CategoryEntity = FixedCategories.savings.toEntity()

    /** 启动时补种的全部内置分类（含攒着） */
    val allCategories: List<CategoryEntity> =
        expenseCategories + incomeCategories + listOf(savingsCategory)

    /** 合法支出分类 id 集合 */
    val expenseCategoryIds: Set<Long> = FixedCategories.expenseIds

    /** 合法收入分类 id 集合 */
    val incomeCategoryIds: Set<Long> = FixedCategories.incomeIds

    private fun com.mucage.sync.FixedCategory.toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        type = if (type == CategoryType.INCOME) RecordType.INCOME else RecordType.EXPENSE,
        sortOrder = sortOrder
    )

    /** 旧版支出分类 id → 新版六类映射（旧版自定义分类统一归入「其他」） */
    fun remapLegacyExpenseId(oldId: Long): Long = when (oldId) {
        101L, 102L -> EXPENSE_FOOD            // 餐饮、零食 → 吃
        103L, 107L, 108L, 109L, 110L, 112L, 114L -> EXPENSE_USE  // 服装、居住、医疗、通讯、购物、宠物、美妆 → 用
        104L, 111L, 113L -> EXPENSE_FUN       // 娱乐、旅行、运动 → 玩
        105L -> EXPENSE_STUDY                 // 教育 → 学
        106L -> EXPENSE_TRAVEL                // 交通 → 出行
        else -> EXPENSE_OTHER                 // 人情往来、其他及自定义 → 其他
    }
}
