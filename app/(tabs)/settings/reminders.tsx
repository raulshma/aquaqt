import { useState } from "react";
import { View } from "react-native";
import { Button, Chip, Text, useTheme } from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import {
    clearDailyReminderSchedule,
    ensureReminderPermissions,
    scheduleDailyReminder,
} from "@/services/notifications";
import { countDueTasks } from "@/services/scheduling";

export default function RemindersSettingsScreen() {
  const theme = useTheme();
  const { settings, taskTemplates, taskExecutions, saveReminderSettings } =
    useAquapt();
  const [remindersEnabled, setRemindersEnabled] = useState(
    settings.notificationsEnabled ?? false,
  );
  const [reminderHour, setReminderHour] = useState(settings.reminderHour ?? 8);
  const [status, setStatus] = useState<string | null>(null);
  const dueTaskCount = countDueTasks(taskTemplates, taskExecutions);

  const saveReminderPreferences = async () => {
    const normalizedHour = Math.min(23, Math.max(0, reminderHour));

    if (remindersEnabled) {
      const granted = await ensureReminderPermissions();
      if (!granted) {
        setStatus(
          "Notifications permission was not granted. Enable it in system settings.",
        );
        return;
      }

      await scheduleDailyReminder(normalizedHour, dueTaskCount);
      setStatus(
        `Daily reminder scheduled for ${String(normalizedHour).padStart(2, "0")}:00.`,
      );
    } else {
      await clearDailyReminderSchedule();
      setStatus("Daily reminders disabled.");
    }

    saveReminderSettings({
      notificationsEnabled: remindersEnabled,
      reminderHour: normalizedHour,
    });
  };

  return (
    <DashboardScrollView>
      <DashboardHero
        title="Task reminders"
        subtitle="Daily notifications with a quick deep link to due tasks."
        tone="primary"
        chips={
          <>
            <Chip compact icon="bell">
              {remindersEnabled ? "Enabled" : "Disabled"}
            </Chip>
            <Chip compact icon="calendar-clock">
              {dueTaskCount} due tasks
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Schedule"
        description="Pick one hour per day for the reminder notification."
      >
        <View
          style={{
            flexDirection: "row",
            gap: 8,
            marginTop: 16,
            marginBottom: 12,
          }}
        >
          <Chip
            selected={remindersEnabled}
            onPress={() => setRemindersEnabled(true)}
          >
            Enabled
          </Chip>
          <Chip
            selected={!remindersEnabled}
            onPress={() => setRemindersEnabled(false)}
          >
            Disabled
          </Chip>
        </View>

        <ScrollableSegmentedButtons
          value={String(reminderHour)}
          onValueChange={(value) => setReminderHour(Number(value))}
          buttons={[6, 7, 8, 9, 10, 12, 14, 18, 20, 22].map((hour) => ({
            label: `${String(hour).padStart(2, "0")}:00`,
            value: String(hour),
          }))}
        />

        <Button
          mode="contained-tonal"
          onPress={() => {
            void saveReminderPreferences();
          }}
          style={{ marginTop: 16, alignSelf: "flex-start" }}
        >
          Save reminder settings
        </Button>

        <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 12 }}>
          Current due tasks snapshot: {dueTaskCount}
        </Text>
        {status ? (
          <Text
            variant="bodySmall"
            style={{ marginTop: 8, color: theme.colors.primary }}
          >
            {status}
          </Text>
        ) : null}
      </DashboardSection>
    </DashboardScrollView>
  );
}
