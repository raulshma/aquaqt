package com.keepaside.aquapt.core.logic

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAlertRulesTest {

    @Test
    fun `freshwater values outside ranges return low and high alerts`() {
        val aquarium = aquarium(WaterType.FRESHWATER)
        val values = WaterParameters(
            nitrate = 30.0,
            ph = 6.0,
            temperatureC = 24.0
        )

        val alerts = evaluateParameterAlerts(aquarium, values)

        assertEquals(2, alerts.size)
        assertTrue(alerts.any { it.key == "nitrate" && it.status == "high" && it.max == 25.0 })
        assertTrue(alerts.any { it.key == "ph" && it.status == "low" && it.min == 6.5 })
    }

    @Test
    fun `marine thresholds include salinity calcium and alkalinity`() {
        val aquarium = aquarium(WaterType.MARINE)
        val values = WaterParameters(
            salinity = 1.03,
            calcium = 350.0,
            alkalinity = 12.0
        )

        val alerts = evaluateParameterAlerts(aquarium, values)

        assertEquals(3, alerts.size)
        assertTrue(alerts.any { it.key == "salinity" && it.status == "high" })
        assertTrue(alerts.any { it.key == "calcium" && it.status == "low" })
        assertTrue(alerts.any { it.key == "alkalinity" && it.status == "high" })
    }

    @Test
    fun `null and NaN values are ignored`() {
        val aquarium = aquarium(WaterType.BRACKISH)
        val values = WaterParameters(
            ammonia = Double.NaN,
            nitrate = null,
            temperatureC = 26.0
        )

        val alerts = evaluateParameterAlerts(aquarium, values)

        assertTrue(alerts.isEmpty())
    }

    private fun aquarium(type: WaterType) = Aquarium(
        id = "a-1",
        name = "Test Tank",
        volumeLiters = 100.0,
        dimensions = "",
        waterType = type,
        setupDate = "2024-01-01"
    )
}
