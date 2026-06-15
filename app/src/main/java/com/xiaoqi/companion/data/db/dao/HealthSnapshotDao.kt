package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSnapshotDao {

    @Query("SELECT * FROM health_snapshots ORDER BY date DESC")
    fun observeAll(): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun observeRange(startDate: Int, endDate: Int): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun findInRange(startDate: Int, endDate: Int): List<HealthSnapshotEntity>

    @Query("SELECT * FROM health_snapshots WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: Int): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): HealthSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HealthSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HealthSnapshotEntity>)

    /**
     * 多源合并写入:仅覆盖 [steps] / [sourcePackages] / [fetchedAt] 三列,
     * **保留** heart_rate / sleep / distance 等其他列。
     *
     * 用法:[SensorManagerHealthSource] 调 — 步数 sensor 只能写步数,
     * 不应抹掉 Health Connect 已经写入的心率/睡眠。`@Query` UPDATE 返回受影响行数,
     * 0 表示该日期无现存 row,调用方应再发一次 [upsert] 创建占位行。
     */
    @Query(
        "UPDATE health_snapshots " +
            "SET steps = :steps, " +
            "    source_packages = :sourcePackages, " +
            "    fetched_at = :fetchedAt " +
            "WHERE date = :date"
    )
    suspend fun updateStepsOnly(
        date: Int,
        steps: Int,
        sourcePackages: String,
        fetchedAt: Long,
    ): Int

    @Query("DELETE FROM health_snapshots")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM health_snapshots")
    suspend fun countAll(): Int
}
