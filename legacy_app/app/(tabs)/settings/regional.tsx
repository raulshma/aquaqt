import { useEffect, useMemo, useState } from "react";
import { View } from "react-native";
import { Button, Chip, Text, TextInput, useTheme } from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import {
    convertCurrencyAmount,
    findCountry,
    formatCurrencyAmount,
    listRegionalCountryOptions,
    listSupportedCurrencyCodes,
} from "@/services/localization";

export default function RegionalSettingsScreen() {
  const theme = useTheme();
  const { settings, saveRegionalPreferences, resetRegionalPreferences } =
    useAquapt();
  const [countryOverride, setCountryOverride] = useState(
    settings.defaultCountryName ?? settings.defaultCountryCode ?? "",
  );
  const [currencyOverride, setCurrencyOverride] = useState(
    settings.defaultCurrency ?? "USD",
  );
  const [exchangeRatePreview, setExchangeRatePreview] = useState("Loading...");
  const [countryPickerQuery, setCountryPickerQuery] = useState("");
  const [currencyPickerQuery, setCurrencyPickerQuery] = useState("");
  const [isCountrySheetVisible, setCountrySheetVisible] = useState(false);
  const [isCurrencySheetVisible, setCurrencySheetVisible] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const countryOptions = useMemo(() => listRegionalCountryOptions(), []);
  const currencyOptions = useMemo(() => listSupportedCurrencyCodes(), []);

  const selectedCountry = useMemo(() => {
    return (
      findCountry(countryOverride) ??
      findCountry(settings.defaultCountryCode) ??
      null
    );
  }, [countryOverride, settings.defaultCountryCode]);

  const selectedCountryLabel = selectedCountry
    ? `${selectedCountry.name} (${selectedCountry.id})`
    : "Select country";

  const selectedCurrencyLabel = useMemo(() => {
    const selected = currencyOverride.trim().toUpperCase();
    if (!selected) {
      return "Select currency";
    }

    return currencyOptions.includes(selected)
      ? selected
      : `${selected} (custom)`;
  }, [currencyOptions, currencyOverride]);

  const filteredCountryOptions = useMemo(() => {
    const query = countryPickerQuery.trim().toLowerCase();
    if (!query) {
      return countryOptions;
    }

    return countryOptions.filter((option) => {
      const haystack =
        `${option.name} ${option.code} ${option.currency}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [countryOptions, countryPickerQuery]);

  const filteredCurrencyOptions = useMemo(() => {
    const query = currencyPickerQuery.trim().toLowerCase();
    if (!query) {
      return currencyOptions;
    }

    return currencyOptions.filter((code) => code.toLowerCase().includes(query));
  }, [currencyOptions, currencyPickerQuery]);

  useEffect(() => {
    let cancelled = false;
    const currencyCode = settings.defaultCurrency ?? "USD";
    const locale = settings.defaultLocale;

    const formatRate = (value: number, targetCurrencyCode: string) =>
      formatCurrencyAmount(value, targetCurrencyCode, locale, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
      });

    if (currencyCode === "USD") {
      setExchangeRatePreview(`1 USD = ${formatRate(1, "USD")}`);
      return () => {
        cancelled = true;
      };
    }

    setExchangeRatePreview("Loading...");

    void convertCurrencyAmount(1, "USD", currencyCode)
      .then((convertedValue) => {
        if (!cancelled) {
          setExchangeRatePreview(
            `1 USD ~= ${formatRate(convertedValue, currencyCode)}`,
          );
        }
      })
      .catch(() => {
        if (!cancelled) {
          setExchangeRatePreview("Live exchange rate unavailable");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [settings.defaultCurrency, settings.defaultLocale]);

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="Regional defaults"
        subtitle="Country, currency, timezone, and money formatting."
        tone="tertiary"
        chips={
          <>
            <Chip compact icon="earth">
              {settings.defaultCountryName ??
                settings.defaultCountryCode ??
                "Unknown"}
            </Chip>
            <Chip compact icon="cash-multiple">
              {settings.defaultCurrency ?? "USD"}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Current defaults"
        description="Country, timezone, and money formatting in your current profile."
      >
        <Text variant="bodyMedium" style={{ marginTop: 8 }}>
          Timezone: {settings.defaultTimezone ?? "UTC"}
        </Text>
        <Text variant="bodyMedium">
          Country:{" "}
          {settings.defaultCountryName ??
            settings.defaultCountryCode ??
            "Unknown"}
        </Text>
        <Text variant="bodyMedium">
          Currency: {settings.defaultCurrency ?? "USD"}
        </Text>
        <Text variant="bodyMedium">{exchangeRatePreview}</Text>
        <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 8 }}>
          Format preview:{" "}
          {formatCurrencyAmount(
            1234.56,
            settings.defaultCurrency ?? "USD",
            settings.defaultLocale,
          )}
        </Text>
        <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 8 }}>
          Choose a country or currency to keep tank and asset costs consistent.
        </Text>

        <View style={{ gap: 10, marginTop: 16 }}>
          <Button
            mode="outlined"
            icon="earth"
            onPress={() => setCountrySheetVisible(true)}
          >
            {`Country override: ${selectedCountryLabel}`}
          </Button>
          <Button
            mode="outlined"
            icon="cash-multiple"
            onPress={() => setCurrencySheetVisible(true)}
          >
            {`Currency override: ${selectedCurrencyLabel}`}
          </Button>
          <View style={{ flexDirection: "row", gap: 10 }}>
            <Button
              mode="contained-tonal"
              onPress={() => {
                const result = saveRegionalPreferences({
                  country: countryOverride,
                  currency: currencyOverride,
                });
                setStatus(result.message);
              }}
            >
              Save regional override
            </Button>
            <Button
              mode="outlined"
              onPress={() => {
                resetRegionalPreferences();
                setStatus("Regional settings reset to your device defaults.");
              }}
            >
              Use device defaults
            </Button>
          </View>
          {status ? (
            <Text variant="bodySmall" style={{ opacity: 0.8 }}>
              {status}
            </Text>
          ) : null}
        </View>
      </DashboardSection>

      <BottomSheet
        visible={isCountrySheetVisible}
        onDismiss={() => setCountrySheetVisible(false)}
        title="Choose country override"
      >
        <TextInput
          mode="outlined"
          label="Search country, code, or currency"
          value={countryPickerQuery}
          onChangeText={setCountryPickerQuery}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <Text variant="bodySmall" style={{ opacity: 0.75 }}>
          Showing {filteredCountryOptions.length} countries
        </Text>
        <View style={{ gap: 6 }}>
          {filteredCountryOptions.slice(0, 100).map((option) => (
            <Button
              key={option.code}
              mode={
                countryOverride === option.code ? "contained-tonal" : "text"
              }
              onPress={() => {
                setCountryOverride(option.code);
                if (!currencyOverride.trim()) {
                  setCurrencyOverride(option.currency);
                }
                setCountrySheetVisible(false);
              }}
            >
              {`${option.name} (${option.code}) • ${option.currency}`}
            </Button>
          ))}
        </View>
      </BottomSheet>

      <BottomSheet
        visible={isCurrencySheetVisible}
        onDismiss={() => setCurrencySheetVisible(false)}
        title="Choose currency override"
      >
        <TextInput
          mode="outlined"
          label="Search currency code"
          value={currencyPickerQuery}
          onChangeText={setCurrencyPickerQuery}
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={3}
        />
        <Text variant="bodySmall" style={{ opacity: 0.75 }}>
          Showing {filteredCurrencyOptions.length} currencies
        </Text>
        <View style={{ gap: 6 }}>
          {filteredCurrencyOptions.map((code) => (
            <Button
              key={code}
              mode={
                currencyOverride.trim().toUpperCase() === code
                  ? "contained-tonal"
                  : "text"
              }
              onPress={() => {
                setCurrencyOverride(code);
                setCurrencySheetVisible(false);
              }}
            >
              {code}
            </Button>
          ))}
        </View>
      </BottomSheet>
    </DashboardScrollView>
  );
}
