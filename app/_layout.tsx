import { useMaterial3Theme } from "@pchmn/expo-material3-theme";
import {
  DarkTheme,
  DefaultTheme,
  ThemeProvider,
} from "@react-navigation/native";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useMemo } from "react";
import { MD3DarkTheme, MD3LightTheme, PaperProvider } from "react-native-paper";
import { en, registerTranslation } from "react-native-paper-dates";
import "react-native-reanimated";

import { AquaptProvider } from "@/context/aquapt-context";
import { useColorScheme } from "@/hooks/use-color-scheme";

export const unstable_settings = {
  anchor: "(tabs)",
};

registerTranslation("en", en);

const VIBRANT_SOURCE_COLOR = "#FF2D8F";

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const { theme: materialTheme } = useMaterial3Theme({
    sourceColor: VIBRANT_SOURCE_COLOR,
    fallbackSourceColor: VIBRANT_SOURCE_COLOR,
    colorFidelity: true,
  });

  const isDark = colorScheme === "dark";
  const materialColors = isDark ? materialTheme.dark : materialTheme.light;

  const navigationTheme = useMemo(
    () => ({
      ...(isDark ? DarkTheme : DefaultTheme),
      colors: {
        ...(isDark ? DarkTheme : DefaultTheme).colors,
        primary: materialColors.primary,
        background: materialColors.background,
        card: materialColors.surface,
        text: materialColors.onBackground,
        border: materialColors.outlineVariant ?? materialColors.outline,
        notification: materialColors.error,
      },
    }),
    [isDark, materialColors],
  );

  const paperTheme = useMemo(
    () =>
      isDark
        ? { ...MD3DarkTheme, colors: materialTheme.dark }
        : { ...MD3LightTheme, colors: materialTheme.light },
    [isDark, materialTheme],
  );

  return (
    <ThemeProvider value={navigationTheme}>
      <PaperProvider theme={paperTheme}>
        <AquaptProvider>
          <Stack>
            <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
            <Stack.Screen
              name="modal"
              options={{ presentation: "modal", title: "Details" }}
            />
          </Stack>
        </AquaptProvider>
      </PaperProvider>
      <StatusBar style={colorScheme === "dark" ? "light" : "dark"} />
    </ThemeProvider>
  );
}
