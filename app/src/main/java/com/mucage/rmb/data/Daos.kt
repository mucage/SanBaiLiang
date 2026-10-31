package com.mucage.rmb.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Delete
    suspend fun delete(record: RecordEntity)

    /** 可见账目（已排除删除墓碑） */
    @Query("SELECT * FROM records WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    /** 软删除：保留行并盖墓碑，导出时随文件传播 */
    @Query("UPDATE records SET deleted = 1, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun markDeleted(ids: List<Long>, updatedAt: Long)

    /** 全量账目（导出同步文件用） */
    @Query("SELECT * FROM records ORDER BY date ASC, id ASC")
    suspend fun getAll(): List<RecordEntity>

    /** 按同步标识查找（导入合并用） */
    @Query("SELECT * FROM records WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): RecordEntity?

    /** 尚无同步标识的历史账目（升级后回填用） */
    @Query("SELECT * FROM records WHERE syncId = ''")
    suspend fun getWithoutSyncId(): List<RecordEntity>

    /** 查询指定时间区间内的记录（含两端） */
    @Query("SELECT * FROM records WHERE deleted = 0 AND date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<RecordEntity>>

    /** 指定类型在时间区间内的金额合计（分） */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM records " +
            "WHERE deleted = 0 AND type = :type AND date BETWEEN :start AND :end"
    )
    suspend fun sumBetween(type: String, start: Long, end: Long): Long

    /** 月度限额用的支出合计：跳过「不计入限额」的账目（含仅豁免日限额的） */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM records " +
            "WHERE deleted = 0 AND type = :type AND date BETWEEN :start AND :end AND limitExempt != 2"
    )
    suspend fun sumBetweenForMonthly(type: String, start: Long, end: Long): Long

    /** 每日限额用的支出合计：仅统计未做任何限额豁免的账目 */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM records " +
            "WHERE deleted = 0 AND type = :type AND date BETWEEN :start AND :end AND limitExempt = 0"
    )
    suspend fun sumBetweenForDaily(type: String, start: Long, end: Long): Long

    /** 批量设置账目的限额豁免状态（同时刷新修改时间，保证改动可跨端同步） */
    @Query("UPDATE records SET limitExempt = :exempt, updatedAt = :updatedAt WHERE id IN (:ids) AND deleted = 0")
    suspend fun setLimitExempt(ids: List<Long>, exempt: Int, updatedAt: Long)

    /** 指定时间之后的净收支（收入-支出，分）：用于总余额计算 */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0) " +
            "FROM records WHERE deleted = 0 AND date >= :since"
    )
    suspend fun netFlowSince(since: Long): Long

    /** 指定时间之后的净收支（收入-支出，分）：Flow 版，余额随账目变动自动刷新 */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0) " +
            "FROM records WHERE deleted = 0 AND date >= :since"
    )
    fun observeNetFlowSince(since: Long): Flow<Long>
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder")
    fun observeByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder")
    suspend fun getByType(type: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM records WHERE deleted = 0 AND categoryId = :categoryId")
    suspend fun countRecordsByCategory(categoryId: Long): Int
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balance WHERE id = 1")
    fun observe(): Flow<BalanceEntity?>

    @Query("SELECT * FROM balance WHERE id = 1")
    suspend fun get(): BalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: BalanceEntity)
}

@Dao
interface AutoPendingDao {
    @Insert
    suspend fun insert(entity: AutoPendingEntity): Long

    @Query("SELECT * FROM auto_pending WHERE status = 0 ORDER BY capturedAt DESC")
    fun observePending(): Flow<List<AutoPendingEntity>>

    @Query("SELECT COUNT(*) FROM auto_pending WHERE dedupKey = :key AND status = 0")
    suspend fun countByDedupKey(key: String): Int

    @Query("SELECT * FROM auto_pending WHERE id = :id")
    suspend fun getById(id: Long): AutoPendingEntity?

    @Query("SELECT COUNT(*) FROM auto_pending WHERE status = 0")
    suspend fun countPending(): Int

    @Query("UPDATE auto_pending SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)
}
