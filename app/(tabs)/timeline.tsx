import { useForm } from "@tanstack/react-form";
import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useEffect, useMemo, useState } from "react";
import { Alert, ScrollView, StyleSheet, View } from "react-native";
import { Button, Card, Chip, FAB, Text, TextInput } from "react-native-paper";
import { DatePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
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
  const insets = useSafeAreaInsets();
  const {
    timeline,
    aquariums,
    taskTemplates,
    taskExecutions,
    addMemo,
    addIssue,
    logParameters,
    logDosing,
    completeTask,
  } = useAquapt();
  const [selectedFilter, setSelectedFilter] = useState<
    TimelineEventType | "all"
  >("all");
  const [selectedAquariumFilter, setSelectedAquariumFilter] = useState("all");
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [isPickingMemoPhoto, setPickingMemoPhoto] = useState(false);
  const [isMemoDatePickerOpen, setMemoDatePickerOpen] = useState(false);

  const form = useForm({
    defaultValues: {
      action: "memo" as "memo" | "issue" | "parameter" | "dosing" | "task",
      selectedAquariumId: aquariums[0]?.id ?? "",
      memo: {
        text: "",
        photoUri: "",
        date: new Date(),
      },
      issue: {
        title: "",
      },
      parameter: {
        nitrate: "",
        ph: "",
      },
      dosing: {
        product: "",
        amount: "",
      },
      task: {
        templateId: "",
        note: "",
      },
    },
    onSubmit: ({ value }) => {
      if (!value.selectedAquariumId) {
        return;
      }

      if (value.action === "memo" && value.memo.text.trim()) {
        addMemo(
          value.selectedAquariumId,
          value.memo.text.trim(),
          value.memo.photoUri || undefined,
          value.memo.date.toISOString(),
        );
        form.setFieldValue("memo.text", "");
        form.setFieldValue("memo.photoUri", "");
        form.setFieldValue("memo.date", new Date());
      }

      if (value.action === "issue" && value.issue.title.trim()) {
        addIssue(value.selectedAquariumId, value.issue.title.trim());
        form.setFieldValue("issue.title", "");
      }

      if (value.action === "parameter") {
        const nitrateValue = Number(value.parameter.nitrate);
        const phValue = Number(value.parameter.ph);

        logParameters(value.selectedAquariumId, {
          nitrate: Number.isFinite(nitrateValue) ? nitrateValue : undefined,
          ph: Number.isFinite(phValue) ? phValue : undefined,
        });

        form.setFieldValue("parameter.nitrate", "");
        form.setFieldValue("parameter.ph", "");
      }

      if (value.action === "dosing") {
        const amountValue = Number(value.dosing.amount);

        if (
          value.dosing.product.trim() &&
          Number.isFinite(amountValue) &&
          amountValue > 0
        ) {
          logDosing(
            value.selectedAquariumId,
            value.dosing.product.trim(),
            amountValue,
          );
          form.setFieldValue("dosing.product", "");
          form.setFieldValue("dosing.amount", "");
        }
      }

      if (value.action === "task") {
        if (!value.task.templateId) {
          return;
        }

        completeTask(
          value.task.templateId,
          value.selectedAquariumId,
          value.task.note.trim() || undefined,
        );
        form.setFieldValue("task.templateId", "");
        form.setFieldValue("task.note", "");
      }

      setDialogOpen(false);
    },
  });

  const resetQuickLogForm = (aquariumId: string) => {
    form.setFieldValue("action", "memo");
    form.setFieldValue("selectedAquariumId", aquariumId);
    form.setFieldValue("memo.text", "");
    form.setFieldValue("memo.photoUri", "");
    form.setFieldValue("memo.date", new Date());
    form.setFieldValue("issue.title", "");
    form.setFieldValue("parameter.nitrate", "");
    form.setFieldValue("parameter.ph", "");
    form.setFieldValue("dosing.product", "");
    form.setFieldValue("dosing.amount", "");
    form.setFieldValue("task.templateId", "");
    form.setFieldValue("task.note", "");
  };

  useEffect(() => {
    const values = form.state.values;

    if (aquariums.length === 0) {
      if (values.selectedAquariumId !== "") {
        form.setFieldValue("selectedAquariumId", "");
      }
      return;
    }

    const selectedExists = aquariums.some(
      (aq) => aq.id === values.selectedAquariumId,
    );
    if (!selectedExists) {
      form.setFieldValue("selectedAquariumId", aquariums[0].id);
    }
  }, [aquariums, form]);

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

  const dueTasksByAquarium = useMemo(() => {
    const now = new Date();
    return aquariums.reduce<Record<string, typeof taskTemplates>>(
      (acc, aquarium) => {
        acc[aquarium.id] = taskTemplates.filter(
          (task) =>
            task.aquariumIds.includes(aquarium.id) &&
            isTaskDue(task, aquarium.id, taskExecutions, now),
        );
        return acc;
      },
      {},
    );
  }, [aquariums, taskExecutions, taskTemplates]);

  const pickMemoPhoto = async () => {
    const pickFromSource = async (source: "camera" | "library") => {
      setPickingMemoPhoto(true);
      try {
        const permission =
          source === "camera"
            ? await ImagePicker.requestCameraPermissionsAsync()
            : await ImagePicker.requestMediaLibraryPermissionsAsync();

        if (!permission.granted) {
          return;
        }

        const result =
          source === "camera"
            ? await ImagePicker.launchCameraAsync({
                mediaTypes: ["images"],
                allowsEditing: true,
                quality: 0.7,
              })
            : await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ["images"],
                allowsEditing: true,
                quality: 0.7,
              });

        if (!result.canceled && result.assets?.[0]?.uri) {
          form.setFieldValue("memo.photoUri", result.assets[0].uri);
        }
      } finally {
        setPickingMemoPhoto(false);
      }
    };

    Alert.alert("Add memo photo", "Choose photo source", [
      {
        text: "Take photo",
        onPress: () => {
          void pickFromSource("camera");
        },
      },
      {
        text: "Choose from library",
        onPress: () => {
          void pickFromSource("library");
        },
      },
      {
        text: "Cancel",
        style: "cancel",
      },
    ]);
  };

  const saveQuickLog = () => {
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
        onDismiss={() => {
          setDialogOpen(false);
          resetQuickLogForm(aquariums[0]?.id ?? "");
        }}
        title="Quick action"
        actions={
          <>
            <Button
              onPress={() => {
                setDialogOpen(false);
                resetQuickLogForm(aquariums[0]?.id ?? "");
              }}
            >
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.values}>
              {(values) => {
                const canSaveByAction: Record<typeof values.action, boolean> = {
                  memo: values.memo.text.trim().length > 0,
                  issue: values.issue.title.trim().length > 0,
                  parameter: true,
                  dosing:
                    values.dosing.product.trim().length > 0 &&
                    Number.isFinite(Number(values.dosing.amount)) &&
                    Number(values.dosing.amount) > 0,
                  task: values.task.templateId.length > 0,
                };

                return (
                  <Button
                    onPress={saveQuickLog}
                    disabled={
                      !values.selectedAquariumId ||
                      !canSaveByAction[values.action]
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
              density="small"
            />
          )}
        </form.Field>

        <form.Field name="action">
          {(field) => (
            <ScrollableSegmentedButtons
              value={field.state.value}
              onValueChange={(value) =>
                field.handleChange(
                  value as "memo" | "issue" | "parameter" | "dosing" | "task",
                )
              }
              style={styles.quickActionSelector}
              buttons={[
                { value: "task", label: "Task" },
                { value: "memo", label: "Memo" },
                { value: "issue", label: "Issue" },
                { value: "parameter", label: "Params" },
                { value: "dosing", label: "Dosing" },
              ]}
            />
          )}
        </form.Field>

        <form.Subscribe selector={(state) => state.values}>
          {(values) => {
            const dueTasksForSelectedAquarium =
              dueTasksByAquarium[values.selectedAquariumId] ?? [];

            return (
              <>
                {values.action === "task" ? (
                  <View style={styles.parameterInputs}>
                    <form.Field name="task.templateId">
                      {(field) => (
                        <ScrollableSegmentedButtons
                          value={field.state.value}
                          onValueChange={field.handleChange}
                          buttons={dueTasksForSelectedAquarium.map((task) => ({
                            label: task.title,
                            value: task.id,
                          }))}
                        />
                      )}
                    </form.Field>
                    {dueTasksForSelectedAquarium.length === 0 ? (
                      <Text variant="bodySmall">
                        No due tasks for this aquarium.
                      </Text>
                    ) : null}
                    <form.Field name="task.note">
                      {(field) => (
                        <TextInput
                          mode="outlined"
                          label="Completion note (optional)"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          multiline
                          numberOfLines={2}
                        />
                      )}
                    </form.Field>
                  </View>
                ) : null}

                {values.action === "memo" ? (
                  <View style={styles.parameterInputs}>
                    <form.Field name="memo.text">
                      {(field) => (
                        <TextInput
                          mode="outlined"
                          label="Memo"
                          multiline
                          numberOfLines={4}
                          value={field.state.value}
                          onChangeText={field.handleChange}
                        />
                      )}
                    </form.Field>
                    <Button
                      mode="contained-tonal"
                      onPress={pickMemoPhoto}
                      loading={isPickingMemoPhoto}
                    >
                      {values.memo.photoUri ? "Change photo" : "Attach photo"}
                    </Button>
                    <Button
                      mode="outlined"
                      icon="calendar"
                      onPress={() => setMemoDatePickerOpen(true)}
                    >
                      Log date: {values.memo.date.toLocaleDateString()}
                    </Button>
                    {values.memo.photoUri ? (
                      <Image
                        source={{ uri: values.memo.photoUri }}
                        style={styles.eventPhoto}
                      />
                    ) : null}
                  </View>
                ) : null}

                {values.action === "issue" ? (
                  <form.Field name="issue.title">
                    {(field) => (
                      <TextInput
                        mode="outlined"
                        label="Issue title"
                        value={field.state.value}
                        onChangeText={field.handleChange}
                        style={styles.quickActionInput}
                      />
                    )}
                  </form.Field>
                ) : null}

                {values.action === "parameter" ? (
                  <View style={styles.parameterInputs}>
                    <form.Field name="parameter.nitrate">
                      {(field) => (
                        <TextInput
                          mode="outlined"
                          label="Nitrate"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </form.Field>
                    <form.Field name="parameter.ph">
                      {(field) => (
                        <TextInput
                          mode="outlined"
                          label="pH"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </form.Field>
                  </View>
                ) : null}

                {values.action === "dosing" ? (
                  <View style={styles.parameterInputs}>
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
                  </View>
                ) : null}
              </>
            );
          }}
        </form.Subscribe>
      </BottomSheet>

      <form.Subscribe selector={(state) => state.values.memo.date}>
        {(memoDate) => (
          <DatePickerModal
            locale="en"
            mode="single"
            visible={isMemoDatePickerOpen}
            date={memoDate}
            onDismiss={() => setMemoDatePickerOpen(false)}
            onConfirm={({ date }) => {
              if (date) {
                form.setFieldValue("memo.date", date);
              }
              setMemoDatePickerOpen(false);
            }}
          />
        )}
      </form.Subscribe>

      <FAB
        icon="plus"
        label="Log"
        onPress={() => {
          resetQuickLogForm(aquariums[0]?.id ?? "");
          setDialogOpen(true);
        }}
        style={styles.fab}
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 132,
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
    borderRadius: 24,
  },
  eventPhoto: {
    width: "100%",
    height: 170,
    borderRadius: 18,
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
    bottom: 88,
  },
});
