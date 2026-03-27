import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import { useAquapt } from "@/context/aquapt-context";
import { withAlpha } from "@/constants/theme";
import {
  convertCurrencyAmount,
  formatCurrencyAmount,
} from "@/services/localization";
import type { AssistantResponseTelemetry } from "@/types/assistant";
import { formatNumber, formatMilliseconds } from "@/utils/assistant-constants";

interface AssistantTelemetryProps {
  telemetry: AssistantResponseTelemetry;
}

export function AssistantTelemetry({ telemetry }: AssistantTelemetryProps) {
  const { settings } = useAquapt();
  const theme = useTheme();
  const [localizedCost, setLocalizedCost] = useState("—");

  useEffect(() => {
    let isCancelled = false;
    const currencyCode = settings.defaultCurrency ?? "USD";
    const locale = settings.defaultLocale;

    if (
      typeof telemetry.cost !== "number" ||
      !Number.isFinite(telemetry.cost)
    ) {
      setLocalizedCost("—");
      return () => {
        isCancelled = true;
      };
    }
    const cost = telemetry.cost;

    const formatCost = (value: number, targetCurrencyCode: string) =>
      formatCurrencyAmount(value, targetCurrencyCode, locale, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 6,
      });

    if (currencyCode === "USD") {
      setLocalizedCost(formatCost(cost, currencyCode));
      return () => {
        isCancelled = true;
      };
    }

    setLocalizedCost(formatCost(cost, "USD"));

    void convertCurrencyAmount(cost, "USD", currencyCode)
      .then((convertedCost) => {
        if (!isCancelled) {
          setLocalizedCost(formatCost(convertedCost, currencyCode));
        }
      })
      .catch(() => {
        if (!isCancelled) {
          setLocalizedCost(formatCost(cost, "USD"));
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [settings.defaultCurrency, settings.defaultLocale, telemetry.cost]);

  const rows = [
    `Provider: ${telemetry.providerName ?? "—"}`,
    `Model: ${telemetry.model ?? "—"}`,
    `Tokens: ${formatNumber(telemetry.promptTokens)} in · ${formatNumber(telemetry.completionTokens)} out · ${formatNumber(telemetry.totalTokens)} total`,
    `Throughput: ${formatNumber(telemetry.throughputTokensPerSecond, 1)} tok/s · ${formatNumber(telemetry.throughputCharsPerSecond, 1)} ch/s`,
    `Latency: ${formatMilliseconds(telemetry.latencyMs)} · Elapsed: ${formatMilliseconds(telemetry.elapsedMs)}`,
    `Cost: ${localizedCost} · Finish: ${telemetry.finishReason ?? "—"}`,
  ];

  return (
    <View style={[styles.wrap, { backgroundColor: withAlpha(theme.colors.onSurface, 0.06) }]}>
      <Text variant="labelSmall" style={styles.label}>
        AI runtime metadata
      </Text>
      {rows.map((row) => (
        <Text key={row} variant="labelSmall" style={styles.text}>
          {row}
        </Text>
      ))}
      {telemetry.generationId ? (
        <Text variant="labelSmall" style={styles.text}>
          Generation: {telemetry.generationId}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginTop: 8,
    borderRadius: 10,
    padding: 8,
    gap: 1,
  },
  label: {
    fontWeight: "700",
    opacity: 0.8,
    marginBottom: 2,
  },
  text: {
    opacity: 0.75,
  },
});
