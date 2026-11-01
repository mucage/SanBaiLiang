package com.mucage.rmb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordEntity::class, CategoryEntity::class,
        AutoPendingEntity::class, BalanceEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun autoPendingDao(): AutoPendingDao
    abstract fun balanceDao(): BalanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v6 → v7：取消分账户模型，改为单一总余额。
         * - 账目去掉 accountId / accountLabel 字段（历史账目保留）；
         * - 删除 accounts、account_totals 表与汇总触发器整套机制；
         * - 待确认表去掉 accountKind 字段；
         * - 新增 balance 总余额锚点表（未初始化，等待用户首次输入）。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 移除旧触发器与缓存表
                db.execSQL("DROP TRIGGER IF EXISTS trg_records_insert")
                db.execSQL("DROP TRIGGER IF EXISTS trg_records_delete")
                db.execSQL("DROP TRIGGER IF EXISTS trg_records_update")
                db.execSQL("DROP TABLE IF EXISTS account_totals")

                // 账目表：去掉账户字段，保留历史数据
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `limitExempt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_records` (`id`,`amountCents`,`type`,`categoryId`,`note`,`date`,`limitExempt`)
                    SELECT `id`,`amountCents`,`type`,`categoryId`,`note`,`date`,`limitExempt` FROM `records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `records`")
                db.execSQL("ALTER TABLE `_new_records` RENAME TO `records`")

                // 待确认表：去掉 accountKind 字段，保留队列与状态
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_auto_pending` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dedupKey` TEXT NOT NULL,
                        `sourcePackage` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        `status` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_auto_pending`
                        (`id`,`dedupKey`,`sourcePackage`,`sourceLabel`,`title`,`text`,`amountCents`,`type`,`merchant`,`capturedAt`,`status`)
                    SELECT `id`,`dedupKey`,`sourcePackage`,`sourceLabel`,`title`,`text`,`amountCents`,`type`,`merchant`,`capturedAt`,`status`
                    FROM `auto_pending`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `auto_pending`")
                db.execSQL("ALTER TABLE `_new_auto_pending` RENAME TO `auto_pending`")

                // 账户表删除
                db.execSQL("DROP TABLE IF EXISTS `accounts`")

                // 总余额锚点表：默认行由 onOpen 统一补种
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `balance` (
                        `id` INTEGER NOT NULL,
                        `balanceCents` INTEGER NOT NULL,
                        `anchoredAt` INTEGER NOT NULL,
                        `initialized` INTEGER NOT NULL,
                        PRIMARY KEY (`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v7 → v8：
         * - 支出分类整合为固定六类（吃/用/玩/学/出行/其他），旧账目按映射归并，自定义分类不再保留；
         * - 账目新增 syncId（跨端同步标识）与 updatedAt（最近修改时间）；
         * - 余额锚点新增 updatedAt。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) 旧支出分类逐条归并（按 id 升序处理，避免目标 id 与源 id 冲突导致重复搬运）
                for (oldId in 101L..116L) {
                    val newId = SeedData.remapLegacyExpenseId(oldId)
                    if (oldId != newId) {
                        db.execSQL(
                            "UPDATE records SET categoryId = $newId " +
                                "WHERE type = 'EXPENSE' AND categoryId = $oldId"
                        )
                    }
                }
                // 其余支出分类（旧版自定义）统一归入「其他」
                db.execSQL(
                    "UPDATE records SET categoryId = ${SeedData.EXPENSE_OTHER} " +
                        "WHERE type = 'EXPENSE' AND categoryId NOT IN " +
                        "(${SeedData.expenseCategoryIds.joinToString(",")})"
                )

                // 2) 分类表重写为固定分类
                db.execSQL("DELETE FROM categories WHERE type = 'EXPENSE'")
                (SeedData.expenseCategories + SeedData.incomeCategories).forEach { category ->
                    db.execSQL(
                        "INSERT OR REPLACE INTO categories (id,name,icon,type,sortOrder) " +
                            "VALUES (${category.id},'${category.name}','${category.icon}'," +
                            "'${category.type}',${category.sortOrder})"
                    )
                }

                // 3) 同步字段
                db.execSQL("ALTER TABLE records ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE balance ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v8 → v9：账目新增 deleted 删除墓碑，删除改为「软删除」，让删除也能随同步文件跨端传播。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v9 → v10：分类恢复可自定义，并支持跨端同步。
         * - 分类新增 updatedAt（最近修改时间）与 deleted（删除墓碑）。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jianzhang.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    // v6 之前的老库没有迁移路径时全新开始（v6 用户走上面的正式迁移）
                    .fallbackToDestructiveMigration()
                    .addCallback(LedgerCallback)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * 每次打开时确保总余额行存在（全新安装 / 升级 / 破坏性迁移统一兜底）。
         * 内置分类只在缺失时补种（INSERT OR IGNORE）：用户的改名、图标调整与删除不会被覆盖。
         */
        private object LedgerCallback : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL(
                    "INSERT OR IGNORE INTO `balance` (`id`,`balanceCents`,`anchoredAt`,`initialized`,`updatedAt`) " +
                        "VALUES (1, 0, 0, 0, 0)"
                )
                (SeedData.expenseCategories + SeedData.incomeCategories).forEach { category ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (id,name,icon,type,sortOrder,updatedAt,deleted) " +
                            "VALUES (${category.id},'${category.name}','${category.icon}'," +
                            "'${category.type}',${category.sortOrder},0,0)"
                    )
                }
            }
        }
    }
}
