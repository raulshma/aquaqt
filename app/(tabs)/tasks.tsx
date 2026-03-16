import { useForm } from "@tanstack/react-form";
import { useEffect, useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
  Button,
  Card,
  Chip,
  Divider,
  FAB,
  Text,
  TextInput,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
import { TaskFrequency } from "@/types/aquapt";

export default function TasksScreen() {
  const insets = useSafeAreaInsets();
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

  const form = useForm({
    defaultValues: {
      dialogAction: "task" as "task" | "dosing",
      selectedAquariumId: aquariums[0]?.id ?? "",
      task: {
        title: "",
        description: "",
        frequency: "weekly" as TaskFrequency,
        aquariumIds: aquariums[0]?.id ? [aquariums[0].id] : [],
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
        });

        form.setFieldValue("task.title", "");
        form.setFieldValue("task.description", "");
        form.setFieldValue("task.frequency", "weekly");
        form.setFieldValue("task.aquariumIds", [value.selectedAquariumId]);
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

  return (
    <>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          { paddingTop: 16 + insets.top },
        ]}
      >
        <Text variant="headlineMedium">Tasks & Maintenance</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Recurring schedules, one-tap completion, and dosing logs.
        </Text>

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Tasks due now
        </Text>

        {dueTasks.map(({ key, task, aquariumId }) => {
          const doneAt = latestExecutionByTemplate[key];
          const targetLivestock = task.livestockId
            ? livestock.find((item) => item.id === task.livestockId)
            : undefined;

          return (
            <Card key={key} style={styles.card} mode="contained">
              <Card.Content>
                <View style={styles.titleRow}>
                  <Text variant="titleMedium">{task.title}</Text>
                  <Chip compact>{task.frequency}</Chip>
                </View>
                <Text variant="bodySmall" style={styles.targetTank}>
                  {getAquariumName(aquariumId)}
                </Text>
                {targetLivestock ? (
                  <Text variant="bodySmall" style={styles.targetTank}>
                    Target livestock: {targetLivestock.name}
                  </Text>
                ) : null}
                {task.description ? (
                  <Text variant="bodyMedium">{task.description}</Text>
                ) : null}
                <Divider style={styles.divider} />
                <View style={styles.actionsRow}>
                  <Text variant="bodySmall" style={styles.lastDoneText}>
                    Last done:{" "}
                    {doneAt ? new Date(doneAt).toLocaleString() : "Never"}
                  </Text>
                  <Button
                    mode="contained"
                    onPress={() => {
                      completeTask(
                        task.id,
                        aquariumId,
                        completionNoteDraft[key]?.trim() || undefined,
                      );
                      setCompletionNoteDraft((prev) => ({
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
          <Card style={styles.card} mode="outlined">
            <Card.Content>
              <Text variant="bodyMedium">
                No due tasks right now. Your tanks are on schedule ✅
              </Text>
            </Card.Content>
          </Card>
        ) : null}

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Latest dosing by tank
        </Text>

        {aquariums.map((aquarium) => (
          <Card key={aquarium.id} style={styles.card} mode="outlined">
            <Card.Title title={aquarium.name} subtitle={aquarium.waterType} />
            <Card.Content>
              <Text variant="bodyMedium">
                {latestDosingByAquarium[aquarium.id] ??
                  "No dosing recorded yet."}
              </Text>
            </Card.Content>
          </Card>
        ))}

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Recent task history
        </Text>

        {recentExecutions.length === 0 ? (
          <Card style={styles.card} mode="outlined">
            <Card.Content>
              <Text variant="bodyMedium">No task execution history yet.</Text>
            </Card.Content>
          </Card>
        ) : (
          recentExecutions.map((execution) => {
            const task = taskTemplates.find(
              (template) => template.id === execution.taskTemplateId,
            );
            const targetLivestock = task?.livestockId
              ? livestock.find((item) => item.id === task.livestockId)
              : undefined;

            return (
              <Card key={execution.id} style={styles.card} mode="outlined">
                <Card.Content>
                  <Text variant="titleSmall">{task?.title ?? "Task"}</Text>
                  <Text variant="bodySmall" style={styles.targetTank}>
                    {getAquariumName(execution.aquariumId)} •{" "}
                    {new Date(execution.completedAt).toLocaleString()}
                  </Text>
                  {execution.note ? (
                    <Text variant="bodyMedium">{execution.note}</Text>
                  ) : null}
                  {targetLivestock ? (
                    <Text variant="bodySmall" style={styles.targetTank}>
                      Target livestock: {targetLivestock.name}
                    </Text>
                  ) : null}
                </Card.Content>
              </Card>
            );
          })
        )}
      </ScrollView>

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
        style={styles.fab}
        onPress={() => {
          resetDialogForm(aquariums[0]?.id ?? "");
          setDialogOpen(true);
        }}
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 24,
    gap: 10,
  },
  subtitle: {
    opacity: 0.75,
    marginBottom: 8,
  },
  sectionTitle: {
    marginTop: 8,
    marginBottom: 4,
  },
  card: {
    marginBottom: 8,
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
  fab: {
    position: "absolute",
    right: 16,
    bottom: 16,
  },
});
