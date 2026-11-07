package com.mucage.rmb.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mucage.sync.AutoSavePeriod

/** 账目类型 */
object RecordType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
}

/**
 * 总余额锚点（单一行，id=1）。
 * 当前总余额 = balanceCents + 锚点之后的净收支（收入-支出）；
 * 每次确认/重设余额时以「天」为粒度刷新锚点，锚点之前的账目不再计入。
 */
@Entity(tableName = "balance")
data class BalanceEntity(
    @PrimaryKey val id: Long = 1,
    val balanceCents: Long = 0,       // 最近一次确认的总余额（分）
    val anchoredAt: Long = 0,         // 锚点时间（当天 0 点）：此时间之前的账目不计入余额
    val initialized: Boolean = false, // 首次启动是否已输入初始余额
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,          // 最近修改时间，用于两端同步时判断新旧
    /** 攒着：总余额中预留出来、仅供查看的部分（不涉及真实资金搬移） */
    @ColumnInfo(defaultValue = "0")
    val savedCents: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val autoSaveEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "MONTHLY")
    val autoSavePeriod: String = AutoSavePeriod.MONTHLY.code,
    @ColumnInfo(defaultValue = "0")
    val autoSaveAmountCents: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val autoSaveCountAsExpense: Boolean = false,
    /** 上次结算到的周期起点；两端凭它防重复，并据此逐期补足 */
    @ColumnInfo(defaultValue = "0")
    val autoSaveLastAt: Long = 0
)

/** 资金用途分类（内置分类 + 用户自建分类） */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String,          // 图标标识，见 CategoryIcons 映射
    val type: String,          // EXPENSE / INCOME
    val sortOrder: Int,
    /** 最近修改时间：跨端合并时较新的分类定义胜出 */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
    /** 删除墓碑：界面不展示，但随同步文件导出，避免另一端把已删除分类重新种回来 */
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false
)

/** 一笔账目记录：只对总余额加减，不记来源与支付方式 */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,     // 金额 × 100 的整数，避免浮点误差
    val type: String,          // EXPENSE / INCOME
    val categoryId: Long,
    val note: String,
    val date: Long,            // 记账日期（epoch 毫秒）
    val limitExempt: Int = LimitExempt.NONE,
    /** 跨端同步标识：两端同一笔账目拥有相同 syncId，用于导入时去重合并 */
    @ColumnInfo(defaultValue = "")
    val syncId: String = java.util.UUID.randomUUID().toString(),
    /** 最近修改时间：导入时与本地比较，较新的版本胜出 */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    /** 删除墓碑：界面不展示，但保留在库中并随同步文件导出，保证删除可跨端传播 */
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false,
    /** 账面上的「攒着」划转：不改变总余额；开启「视作支出」时会参与限额与统计 */
    @ColumnInfo(defaultValue = "0")
    val savingsTransfer: Boolean = false
)

/** 账目的限额豁免状态 */
object LimitExempt {
    const val NONE = 0        // 计入所有限额
    const val SKIP_DAILY = 1  // 不计入当日限额（仍保留在月限额中）
    const val SKIP_ALL = 2    // 不计入任何限额
}

/** 自动记账待确认记录的状态 */
object AutoPendingStatus {
    const val PENDING = 0      // 待确认
    const val CONFIRMED = 1    // 已入账
    const val DISCARDED = 2    // 已忽略
}

/** 自动记账待确认记录：由通知/短信捕获，用户确认后写入 records（来源仅用于展示，不写入账目） */
@Entity(tableName = "auto_pending")
data class AutoPendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupKey: String,       // 去重键，防止同一笔交易重复捕获
    val sourcePackage: String,  // 来源应用包名（微信 / 支付宝）
    val sourceLabel: String,    // 来源应用名（微信 / 支付宝），仅用于待确认列表展示
    val title: String,          // 通知标题
    val text: String,           // 通知正文
    val amountCents: Long,      // 解析出的金额（分）
    val type: String,           // EXPENSE / INCOME
    val merchant: String,       // 解析出的商户/对方
    val capturedAt: Long,       // 捕获时间（epoch 毫秒）
    val status: Int             // AutoPendingStatus
)
