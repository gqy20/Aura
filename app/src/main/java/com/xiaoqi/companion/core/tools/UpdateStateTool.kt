package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * 合并 tool：情绪 + 关系 + 记忆，一次调用完成状态更新。
 * 所有字段 optional，LLM 只填需要更新的部分。
 */
class UpdateStateTool @Inject constructor(
    private val emotionMachine: EmotionStateMachine,
    private val relationshipModel: RelationshipModel,
    private val memoryRepository: MemoryRepository,
) : SimpleTool<UpdateStateTool.Args>(
    typeToken<Args>(),
    name = "update_state",
    description = """
        Update companion's emotional state, relationship level, and optionally save a memory — all in one call.
        All fields are optional; only fill what genuinely changed.

        WHEN to call (any one is enough):
        - Emotional shift: user's words make you feel noticeably happier/sadder/more worried/etc.
        - Relationship change: user shares something intimate, or a conflict creates distance.
        - Memory worth saving — trigger if user mentions ANY of:
          • Personal facts: name, age, job, location, family/friend names
          • Preferences or strong opinions: "我喜欢…", "我讨厌…", "我觉得…最好"
          • Plans or scheduled events: "下周…", "明天要…", "打算…"
          • Health: illness, medication, exercise habits, sleep issues
          • Correcting you: "不是那样的", "我其实…", "你记错了"
          • Significant life events: new job, breakup, achievement, loss
        If none of the above genuinely occurred, skip this tool entirely.

        Do NOT call every turn out of habit. Memory content should be a concise fact
        ("用户名叫小明", "用户下周三有面试"), not a conversation summary.
    """.trimIndent(),
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Current mood name, e.g. happy/sad/calm/excited/neutral. Omit if unchanged.")
        val mood: String? = null,
        @param:LLMDescription("Mood intensity 0.0-1.0. Omit if mood is omitted.")
        val intensity: Float? = null,
        @param:LLMDescription("Brief reason for mood change.")
        val mood_reason: String? = null,
        @param:LLMDescription("Affinity delta -0.1 to +0.1. Positive = closer, negative = more distant. Omit if unchanged.")
        val affinity_delta: Float? = null,
        @param:LLMDescription("Brief reason for relationship change.")
        val affinity_reason: String? = null,
        @param:LLMDescription("Content worth remembering for future conversations. Omit if nothing notable.")
        val memory_content: String? = null,
        @param:LLMDescription("Memory type: FACT, EPISODE, or PROCEDURAL.")
        val memory_type: String? = null,
        @param:LLMDescription("Memory importance 0.0-1.0. Default 0.5.")
        val memory_importance: Float? = null,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        // — 情绪 —
        if (args.mood != null) {
            emotionMachine.record(
                mood = args.mood,
                intensity = args.intensity ?: 0.5f,
                reason = args.mood_reason ?: "",
            )
            AppLogger.debug(LogTags.Emotion, "state_tool_mood", "mood" to args.mood)
        }

        // — 关系 —
        if (args.affinity_delta != null) {
            relationshipModel.applyDelta(
                delta = args.affinity_delta,
                reason = args.affinity_reason ?: "",
            )
            AppLogger.debug(LogTags.Relation, "state_tool_affinity", "delta" to args.affinity_delta)
        }

        // — 记忆 —
        var memorySaved = 0
        val content = args.memory_content?.trim().orEmpty()
        if (content.isNotBlank()) {
            val type = MemoryType.entries.firstOrNull { it.name == args.memory_type?.uppercase() }
                ?: MemoryType.FACT
            memoryRepository.saveMemory(
                SaveMemoryRequest(
                    content = content,
                    type = type,
                    importance = (args.memory_importance ?: 0.5f).coerceIn(0f, 1f),
                    source = "tool:update_state",
                ),
            )
            memorySaved = 1
            AppLogger.debug(LogTags.Repo, "state_tool_memory", "type" to type, "len" to content.length)
        }

        val data = buildJsonObject {
            put("mood", args.mood ?: emotionMachine.currentMood)
            put("intensity", args.intensity ?: emotionMachine.latestIntensity)
            put("relationshipLevel", relationshipModel.currentLevel)
            put("memorySaved", memorySaved)
        }
        encode(ToolEnvelopeFactory.ok(data))
    }

}
