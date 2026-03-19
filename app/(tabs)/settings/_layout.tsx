import { Stack } from "expo-router";
import { useTheme } from "react-native-paper";

export default function SettingsLayout() {
  const theme = useTheme();

  return (
    <Stack
      screenOptions={{
        headerTitleAlign: "center",
        headerStyle: {
          backgroundColor: theme.colors.surface,
        },
        headerTintColor: theme.colors.onSurface,
        headerShadowVisible: false,
        contentStyle: {
          backgroundColor: theme.colors.background,
        },
      }}
    >
      <Stack.Screen
        name="index"
        options={{ headerShown: false, title: "Settings" }}
      />
      <Stack.Screen
        name="appearance"
        options={{ headerShown: false, title: "Appearance" }}
      />
      <Stack.Screen
        name="regional"
        options={{ headerShown: false, title: "Regional defaults" }}
      />
      <Stack.Screen
        name="assistant"
        options={{ headerShown: false, title: "OpenRouter assistant" }}
      />
      <Stack.Screen
        name="reminders"
        options={{ headerShown: false, title: "Task reminders" }}
      />
      <Stack.Screen
        name="backup"
        options={{ headerShown: false, title: "Backup & restore" }}
      />
      <Stack.Screen
        name="memory"
        options={{ headerShown: false, title: "Assistant memory" }}
      />
      <Stack.Screen
        name="workflows"
        options={{ headerShown: false, title: "AI workflows" }}
      />
    </Stack>
  );
}
