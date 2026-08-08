package com.xiaoqi.companion.feature.chat.map

import com.xiaoqi.companion.core.tools.normalizeToolResultJson
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MapCoordinate(
    val longitude: Double,
    val latitude: Double,
) {
    val queryValue: String = "$longitude,$latitude"
}

sealed interface MapToolInteraction {
    data class Place(
        val name: String,
        val address: String,
        val coordinate: MapCoordinate,
    ) : MapToolInteraction

    data class Route(
        val origin: MapCoordinate,
        val destination: MapCoordinate,
        val travelMode: MapTravelMode,
        val distanceMeters: Long?,
        val durationSeconds: Long?,
    ) : MapToolInteraction
}

enum class MapTravelMode(val label: String, val promptValue: String, val amapType: Int) {
    WALKING("步行", "步行", 2),
    DRIVING("驾车", "驾车", 0),
    BICYCLING("骑行", "骑行", 3),
    TRANSIT("公交", "公共交通", 1),
}

data class MapRouteDraft(
    val origin: String,
    val destination: String,
    val travelMode: MapTravelMode,
)

object MapToolInteractionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(toolName: String, argumentsJson: String, resultJson: String?): MapToolInteraction? {
        val normalizedName = toolName.lowercase()
        if ("maps_" !in normalizedName && "amap" !in normalizedName) return null
        val arguments = argumentsJson.parseObjectOrNull() ?: JsonObject(emptyMap())
        val result = resultJson
            ?.let(::normalizeToolResultJson)
            ?.parseObjectOrNull()
            ?: return null

        return when {
            "direction" in normalizedName -> parseRoute(normalizedName, result)
            normalizedName.endsWith("maps_geo") || normalizedName.endsWith("__maps_geo") -> {
                parsePlace(arguments, result)
            }
            else -> null
        }
    }

    private fun parsePlace(arguments: JsonObject, result: JsonObject): MapToolInteraction.Place? {
        val first = result.array("results")?.firstOrNull()?.objectOrNull() ?: return null
        val coordinate = first.string("location")?.toCoordinateOrNull() ?: return null
        val name = arguments.string("address")?.takeIf(String::isNotBlank) ?: "地图位置"
        val address = listOf("province", "city", "district", "street", "number")
            .mapNotNull { key -> first.string(key) }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString()
            .ifBlank { arguments.string("city").orEmpty() }
        return MapToolInteraction.Place(name = name, address = address, coordinate = coordinate)
    }

    private fun parseRoute(toolName: String, result: JsonObject): MapToolInteraction.Route? {
        val route = result["route"]?.objectOrNull() ?: return null
        val origin = route.string("origin")?.toCoordinateOrNull() ?: return null
        val destination = route.string("destination")?.toCoordinateOrNull() ?: return null
        val firstPath = route.array("paths")?.firstOrNull()?.objectOrNull()
        return MapToolInteraction.Route(
            origin = origin,
            destination = destination,
            travelMode = when {
                "driving" in toolName -> MapTravelMode.DRIVING
                "bicycling" in toolName || "cycling" in toolName -> MapTravelMode.BICYCLING
                "transit" in toolName -> MapTravelMode.TRANSIT
                else -> MapTravelMode.WALKING
            },
            distanceMeters = firstPath?.long("distance"),
            durationSeconds = firstPath?.long("duration"),
        )
    }

    private fun String.parseObjectOrNull(): JsonObject? = runCatching {
        json.parseToJsonElement(this).jsonObject
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = this[key]
        ?.let { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun JsonObject.long(key: String): Long? = this[key]
        ?.let { element -> runCatching { element.jsonPrimitive.longOrNull }.getOrNull() }

    private fun JsonObject.array(key: String): JsonArray? = this[key]
        ?.let { element -> runCatching { element.jsonArray }.getOrNull() }

    private fun JsonElement.objectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun String.toCoordinateOrNull(): MapCoordinate? {
        val parts = split(',')
        if (parts.size != 2) return null
        val longitude = parts[0].trim().toDoubleOrNull() ?: return null
        val latitude = parts[1].trim().toDoubleOrNull() ?: return null
        if (longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
        return MapCoordinate(longitude, latitude)
    }
}

object MapRoutePromptBuilder {
    fun build(draft: MapRouteDraft): String {
        val origin = draft.origin.trim()
        val destination = draft.destination.trim()
        require(origin.isNotEmpty() && destination.isNotEmpty())
        return "请重新规划路线。起点：$origin；终点：$destination；出行方式：${draft.travelMode.promptValue}。" +
            "请调用地图工具查询最新路线，并给出地图结果。"
    }
}

object MapLaunchUrlBuilder {
    fun amapPlace(place: MapToolInteraction.Place): String =
        "androidamap://viewMap?sourceApplication=Aura" +
            "&poiname=${encode(place.name)}" +
            "&lat=${place.coordinate.latitude}&lon=${place.coordinate.longitude}&dev=0"

    fun systemPlace(place: MapToolInteraction.Place): String =
        "geo:${place.coordinate.latitude},${place.coordinate.longitude}" +
            "?q=${place.coordinate.latitude},${place.coordinate.longitude}(${encode(place.name)})"

    fun amapRoute(route: MapToolInteraction.Route): String =
        "androidamap://route/plan/?sourceApplication=Aura" +
            "&slat=${route.origin.latitude}&slon=${route.origin.longitude}&sname=${encode("起点")}" +
            "&dlat=${route.destination.latitude}&dlon=${route.destination.longitude}&dname=${encode("终点")}" +
            "&dev=0&t=${route.travelMode.amapType}"

    fun webRoute(route: MapToolInteraction.Route): String =
        "https://uri.amap.com/navigation?from=${route.origin.queryValue},${encode("起点")}" +
            "&to=${route.destination.queryValue},${encode("终点")}" +
            "&mode=${route.travelMode.webValue()}&policy=1&src=Aura&coordinate=gaode&callnative=1"

    private fun MapTravelMode.webValue(): String = when (this) {
        MapTravelMode.WALKING -> "walk"
        MapTravelMode.DRIVING -> "car"
        MapTravelMode.BICYCLING -> "ride"
        MapTravelMode.TRANSIT -> "bus"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
