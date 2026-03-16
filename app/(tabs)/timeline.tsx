import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    FAB,
    SegmentedButtons,
    Text,
    TextInput,
} from "react-native-paper";
import { DatePickerModal } from "react-native-paper-dates";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { useAquapt } from "@/context/aquapt-context";
import { TimelineEventType } from "@/types/aquapt";

const filters: { value: TimelineEventType | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "task", label: "Tasks" },
  { value: "parameter", label: "Parameters" },
  { value: "dosing", label: "Dosing" },
  { value: "issue", label: "Issues" },
  { value: "livestock", label: "Livestock" },
  { value: "asset", label: "Assets" },
  { value: "consumable", label: "Consumables" },
  { value: "memo", label: "Memos" },
];

export default function TimelineScreen() {
  const { timeline, aquariums, addMemo, addIssue, logParameters, logDosing } =
    useAquapt();
  const [selectedFilter, setSelectedFilter] = useState<
    TimelineEventType | "all"
  >("all");
  const [selectedAquariumFilter, setSelectedAquariumFilter] = useState("all");
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [action, setAction] = useState<
    "memo" | "issue" | "parameter" | "dosing"
  >("memo");
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [memo, setMemo] = useState("");
  const [issueTitle, setIssueTitle] = useState("");
  const [nitrate, setNitrate] = useState("");
  const [ph, setPh] = useState("");
  const [doseProduct, setDoseProduct] = useState("");
  const [doseAmount, setDoseAmount] = useState("");
  const [memoPhotoUri, setMemoPhotoUri] = useState("");
  const [isPickingMemoPhoto, setPickingMemoPhoto] = useState(false);
  const [memoDate, setMemoDate] = useState(new Date());
  const [isMemoDatePickerOpen, setMemoDatePickerOpen] = useState(false);

  const filteredTimeline = useMemo(() => {
    const list = [...timeline].sort(
      (a, b) => +new Date(b.createdAt) - +new Date(a.createdAt),
    );

    const byType =
      selectedFilter === "all"
        ? list
        : list.filter((item) => item.type === selectedFilter);

    if (selectedAquariumFilter === "all") {
      return byType;
    }

    return byType.filter((item) => item.aquariumId === selectedAquariumFilter);
  }, [selectedAquariumFilter, selectedFilter, timeline]);

  const aquariumNameById = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      acc[aquarium.id] = aquarium.name;
      return acc;
    }, {});
  }, [aquariums]);

  const pickMemoPhoto = async () => {
    setPickingMemoPhoto(true);
    try {
      const permission =
        await ImagePicker.requestMediaLibraryPermissionsAsync();

      if (!permission.granted) {
        return;
      }

      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsEditing: true,
        quality: 0.7,
      });

      if (!result.canceled && result.assets?.[0]?.uri) {
        setMemoPhotoUri(result.assets[0].uri);
      }
    } finally {
      setPickingMemoPhoto(false);
    }
  };

  const saveQuickLog = () => {
    if (!selectedAquariumId) {
      return;
    }

    if (action === "memo" && memo.trim()) {
      addMemo(
        selectedAquariumId,
        memo.trim(),
        memoPhotoUri || undefined,
        memoDate.toISOString(),
      );
      setMemo("");
      setMemoPhotoUri("");
      setMemoDate(new Date());
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

    if (action === "dosing") {
      const amountValue = Number(doseAmount);

      if (
        doseProduct.trim() &&
        Number.isFinite(amountValue) &&
        amountValue > 0
      ) {
        logDosing(selectedAquariumId, doseProduct.trim(), amountValue);
        setDoseProduct("");
        setDoseAmount("");
      }
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

        <View style={styles.filterRow}>
          <Chip
            selected={selectedAquariumFilter === "all"}
            onPress={() => setSelectedAquariumFilter("all")}
            style={styles.filterChip}
          >
            All tanks
          </Chip>
          {aquariums.map((aquarium) => (
            <Chip
              key={aquarium.id}
              selected={selectedAquariumFilter === aquarium.id}
              onPress={() => setSelectedAquariumFilter(aquarium.id)}
              style={styles.filterChip}
            >
              {aquarium.name}
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
              {event.photoUri ? (
                <Image
                  source={{ uri: event.photoUri }}
                  style={styles.eventPhoto}
                />
              ) : null}
            </Card.Content>
          </Card>
        ))}

        {filteredTimeline.length === 0 ? (
          <Card mode="outlined">
            <Card.Content>
              <Text variant="bodyMedium">
                No timeline events for this filter yet.
              </Text>
            </Card.Content>
          </Card>
        ) : null}
      </ScrollView>

      <BottomSheet
        visible={isDialogOpen}
        onDismiss={() => setDialogOpen(false)}
        title="Quick action"
        actions={
          <>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button onPress={saveQuickLog}>Save</Button>
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
          density="small"
        />

        <SegmentedButtons
          value={action}
          onValueChange={(value) =>
            setAction(value as "memo" | "issue" | "parameter" | "dosing")
          }
          style={styles.quickActionSelector}
          buttons={[
            { value: "memo", label: "Memo" },
            { value: "issue", label: "Issue" },
            { value: "parameter", label: "Params" },
            { value: "dosing", label: "Dosing" },
          ]}
        />

        {action === "memo" ? (
          <View style={styles.parameterInputs}>
            <TextInput
              mode="outlined"
              label="Memo"
              multiline
              numberOfLines={4}
              value={memo}
              onChangeText={setMemo}
            />
            <Button
              mode="contained-tonal"
              onPress={pickMemoPhoto}
              loading={isPickingMemoPhoto}
            >
              {memoPhotoUri ? "Change photo" : "Attach photo"}
            </Button>
            <Button
              mode="outlined"
              icon="calendar"
              onPress={() => setMemoDatePickerOpen(true)}
            >
              Log date: {memoDate.toLocaleDateString()}
            </Button>
            {memoPhotoUri ? (
              <Image source={{ uri: memoPhotoUri }} style={styles.eventPhoto} />
            ) : null}
          </View>
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

        {action === "dosing" ? (
          <View style={styles.parameterInputs}>
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
          </View>
        ) : null}
      </BottomSheet>

      <DatePickerModal
        locale="en"
        mode="single"
        visible={isMemoDatePickerOpen}
        date={memoDate}
        onDismiss={() => setMemoDatePickerOpen(false)}
        onConfirm={({ date }) => {
          if (date) {
            setMemoDate(date);
          }
          setMemoDatePickerOpen(false);
        }}
      />

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
  eventPhoto: {
    width: "100%",
    height: 170,
    borderRadius: 12,
    marginTop: 10,
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
