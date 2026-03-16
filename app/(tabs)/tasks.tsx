import { useMemo } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Button, Card, Chip, Divider, Text } from "react-native-paper";

import { useAquapt } from "@/context/aquapt-context";

export default function TasksScreen() {
  const { aquariums, taskTemplates, taskExecutions, completeTask } =
    useAquapt();

  const todayTasks = useMemo(() => {
    return taskTemplates.flatMap((task) =>
      task.aquariumIds.map((aquariumId) => ({
        key: `${task.id}-${aquariumId}`,
        task,
        aquariumId,
      })),
    );
  }, [taskTemplates]);

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

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text variant="headlineMedium">Tasks & Maintenance</Text>
      <Text variant="bodyMedium" style={styles.subtitle}>
        One-tap completion for recurring maintenance and dosing.
      </Text>

      {todayTasks.map(({ key, task, aquariumId }) => {
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
    </ScrollView>
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
});
