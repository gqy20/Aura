package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis ASC")
    fun observeScheduled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReminderEntity)

    @Query(
        """
        UPDATE reminders
        SET status = 'FIRED', firedAt = :time, updatedAt = :time
        WHERE id = :id AND status = 'SCHEDULED'
        """
    )
    suspend fun markFired(id: String, time: Long)

    @Query(
        """
        UPDATE reminders
        SET status = 'CANCELED', canceledAt = :time, updatedAt = :time
        WHERE id = :id AND status = 'SCHEDULED'
        """
    )
    suspend fun markCanceled(id: String, time: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}
