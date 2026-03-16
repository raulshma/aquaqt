import { useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    Divider,
    FAB,
    SegmentedButtons,
    Text,
    TextInput,
} from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
import { TaskFrequency } from "@/types/aquapt";

export default function TasksScreen() {
  const {
    aquariums,
    taskTemplates,
    taskExecutions,
    dosingLogs,
    completeTask,
    addTaskTemplate,
    logDosing,
  } = useAquapt();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [dialogAction, setDialogAction] = useState<"task" | "dosing">("task");
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [taskFrequency, setTaskFrequency] = useState<TaskFrequency>("weekly");
  const [taskAquariumIds, setTaskAquariumIds] = useState<string[]>(
    aquariums[0]?.id ? [aquariums[0].id] : [],
  );
  const [doseProduct, setDoseProduct] = useState("");
  const [doseAmount, setDoseAmount] = useState("");
  const [doseNote, setDoseNote] = useState("");

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

  const saveDialog = () => {
    if (!selectedAquariumId) {
      return;
    }

    if (dialogAction === "task") {
      if (!taskTitle.trim()) {
        return;
      }

      addTaskTemplate({
        title: taskTitle.trim(),
        description: taskDescription.trim() || undefined,
        frequency: taskFrequency,
        aquariumIds: taskAquariumIds.length
          ? taskAquariumIds
          : [selectedAquariumId],
      });

      setTaskTitle("");
      setTaskDescription("");
      setTaskFrequency("weekly");
      setTaskAquariumIds(selectedAquariumId ? [selectedAquariumId] : []);
      setDialogOpen(false);
      return;
    }

    const amount = Number(doseAmount);
    if (!doseProduct.trim() || !Number.isFinite(amount) || amount <= 0) {
      return;
    }

    logDosing(
      selectedAquariumId,
      doseProduct.trim(),
      amount,
      doseNote.trim() || undefined,
    );
    setDoseProduct("");
    setDoseAmount("");
    setDoseNote("");
    setDialogOpen(false);
  };

  return (
    <>
      <ScrollView contentContainerStyle={styles.container}>
        <Text variant="headlineMedium">Tasks & Maintenance</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Recurring schedules, one-tap completion, and dosing logs.
        </Text>

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Tasks due now
        </Text>

        {dueTasks.map(({ key, task, aquariumId }) => {
          const doneAt = latestExecutionByTemplate[key];

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
                    onPress={() => completeTask(task.id, aquariumId)}
                  >
                    Complete
                  </Button>
                </View>
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
      </ScrollView>

      <BottomSheet
        visible={isDialogOpen}
        onDismiss={() => setDialogOpen(false)}
        title="Add maintenance log"
        actions={
          <>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button onPress={saveDialog}>Save</Button>
          </>
        }
      >
        <SegmentedButtons
          value={selectedAquariumId}
          onValueChange={setSelectedAquariumId}
          buttons={aquariums.map((aq) => ({
            label: aq.name,
            value: aq.id,
          }))}
        />

        <SegmentedButtons
          value={dialogAction}
          onValueChange={(value) => setDialogAction(value as "task" | "dosing")}
          style={styles.actionToggle}
          buttons={[
            { label: "Task", value: "task" },
            { label: "Dosing", value: "dosing" },
          ]}
        />

        {dialogAction === "task" ? (
          <View style={styles.formSection}>
            <TextInput
              mode="outlined"
              label="Task title"
              value={taskTitle}
              onChangeText={setTaskTitle}
            />
            <View style={styles.chipsWrap}>
              {aquariums.map((aq) => {
                const selected = taskAquariumIds.includes(aq.id);
                return (
                  <Chip
                    key={aq.id}
                    selected={selected}
                    onPress={() =>
                      setTaskAquariumIds((prev) =>
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
            <SegmentedButtons
              value={taskFrequency}
              onValueChange={(value) =>
                setTaskFrequency(value as TaskFrequency)
              }
              buttons={[
                { label: "Daily", value: "daily" },
                { label: "Weekly", value: "weekly" },
                { label: "Bi-weekly", value: "bi-weekly" },
                { label: "Monthly", value: "monthly" },
              ]}
            />
            <TextInput
              mode="outlined"
              label="Description"
              value={taskDescription}
              onChangeText={setTaskDescription}
              multiline
              numberOfLines={3}
            />
          </View>
        ) : (
          <View style={styles.formSection}>
            <TextInput
              mode="outlined"
              label="Product"
              value={doseProduct}
              onChangeText={setDoseProduct}
            />
            <TextInput
              mode="outlined"
              label="Amount (ml)"
              value={doseAmount}
              onChangeText={setDoseAmount}
              keyboardType="numeric"
            />
            <TextInput
              mode="outlined"
              label="Note"
              value={doseNote}
              onChangeText={setDoseNote}
            />
          </View>
        )}
      </BottomSheet>

      <FAB
        icon="plus"
        label="Add"
        style={styles.fab}
        onPress={() => setDialogOpen(true)}
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
