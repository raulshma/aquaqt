import { router } from "expo-router";
import { Chip, List } from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import { countDueTasks } from "@/services/scheduling";

const settingsSections = [
  {
    title: "Core",
    pages: [
      {
        title: "Regional defaults",
        description: "Country, currency, timezone, and formatting.",
        info: "Used for currency conversion and locale-aware labels.",
        icon: "earth",
        route: "/settings/regional",
      },
      {
        title: "Appearance",
        description: "Theme preference for light, dark, or system.",
        info: "Controls the app’s Material 3 palette.",
        icon: "theme-light-dark",
        route: "/settings/appearance",
      },
      {
        title: "Task reminders",
        description: "Daily reminder schedule and notification hour.",
        info: "Deep-links to due tasks from the notification.",
        icon: "calendar-clock",
        route: "/settings/reminders",
      },
    ],
  },
  {
    title: "Assistant",
    pages: [
      {
        title: "OpenRouter assistant",
        description: "Save your API key and choose the default model.",
        info: "Model browsing is loaded on demand to keep this screen fast.",
        icon: "brain",
        route: "/settings/assistant",
      },
      {
        title: "Assistant memory",
        description: "Review, clear, and forget semantic snippets.",
        info: "Loads after the screen settles and only when enabled.",
        icon: "brain",
        route: "/settings/memory",
      },
      {
        title: "AI workflows",
        description:
          "Contextual assistant, diagnostics, and compatibility checks.",
        info: "Heavy AI tools live here instead of on the landing page.",
        icon: "message-text",
        route: "/settings/workflows",
      },
    ],
  },
  {
    title: "Storage",
    pages: [
      {
        title: "Backup & restore",
        description: "Encrypted S3 sync plus JSON export/import.",
        info: "Keeps the bigger backup tools off the first render.",
        icon: "cloud-sync",
        route: "/settings/backup",
      },
    ],
  },
] as const;

export default function SettingsIndexScreen() {
  const { settings, taskTemplates, taskExecutions, aquariums } = useAquapt();
  const dueTaskCount = countDueTasks(taskTemplates, taskExecutions);

  return (
    <DashboardScrollView>
      <DashboardHero
        title="Settings"
        subtitle="Pick a section below. The details now live in nested pages so this tab opens quickly."
        tone="primary"
        chips={
          <>
            <Chip compact icon="fish">
              {aquariums.length} tanks
            </Chip>
            <Chip compact icon="calendar-clock">
              {dueTaskCount} due tasks
            </Chip>
            <Chip compact icon="theme-light-dark">
              {settings.themePreference ?? "system"}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Quick status"
        description="Your most-used settings are split into focused child pages. Tap one to jump straight there."
      />

      <List.Section>
        {settingsSections.map((section) => (
          <List.Section key={section.title}>
            <List.Subheader>{section.title}</List.Subheader>
            {section.pages.map((page) => (
              <List.Item
                key={page.route}
                title={page.title}
                description={`${page.description} ${page.info}`}
                descriptionNumberOfLines={2}
                left={(props) => <List.Icon {...props} icon={page.icon} />}
                right={(props) => (
                  <List.Icon {...props} icon="chevron-right" />
                )}
                onPress={() => router.push(page.route)}
              />
            ))}
          </List.Section>
        ))}
      </List.Section>
    </DashboardScrollView>
  );
}
