package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class UpdateMoodTool(
    private val moodSnapshotDao: MoodSnapshotDao,
    private val companionIdProvider: () -> String = { "default" },
) : SimpleTool<UpdateMoodTool.Args>(
    typeToken<Args>(),
    name = "update_mood",
    description = "Record the current emotional state as a mood snapshot for tracking emotion history.",
) {

    @Inject
    constructor(
        moodSnapshotDao: MoodSnapshotDao,
    ) : this(
        moodSnapshotDao = moodSnapshotDao,
        companionIdProvider = { "default" },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Current mood label (e.g. happy, sad, calm, excited).")
        val mood: String,
        @param:LLMDescription("What triggered this mood change. Optional.")
        val trigger: String? = null,
        @param:LLMDescription("Mood intensity from 0.0 to 1.0.")
        val intensity: Float = 0.5f,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val safeIntensity = args.intensity.coerceIn(0f, 1f)
            val snapshotId = UUID.randomUUID().toString()
            moodSnapshotDao.insert(
                MoodSnapshotEntity(
                    id = snapshotId,
                    companionId = companionIdProvider(),
                    mood = args.mood,
                    trigger = args.trigger,
                    intensity = safeIntensity,
                    timestamp = System.currentTimeMillis(),
                )
            )
            """{"status":"saved","snapshotId":"$snapshotId","mood":"${args.mood}"}"""
        }
}
