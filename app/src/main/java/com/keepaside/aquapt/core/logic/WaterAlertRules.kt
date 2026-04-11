package com.keepaside.aquapt.core.logic

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType

private data class NumericRange(
    val min: Double? = null,
    val max: Double? = null
)

data class ParameterAlert(
    val key: String,
    val label: String,
    val value: Double,
    val unit: String,
    val status: String,
    val min: Double? = null,
    val max: Double? = null
)

private val PARAMETER_LABELS: Map<String, String> = mapOf(
    "ammonia" to "Ammonia",
    "nitrite" to "Nitrite",
    "nitrate" to "Nitrate",
    "ph" to "pH",
    "temperatureC" to "Temperature",
    "gh" to "GH",
    "kh" to "KH",
    "salinity" to "Salinity",
    "calcium" to "Calcium",
    "alkalinity" to "Alkalinity"
)

private val PARAMETER_UNITS: Map<String, String> = mapOf(
    "ammonia" to "ppm",
    "nitrite" to "ppm",
    "nitrate" to "ppm",
    "ph" to "",
    "temperatureC" to "°C",
    "gh" to "dGH",
    "kh" to "dKH",
    "salinity" to "sg",
    "calcium" to "ppm",
    "alkalinity" to "dKH"
)

private val RANGES_BY_WATER_TYPE: Map<WaterType, Map<String, NumericRange>> = mapOf(
    WaterType.FRESHWATER to mapOf(
        "ammonia" to NumericRange(max = 0.0),
        "nitrite" to NumericRange(max = 0.0),
        "nitrate" to NumericRange(max = 25.0),
        "ph" to NumericRange(min = 6.5, max = 7.8),
        "temperatureC" to NumericRange(min = 22.0, max = 27.0),
        "gh" to NumericRange(min = 4.0, max = 12.0),
        "kh" to NumericRange(min = 2.0, max = 8.0)
    ),
    WaterType.BRACKISH to mapOf(
        "ammonia" to NumericRange(max = 0.0),
        "nitrite" to NumericRange(max = 0.0),
        "nitrate" to NumericRange(max = 30.0),
        "ph" to NumericRange(min = 7.2, max = 8.4),
        "temperatureC" to NumericRange(min = 24.0, max = 28.0),
        "salinity" to NumericRange(min = 1.005, max = 1.02)
    ),
    WaterType.MARINE to mapOf(
        "ammonia" to NumericRange(max = 0.0),
        "nitrite" to NumericRange(max = 0.0),
        "nitrate" to NumericRange(max = 15.0),
        "ph" to NumericRange(min = 7.9, max = 8.4),
        "temperatureC" to NumericRange(min = 24.0, max = 27.0),
        "salinity" to NumericRange(min = 1.023, max = 1.026),
        "calcium" to NumericRange(min = 380.0, max = 460.0),
        "alkalinity" to NumericRange(min = 7.0, max = 11.0)
    )
)

fun evaluateParameterAlerts(
    aquarium: Aquarium,
    values: WaterParameters
): List<ParameterAlert> {
    val ranges = RANGES_BY_WATER_TYPE[aquarium.waterType].orEmpty()
    val alerts = mutableListOf<ParameterAlert>()

    for ((key, range) in ranges) {
        val value = valueForKey(values, key)
        if (value == null || value.isNaN()) continue

        if (range.min != null && value < range.min) {
            alerts += ParameterAlert(
                key = key,
                label = PARAMETER_LABELS[key].orEmpty(),
                value = value,
                unit = PARAMETER_UNITS[key].orEmpty(),
                status = "low",
                min = range.min,
                max = range.max
            )
            continue
        }

        if (range.max != null && value > range.max) {
            alerts += ParameterAlert(
                key = key,
                label = PARAMETER_LABELS[key].orEmpty(),
                value = value,
                unit = PARAMETER_UNITS[key].orEmpty(),
                status = "high",
                min = range.min,
                max = range.max
            )
        }
    }

    return alerts
}

private fun valueForKey(values: WaterParameters, key: String): Double? =
    when (key) {
        "ammonia" -> values.ammonia
        "nitrite" -> values.nitrite
        "nitrate" -> values.nitrate
        "ph" -> values.ph
        "temperatureC" -> values.temperatureC
        "gh" -> values.gh
        "kh" -> values.kh
        "salinity" -> values.salinity
        "calcium" -> values.calcium
        "alkalinity" -> values.alkalinity
        else -> null
    }
