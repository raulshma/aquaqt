package com.keepaside.aquapt.core.localization

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

const val defaultRegionalCountryCode = "US"
const val defaultRegionalCurrencyCode = "USD"
const val defaultRegionalLocale = "en-US"
const val defaultRegionalTimezone = "UTC"

private const val frankfurterApiBaseUrl = "https://api.frankfurter.app"
private const val fallbackExchangeApiBaseUrl =
    "https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies"

private const val invalidManualCountryMessage = "Enter a valid country name or 2-letter country code."
private const val invalidManualCurrencyMessage = "Enter a valid 3-letter currency code."

data class RegionalDefaults(
    val regionalPreferencesMode: RegionalPreferencesMode = RegionalPreferencesMode.AUTO,
    val defaultCountryCode: String,
    val defaultCountryName: String,
    val defaultCurrency: String,
    val defaultLocale: String,
    val defaultTimezone: String
)

data class RegionalCountryOption(
    val code: String,
    val name: String,
    val currency: String
)

data class ManualRegionalSettingsInput(
    val country: String? = null,
    val currency: String? = null,
    val fallbackCountryCode: String? = null
)

data class ManualRegionalSettingsValue(
    val defaultCountryCode: String,
    val defaultCountryName: String,
    val defaultCurrency: String
)

sealed interface ManualRegionalSettingsResult {
    data class Success(val value: ManualRegionalSettingsValue) : ManualRegionalSettingsResult
    data class Error(val message: String) : ManualRegionalSettingsResult
}

data class ExchangeRateHttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface ExchangeRateHttpClient {
    suspend fun get(url: String): ExchangeRateHttpResponse
}

object DefaultExchangeRateHttpClient : ExchangeRateHttpClient {
    override suspend fun get(url: String): ExchangeRateHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            ExchangeRateHttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }
}

fun normalizeCountryCode(value: String?): String? {
    val normalized = value?.trim()?.uppercase(Locale.US)
    if (normalized.isNullOrEmpty() || normalized.length != 2) {
        return null
    }
    return normalized
}

fun normalizeCurrencyCode(value: String?): String? {
    val normalized = value?.trim()?.uppercase(Locale.US)
    if (normalized.isNullOrEmpty() || normalized.length != 3) {
        return null
    }
    return normalized
}

fun isSupportedCurrencyCode(value: String?): Boolean {
    val normalized = normalizeCurrencyCode(value) ?: return false
    return runCatching {
        val currency = Currency.getInstance(normalized)
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag(defaultRegionalLocale)).apply {
            this.currency = currency
        }.format(1)
    }.isSuccess
}

fun findCountry(input: String?): RegionalCountryOption? {
    val normalizedCountryCode = normalizeCountryCode(input)
    if (normalizedCountryCode != null) {
        return countryOptionsByCode[normalizedCountryCode]
    }

    val normalizedName = normalizeCountryName(input)
    if (normalizedName.isEmpty()) {
        return null
    }

    return countryOptionsByName[normalizedName]
}

fun getCurrencyForCountry(countryCode: String?): String {
    val normalizedCountryCode = normalizeCountryCode(countryCode) ?: return defaultRegionalCurrencyCode
    return runCatching {
        Currency.getInstance(Locale("", normalizedCountryCode)).currencyCode
    }.getOrDefault(defaultRegionalCurrencyCode)
}

fun listRegionalCountryOptions(): List<RegionalCountryOption> = regionalCountryOptions

fun listSupportedCurrencyCodes(): List<String> = supportedCurrencyCodes

