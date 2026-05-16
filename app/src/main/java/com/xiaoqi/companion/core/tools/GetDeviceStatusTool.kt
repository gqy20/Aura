package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.context.DeviceStatusProvider
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetDeviceStatusTool @Inject constructor(
    private val appPreferences: AppPreferences,
    private val deviceStatusProvider: DeviceStatusProvider,
) : SimpleTool<GetDeviceStatusTool.Args>(
    typeToken<Args>(),
    name = "get_device_status",
    description = "Read low-risk device context such as battery level, charging state, network status, and power saver mode.",
) {

    @Serializable
    data class Args(
        val includeNetwork: Boolean = true,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.Default) {
            if (!appPreferences.deviceStatusContextEnabled.first()) {
                return@withContext buildJsonObject {
                    put("status", "disabled")
                    put("reason", "device_status_context_disabled")
                }.toString()
            }

            val status = deviceStatusProvider.getStatus()
            buildJsonObject {
                put("status", "ok")
                put("batteryPercent", status.batteryPercent)
                put("isCharging", status.isCharging)
                put("powerSaveMode", status.powerSaveMode)
                put("isOnline", status.isOnline)
                put("networkType", status.networkType)
            }.toString()
        }
}
