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
import { TimelineEventType } from "@/types/aquapt";

const filters: { value: TimelineEventType | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "task", label: "Tasks" },
  { value: "parameter", label: "Parameters" },
  { value: "issue", label: "Issues" },
  { value: "memo", label: "Memos" },
];

export default function TimelineScreen() {
  const { timeline, aquariums, addMemo, addIssue, logParameters } = useAquapt();
  const [selectedFilter, setSelectedFilter] = useState<
    TimelineEventType | "all"
  >("all");
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [action, setAction] = useState<"memo" | "issue" | "parameter">("memo");
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [memo, setMemo] = useState("");
  const [issueTitle, setIssueTitle] = useState("");
  const [nitrate, setNitrate] = useState("");
  const [ph, setPh] = useState("");

  const filteredTimeline = useMemo(() => {
    const list = [...timeline].sort(
      (a, b) => +new Date(b.createdAt) - +new Date(a.createdAt),
    );

    if (selectedFilter === "all") {
      return list;
    }

    return list.filter((item) => item.type === selectedFilter);
  }, [selectedFilter, timeline]);

  const aquariumNameById = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      acc[aquarium.id] = aquarium.name;
      return acc;
    }, {});
  }, [aquariums]);

  const saveQuickLog = () => {
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

      logParameters(selectedAquariumId, {
        nitrate: Number.isFinite(nitrateValue) ? nitrateValue : undefined,
        ph: Number.isFinite(phValue) ? phValue : undefined,
      });

      setNitrate("");
      setPh("");
    }

    setDialogOpen(false);
  };

  return (
    <>
      <ScrollView contentContainerStyle={styles.container}>
        <Text variant="headlineMedium">Unified Timeline</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Chronological events across tasks, parameters, issues, and memos.
        </Text>

        <View style={styles.filterRow}>
          {filters.map((filter) => (
            <Chip
              key={filter.value}
              selected={selectedFilter === filter.value}
              onPress={() => setSelectedFilter(filter.value)}
              style={styles.filterChip}
            >
              {filter.label}
            </Chip>
          ))}
        </View>

        {filteredTimeline.map((event) => (
          <Card key={event.id} style={styles.eventCard} mode="outlined">
            <Card.Content>
              <View style={styles.eventHeader}>
                <Chip compact>{event.type}</Chip>
                <Text variant="labelSmall">
                  {new Date(event.createdAt).toLocaleString()}
                </Text>
              </View>
              <Text variant="titleSmall" style={styles.eventTitle}>
                {event.title}
              </Text>
              <Text variant="bodySmall" style={styles.aquariumName}>
                {aquariumNameById[event.aquariumId] ?? "Unknown tank"}
              </Text>
              {event.description ? (
                <Text variant="bodyMedium">{event.description}</Text>
              ) : null}
            </Card.Content>
          </Card>
        ))}
      </ScrollView>

      <Portal>
        <Dialog visible={isDialogOpen} onDismiss={() => setDialogOpen(false)}>
          <Dialog.Title>Quick action</Dialog.Title>
          <Dialog.Content>
            <SegmentedButtons
              value={selectedAquariumId}
              onValueChange={setSelectedAquariumId}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              }))}
              density="small"
            />

            <SegmentedButtons
              value={action}
              onValueChange={(value) =>
                setAction(value as "memo" | "issue" | "parameter")
              }
              style={styles.quickActionSelector}
              buttons={[
                { value: "memo", label: "Memo" },
                { value: "issue", label: "Issue" },
                { value: "parameter", label: "Params" },
              ]}
            />

            {action === "memo" ? (
              <TextInput
                mode="outlined"
                label="Memo"
                multiline
                numberOfLines={4}
                value={memo}
                onChangeText={setMemo}
                style={styles.quickActionInput}
              />
            ) : null}

            {action === "issue" ? (
              <TextInput
                mode="outlined"
                label="Issue title"
                value={issueTitle}
                onChangeText={setIssueTitle}
                style={styles.quickActionInput}
              />
            ) : null}

            {action === "parameter" ? (
              <View style={styles.parameterInputs}>
                <TextInput
                  mode="outlined"
                  label="Nitrate"
                  value={nitrate}
                  onChangeText={setNitrate}
                  keyboardType="numeric"
                />
                <TextInput
                  mode="outlined"
                  label="pH"
                  value={ph}
                  onChangeText={setPh}
                  keyboardType="numeric"
                />
              </View>
            ) : null}
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button onPress={saveQuickLog}>Save</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      <FAB
        icon="plus"
        label="Log"
        onPress={() => setDialogOpen(true)}
        style={styles.fab}
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 24,
  },
  subtitle: {
    opacity: 0.75,
    marginTop: 6,
    marginBottom: 12,
  },
  filterRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 12,
  },
  filterChip: {
    marginBottom: 4,
  },
  quickActionSelector: {
    marginTop: 12,
  },
  quickActionInput: {
    marginTop: 12,
  },
  parameterInputs: {
    marginTop: 12,
    gap: 10,
  },
  eventCard: {
    marginBottom: 10,
  },
  eventHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  eventTitle: {
    marginBottom: 2,
  },
  aquariumName: {
    opacity: 0.75,
    marginBottom: 8,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 16,
  },
});
