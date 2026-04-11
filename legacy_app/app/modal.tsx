import { Link } from "expo-router";
import { StyleSheet } from "react-native";
import { Card, Chip, Text, useTheme } from "react-native-paper";

import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { getCardTone } from "@/components/ui/card-tone";
import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
import { evaluateParameterAlerts } from "@/services/water-alerts";

export default function ModalScreen() {
  const theme = useTheme();
  const actionTone = getCardTone(theme, "surfaceVariant");
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
    <DashboardScrollView contentContainerStyle={styles.container}>
      <DashboardHero
        title="Global Insights"
        subtitle="Quick portfolio health across all tanks, using the same dashboard card language."
        tone="primary"
        chips={
          <>
            <Chip icon="fish">{aquariums.length} tanks</Chip>
            <Chip icon="calendar-clock">{dueTaskCount} due tasks</Chip>
            <Chip icon="alert-circle">{activeIssueCount} active issues</Chip>
            <Chip icon="shield-alert">{safetyAlertCount} safety alerts</Chip>
          </>
        }
      />

      <DashboardSection
        title="What to do next"
        description="A short focus list before you head back into the main app."
      >
        <Card
          mode="contained"
          style={[styles.actionCard, { backgroundColor: actionTone.backgroundColor }]}
        >
          <Card.Content style={styles.actionContent}>
            <Text variant="bodyMedium" style={{ color: actionTone.textColor }}>
              Complete due tasks first to keep schedule drift low.
            </Text>
            <Text variant="bodyMedium" style={{ color: actionTone.textColor }}>
              Resolve safety alerts before adding livestock.
            </Text>
            <Text variant="bodyMedium" style={{ color: actionTone.textColor }}>
              Close open issues with resolution notes for better diagnostics.
            </Text>
          </Card.Content>
        </Card>

        <Link href="/" dismissTo style={styles.link}>
          <Text variant="labelLarge">Back to dashboard</Text>
        </Link>
      </DashboardSection>
    </DashboardScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingBottom: 40,
  },
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  actionCard: {
    borderRadius: 24,
  },
  actionContent: {
    gap: 10,
  },
  link: {
    marginTop: 4,
    paddingVertical: 10,
    alignSelf: "flex-start",
  },
});
