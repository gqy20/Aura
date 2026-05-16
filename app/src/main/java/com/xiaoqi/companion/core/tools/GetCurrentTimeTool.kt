package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetCurrentTimeTool(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val defaultZoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : SimpleTool<GetCurrentTimeTool.Args>(
    typeToken<Args>(),
    name = "get_current_time",
    description = "Get the user's current local date and time, including weekday and timezone.",
) {

    @Inject
    constructor() : this(
        nowProvider = { System.currentTimeMillis() },
        defaultZoneProvider = { ZoneId.systemDefault() },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Optional IANA timezone ID, such as Asia/Shanghai. Empty uses the device timezone.")
        val timezone: String = "",
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.Default) {
            val zone = args.timezone
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: defaultZoneProvider()
            val now = Instant.ofEpochMilli(nowProvider()).atZone(zone)

            buildJsonObject {
                put("epochMillis", now.toInstant().toEpochMilli())
                put("timezone", zone.id)
                put("utcOffset", now.offset.id)
                put("date", now.toLocalDate().toString())
                put("time", now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                put("dayOfWeek", now.dayOfWeek.name)
                put("isoLocalDateTime", now.toLocalDateTime().toString())
            }.toString()
        }
}
