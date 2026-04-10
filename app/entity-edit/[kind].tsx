import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    HelperText,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";
import { DatePickerModal, TimePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    DashboardHero,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import type {
  EditKind,
  TaskFrequency,
  TaskTemplate,
} from "@/types/aquapt";

function getSingleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

const FREQUENCY_OPTIONS: { label: string; value: TaskFrequency }[] = [
  { label: "Daily", value: "daily" },
  { label: "Weekly", value: "weekly" },
  { label: "Bi-weekly", value: "bi-weekly" },
  { label: "Monthly", value: "monthly" },
];

const CATEGORY_OPTIONS: {
  label: string;
  value: TaskTemplate["category"];
}[] = [
  { label: "Maintenance", value: "maintenance" },
  { label: "Feeding", value: "feeding" },
];

export default function EntityEditScreen() {
  const router = useRouter();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{
    kind?: string;
    id?: string;
  }>();

  const {
    aquariums,
    taskTemplates,
    taskExecutions,
    reminderGroups,
    editTaskTemplate,
    deleteTaskTemplate,
    editTaskExecution,
    deleteTaskExecution,
  } = useAquapt();

  const kindParam = getSingleParam(params.kind) as EditKind | undefined;
  const entityId = getSingleParam(params.id) ?? "";

  const taskTemplate = useMemo(
    () => taskTemplates.find((t) => t.id === entityId),
    [taskTemplates, entityId],
  );

  const taskExecution = useMemo(
    () => taskExecutions.find((e) => e.id === entityId),
    [taskExecutions, entityId],
  );

  // Task template edit state
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [frequency, setFrequency] = useState<TaskFrequency>("weekly");
  const [category, setCategory] = useState<TaskTemplate["category"]>(
    "maintenance",
  );
  const [aquariumIds, setAquariumIds] = useState<string[]>([]);
  const [timesPerDay, setTimesPerDay] = useState(1);
  const [startDate, setStartDate] = useState("");
  const [taskReminderHours, setTaskReminderHours] = useState<number[]>([]);
  const [taskReminderGroupId, setTaskReminderGroupId] = useState<string | undefined>(undefined);

  // Task execution edit state
  const [completedAt, setCompletedAt] = useState("");
  const [executionNote, setExecutionNote] = useState("");

  // Date/time picker state
  const [isDatePickerOpen, setDatePickerOpen] = useState(false);
  const [isTimePickerOpen, setTimePickerOpen] = useState(false);
  const [dateTimeDraft, setDateTimeDraft] = useState(new Date());

  // Delete confirmation
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [errorText, setErrorText] = useState("");

  useEffect(() => {
    if (kindParam === "task-template" && taskTemplate) {
      setTitle(taskTemplate.title);
      setDescription(taskTemplate.description ?? "");
      setFrequency(taskTemplate.frequency);
      setCategory(taskTemplate.category ?? "maintenance");
      setAquariumIds(taskTemplate.aquariumIds);
      setTimesPerDay(taskTemplate.timesPerDay ?? 1);
      setStartDate(taskTemplate.startDate ?? "");
      setTaskReminderHours(taskTemplate.reminderHours ?? []);
      setTaskReminderGroupId(taskTemplate.reminderGroupId);
    } else if (kindParam === "task-execution" && taskExecution) {
      setCompletedAt(taskExecution.completedAt);
      setExecutionNote(taskExecution.note ?? "");
    }
  }, [kindParam, taskTemplate, taskExecution]);

  if (
    !kindParam ||
    (kindParam !== "task-template" && kindParam !== "task-execution") ||
    (kindParam === "task-template" && !taskTemplate) ||
    (kindParam === "task-execution" && !taskExecution)
  ) {
    return (
      <View style={styles.screen}>
        <Stack.Screen options={{ headerShown: false }} />
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={[
            styles.scrollContent,
            { paddingTop: 16 + insets.top, paddingBottom: 24 + insets.bottom },
          ]}
        >
          <DashboardHero
            title="Invalid edit action"
            subtitle="The record could not be found or the edit type is not supported."
            tone="error"
          />
        </ScrollView>
      </View>
    );
  }

  const handleSubmit = () => {
    setErrorText("");

    if (kindParam === "task-template") {
      if (!title.trim()) {
        setErrorText("Title is required.");
        return;
      }
      if (aquariumIds.length === 0) {
        setErrorText("At least one aquarium must be selected.");
        return;
      }

      editTaskTemplate(taskTemplate!.id, {
        title: title.trim(),
        description: description.trim() || undefined,
        category,
        frequency,
        aquariumIds,
        timesPerDay: frequency === "daily" ? timesPerDay : undefined,
        startDate: startDate || undefined,
        reminderHours: taskReminderHours.length > 0 ? taskReminderHours : undefined,
        reminderGroupId: taskReminderGroupId,
      });
      router.back();
      return;
    }

    if (kindParam === "task-execution") {
      if (!completedAt.trim()) {
        setErrorText("Completion date is required.");
        return;
      }

      editTaskExecution(taskExecution!.id, {
        completedAt: completedAt.trim(),
        note: executionNote.trim() || undefined,
      });
      router.back();
      return;
    }
  };

  const handleDelete = () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }

    if (kindParam === "task-template") {
      deleteTaskTemplate(taskTemplate!.id);
    } else if (kindParam === "task-execution") {
      deleteTaskExecution(taskExecution!.id);
    }

    router.back();
  };

  const formTitle =
    kindParam === "task-template"
      ? `Edit: ${taskTemplate?.title ?? "Task"}`
      : "Edit execution";

  const aquariumName =
    kindParam === "task-execution"
      ? (aquariums.find((aq) => aq.id === taskExecution?.aquariumId)?.name ??
        "Unknown tank")
      : undefined;

  const parentTaskName =
    kindParam === "task-execution"
      ? (taskTemplates.find((t) => t.id === taskExecution?.taskTemplateId)
          ?.title ?? "Unknown task")
      : undefined;

  const formatDateTimeLabel = (value: string) => {
    if (!value.trim()) return "Select date & time";
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime())
      ? "Select date & time"
      : parsed.toLocaleString();
  };

  const openDateTimePicker = () => {
    setDateTimeDraft(completedAt ? new Date(completedAt) : new Date());
    setDatePickerOpen(true);
  };

  const closeDateTimePicker = () => {
    setDatePickerOpen(false);
    setTimePickerOpen(false);
  };

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.scrollContent,
          { paddingTop: 16 + insets.top, paddingBottom: 144 + insets.bottom },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <DashboardHero
          title={formTitle}
          subtitle={
            kindParam === "task-execution"
              ? `${parentTaskName} • ${aquariumName}`
              : `${taskTemplate?.frequency} • ${taskTemplate?.aquariumIds.length ?? 0} tank(s)`
          }
          tone="primary"
        />

        <DashboardSection
          title="Properties"
          description="Modify the fields below and save your changes."
        >
          <Card
            mode="contained"
            style={[
              styles.card,
              { backgroundColor: theme.colors.surfaceVariant },
            ]}
          >
            <Card.Content style={styles.cardContent}>
              {kindParam === "task-template" ? (
                <>
                  <TextInput
                    label="Title *"
                    value={title}
                    onChangeText={setTitle}
                    mode="outlined"
                  />

                  <TextInput
                    label="Description"
                    value={description}
                    onChangeText={setDescription}
                    mode="outlined"
                    multiline
                    numberOfLines={3}
                    style={styles.fieldGap}
                  />

                  <View style={[styles.fieldGap, styles.fieldBlock]}>
                    <Text variant="labelLarge">Frequency</Text>
                    <View style={styles.chipRow}>
                      {FREQUENCY_OPTIONS.map((opt) => (
                        <Chip
                          key={opt.value}
                          compact
                          selected={frequency === opt.value}
                          onPress={() => setFrequency(opt.value)}
                        >
                          {opt.label}
                        </Chip>
                      ))}
                    </View>
                  </View>

                  <View style={[styles.fieldGap, styles.fieldBlock]}>
                    <Text variant="labelLarge">Category</Text>
                    <View style={styles.chipRow}>
                      {CATEGORY_OPTIONS.map((opt) => (
                        <Chip
                          key={opt.value}
                          compact
                          selected={category === opt.value}
                          onPress={() => setCategory(opt.value)}
                        >
                          {opt.label}
                        </Chip>
                      ))}
                    </View>
                  </View>

                  <View style={[styles.fieldGap, styles.fieldBlock]}>
                    <Text variant="labelLarge">Aquariums</Text>
                    <View style={styles.chipRow}>
                      {aquariums.map((aq) => {
                        const selected = aquariumIds.includes(aq.id);
                        return (
                          <Chip
                            key={aq.id}
                            compact
                            selected={selected}
                            onPress={() =>
                              setAquariumIds((prev) =>
                                selected
                                  ? prev.filter((id) => id !== aq.id)
                                  : [...prev, aq.id],
                              )
                            }
                          >
                            {aq.name}
                          </Chip>
                        );
                      })}
                    </View>
                  </View>

                  {frequency === "daily" ? (
                    <TextInput
                      label="Times per day"
                      value={String(timesPerDay)}
                      onChangeText={(val) => {
                        const num = parseInt(val, 10);
                        setTimesPerDay(Number.isNaN(num) || num < 1 ? 1 : num);
                      }}
                      mode="outlined"
                      keyboardType="numeric"
                      style={styles.fieldGap}
                    />
                  ) : null}

                  <TextInput
                    label="Start date"
                    value={startDate}
                    onChangeText={setStartDate}
                    mode="outlined"
                    placeholder="YYYY-MM-DD"
                    style={styles.fieldGap}
                  />

                  <View style={[styles.fieldGap, styles.fieldBlock]}>
                    <Text variant="labelLarge">Reminder group</Text>
                    <View style={styles.chipRow}>
                      <Chip
                        compact
                        selected={!taskReminderGroupId}
                        onPress={() => setTaskReminderGroupId(undefined)}
                      >
                        Default
                      </Chip>
                      {reminderGroups.map((group) => (
                        <Chip
                          key={group.id}
                          compact
                          selected={taskReminderGroupId === group.id}
                          onPress={() => {
                            setTaskReminderGroupId(group.id);
                            setTaskReminderHours([]);
                          }}
                        >
                          {group.name}
                        </Chip>
                      ))}
                    </View>
                  </View>

                  {!taskReminderGroupId && (
                    <View style={[styles.fieldGap, styles.fieldBlock]}>
                      <Text variant="labelLarge">Custom reminder hours</Text>
                      <View style={styles.chipRow}>
                        {Array.from({ length: 24 }, (_, i) => i).map((hour) => (
                          <Chip
                            key={hour}
                            compact
                            selected={taskReminderHours.includes(hour)}
                            onPress={() =>
                              setTaskReminderHours((prev) =>
                                prev.includes(hour)
                                  ? prev.filter((h) => h !== hour)
                                  : [...prev, hour].sort((a, b) => a - b),
                              )
                            }
                          >
                            {`${String(hour).padStart(2, "0")}:00`}
                          </Chip>
                        ))}
                      </View>
                    </View>
                  )}
                </>
              ) : (
                <>
                  <View style={styles.fieldBlock}>
                    <Text variant="labelLarge">Completed at *</Text>
                    <Button
                      mode="outlined"
                      icon="calendar-clock"
                      onPress={openDateTimePicker}
                      contentStyle={styles.dateButtonContent}
                    >
                      {formatDateTimeLabel(completedAt)}
                    </Button>
                  </View>

                  <TextInput
                    label="Note"
                    value={executionNote}
                    onChangeText={setExecutionNote}
                    mode="outlined"
                    multiline
                    numberOfLines={3}
                    style={styles.fieldGap}
                  />
                </>
              )}

              <HelperText type="error" visible={!!errorText}>
                {errorText}
              </HelperText>
            </Card.Content>
          </Card>
        </DashboardSection>

        <DashboardSection
          title="Danger zone"
          description="Permanently remove this record. This cannot be undone."
        >
          <Card
            mode="contained"
            style={[
              styles.card,
              { backgroundColor: theme.colors.errorContainer },
            ]}
          >
            <Card.Content style={styles.cardContent}>
              <Button
                mode={confirmDelete ? "contained" : "outlined"}
                icon="delete"
                onPress={handleDelete}
                buttonColor={confirmDelete ? theme.colors.error : undefined}
                textColor={
                  confirmDelete ? theme.colors.onError : theme.colors.error
                }
              >
                {confirmDelete
                  ? `Confirm: Delete this ${kindParam === "task-template" ? "task and all its history" : "execution"}`
                  : "Delete"}
              </Button>
              {confirmDelete ? (
                <Button
                  mode="text"
                  onPress={() => setConfirmDelete(false)}
                  style={styles.fieldGap}
                >
                  Cancel
                </Button>
              ) : null}
            </Card.Content>
          </Card>
        </DashboardSection>
      </ScrollView>

      {kindParam === "task-execution" ? (
        <>
          <DatePickerModal
            locale="en"
            mode="single"
            visible={isDatePickerOpen}
            date={dateTimeDraft}
            onDismiss={closeDateTimePicker}
            onConfirm={({ date }: { date: Date | undefined }) => {
              if (date) {
                setDateTimeDraft(date);
                setDatePickerOpen(false);
                setTimePickerOpen(true);
                return;
              }
              closeDateTimePicker();
            }}
          />

          <TimePickerModal
            locale="en"
            visible={isTimePickerOpen}
            onDismiss={closeDateTimePicker}
            onConfirm={({
              hours,
              minutes,
            }: {
              hours: number;
              minutes: number;
            }) => {
              const nextDate = new Date(dateTimeDraft);
              nextDate.setHours(hours);
              nextDate.setMinutes(minutes);
              nextDate.setSeconds(0);
              nextDate.setMilliseconds(0);
              setCompletedAt(nextDate.toISOString());
              closeDateTimePicker();
            }}
            hours={dateTimeDraft.getHours()}
            minutes={dateTimeDraft.getMinutes()}
          />
        </>
      ) : null}

      <View
        style={[
          styles.footer,
          {
            paddingBottom: Math.max(insets.bottom, 16),
            backgroundColor: theme.colors.surface,
            borderTopColor: theme.colors.outlineVariant,
          },
        ]}
      >
        <View style={styles.footerButtons}>
          <Button
            mode="outlined"
            onPress={() => router.back()}
            style={styles.footerButton}
          >
            Cancel
          </Button>
          <Button
            mode="contained"
            onPress={handleSubmit}
            style={styles.footerButton}
          >
            Save
          </Button>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  scrollView: { flex: 1 },
  scrollContent: {
    paddingHorizontal: 16,
    gap: 12,
  },
  card: {
    borderRadius: 24,
    marginTop: 0,
  },
  cardContent: {
    gap: 8,
  },
  fieldGap: {
    marginTop: 4,
  },
  fieldBlock: {
    gap: 6,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  dateButtonContent: {
    justifyContent: "flex-start",
  },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 16,
    paddingTop: 12,
  },
  footerButtons: {
    flexDirection: "row",
    gap: 12,
  },
  footerButton: {
    flex: 1,
  },
});
