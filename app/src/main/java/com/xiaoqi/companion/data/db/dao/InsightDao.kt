package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.InsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

    /** 主页卡片用:VISIBLE 按时间倒序,取前 N 条 */
    @Query(
        """
        SELECT * FROM insights
        WHERE status = 'VISIBLE'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeVisible(limit: Int): Flow<List<InsightEntity>>

    /** 给 InsightValidator 30 天去重用:同 category 内最近 30 天的活跃 insight */
    @Query(
        """
        SELECT * FROM insights
        WHERE category = :category
          AND createdAt >= :since
          AND status IN ('VISIBLE', 'DISMISSED')
        ORDER BY createdAt DESC
        """
    )
    fun observeByCategory(category: String, since: Long): Flow<List<InsightEntity>>

    /** 给 Validator 30 天 heading 去重(轻量:只取 headline 字段) */
    @Query(
        """
        SELECT headline FROM insights
        WHERE createdAt >= :since
          AND status IN ('VISIBLE', 'DISMISSED')
        """
    )
    fun observeRecentHeadings(since: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: InsightEntity): Long

    @Query("UPDATE insights SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query(
        """
        UPDATE insights
        SET status = :status, mutedUntil = :mutedUntil
        WHERE id = :id
        """
    )
    suspend fun setStatusWithMute(id: Long, status: String, mutedUntil: Long?)

    @Query("UPDATE insights SET userClickedAt = :time WHERE id = :id")
    suspend fun setUserClickedAt(id: Long, time: Long)

    @Query("UPDATE insights SET userFeedback = :feedback WHERE id = :id")
    suspend fun setUserFeedback(id: Long, feedback: String)

    @Query("SELECT COUNT(*) FROM insights")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM insights WHERE status = 'VISIBLE'")
    suspend fun countVisible(): Int

    @Query(
        """
        SELECT * FROM insights
        WHERE status = 'VISIBLE'
          AND (mutedUntil IS NULL OR mutedUntil < :now)
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeVisibleNotMuted(limit: Int, now: Long): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE id = :id")
    suspend fun getById(id: Long): InsightEntity?

    @Query("DELETE FROM insights")
    suspend fun clearAll()
}
