import { useMaterial3Theme } from "@pchmn/expo-material3-theme";
import {
    DarkTheme,
    DefaultTheme,
    ThemeProvider,
} from "@react-navigation/native";
import { Stack, useRouter } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect, useMemo } from "react";
import "react-native-gesture-handler";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { MD3DarkTheme, MD3LightTheme, PaperProvider } from "react-native-paper";
import { en, registerTranslation } from "react-native-paper-dates";
import "react-native-reanimated";

import { AquaptProvider, useAquapt } from "@/context/aquapt-context";
import { useColorScheme } from "@/hooks/use-color-scheme";
import {
    clearDailyReminderSchedule,
    ensureReminderPermissions,
    registerNotificationResponseHandler,
    routeFromLastNotification,
    scheduleDailyReminder,
} from "@/services/notifications";
import { countDueTasks } from "@/services/scheduling";

export const unstable_settings = {
  anchor: "(tabs)",
};

registerTranslation("en", en);

const KEEP_SOURCE_COLOR = "#F9AB00";

void SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <AquaptProvider>
        <ThemedRoot />
      </AquaptProvider>
    </GestureHandlerRootView>
  );
}

function ThemedRoot() {
  const { isHydrated, settings } = useAquapt();
  const colorScheme = useColorScheme();
  const { theme: materialTheme } = useMaterial3Theme({
    sourceColor: KEEP_SOURCE_COLOR,
    fallbackSourceColor: KEEP_SOURCE_COLOR,
    colorFidelity: true,
  });

  useEffect(() => {
    if (!isHydrated) {
      return;
    }

    void SplashScreen.hideAsync();
  }, [isHydrated]);

  if (!isHydrated) {
    return null;
  }

  const resolvedColorScheme =
    settings.themePreference && settings.themePreference !== "system"
      ? settings.themePreference
      : (colorScheme ?? "light");
  const isDark = resolvedColorScheme === "dark";
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

  const paperTheme = useMemo(() => {
    if (isDark) {
      return {
        ...MD3DarkTheme,
        roundness: 24,
        colors: {
          ...materialTheme.dark,
          primary: "#FFD54F",
          secondary: "#A5D6A7",
          tertiary: "#80CBC4",
          background: "#10120F",
          surface: "#1A1D19",
          surfaceVariant: "#242924",
        },
      };
    }

    return {
      ...MD3LightTheme,
      roundness: 24,
      colors: {
        ...materialTheme.light,
        primary: "#F9AB00",
        secondary: "#81C784",
        tertiary: "#4DB6AC",
        background: "#FFFDF7",
        surface: "#FFFFFF",
        surfaceVariant: "#FFF3D6",
      },
    };
  }, [isDark, materialTheme]);

  return (
    <ThemeProvider value={navigationTheme}>
      <PaperProvider theme={paperTheme}>
        <AppShell />
      </PaperProvider>
      <StatusBar style={resolvedColorScheme === "dark" ? "light" : "dark"} />
    </ThemeProvider>
  );
}

function AppShell() {
  const router = useRouter();
  const { settings, taskTemplates, taskExecutions } = useAquapt();

  useEffect(() => {
    const removeListener = registerNotificationResponseHandler((route) => {
      router.push(route as never);
    });

    void routeFromLastNotification((route) => {
      router.push(route as never);
    });

    return () => {
      removeListener();
    };
  }, [router]);

  useEffect(() => {
    const syncReminderSchedule = async () => {
      if (!settings.notificationsEnabled) {
        await clearDailyReminderSchedule();
        return;
      }

      const granted = await ensureReminderPermissions();
      if (!granted) {
        return;
      }

      const dueCount = countDueTasks(taskTemplates, taskExecutions, new Date());
      await scheduleDailyReminder(settings.reminderHour ?? 8, dueCount);
    };

    void syncReminderSchedule();
  }, [
    settings.notificationsEnabled,
    settings.reminderHour,
    taskTemplates,
    taskExecutions,
  ]);

  return (
    <Stack>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="entity/[kind]/[id]" options={{ title: "Details" }} />
      <Stack.Screen
        name="modal"
        options={{ presentation: "modal", title: "Details" }}
      />
    </Stack>
  );
}