fun resolveRegionalDefaults(
    localeProvider: () -> Locale = { Locale.getDefault() },
    timezoneProvider: () -> String = { TimeZone.getDefault().id }
): RegionalDefaults {
    val locale = runCatching { localeProvider() }.getOrNull() ?: Locale.forLanguageTag(defaultRegionalLocale)
    val localeTag = locale.toLanguageTag().takeIf { it.isNotBlank() } ?: defaultRegionalLocale
    val timezone = runCatching { timezoneProvider() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: defaultRegionalTimezone

    val localeCountryCode = normalizeCountryCode(locale.country)
        ?: countryCodeFromLocaleTag(localeTag)
    val timezoneCountryCode = countryCodeFromTimezone(timezone)
    val countryCode = timezoneCountryCode ?: localeCountryCode ?: defaultRegionalCountryCode
    val countryName = findCountry(countryCode)?.name
        ?: findCountry(defaultRegionalCountryCode)?.name
        ?: "United States"

    return RegionalDefaults(
        regionalPreferencesMode = RegionalPreferencesMode.AUTO,
        defaultCountryCode = countryCode,
        defaultCountryName = countryName,
        defaultCurrency = getCurrencyForCountry(countryCode),
        defaultLocale = localeTag,
        defaultTimezone = timezone
    )
}

fun resolveManualRegionalSettings(
    input: ManualRegionalSettingsInput,
    detectedDefaults: RegionalDefaults = resolveRegionalDefaults()
): ManualRegionalSettingsResult {
    val countryInput = input.country?.trim().orEmpty()
    val matchedCountry = if (countryInput.isNotEmpty()) {
        findCountry(countryInput)
    } else {
        findCountry(input.fallbackCountryCode)
            ?: findCountry(detectedDefaults.defaultCountryCode)
    }

    if (countryInput.isNotEmpty() && matchedCountry == null) {
        return ManualRegionalSettingsResult.Error(invalidManualCountryMessage)
    }

    val currencyInput = input.currency?.trim().orEmpty()
    if (currencyInput.isNotEmpty() && !isSupportedCurrencyCode(currencyInput)) {
        return ManualRegionalSettingsResult.Error(invalidManualCurrencyMessage)
    }

    val countryCode = matchedCountry?.code ?: detectedDefaults.defaultCountryCode
    val countryName = matchedCountry?.name ?: detectedDefaults.defaultCountryName

    return ManualRegionalSettingsResult.Success(
        ManualRegionalSettingsValue(
            defaultCountryCode = countryCode,
            defaultCountryName = countryName,
            defaultCurrency = normalizeCurrencyCode(currencyInput)
                ?: getCurrencyForCountry(countryCode)
        )
    )
}

fun applyRegionalDefaults(
    settings: AppSettings,
    detectedDefaults: RegionalDefaults = resolveRegionalDefaults()
): AppSettings {
    if (settings.regionalPreferencesMode != RegionalPreferencesMode.MANUAL) {
        return settings.copy(
            regionalPreferencesMode = RegionalPreferencesMode.AUTO,
            defaultLocale = detectedDefaults.defaultLocale,
            defaultTimezone = detectedDefaults.defaultTimezone,
            defaultCountryCode = detectedDefaults.defaultCountryCode,
            defaultCountryName = detectedDefaults.defaultCountryName,
            defaultCurrency = detectedDefaults.defaultCurrency
        )
    }

    val manualResolution = resolveManualRegionalSettings(
        input = ManualRegionalSettingsInput(
            country = settings.defaultCountryCode ?: settings.defaultCountryName,
            currency = settings.defaultCurrency,
            fallbackCountryCode = detectedDefaults.defaultCountryCode
        ),
        detectedDefaults = detectedDefaults
    )

    val manualValue = (manualResolution as? ManualRegionalSettingsResult.Success)?.value

    return settings.copy(
        regionalPreferencesMode = RegionalPreferencesMode.MANUAL,
        defaultLocale = settings.defaultLocale
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: detectedDefaults.defaultLocale,
        defaultTimezone = settings.defaultTimezone
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: detectedDefaults.defaultTimezone,
        defaultCountryCode = manualValue?.defaultCountryCode ?: detectedDefaults.defaultCountryCode,
        defaultCountryName = manualValue?.defaultCountryName ?: detectedDefaults.defaultCountryName,
        defaultCurrency = manualValue?.defaultCurrency ?: detectedDefaults.defaultCurrency
    )
}

fun formatCurrencyAmount(
    value: Double,
    currencyCode: String = defaultRegionalCurrencyCode,
    localeTag: String = defaultRegionalLocale,
    maximumFractionDigits: Int? = null
): String {
    val normalizedCurrency = normalizeCurrencyCode(currencyCode) ?: defaultRegionalCurrencyCode
    return runCatching {
        val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(localeTag))
        formatter.currency = Currency.getInstance(normalizedCurrency)
        maximumFractionDigits?.let { formatter.maximumFractionDigits = it }
        formatter.format(value)
    }.getOrElse {
        val fractionDigits = maximumFractionDigits ?: 2
        normalizedCurrency + " " + String.format(Locale.US, "%.${fractionDigits}f", value)
    }
}

suspend fun convertCurrencyAmount(
    value: Double,
    fromCurrency: String,
    toCurrency: String,
    httpClient: ExchangeRateHttpClient = DefaultExchangeRateHttpClient
): Double {
    val normalizedFrom = normalizeCurrencyCode(fromCurrency)
        ?: throw IllegalArgumentException("Invalid from-currency code.")
    val normalizedTo = normalizeCurrencyCode(toCurrency)
        ?: throw IllegalArgumentException("Invalid to-currency code.")

    if (normalizedFrom == normalizedTo) {
        return value
    }

    val exchangeRate = getExchangeRate(
        fromCurrency = normalizedFrom,
        toCurrency = normalizedTo,
        httpClient = httpClient
    )
    return value * exchangeRate
}

internal suspend fun clearExchangeRateCache() {
    exchangeRateCacheMutex.withLock {
        exchangeRateCache.clear()
    }
}

private suspend fun getExchangeRate(
    fromCurrency: String,
    toCurrency: String,
    httpClient: ExchangeRateHttpClient
): Double {
    val normalizedFrom = fromCurrency.trim().uppercase(Locale.US)
    val normalizedTo = toCurrency.trim().uppercase(Locale.US)
    val cacheKey = "${normalizedFrom.lowercase(Locale.US)}-${normalizedTo.lowercase(Locale.US)}"

    exchangeRateCacheMutex.withLock {
        exchangeRateCache[cacheKey]?.let { rate ->
            return rate
        }
    }

    val resolvedRate = runCatching {
        loadFrankfurterRate(
            fromCurrency = normalizedFrom,
            toCurrency = normalizedTo,
            httpClient = httpClient
        )
    }.getOrElse {
        loadFallbackRate(
            fromCurrency = normalizedFrom,
            toCurrency = normalizedTo,
            httpClient = httpClient
        )
    }

    exchangeRateCacheMutex.withLock {
        exchangeRateCache[cacheKey] = resolvedRate
    }

    return resolvedRate
}

