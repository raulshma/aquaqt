import { router } from "expo-router";
import { useState } from "react";
import { View } from "react-native";
import { Button, Chip, List, Text, useTheme } from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import {
    clearDailyReminderSchedule,
    ensureReminderPermissions,
    scheduleDailyReminders,
} from "@/services/notifications";
import { countDueTasks } from "@/services/scheduling";

const AVAILABLE_HOURS = Array.from({ length: 24 }, (_, i) => i);

export default function RemindersSettingsScreen() {
  const theme = useTheme();
  const {
    settings,
    taskTemplates,
    taskExecutions,
    reminderGroups,
    saveReminderSettings,
  } = useAquapt();
  const [remindersEnabled, setRemindersEnabled] = useState(
    settings.notificationsEnabled ?? false,
  );
  const [selectedHours, setSelectedHours] = useState<number[]>(
    settings.reminderHours ?? (settings.reminderHour !== undefined ? [settings.reminderHour] : [8]),
  );
  const [status, setStatus] = useState<string | null>(null);
  const dueTaskCount = countDueTasks(taskTemplates, taskExecutions);

  const toggleHour = (hour: number) => {
    setSelectedHours((prev) =>
      prev.includes(hour)
        ? prev.filter((h) => h !== hour)
        : [...prev, hour].sort((a, b) => a - b),
    );
  };

  const saveReminderPreferences = async () => {
    if (remindersEnabled && selectedHours.length === 0) {
      setStatus("Select at least one hour for reminders.");
      return;
    }

    if (remindersEnabled) {
      const granted = await ensureReminderPermissions();
      if (!granted) {
        setStatus(
          "Notifications permission was not granted. Enable it in system settings.",
        );
        return;
      }

      await scheduleDailyReminders(selectedHours, dueTaskCount);
      const label =
        selectedHours.length === 1
          ? `Daily reminder scheduled for ${String(selectedHours[0]).padStart(2, "0")}:00.`
          : `Daily reminders scheduled at ${selectedHours.map((h) => `${String(h).padStart(2, "0")}:00`).join(", ")}.`;
      setStatus(label);
    } else {
      await clearDailyReminderSchedule();
      setStatus("Daily reminders disabled.");
    }

    saveReminderSettings({
      notificationsEnabled: remindersEnabled,
      reminderHour: selectedHours[0] ?? 8,
      reminderHours: selectedHours,
    });
  };

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="Task reminders"
        subtitle="Schedule one or more daily notifications with a quick link to due tasks."
        tone="primary"
        chips={
          <>
            <Chip compact icon="bell">
              {remindersEnabled ? "Enabled" : "Disabled"}
            </Chip>
            <Chip compact icon="calendar-clock">
              {dueTaskCount} due tasks
            </Chip>
            <Chip compact icon="account-group">
              {reminderGroups.length} group{reminderGroups.length !== 1 ? "s" : ""}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Default schedule"
        description={
          remindersEnabled
            ? "These hours apply to tasks with no per-task or group override."
            : "Enable reminders to configure the schedule."
        }
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

        {remindersEnabled && (
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
            {AVAILABLE_HOURS.map((hour) => (
              <Chip
                key={hour}
                selected={selectedHours.includes(hour)}
                onPress={() => toggleHour(hour)}
                compact
              >
                {`${String(hour).padStart(2, "0")}:00`}
              </Chip>
            ))}
          </View>
        )}

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

      <DashboardSection
        title="Reminder groups"
        description="Group tasks under named schedules for per-group reminder times."
      >
        <List.Item
          title="Manage reminder groups"
          description={`${reminderGroups.length} group${reminderGroups.length !== 1 ? "s" : ""} configured`}
          left={(props) => <List.Icon {...props} icon="account-group" />}
          right={(props) => <List.Icon {...props} icon="chevron-right" />}
          onPress={() => router.push("/settings/reminder-groups")}
        />
      </DashboardSection>
    </DashboardScrollView>
  );
}
