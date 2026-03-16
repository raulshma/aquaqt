import { useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    Dialog,
    FAB,
    Portal,
    SegmentedButtons,
    Text,
    TextInput,
} from "react-native-paper";

import { useAquapt } from "@/context/aquapt-context";
import { IssueStatus } from "@/types/aquapt";

export default function HomeScreen() {
  const {
    aquariums,
    issues,
    parameterLogs,
    livestockCountByAquarium,
    openIssuesByAquarium,
    addMemo,
    addIssue,
    logParameters,
    setIssueStatus,
  } = useAquapt();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [action, setAction] = useState<"parameter" | "memo" | "issue">(
    "parameter",
  );
  const [memo, setMemo] = useState("");
  const [issueTitle, setIssueTitle] = useState("");
  const [nitrate, setNitrate] = useState("");
  const [ph, setPh] = useState("");
  const [temperature, setTemperature] = useState("");
  const [issueStatusDraft, setIssueStatusDraft] = useState<
    Record<string, IssueStatus>
  >({});
  const [resolutionNoteDraft, setResolutionNoteDraft] = useState<
    Record<string, string>
  >({});

  const latestParameterByAquarium = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      const latest = parameterLogs
        .filter((entry) => entry.aquariumId === aquarium.id)
        .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))[0];

      if (!latest) {
        acc[aquarium.id] = "No measurements logged yet";
        return acc;
      }

      const values = latest.values;
      acc[aquarium.id] =
        `NO3 ${values.nitrate ?? "-"} • pH ${values.ph ?? "-"} • ${values.temperatureC ?? "-"}°C`;

      return acc;
    }, {});
  }, [aquariums, parameterLogs]);

  const handleSubmitQuickAction = () => {
    if (!selectedAquariumId) {
      return;
    }

    if (action === "memo" && memo.trim()) {
      addMemo(selectedAquariumId, memo.trim());
      setMemo("");
    }

    if (action === "issue" && issueTitle.trim()) {
      addIssue(selectedAquariumId, issueTitle.trim());
      setIssueTitle("");
    }

    if (action === "parameter") {
      const nitrateValue = Number(nitrate);
      const phValue = Number(ph);
      const temperatureValue = Number(temperature);

      logParameters(selectedAquariumId, {
        nitrate: Number.isFinite(nitrateValue) ? nitrateValue : undefined,
        ph: Number.isFinite(phValue) ? phValue : undefined,
        temperatureC: Number.isFinite(temperatureValue)
          ? temperatureValue
          : undefined,
      });

      setNitrate("");
      setPh("");
      setTemperature("");
    }

    setDialogOpen(false);
  };

  const totalOpenIssues = issues.filter(
    (issue) => issue.status !== "resolved",
  ).length;

  return (
    <>
      <ScrollView contentContainerStyle={styles.container}>
        <Text variant="headlineMedium">Aquapt Dashboard</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Monitor tank health, log key events, and catch issues early.
        </Text>

        <Card style={styles.summaryCard} mode="elevated">
          <Card.Content>
            <Text variant="titleMedium">Today at a glance</Text>
            <View style={styles.summaryRow}>
              <Chip compact icon="fish">
                {aquariums.length} Tanks
              </Chip>
              <Chip compact icon="alert">
                {totalOpenIssues} Active issues
              </Chip>
            </View>
          </Card.Content>
        </Card>

        {aquariums.map((aquarium) => (
          <Card key={aquarium.id} style={styles.tankCard} mode="contained">
            <Card.Title
              title={aquarium.name}
              subtitle={`${aquarium.volumeLiters}L • ${aquarium.waterType}`}
            />
            <Card.Content>
              <Text variant="bodyMedium">
                Latest parameters: {latestParameterByAquarium[aquarium.id]}
              </Text>
              <View style={styles.summaryRow}>
                <Chip compact icon="fish">
                  {livestockCountByAquarium[aquarium.id] ?? 0} livestock
                </Chip>
                <Chip compact icon="alert-circle">
                  {openIssuesByAquarium[aquarium.id] ?? 0} issues
                </Chip>
              </View>
            </Card.Content>
          </Card>
        ))}

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Issue tracking
        </Text>
        {issues.length === 0 ? (
          <Card mode="outlined">
            <Card.Content>
              <Text variant="bodyMedium">
                No issues logged yet. Great news for your tanks 🐠
              </Text>
            </Card.Content>
          </Card>
        ) : null}

        {issues.map((issue) => {
          const currentStatus = issueStatusDraft[issue.id] ?? issue.status;
          const resolutionNote =
            resolutionNoteDraft[issue.id] ?? issue.resolutionNote ?? "";

          return (
            <Card key={issue.id} style={styles.issueCard} mode="outlined">
              <Card.Content>
                <Text variant="titleSmall">{issue.title}</Text>
                <Text variant="bodySmall" style={styles.issueMeta}>
                  {aquariums.find((aq) => aq.id === issue.aquariumId)?.name ??
                    "Unknown tank"}{" "}
                  • Logged {new Date(issue.createdAt).toLocaleString()}
                </Text>

                <SegmentedButtons
                  value={currentStatus}
                  onValueChange={(value) =>
                    setIssueStatusDraft((prev) => ({
                      ...prev,
                      [issue.id]: value as IssueStatus,
                    }))
                  }
                  buttons={[
                    { label: "Open", value: "open" },
                    { label: "Monitoring", value: "monitoring" },
                    { label: "Resolved", value: "resolved" },
                  ]}
                  style={styles.issueStatusSelector}
                />

                {currentStatus === "resolved" ? (
                  <TextInput
                    mode="outlined"
                    label="Resolution note"
                    value={resolutionNote}
                    onChangeText={(value) =>
                      setResolutionNoteDraft((prev) => ({
                        ...prev,
                        [issue.id]: value,
                      }))
                    }
                    multiline
                    numberOfLines={3}
                    style={styles.issueResolutionInput}
                  />
                ) : null}

                <Button
                  mode="contained"
                  style={styles.issueSaveButton}
                  onPress={() =>
                    setIssueStatus(
                      issue.id,
                      currentStatus,
                      resolutionNote.trim() || undefined,
                    )
                  }
                >
                  Save issue update
                </Button>
              </Card.Content>
            </Card>
          );
        })}
      </ScrollView>

      <Portal>
        <Dialog visible={isDialogOpen} onDismiss={() => setDialogOpen(false)}>
          <Dialog.Title>Quick Log</Dialog.Title>
          <Dialog.Content>
            <SegmentedButtons
              value={selectedAquariumId}
              onValueChange={setSelectedAquariumId}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
                style: styles.segmentButton,
              }))}
              density="small"
            />

            <SegmentedButtons
              value={action}
              onValueChange={(value) =>
                setAction(value as "parameter" | "memo" | "issue")
              }
              buttons={[
                { label: "Parameters", value: "parameter" },
                { label: "Memo", value: "memo" },
                { label: "Issue", value: "issue" },
              ]}
              style={styles.actionSelector}
            />

            {action === "parameter" ? (
              <View style={styles.inputsContainer}>
                <TextInput
                  label="Nitrate (ppm)"
                  mode="outlined"
                  value={nitrate}
                  onChangeText={setNitrate}
                  keyboardType="numeric"
                />
                <TextInput
                  label="pH"
                  mode="outlined"
                  value={ph}
                  onChangeText={setPh}
                  keyboardType="numeric"
                />
                <TextInput
                  label="Temperature °C"
                  mode="outlined"
                  value={temperature}
                  onChangeText={setTemperature}
                  keyboardType="numeric"
                />
              </View>
            ) : null}

            {action === "memo" ? (
              <TextInput
                label="Memo"
                mode="outlined"
                value={memo}
                onChangeText={setMemo}
                multiline
                numberOfLines={4}
                style={styles.inputTopSpacing}
              />
            ) : null}

            {action === "issue" ? (
              <TextInput
                label="Issue title"
                mode="outlined"
                value={issueTitle}
                onChangeText={setIssueTitle}
                style={styles.inputTopSpacing}
              />
            ) : null}
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button onPress={handleSubmitQuickAction}>Save</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      <FAB
        icon="plus"
        style={styles.fab}
        onPress={() => setDialogOpen(true)}
        label="Quick Log"
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 96,
    gap: 12,
  },
  subtitle: {
    opacity: 0.75,
    marginBottom: 4,
  },
  summaryCard: {
    marginVertical: 4,
  },
  tankCard: {
    marginTop: 8,
  },
  summaryRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 10,
  },
  actionSelector: {
    marginTop: 12,
  },
  sectionTitle: {
    marginTop: 16,
  },
  issueCard: {
    marginTop: 8,
  },
  issueMeta: {
    marginTop: 4,
    opacity: 0.75,
  },
  issueStatusSelector: {
    marginTop: 12,
  },
  issueResolutionInput: {
    marginTop: 12,
  },
  issueSaveButton: {
    marginTop: 12,
    alignSelf: "flex-start",
  },
  segmentButton: {
    minWidth: 80,
  },
  inputsContainer: {
    gap: 10,
    marginTop: 12,
  },
  inputTopSpacing: {
    marginTop: 12,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 16,
  },
});