private suspend fun loadFrankfurterRate(
    fromCurrency: String,
    toCurrency: String,
    httpClient: ExchangeRateHttpClient
): Double {
    val response = httpClient.get(
        "$frankfurterApiBaseUrl/latest?from=$fromCurrency&to=$toCurrency"
    )
    if (response.statusCode !in 200..299) {
        throw IllegalStateException(
            "Failed to load Frankfurter exchange rate (${response.statusCode})."
        )
    }

    return parseFrankfurterRate(
        payload = response.body,
        toCurrency = toCurrency
    )
}

private suspend fun loadFallbackRate(
    fromCurrency: String,
    toCurrency: String,
    httpClient: ExchangeRateHttpClient
): Double {
    val response = httpClient.get(
        "$fallbackExchangeApiBaseUrl/${fromCurrency.lowercase(Locale.US)}/${toCurrency.lowercase(Locale.US)}.json"
    )
    if (response.statusCode !in 200..299) {
        throw IllegalStateException(
            "Failed to load exchange rate (${response.statusCode})."
        )
    }

    return parseFallbackRate(
        payload = response.body,
        fromCurrency = fromCurrency,
        toCurrency = toCurrency
    )
}

private fun parseFrankfurterRate(payload: String, toCurrency: String): Double {
    val root = json.parseToJsonElement(payload).jsonObject
    val rates = root["rates"]?.jsonObject
        ?: throw IllegalStateException("Frankfurter exchange rates missing.")
    val rate = rates[toCurrency.uppercase(Locale.US)]?.jsonPrimitive?.doubleOrNull
        ?: throw IllegalStateException("Frankfurter rate not found for $toCurrency.")

    if (!rate.isFinite()) {
        throw IllegalStateException("Frankfurter returned a non-finite exchange rate.")
    }

    return rate
}

private fun parseFallbackRate(
    payload: String,
    fromCurrency: String,
    toCurrency: String
): Double {
    val root = json.parseToJsonElement(payload).jsonObject
    val normalizedFrom = fromCurrency.lowercase(Locale.US)
    val normalizedTo = toCurrency.lowercase(Locale.US)

    val directRate = root[normalizedTo]?.jsonPrimitive?.doubleOrNull
    if (directRate != null && directRate.isFinite()) {
        return directRate
    }

    val nestedRate = root[normalizedFrom]
        ?.jsonObject
        ?.get(normalizedTo)
        ?.jsonPrimitive
        ?.doubleOrNull
    if (nestedRate != null && nestedRate.isFinite()) {
        return nestedRate
    }

    throw IllegalStateException("Exchange rate not found for $fromCurrency/$toCurrency.")
}

private fun countryCodeFromLocaleTag(localeTag: String): String? {
    val normalizedLocaleTag = localeTag.replace('_', '-')
    val match = Regex("-([A-Za-z]{2})(?:-|$)").find(normalizedLocaleTag)
    return normalizeCountryCode(match?.groupValues?.getOrNull(1))
}

private fun countryCodeFromTimezone(timezoneId: String): String? {
    val prefix = timezoneId.substringBefore('/').trim()
    return normalizeCountryCode(prefix)
}

private fun normalizeCountryName(value: String?): String = value
    .orEmpty()
    .trim()
    .lowercase(Locale.US)
    .replace(Regex("[^a-z]"), "")

private val regionalCountryOptions: List<RegionalCountryOption> by lazy {
    Locale.getISOCountries()
        .mapNotNull { code ->
            val normalizedCode = normalizeCountryCode(code) ?: return@mapNotNull null
            val name = Locale("", normalizedCode).getDisplayCountry(Locale.ENGLISH).trim()
            if (name.isEmpty()) {
                return@mapNotNull null
            }

            RegionalCountryOption(
                code = normalizedCode,
                name = name,
                currency = getCurrencyForCountry(normalizedCode)
            )
        }
        .sortedBy { option -> option.name }
}

private val countryOptionsByCode: Map<String, RegionalCountryOption> by lazy {
    regionalCountryOptions.associateBy { option -> option.code }
}

private val countryOptionsByName: Map<String, RegionalCountryOption> by lazy {
    regionalCountryOptions.associateBy { option -> normalizeCountryName(option.name) }
}

private val supportedCurrencyCodes: List<String> by lazy {
    Currency.getAvailableCurrencies()
        .map { currency -> currency.currencyCode }
        .distinct()
        .sorted()
}

private val json = Json {
    ignoreUnknownKeys = true
}

private val exchangeRateCache: MutableMap<String, Double> = mutableMapOf()
private val exchangeRateCacheMutex = Mutex()
