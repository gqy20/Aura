package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentAction
import com.xiaoqi.companion.core.companion.model.EmotionSignal
import com.xiaoqi.companion.core.companion.model.InteractionSignal
import com.xiaoqi.companion.core.companion.model.ParsedOutput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags

class OutputParser {

    private val moodRegex = Regex("\\[mood:(\\w+)]")
    private val intensityRegex = Regex("\\[intensity:([\\d.]+)]")
    private val affinityRegex = Regex("\\[affinity:([+-]?[\\d.]+)]")
    private val topicsRegex = Regex("\\[topics:([^\\]]+)]")
    private val actionRegex = Regex("\\[action:(\\w+)(?:\\[(.+?)\\])?](?:\\[text:(.+?)])?")
    private val allTagRegex = Regex("\\[[^\\]]+]")

    fun parse(raw: String?): ParsedOutput {
        if (raw.isNullOrEmpty()) {
            AppLogger.warn(LogTags.Parser, "parse_empty_input")
            return ParsedOutput()
        }

        val text = raw
        val cleanText = allTagRegex.replace(text, "").trim()

        val result = ParsedOutput(
            textReply = cleanText,
            emotionSignal = EmotionSignal(
                mood = moodRegex.find(text)?.groupValues?.get(1) ?: "neutral",
                intensity = intensityRegex.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.5f,
            ),
            interactionSignal = InteractionSignal(
                affinityDelta = affinityRegex.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
                topicTags = topicsRegex.find(text)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList(),
            ),
            actions = actionRegex.findAll(text).map { match ->
                AgentAction(
                    type = match.groupValues[1],
                    params = buildMap {
                        match.groupValues.getOrNull(2)?.let { put("raw", it) }
                        match.groupValues.getOrNull(3)?.let { put("text", it) }
                    },
                )
            }.toList(),
        )

        AppLogger.debug(
            LogTags.Parser,
            "parse_completed",
            "rawLength" to raw.length,
            "replyLength" to result.textReply.length,
            "mood" to result.emotionSignal.mood,
            "intensity" to result.emotionSignal.intensity,
            "affinityDelta" to result.interactionSignal.affinityDelta,
            "actionCount" to result.actions.size,
        )

        return result
    }
}
