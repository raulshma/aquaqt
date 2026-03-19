import { View } from "react-native";
import { Chip, List } from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";

export default function AppearanceSettingsScreen() {
  const { settings, saveThemePreference } = useAquapt();

  return (
    <DashboardScrollView>
      <DashboardHero
        title="Appearance"
        subtitle="Choose how Aquapt colors the app."
        tone="secondary"
        chips={
          <Chip compact icon="palette">
            {settings.themePreference ?? "system"}
          </Chip>
        }
      />

      <DashboardSection
        title="Theme mode"
        description="System mode follows your device, while light and dark stay pinned."
      >
        <View style={{ marginTop: 16 }}>
          <List.Section>
            {(
              [
                {
                  key: "system",
                  title: "System",
                  description:
                    "Follows the device theme and changes automatically.",
                },
                {
                  key: "light",
                  title: "Light",
                  description: "Always uses the brighter palette.",
                },
                {
                  key: "dark",
                  title: "Dark",
                  description: "Always uses the darker palette.",
                },
              ] as const
            ).map((option) => (
              <List.Item
                key={option.key}
                title={option.title}
                description={option.description}
                descriptionNumberOfLines={2}
                left={() => (
                  <List.Icon
                    icon={
                      (settings.themePreference ?? "system") === option.key
                        ? "check-circle"
                        : "circle-outline"
                    }
                  />
                )}
                onPress={() => saveThemePreference(option.key)}
              />
            ))}
          </List.Section>
        </View>
      </DashboardSection>
    </DashboardScrollView>
  );
}
