package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetUserContextSettingsTool @Inject constructor(
    private val appPreferences: AppPreferences,
    private val permissionReader: ContextPermissionReader,
) : SimpleTool<GetUserContextSettingsTool.Args>(
    typeToken<Args>(),
    name = "get_user_context_settings",
    description = "Read which local context capabilities are enabled and whether sensitive Android permissions are granted.",
) {

    @Serializable
    data class Args(
        val includePermissions: Boolean = true,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            buildJsonObject {
                put("deviceStatusContextEnabled", appPreferences.deviceStatusContextEnabled.first())
                put("locationContextEnabled", appPreferences.locationContextEnabled.first())
                put("weatherContextEnabled", appPreferences.weatherContextEnabled.first())
                put("reminderToolEnabled", appPreferences.reminderToolEnabled.first())
                put("notificationEnabled", appPreferences.notificationEnabled.first())
                put("hasCoarseLocationPermission", permissionReader.hasCoarseLocation())
                put("hasFineLocationPermission", permissionReader.hasFineLocation())
                put("hasNotificationPermission", permissionReader.hasPostNotifications())
            }.toString()
        }
}
