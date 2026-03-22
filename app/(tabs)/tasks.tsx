import { useForm } from "@tanstack/react-form";
import { useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import {
  Button,
  Card,
  Chip,
  Divider,
  FAB,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { getCardTextColorForBackground } from "@/components/ui/card-tone";
import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { createEntityRef, getEntityHref } from "@/services/entity-links";
import { getCompletionsToday, isTaskDue } from "@/services/scheduling";
import { TaskFrequency } from "@/types/aquapt";

export default function TasksScreen() {
  const router = useRouter();
  const theme = useTheme();
  const {
    aquariums,
    livestock,
    taskTemplates,
    taskExecutions,
    dosingLogs,
    completeTask,
    addTaskTemplate,
    logDosing,
  } = useAquapt();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [completionNoteDraft, setCompletionNoteDraft] = useState<
    Record<string, string>
  >({});
  const [completionDateDraft, setCompletionDateDraft] = useState<
    Record<string, string>
  >({});

  const form = useForm({
    defaultValues: {
      dialogAction: "task" as "task" | "dosing",
      selectedAquariumId: aquariums[0]?.id ?? "",
      task: {
        title: "",
        description: "",
        frequency: "weekly" as TaskFrequency,
        aquariumIds: aquariums[0]?.id ? [aquariums[0].id] : [],
        timesPerDay: 1,
        startDate: new Date().toISOString().split("T")[0],
      },
      dosing: {
        product: "",
        amount: "",
        note: "",
      },
    },
    onSubmit: ({ value }) => {
      if (!value.selectedAquariumId) {
        return;
      }

      if (value.dialogAction === "task") {
        if (!value.task.title.trim()) {
          return;
        }

        addTaskTemplate({
          title: value.task.title.trim(),
          description: value.task.description.trim() || undefined,
          category: "maintenance",
          frequency: value.task.frequency,
          aquariumIds: value.task.aquariumIds.length
            ? value.task.aquariumIds
            : [value.selectedAquariumId],
          timesPerDay: value.task.timesPerDay,
          startDate: value.task.startDate,
        });

        form.setFieldValue("task.title", "");
        form.setFieldValue("task.description", "");
        form.setFieldValue("task.frequency", "weekly");
        form.setFieldValue("task.aquariumIds", [value.selectedAquariumId]);
        form.setFieldValue("task.timesPerDay", 1);
        form.setFieldValue("task.startDate", new Date().toISOString().split("T")[0]);
        setDialogOpen(false);
        return;
      }

      const amount = Number(value.dosing.amount);
      if (
        !value.dosing.product.trim() ||
        !Number.isFinite(amount) ||
        amount <= 0
      ) {
        return;
      }

      logDosing(
        value.selectedAquariumId,
        value.dosing.product.trim(),
        amount,
        value.dosing.note.trim() || undefined,
      );

      form.setFieldValue("dosing.product", "");
      form.setFieldValue("dosing.amount", "");
      form.setFieldValue("dosing.note", "");
      setDialogOpen(false);
    },
  });

  const resetDialogForm = (aquariumId: string) => {
    form.setFieldValue("dialogAction", "task");
    form.setFieldValue("selectedAquariumId", aquariumId);
    form.setFieldValue("task.title", "");
    form.setFieldValue("task.description", "");
    form.setFieldValue("task.frequency", "weekly");
    form.setFieldValue("task.aquariumIds", aquariumId ? [aquariumId] : []);
    form.setFieldValue("task.timesPerDay", 1);
    form.setFieldValue("task.startDate", new Date().toISOString().split("T")[0]);
    form.setFieldValue("dosing.product", "");
    form.setFieldValue("dosing.amount", "");
    form.setFieldValue("dosing.note", "");
  };

  useEffect(() => {
    const values = form.state.values;

    if (aquariums.length === 0) {
      if (values.selectedAquariumId !== "") {
        form.setFieldValue("selectedAquariumId", "");
      }
      if (values.task.aquariumIds.length > 0) {
        form.setFieldValue("task.aquariumIds", []);
      }
      return;
    }

    const aquariumIds = new Set(aquariums.map((aquarium) => aquarium.id));
    if (!aquariumIds.has(values.selectedAquariumId)) {
      form.setFieldValue("selectedAquariumId", aquariums[0].id);
    }

    const validAquariumIds = values.task.aquariumIds.filter((id) =>
      aquariumIds.has(id),
    );

    if (validAquariumIds.length !== values.task.aquariumIds.length) {
      form.setFieldValue(
        "task.aquariumIds",
        validAquariumIds.length > 0 ? validAquariumIds : [aquariums[0].id],
      );
    } else if (validAquariumIds.length === 0) {
      form.setFieldValue("task.aquariumIds", [aquariums[0].id]);
    }
  }, [aquariums, form]);

  const taskMatrix = useMemo(() => {
    return taskTemplates.flatMap((task) =>
      task.aquariumIds.map((aquariumId) => ({
        key: `${task.id}-${aquariumId}`,
        task,
        aquariumId,
      })),
    );
  }, [taskTemplates]);

  const dueTasks = useMemo(() => {
    return taskMatrix.filter(({ task, aquariumId }) =>
      isTaskDue(task, aquariumId, taskExecutions, new Date()),
    );
  }, [taskExecutions, taskMatrix]);

  const getAquariumName = (aquariumId: string) => {
    return (
      aquariums.find((aquarium) => aquarium.id === aquariumId)?.name ??
      "Unknown tank"
    );
  };

  const latestExecutionByTemplate = useMemo(() => {
    return taskExecutions.reduce<Record<string, string>>((acc, execution) => {
      const key = `${execution.taskTemplateId}-${execution.aquariumId}`;
      if (!acc[key]) {
        acc[key] = execution.completedAt;
      }
      return acc;
    }, {});
  }, [taskExecutions]);

  const latestDosingByAquarium = useMemo(() => {
    return dosingLogs.reduce<Record<string, string>>((acc, entry) => {
      if (!acc[entry.aquariumId]) {
        acc[entry.aquariumId] =
          `${entry.product} • ${entry.amountMl}ml • ${new Date(
            entry.createdAt,
          ).toLocaleString()}`;
      }

      return acc;
    }, {});
  }, [dosingLogs]);

  const recentExecutions = useMemo(() => {
    return [...taskExecutions]
      .sort((a, b) => +new Date(b.completedAt) - +new Date(a.completedAt))
      .slice(0, 24);
  }, [taskExecutions]);

  const saveDialog = () => {
    void form.handleSubmit();
  };

  const getKeepTone = (index: number) => {
    const tones = [
      theme.colors.secondaryContainer,
      theme.colors.tertiaryContainer,
      theme.colors.surfaceVariant,
    ];

    return tones[index % tones.length];
  };

  return (
    <>
      <DashboardScrollView>
        <DashboardHero
          title="Tasks & Maintenance"
          subtitle="Recurring schedules, quick completion, and dosing logs in the same dashboard rhythm."
          tone="secondary"
          chips={
            <>
              <Chip compact icon="calendar-clock">
                {dueTasks.length} due now
              </Chip>
              <Chip compact icon="fish">
                {aquariums.length} tanks
              </Chip>
              <Chip compact icon="test-tube">
                {dosingLogs.length} dosing logs
              </Chip>
              <Chip compact icon="history">
                {recentExecutions.length} recent entries
              </Chip>
            </>
          }
        />

        <DashboardSection
          title="Tasks due now"
          description="Clear the highest priority maintenance items before they drift."
        >
          {dueTasks.map(({ key, task, aquariumId }, index) => {
            const doneAt = latestExecutionByTemplate[key];
            const targetLivestock = task.livestockId
              ? livestock.find((item) => item.id === task.livestockId)
              : undefined;
            const backgroundColor = getKeepTone(index);
            const textColor = getCardTextColorForBackground(
              theme,
              backgroundColor,
            );
            const completionsToday = getCompletionsToday(
              task,
              aquariumId,
              taskExecutions,
              new Date(),
            );
            const timesPerDay = task.timesPerDay ?? 1;
            const showProgress =
              task.frequency === "daily" && timesPerDay > 1;

            return (
              <Card
                key={key}
                style={[styles.card, { backgroundColor }]}
                mode="contained"
                onPress={() =>
                  router.push(
                    getEntityHref(createEntityRef("task", task.id, aquariumId)) as never,
                  )
                }
              >
                <Card.Content>
                  <View style={styles.titleRow}>
                    <Text variant="titleMedium" style={{ color: textColor }}>
                      {task.title}
                    </Text>
                    <Chip compact>{task.frequency}</Chip>
                  </View>
                  <Text
                    variant="bodySmall"
                    style={[styles.targetTank, { color: textColor }]}
                  >
                    {getAquariumName(aquariumId)}
                  </Text>
                  <View style={styles.metaChipRow}>
                    <Chip
                      compact
                      icon="fishbowl"
                      onPress={() =>
                        router.push(
                          getEntityHref(
                            createEntityRef("aquarium", aquariumId, aquariumId),
                          ) as never,
                        )
                      }
                    >
                      Aquarium
                    </Chip>
                    {targetLivestock ? (
                      <Chip
                        compact
                        icon="fish"
                        onPress={() =>
                          router.push(
                            getEntityHref(
                              createEntityRef(
                                "livestock",
                                targetLivestock.id,
                                aquariumId,
                              ),
                            ) as never,
                          )
                        }
                      >
                        {targetLivestock.name}
                      </Chip>
                    ) : null}
                  </View>
                  {targetLivestock ? (
                    <Text
                      variant="bodySmall"
                      style={[styles.targetTank, { color: textColor }]}
                    >
                      Target livestock: {targetLivestock.name}
                    </Text>
                  ) : null}
                  {task.description ? (
                    <Text variant="bodyMedium" style={{ color: textColor }}>
                      {task.description}
                    </Text>
                  ) : null}
                  {showProgress ? (
                    <Text
                      variant="bodySmall"
                      style={[styles.targetTank, { color: textColor }]}
                    >
                      Progress: {completionsToday} / {timesPerDay} done today
                    </Text>
                  ) : null}
                  <Divider style={styles.divider} />
                  <View style={styles.actionsRow}>
                    <Text
                      variant="bodySmall"
                      style={[styles.lastDoneText, { color: textColor }]}
                    >
                      Last done:{" "}
                      {doneAt ? new Date(doneAt).toLocaleString() : "Never"}
                    </Text>
                    <Button
                      mode="contained"
                      onPress={() => {
                        const backDate = completionDateDraft[key]?.trim();
                        completeTask(
                          task.id,
                          aquariumId,
                          completionNoteDraft[key]?.trim() || undefined,
                          backDate || undefined,
                        );
                        setCompletionNoteDraft((prev) => ({
                          ...prev,
                          [key]: "",
                        }));
                        setCompletionDateDraft((prev) => ({
                          ...prev,
                          [key]: "",
                        }));
                      }}
                    >
                      Complete
                    </Button>
                  </View>
                  <TextInput
                    mode="outlined"
                    label="Completion date (optional, backdate)"
                    value={completionDateDraft[key] ?? ""}
                    onChangeText={(value) =>
                      setCompletionDateDraft((prev) => ({
                        ...prev,
                        [key]: value,
                      }))
                    }
                    placeholder="YYYY-MM-DDTHH:mm"
                    style={styles.completionDateInput}
                  />
                  <TextInput
                    mode="outlined"
                    label="Completion note (optional)"
                    value={completionNoteDraft[key] ?? ""}
                    onChangeText={(value) =>
                      setCompletionNoteDraft((prev) => ({
                        ...prev,
                        [key]: value,
                      }))
                    }
                    style={styles.completionNoteInput}
                  />
                </Card.Content>
              </Card>
            );
          })}

          {dueTasks.length === 0 ? (
            <Card
              style={[
                styles.card,
                { backgroundColor: theme.colors.surfaceVariant },
              ]}
              mode="contained"
            >
              <Card.Content>
                <Text variant="bodyMedium">
                  No due tasks right now. Your tanks are on schedule.
                </Text>
              </Card.Content>
            </Card>
          ) : null}
        </DashboardSection>

        <DashboardSection
          title="Latest dosing by tank"
          description="A quick dosing snapshot across every aquarium."
        >
          {aquariums.map((aquarium, index) => {
            const backgroundColor = getKeepTone(index);
            const textColor = getCardTextColorForBackground(
              theme,
              backgroundColor,
            );

            return (
              <Card
                key={aquarium.id}
                style={[styles.card, { backgroundColor }]}
                mode="contained"
                onPress={() =>
                  router.push(
                    getEntityHref(
                      createEntityRef("aquarium", aquarium.id, aquarium.id),
                    ) as never,
                  )
                }
              >
                <Card.Title
                  title={aquarium.name}
                  subtitle={aquarium.waterType}
                  titleStyle={{ color: textColor }}
                  subtitleStyle={{ color: textColor, opacity: 0.8 }}
                />
                <Card.Content>
                  <Text variant="bodyMedium" style={{ color: textColor }}>
                    {latestDosingByAquarium[aquarium.id] ??
                      "No dosing recorded yet."}
                  </Text>
                </Card.Content>
              </Card>
            );
          })}
        </DashboardSection>

        <DashboardSection
          title="Recent task history"
          description="Recent completions, notes, and livestock-specific work."
        >
          {recentExecutions.length === 0 ? (
            <Card
              style={[
                styles.card,
                { backgroundColor: theme.colors.surfaceVariant },
              ]}
              mode="contained"
            >
              <Card.Content>
                <Text variant="bodyMedium">No task execution history yet.</Text>
              </Card.Content>
            </Card>
          ) : (
            recentExecutions.map((execution, index) => {
              const task = taskTemplates.find(
                (template) => template.id === execution.taskTemplateId,
              );
              const targetLivestock = task?.livestockId
                ? livestock.find((item) => item.id === task.livestockId)
                : undefined;
              const backgroundColor = getKeepTone(index);
              const textColor = getCardTextColorForBackground(
                theme,
                backgroundColor,
              );

              return (
                <Card
                  key={execution.id}
                  style={[styles.card, { backgroundColor }]}
                  mode="contained"
                  onPress={() =>
                    task
                      ? router.push(
                          getEntityHref(
                            createEntityRef(
                              "task",
                              task.id,
                              execution.aquariumId,
                            ),
                          ) as never,
                        )
                      : undefined
                  }
                >
                  <Card.Content>
                    <Text variant="titleSmall" style={{ color: textColor }}>
                      {task?.title ?? "Task"}
                    </Text>
                    <Text
                      variant="bodySmall"
                      style={[styles.targetTank, { color: textColor }]}
                    >
                      {getAquariumName(execution.aquariumId)} •{" "}
                      {new Date(execution.completedAt).toLocaleString()}
                    </Text>
                    <View style={styles.metaChipRow}>
                      <Chip
                        compact
                        icon="fishbowl"
                        onPress={() =>
                          router.push(
                            getEntityHref(
                              createEntityRef(
                                "aquarium",
                                execution.aquariumId,
                                execution.aquariumId,
                              ),
                            ) as never,
                          )
                        }
                      >
                        Aquarium
                      </Chip>
                      {targetLivestock ? (
                        <Chip
                          compact
                          icon="fish"
                          onPress={() =>
                            router.push(
                              getEntityHref(
                                createEntityRef(
                                  "livestock",
                                  targetLivestock.id,
                                  execution.aquariumId,
                                ),
                              ) as never,
                            )
                          }
                        >
                          {targetLivestock.name}
                        </Chip>
                      ) : null}
                    </View>
                    {execution.note ? (
                      <Text variant="bodyMedium" style={{ color: textColor }}>
                        {execution.note}
                      </Text>
                    ) : null}
                    {targetLivestock ? (
                      <Text
                        variant="bodySmall"
                        style={[styles.targetTank, { color: textColor }]}
                      >
                        Target livestock: {targetLivestock.name}
                      </Text>
                    ) : null}
                  </Card.Content>
                </Card>
              );
            })
          )}
        </DashboardSection>
      </DashboardScrollView>

      <BottomSheet
        visible={isDialogOpen}
        onDismiss={() => {
          setDialogOpen(false);
          resetDialogForm(aquariums[0]?.id ?? "");
        }}
        title="Add maintenance log"
        actions={
          <>
            <Button
              onPress={() => {
                setDialogOpen(false);
                resetDialogForm(aquariums[0]?.id ?? "");
              }}
            >
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.values}>
              {(values) => {
                const canSaveTask =
                  !!values.selectedAquariumId &&
                  values.task.title.trim().length > 0 &&
                  values.task.aquariumIds.length > 0;

                const doseAmount = Number(values.dosing.amount);
                const canSaveDosing =
                  !!values.selectedAquariumId &&
                  values.dosing.product.trim().length > 0 &&
                  Number.isFinite(doseAmount) &&
                  doseAmount > 0;

                return (
                  <Button
                    onPress={saveDialog}
                    disabled={
                      values.dialogAction === "task"
                        ? !canSaveTask
                        : !canSaveDosing
                    }
                  >
                    Save
                  </Button>
                );
              }}
            </form.Subscribe>
          </>
        }
      >
        <form.Field name="selectedAquariumId">
          {(field) => (
            <ScrollableSegmentedButtons
              value={field.state.value}
              onValueChange={field.handleChange}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              }))}
            />
          )}
        </form.Field>

        <form.Field name="dialogAction">
          {(field) => (
            <ScrollableSegmentedButtons
              value={field.state.value}
              onValueChange={(value) =>
                field.handleChange(value as "task" | "dosing")
              }
              style={styles.actionToggle}
              buttons={[
                { label: "Task", value: "task" },
                { label: "Dosing", value: "dosing" },
              ]}
            />
          )}
        </form.Field>

        <form.Subscribe selector={(state) => state.values.dialogAction}>
          {(dialogAction) =>
            dialogAction === "task" ? (
              <View style={styles.formSection}>
                <form.Field name="task.title">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Task title"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                    />
                  )}
                </form.Field>
                <form.Field name="task.aquariumIds">
                  {(field) => (
                    <View style={styles.chipsWrap}>
                      {aquariums.map((aq) => {
                        const selected = field.state.value.includes(aq.id);
                        return (
                          <Chip
                            key={aq.id}
                            selected={selected}
                            onPress={() =>
                              field.handleChange(
                                selected
                                  ? field.state.value.filter(
                                      (id) => id !== aq.id,
                                    )
                                  : [...field.state.value, aq.id],
                              )
                            }
                          >
                            {aq.name}
                          </Chip>
                        );
                      })}
                    </View>
                  )}
                </form.Field>
                <form.Field name="task.frequency">
                  {(field) => (
                    <ScrollableSegmentedButtons
                      value={field.state.value}
                      onValueChange={(value) =>
                        field.handleChange(value as TaskFrequency)
                      }
                      buttons={[
                        { label: "Daily", value: "daily" },
                        { label: "Weekly", value: "weekly" },
                        { label: "Bi-weekly", value: "bi-weekly" },
                        { label: "Monthly", value: "monthly" },
                      ]}
                    />
                  )}
                </form.Field>
                <form.Subscribe selector={(state) => state.values.task.frequency}>
                  {(frequency) =>
                    frequency === "daily" ? (
                      <form.Field name="task.timesPerDay">
                        {(field) => (
                          <TextInput
                            mode="outlined"
                            label="Times per day"
                            value={String(field.state.value)}
                            onChangeText={(value) => {
                              const num = parseInt(value, 10);
                              field.handleChange(
                                Number.isNaN(num) || num < 1 ? 1 : num,
                              );
                            }}
                            keyboardType="numeric"
                          />
                        )}
                      </form.Field>
                    ) : null
                  }
                </form.Subscribe>
                <form.Field name="task.startDate">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Start date"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                      placeholder="YYYY-MM-DD"
                    />
                  )}
                </form.Field>
                <form.Field name="task.description">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Description"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                      multiline
                      numberOfLines={3}
                    />
                  )}
                </form.Field>
              </View>
            ) : (
              <View style={styles.formSection}>
                <form.Field name="dosing.product">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Product"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                    />
                  )}
                </form.Field>
                <form.Field name="dosing.amount">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Amount (ml)"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                      keyboardType="numeric"
                    />
                  )}
                </form.Field>
                <form.Field name="dosing.note">
                  {(field) => (
                    <TextInput
                      mode="outlined"
                      label="Note"
                      value={field.state.value}
                      onChangeText={field.handleChange}
                    />
                  )}
                </form.Field>
              </View>
            )
          }
        </form.Subscribe>
      </BottomSheet>

      <FAB
        icon="plus"
        label="Add"
        style={[
          styles.fab,
          { backgroundColor: theme.colors.primaryContainer },
        ]}
        color={theme.colors.onPrimaryContainer}
        onPress={() => {
          resetDialogForm(aquariums[0]?.id ?? "");
          setDialogOpen(true);
        }}
      />
    </>
  );
}

const styles = StyleSheet.create({
  card: {
    marginTop: 0,
    borderRadius: 24,
  },
  titleRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 10,
    marginBottom: 6,
  },
  targetTank: {
    marginBottom: 8,
    opacity: 0.75,
  },
  divider: {
    marginVertical: 10,
  },
  actionsRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 12,
  },
  lastDoneText: {
    flex: 1,
  },
  actionToggle: {
    marginTop: 12,
  },
  completionDateInput: {
    marginTop: 10,
  },
  completionNoteInput: {
    marginTop: 10,
  },
  formSection: {
    marginTop: 12,
    gap: 10,
  },
  chipsWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  metaChipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 8,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 88,
  },
});
