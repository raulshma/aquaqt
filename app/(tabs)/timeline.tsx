import { useForm } from "@tanstack/react-form";
import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import {
    Button,
    Chip,
    FAB,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";
import { DatePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { BottomSheet } from "@/components/ui/bottom-sheet";
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
import { TimelineEventType, TimelineEvent } from "@/types/aquapt";

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

function formatRelativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

function groupEventsByDate(events: TimelineEvent[]) {
  const groups: { label: string; events: TimelineEvent[] }[] = [];
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today.getTime() - 86400000);

  for (const event of events) {
    const d = new Date(event.createdAt);
    const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());

    let label: string;
    if (day.getTime() === today.getTime()) {
      label = "Today";
    } else if (day.getTime() === yesterday.getTime()) {
      label = "Yesterday";
    } else {
      label = day.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    }

    const last = groups[groups.length - 1];
    if (last && last.label === label) {
      last.events.push(event);
    } else {
      groups.push({ label, events: [event] });
    }
  }
  return groups;
}

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

  const getEventAccentColor = (eventType: TimelineEventType) => {
    switch (eventType) {
      case "issue":
        return theme.colors.error;
      case "parameter":
        return theme.colors.secondary;
      case "task":
        return theme.colors.primary;
      case "memo":
        return theme.colors.tertiary;
      case "livestock":
        return theme.colors.onSurfaceVariant;
      case "asset":
        return theme.colors.secondary;
      case "consumable":
        return theme.colors.primary;
      case "dosing":
        return theme.colors.tertiary;
      default:
        return theme.colors.onSurfaceVariant;
    }
  };

  const groupedEvents = useMemo(
    () => groupEventsByDate(filteredTimeline),
    [filteredTimeline],
  );

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
          {groupedEvents.map((group) => (
            <View key={group.label}>
              <Text
                variant="labelMedium"
                style={[
                  styles.feedDateHeader,
                  { color: theme.colors.onSurfaceVariant },
                ]}
              >
                {group.label}
              </Text>
              {group.events.map((event, index) => {
                const accentColor = getEventAccentColor(event.type);
                const sourceTarget = getTimelineEventTarget(event);
                const thumbUri =
                  event.photoUri || aquariumPhotoById[event.aquariumId];
                const isLast = index === group.events.length - 1;

                return (
                  <Pressable
                    key={event.id}
                    style={!isLast ? styles.feedDivider : undefined}
                    onPress={() =>
                      router.push(getEntityHref(sourceTarget) as never)
                    }
                  >
                    <View style={styles.feedRow}>
                      <View
                        style={[
                          styles.feedAccent,
                          { backgroundColor: accentColor },
                        ]}
                      />
                      {thumbUri ? (
                        <Image
                          source={{ uri: thumbUri }}
                          style={styles.feedThumbnail}
                          contentFit="cover"
                        />
                      ) : null}
                      <View style={styles.feedContent}>
                        <View style={styles.feedMeta}>
                          <Text
                            variant="labelSmall"
                            style={{ color: accentColor }}
                            numberOfLines={1}
                          >
                            {EVENT_LABELS[event.type]}
                          </Text>
                          <Text
                            variant="labelSmall"
                            style={{ color: theme.colors.onSurfaceVariant }}
                            numberOfLines={1}
                          >
                            {formatRelativeTime(event.createdAt)}
                          </Text>
                        </View>
                        <Text
                          variant="bodyMedium"
                          style={{ color: theme.colors.onSurface }}
                          numberOfLines={2}
                        >
                          {event.title}
                        </Text>
                        <Text
                          variant="bodySmall"
                          style={{ color: theme.colors.onSurfaceVariant }}
                          numberOfLines={1}
                        >
                          {aquariumNameById[event.aquariumId] ?? "Unknown tank"}
                          {event.description
                            ? ` · ${event.description}`
                            : ""}
                        </Text>
                      </View>
                    </View>
                  </Pressable>
                );
              })}
            </View>
          ))}

          {filteredTimeline.length === 0 ? (
            <Text
              variant="bodyMedium"
              style={{ color: theme.colors.onSurfaceVariant }}
            >
              No timeline events for this filter yet.
            </Text>
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
                        style={styles.memoPreviewPhoto}
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
  memoPreviewPhoto: {
    width: "100%",
    height: 160,
    borderRadius: 12,
  },
  feedDateHeader: {
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 6,
    marginTop: 4,
  },
  feedDivider: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "rgba(128,128,128,0.2)",
  },
  feedRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
    paddingVertical: 10,
  },
  feedAccent: {
    width: 3,
    height: 32,
    borderRadius: 2,
    marginTop: 2,
    flexShrink: 0,
  },
  feedThumbnail: {
    width: 44,
    height: 44,
    borderRadius: 8,
    flexShrink: 0,
  },
  feedContent: {
    flex: 1,
    gap: 2,
  },
  feedMeta: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 88,
  },
});
