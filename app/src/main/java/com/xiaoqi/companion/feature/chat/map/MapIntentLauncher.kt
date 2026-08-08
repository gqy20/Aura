package com.xiaoqi.companion.feature.chat.map

import android.content.Context
import android.content.Intent
import android.net.Uri

object MapIntentLauncher {
    fun open(context: Context, interaction: MapToolInteraction): Boolean {
        val candidates = when (interaction) {
            is MapToolInteraction.Place -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse(MapLaunchUrlBuilder.amapPlace(interaction))).apply {
                    setPackage(AMAP_PACKAGE)
                },
                Intent(Intent.ACTION_VIEW, Uri.parse(MapLaunchUrlBuilder.systemPlace(interaction))),
            )
            is MapToolInteraction.Route -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse(MapLaunchUrlBuilder.amapRoute(interaction))).apply {
                    setPackage(AMAP_PACKAGE)
                },
                Intent(Intent.ACTION_VIEW, Uri.parse(MapLaunchUrlBuilder.webRoute(interaction))),
            )
        }
        return candidates.any { intent ->
            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }

    private const val AMAP_PACKAGE = "com.autonavi.minimap"
}
