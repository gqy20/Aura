package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentStateDao {

    @Query("SELECT * FROM agent_state WHERE companion_id = :companionId")
    fun observeByCompanionId(companionId: String): Flow<AgentStateEntity?>

    @Query("SELECT * FROM agent_state WHERE companion_id = :companionId")
    suspend fun getByCompanionId(companionId: String): AgentStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AgentStateEntity)

    @Query("UPDATE agent_state SET mood = :mood, updated_at = :now WHERE companion_id = :companionId")
    suspend fun updateMood(companionId: String, mood: String, now: Long)

    @Query("UPDATE agent_state SET relationship_level = :level, updated_at = :now WHERE companion_id = :companionId")
    suspend fun updateRelationshipLevel(companionId: String, level: Float, now: Long)

    @Query("DELETE FROM agent_state WHERE companion_id = :companionId")
    suspend fun deleteByCompanionId(companionId: String)
}
