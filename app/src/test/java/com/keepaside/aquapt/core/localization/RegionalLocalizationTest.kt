package com.keepaside.aquapt.core.localization

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalLocalizationTest {

    @Test
    fun `maps India to INR`() {
        assertEquals("INR", getCurrencyForCountry("IN"))
    }

    @Test
    fun `exposes country options for picker UI`() {
        val options = listRegionalCountryOptions()

        assertTrue(options.size >= 100)
        assertTrue(options.any { option -> option.code == "IN" })
        assertTrue(options.any { option -> option.currency == "USD" })
    }

    @Test
    fun `exposes currency options for picker UI`() {
        val options = listSupportedCurrencyCodes()

        assertTrue(options.contains("USD"))
        assertTrue(options.contains("INR"))
        assertEquals(options.sorted(), options)
    }

    @Test
    fun `resolves manual India override from country name`() {
        val resolved = resolveManualRegionalSettings(
            input = ManualRegionalSettingsInput(
                country = "India",
                currency = ""
            )
        )

        assertTrue(resolved is ManualRegionalSettingsResult.Success)
        val value = (resolved as ManualRegionalSettingsResult.Success).value
        assertEquals(
            ManualRegionalSettingsValue(
                defaultCountryCode = "IN",
                defaultCountryName = "India",
                defaultCurrency = "INR"
            ),
            value
        )
    }

    @Test
    fun `keeps manually chosen currency when country changes`() {
        val resolved = resolveManualRegionalSettings(
            input = ManualRegionalSettingsInput(
                country = "India",
                currency = "usd"
            )
        )

        assertTrue(resolved is ManualRegionalSettingsResult.Success)
        val value = (resolved as ManualRegionalSettingsResult.Success).value
        assertEquals("USD", value.defaultCurrency)
    }

    @Test
    fun `rejects invalid currency overrides`() {
        val resolved = resolveManualRegionalSettings(
            input = ManualRegionalSettingsInput(
                country = "India",
                currency = "rupees"
            )
        )

        assertTrue(resolved is ManualRegionalSettingsResult.Error)
        assertEquals(
            "Enter a valid 3-letter currency code.",
            (resolved as ManualRegionalSettingsResult.Error).message
        )
    }

    @Test
    fun `apply regional defaults keeps manual override fields`() {
        val settings = applyRegionalDefaults(
            AppSettings(
                regionalPreferencesMode = RegionalPreferencesMode.MANUAL,
                defaultCountryName = "India",
                defaultCurrency = "USD"
            )
        )

        assertEquals(RegionalPreferencesMode.MANUAL, settings.regionalPreferencesMode)
        assertEquals("IN", settings.defaultCountryCode)
        assertEquals("India", settings.defaultCountryName)
        assertEquals("USD", settings.defaultCurrency)
    }
}
