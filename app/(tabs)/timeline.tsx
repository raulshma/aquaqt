import { useForm } from "@tanstack/react-form";
import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    FAB,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";
import { DatePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { getCardTextColorForBackground } from "@/components/ui/card-tone";
import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import {
    PhotoSourceDialog,
    type PhotoSource,
} from "@/components/ui/photo-source-dialog";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { getEntityHref, getTimelineEventTarget } from "@/services/entity-links";
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

const EVENT_LABELS: Record<TimelineEventType, string> = {
  task: "Task",
  parameter: "Parameters",
  issue: "Issue",
  livestock: "Livestock",
  memo: "Memo",
  dosing: "Dosing",
  asset: "Asset",
  consumable: "Consumable",
};

export default function TimelineScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { bottom } = useSafeAreaInsets();
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
  const [isPhotoSourceDialogOpen, setPhotoSourceDialogOpen] = useState(false);
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

  const aquariumPhotoById = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      if (aquarium.photoUri) {
        acc[aquarium.id] = aquarium.photoUri;
      }
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

  const pickMemoPhotoFromSource = async (source: PhotoSource) => {
    setPhotoSourceDialogOpen(false);
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

  const pickMemoPhoto = () => {
    setPhotoSourceDialogOpen(true);
  };

  const saveQuickLog = () => {
    void form.handleSubmit();
  };

  const getEventBackground = (eventType: TimelineEventType) => {
    switch (eventType) {
      case "issue":
        return theme.colors.errorContainer;
      case "parameter":
        return theme.colors.secondaryContainer;
      case "task":
        return theme.colors.primaryContainer;
      case "memo":
        return theme.colors.tertiaryContainer;
      case "livestock":
        return theme.colors.surfaceVariant;
      case "asset":
        return theme.colors.secondaryContainer;
      case "consumable":
        return theme.colors.primaryContainer;
      case "dosing":
        return theme.colors.tertiaryContainer;
      default:
        return theme.colors.surfaceVariant;
    }
  };

  return (
    <>
      <DashboardScrollView>
        <DashboardHero
          title="Unified Timeline"
          subtitle="Chronological activity across tasks, parameters, issues, and memos with the same dashboard framing."
          tone="tertiary"
          chips={
            <>
              <Chip compact icon="timeline-clock">
                {filteredTimeline.length} visible events
              </Chip>
              <Chip compact icon="filter-variant">
                {selectedFilter === "all" ? "All types" : selectedFilter}
              </Chip>
              <Chip compact icon="fish">
                {selectedAquariumFilter === "all"
                  ? "All tanks"
                  : (aquariumNameById[selectedAquariumFilter] ?? "1 tank")}
              </Chip>
            </>
          }
        />

        <DashboardSection
          title="Filters"
          description="Narrow the stream by event type and aquarium."
        >
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
        </DashboardSection>

        <DashboardSection
          title="Event stream"
          description="The latest cross-app activity, ordered most recent first."
        >
          {filteredTimeline.map((event) => {
            const eventImageUri =
              event.photoUri ||
              aquariumPhotoById[event.aquariumId] ||
              undefined;
            const backgroundColor = getEventBackground(event.type);
            const cardBackground = eventImageUri
              ? theme.colors.surface
              : backgroundColor;
            const textColor = getCardTextColorForBackground(
              theme,
              cardBackground,
            );
            const sourceTarget = getTimelineEventTarget(event);

            return (
              <Card
                key={event.id}
                style={[styles.eventCard, { backgroundColor: cardBackground }]}
                mode="contained"
                onPress={() =>
                  router.push(getEntityHref(sourceTarget) as never)
                }
              >
                {eventImageUri ? (
                  <View style={styles.eventPhotoShell}>
                    <Image
                      source={{ uri: eventImageUri }}
                      style={styles.eventPhoto}
                      contentFit="cover"
                    />
                    <LinearGradient
                      colors={["rgba(15,23,42,0.08)", "rgba(15,23,42,0.84)"]}
                      style={styles.eventPhotoOverlay}
                    />
                    <View style={styles.eventPhotoHeader}>
                      <View style={styles.eventPhotoTypePill}>
                        <Text
                          variant="labelSmall"
                          style={styles.eventPhotoTypeText}
                        >
                          {EVENT_LABELS[event.type]}
                        </Text>
                      </View>
                      <Text
                        variant="labelSmall"
                        style={styles.eventPhotoTimestamp}
                      >
                        {new Date(event.createdAt).toLocaleString()}
                      </Text>
                    </View>
                    <View style={styles.eventPhotoFooter}>
                      <Text
                        variant="labelLarge"
                        style={styles.eventPhotoAquarium}
                      >
                        {aquariumNameById[event.aquariumId] ?? "Unknown tank"}
                      </Text>
                    </View>
                  </View>
                ) : null}
                <Card.Content>
                  {!eventImageUri ? (
                    <View style={styles.eventHeader}>
                      <View style={[styles.eventTypePill, { backgroundColor }]}>
                        <Text
                          variant="labelSmall"
                          style={{
                            color: getCardTextColorForBackground(
                              theme,
                              backgroundColor,
                            ),
                          }}
                        >
                          {EVENT_LABELS[event.type]}
                        </Text>
                      </View>
                      <Text variant="labelSmall" style={{ color: textColor }}>
                        {new Date(event.createdAt).toLocaleString()}
                      </Text>
                    </View>
                  ) : null}
                  <Text
                    variant="titleSmall"
                    style={[styles.eventTitle, { color: textColor }]}
                  >
                    {event.title}
                  </Text>
                  {!eventImageUri ? (
                    <Text
                      variant="bodySmall"
                      style={[styles.aquariumName, { color: textColor }]}
                    >
                      {aquariumNameById[event.aquariumId] ?? "Unknown tank"}
                    </Text>
                  ) : null}
                  {event.description ? (
                    <Text variant="bodyMedium" style={{ color: textColor }}>
                      {event.description}
                    </Text>
                  ) : null}
                  <View style={styles.eventFooter}>
                    <Chip
                      compact
                      icon="arrow-top-right"
                      onPress={() =>
                        router.push(getEntityHref(sourceTarget) as never)
                      }
                    >
                      {sourceTarget.kind === "aquarium"
                        ? "Open aquarium"
                        : "Open linked record"}
                    </Chip>
                  </View>
                </Card.Content>
              </Card>
            );
          })}

          {filteredTimeline.length === 0 ? (
            <Card
              style={[
                styles.eventCard,
                { backgroundColor: theme.colors.surfaceVariant },
              ]}
              mode="contained"
            >
              <Card.Content>
                <Text variant="bodyMedium">
                  No timeline events for this filter yet.
                </Text>
              </Card.Content>
            </Card>
          ) : null}
        </DashboardSection>
      </DashboardScrollView>

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

      <PhotoSourceDialog
        visible={isPhotoSourceDialogOpen}
        title="Add memo photo"
        description="Choose photo source"
        hasCurrentPhoto={Boolean(form.state.values.memo.photoUri)}
        loading={isPickingMemoPhoto}
        onDismiss={() => setPhotoSourceDialogOpen(false)}
        onPickSource={(source) => {
          void pickMemoPhotoFromSource(source);
        }}
        onRemovePhoto={() => {
          form.setFieldValue("memo.photoUri", "");
          setPhotoSourceDialogOpen(false);
        }}
      />

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
        style={[
          styles.fab,
          {
            backgroundColor: theme.colors.primaryContainer,
            bottom: 88 + bottom,
          },
        ]}
        color={theme.colors.onPrimaryContainer}
      />
    </>
  );
}

const styles = StyleSheet.create({
  filterRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
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
    marginTop: 0,
    borderRadius: 24,
    overflow: "hidden",
  },
  eventPhoto: {
    width: "100%",
    height: 220,
  },
  eventPhotoShell: {
    position: "relative",
  },
  eventPhotoOverlay: {
    ...StyleSheet.absoluteFillObject,
  },
  eventPhotoHeader: {
    position: "absolute",
    top: 14,
    left: 14,
    right: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 10,
  },
  eventPhotoFooter: {
    position: "absolute",
    left: 14,
    right: 14,
    bottom: 14,
  },
  eventPhotoTypePill: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: "rgba(255,255,255,0.18)",
  },
  eventPhotoTypeText: {
    color: "#fff",
    fontWeight: "700",
  },
  eventPhotoTimestamp: {
    color: "rgba(255,255,255,0.9)",
    textAlign: "right",
    flex: 1,
  },
  eventPhotoAquarium: {
    color: "#fff",
    fontWeight: "700",
  },
  eventTypePill: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
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
  eventFooter: {
    marginTop: 10,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 88,
  },
});
