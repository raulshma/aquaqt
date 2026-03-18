import { Link } from "expo-router";
import { ScrollView, StyleSheet, View } from "react-native";
import { Card, Chip, Text } from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
import { evaluateParameterAlerts } from "@/services/water-alerts";

export default function ModalScreen() {
  const insets = useSafeAreaInsets();
  const { aquariums, taskTemplates, taskExecutions, issues, parameterLogs } =
    useAquapt();

  const dueTaskCount = taskTemplates.flatMap((task) =>
    task.aquariumIds.filter((aquariumId) =>
      isTaskDue(task, aquariumId, taskExecutions, new Date()),
    ),
  ).length;

  const activeIssueCount = issues.filter(
    (issue) => issue.status !== "resolved",
  ).length;

  const safetyAlertCount = aquariums.reduce((sum, aquarium) => {
    const latest = parameterLogs
      .filter((entry) => entry.aquariumId === aquarium.id)
      .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))[0];

    if (!latest) {
      return sum;
    }

    return sum + evaluateParameterAlerts(aquarium, latest.values).length;
  }, 0);

  return (
    <ScrollView
      contentContainerStyle={[
        styles.container,
        { paddingTop: 16 + insets.top },
      ]}
    >
      <Text variant="headlineSmall">Global Insights</Text>
      <Text variant="bodyMedium" style={styles.subtitle}>
        Quick portfolio health snapshot across all tanks.
      </Text>

      <Card mode="contained">
        <Card.Content>
          <View style={styles.row}>
            <Chip icon="fish">{aquariums.length} tanks</Chip>
            <Chip icon="calendar-clock">{dueTaskCount} due tasks</Chip>
            <Chip icon="alert-circle">{activeIssueCount} active issues</Chip>
            <Chip icon="shield-alert">{safetyAlertCount} safety alerts</Chip>
          </View>
        </Card.Content>
      </Card>

      <Card mode="outlined">
        <Card.Title title="What to do next" />
        <Card.Content>
          <Text variant="bodyMedium">
            • Complete due tasks first to keep schedule drift low.
          </Text>
          <Text variant="bodyMedium">
            • Resolve safety alerts before adding livestock.
          </Text>
          <Text variant="bodyMedium">
            • Close open issues with resolution notes for better diagnostics.
          </Text>
        </Card.Content>
      </Card>

      <Link href="/" dismissTo style={styles.link}>
        <Text variant="labelLarge">Back to dashboard</Text>
      </Link>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 12,
  },
  subtitle: {
    opacity: 0.75,
  },
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  link: {
    marginTop: 4,
    paddingVertical: 10,
    alignSelf: "flex-start",
  },
});
