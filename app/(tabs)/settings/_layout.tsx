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
      <Stack.Screen name="index" options={{ title: "Settings" }} />
      <Stack.Screen name="appearance" options={{ title: "Appearance" }} />
      <Stack.Screen name="regional" options={{ title: "Regional defaults" }} />
      <Stack.Screen
        name="assistant"
        options={{ title: "OpenRouter assistant" }}
      />
      <Stack.Screen name="reminders" options={{ title: "Task reminders" }} />
      <Stack.Screen name="backup" options={{ title: "Backup & restore" }} />
      <Stack.Screen name="memory" options={{ title: "Assistant memory" }} />
      <Stack.Screen name="workflows" options={{ title: "AI workflows" }} />
    </Stack>
  );
}
