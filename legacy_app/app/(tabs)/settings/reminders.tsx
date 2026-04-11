import { MaterialCommunityIcons } from "@expo/vector-icons";
import { router } from "expo-router";
import { useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import {
  Button,
  Chip,
  List,
  Surface,
  Text,
  useTheme,
} from "react-native-paper";

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
import {
  collectDueTasksByHour,
  countDueTasks,
} from "@/services/scheduling";
import { type TaskTemplate, getFrequencyLabel } from "@/types/aquapt";

const AVAILABLE_HOURS = Array.from({ length: 24 }, (_, i) => i);

type UpcomingReminder = {
  hour: number;
  minute: number;
  label: string;
  tasks: { title: string; frequency: string; aquariumNames: string[] }[];
};

function buildUpcomingReminders(
  tasksByHour: Map<number, TaskTemplate[]>,
  maxItems: number,
  now: Date,
): UpcomingReminder[] {
  const currentHour = now.getHours();
  const sorted = Array.from(tasksByHour.entries())
    .map(([hour, tasks]) => ({ hour, tasks }))
    .sort((a, b) => {
      const distA = (a.hour - currentHour + 24) % 24;
      const distB = (b.hour - currentHour + 24) % 24;
      return distA - distB;
    });

  return sorted.slice(0, maxItems).map(({ hour, tasks }) => ({
    hour,
    minute: 0,
    label:
      hour === currentHour
        ? "Now"
        : hour > currentHour
          ? `Today at ${String(hour).padStart(2, "0")}:00`
          : `Tomorrow at ${String(hour).padStart(2, "0")}:00`,
    tasks: tasks.map((t) => ({
      title: t.title,
      frequency: getFrequencyLabel(t.frequency),
      aquariumNames: [],
    })),
  }));
}

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
    settings.reminderHours ??
      (settings.reminderHour !== undefined ? [settings.reminderHour] : [8]),
  );
  const [status, setStatus] = useState<string | null>(null);
  const now = new Date();
  const dueTaskCount = countDueTasks(taskTemplates, taskExecutions);

  const globalHours = useMemo(
    () =>
      settings.reminderHours ??
      (settings.reminderHour !== undefined
        ? [settings.reminderHour]
        : [8]),
    [settings.reminderHours, settings.reminderHour],
  );

  const tasksByHour = useMemo(
    () =>
      collectDueTasksByHour(
        taskTemplates,
        taskExecutions,
        reminderGroups,
        globalHours,
        now,
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [taskTemplates, taskExecutions, reminderGroups, globalHours],
  );

  const upcomingReminders = useMemo(
    () => buildUpcomingReminders(tasksByHour, 8, now),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [tasksByHour],
  );

  const activeReminderCount = tasksByHour.size;

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
              {dueTaskCount} due task{dueTaskCount === 1 ? "" : "s"}
            </Chip>
            <Chip compact icon="account-group">
              {reminderGroups.length} group
              {reminderGroups.length !== 1 ? "s" : ""}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Upcoming reminders"
        description={
          activeReminderCount > 0
            ? `${activeReminderCount} reminder slot${activeReminderCount === 1 ? "" : "s"} with ${dueTaskCount} due task${dueTaskCount === 1 ? "" : "s"} scheduled for today.`
            : remindersEnabled
              ? "No due tasks found for the current schedule."
              : "Enable reminders to see upcoming notifications."
        }
      >
        {upcomingReminders.length > 0 ? (
          <View style={reminderStyles.timeline}>
            {upcomingReminders.map((reminder, index) => {
              const isNext = index === 0;
              return (
                <View key={reminder.hour} style={reminderStyles.timelineItem}>
                  <View
                    style={[
                      reminderStyles.timelineTrack,
                      {
                        borderLeftColor: isNext
                          ? theme.colors.primary
                          : theme.colors.outlineVariant,
                      },
                    ]}
                  >
                    <View
                      style={[
                        reminderStyles.timelineDot,
                        {
                          backgroundColor: isNext
                            ? theme.colors.primary
                            : theme.colors.outlineVariant,
                        },
                      ]}
                    />
                  </View>
                  <Surface
                    elevation={isNext ? 1 : 0}
                    style={[
                      reminderStyles.timelineCard,
                      {
                        backgroundColor: isNext
                          ? theme.colors.primaryContainer
                          : theme.colors.surfaceVariant,
                      },
                    ]}
                  >
                    <View style={reminderStyles.timelineCardHeader}>
                      <MaterialCommunityIcons
                        name={isNext ? "bell-ring" : "bell-outline"}
                        size={18}
                        color={
                          isNext
                            ? theme.colors.onPrimaryContainer
                            : theme.colors.onSurfaceVariant
                        }
                      />
                      <Text
                        variant="labelLarge"
                        style={{
                          color: isNext
                            ? theme.colors.onPrimaryContainer
                            : theme.colors.onSurface,
                        }}
                      >
                        {reminder.label}
                      </Text>
                      {isNext ? (
                        <View
                          style={[
                            reminderStyles.nextBadge,
                            {
                              backgroundColor: theme.colors.primary,
                            },
                          ]}
                        >
                          <Text
                            variant="labelSmall"
                            style={{
                              color: theme.colors.onPrimary,
                              fontSize: 10,
                            }}
                          >
                            NEXT
                          </Text>
                        </View>
                      ) : null}
                    </View>
                    <View style={reminderStyles.taskList}>
                      {reminder.tasks.map((task) => (
                        <View key={task.title} style={reminderStyles.taskRow}>
                          <MaterialCommunityIcons
                            name="checkbox-marked-circle-outline"
                            size={14}
                            color={
                              isNext
                                ? theme.colors.onPrimaryContainer
                                : theme.colors.onSurfaceVariant
                            }
                          />
                          <Text
                            variant="bodySmall"
                            style={{
                              color: isNext
                                ? theme.colors.onPrimaryContainer
                                : theme.colors.onSurfaceVariant,
                              flex: 1,
                            }}
                            numberOfLines={1}
                          >
                            {task.title}
                          </Text>
                          <Text
                            variant="labelSmall"
                            style={{
                              color: isNext
                                ? theme.colors.onPrimaryContainer
                                : theme.colors.onSurfaceVariant,
                              opacity: 0.7,
                            }}
                          >
                            {task.frequency}
                          </Text>
                        </View>
                      ))}
                    </View>
                  </Surface>
                </View>
              );
            })}
          </View>
        ) : (
          <View style={reminderStyles.emptyTimeline}>
            <MaterialCommunityIcons
              name="bell-off-outline"
              size={32}
              color={theme.colors.onSurfaceVariant}
            />
            <Text
              variant="bodyMedium"
              style={{ color: theme.colors.onSurfaceVariant, textAlign: "center" }}
            >
              {remindersEnabled
                ? "No due tasks match your current schedule. Tasks will appear here as they become due."
                : "Enable reminders and save to see upcoming notifications here."}
            </Text>
          </View>
        )}
      </DashboardSection>

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

const reminderStyles = StyleSheet.create({
  timeline: {
    gap: 12,
    marginTop: 4,
  },
  timelineItem: {
    flexDirection: "row",
    gap: 12,
  },
  timelineTrack: {
    width: 20,
    alignItems: "center",
    borderLeftWidth: 2,
    marginLeft: 8,
  },
  timelineDot: {
    width: 10,
    height: 10,
    borderRadius: 10,
    marginTop: 14,
    marginLeft: -6,
  },
  timelineCard: {
    flex: 1,
    borderRadius: 16,
    overflow: "hidden",
  },
  timelineCardHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 4,
  },
  nextBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    marginLeft: "auto",
  },
  taskList: {
    paddingHorizontal: 14,
    paddingBottom: 12,
    gap: 4,
  },
  taskRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  emptyTimeline: {
    alignItems: "center",
    gap: 10,
    paddingVertical: 20,
  },
});
