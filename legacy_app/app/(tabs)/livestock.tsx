import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Image } from "expo-image";
import { Stack, useRouter } from "expo-router";
import { DatePickerModal } from "react-native-paper-dates";
import {
  type Dispatch,
  memo,
  type SetStateAction,
  useCallback,
  useMemo,
  useState,
} from "react";
import {
  LayoutAnimation,
  Pressable,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  View,
} from "react-native";
import {
  Button,
  Card,
  Chip,
  Surface,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { LivestockBackground } from "@/components/illustrations/AnimatedCardBackgrounds";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { withAlpha } from "@/constants/theme";
import { useAquapt } from "@/context/aquapt-context";
import { createEntityRef, getEntityHref } from "@/services/entity-links";
import { toIsoDate } from "@/services/scheduling";
import {
  type EntityRef,
  type Livestock,
  type LivestockKind,
  type TaskFrequency,
  type TaskTemplate,
  getFrequencyLabel,
} from "@/types/aquapt";

function getKindIcon(kind: LivestockKind): string {
  const mapping: Record<LivestockKind, string> = {
    fish: "fish",
    shrimp: "bug",
    snail: "snail",
    coral: "flower",
    plant: "leaf",
    other: "paw",
  };
  return mapping[kind] ?? "paw";
}

function getStatusColor(
  status: string,
  theme: { colors: { primary: string; error: string; tertiary: string } },
): string {
  if (status === "ill") return theme.colors.error;
  if (status === "deceased") return theme.colors.tertiary;
  return theme.colors.primary;
}

const LIVESTOCK_STATUS_BUTTONS = [
  { label: "Active", value: "active" },
  { label: "Ill", value: "ill" },
  { label: "Deceased", value: "deceased" },
];

const FEEDING_TASK_FREQUENCY_BUTTONS = [
  { label: "Daily", value: "daily" },
  { label: "Weekly", value: "weekly" },
  { label: "Bi-weekly", value: "bi-weekly" },
  { label: "Monthly", value: "monthly" },
  { label: "Custom", value: "custom" },
];

type FeedingFrequency = TaskFrequency | "custom";

function formatAge(acquiredAt: string): string {
  const acquired = new Date(acquiredAt);
  if (Number.isNaN(acquired.getTime())) return "";
  const now = new Date();
  if (now < acquired) return "";
  let years = now.getFullYear() - acquired.getFullYear();
  let months = now.getMonth() - acquired.getMonth();
  let days = now.getDate() - acquired.getDate();
  if (days < 0) {
    months -= 1;
    const prevMonth = new Date(now.getFullYear(), now.getMonth(), 0);
    days += prevMonth.getDate();
  }
  if (months < 0) {
    years -= 1;
    months += 12;
  }
  const totalDays = Math.floor(
    (now.getTime() - acquired.getTime()) / 86400000,
  );
  if (totalDays < 1) return "Today";
  if (totalDays === 1) return "1 day";
  if (totalDays < 14) return `${totalDays} days`;
  if (totalDays < 30) {
    const weeks = Math.floor(totalDays / 7);
    return weeks === 1 ? "1 week" : `${weeks} weeks`;
  }
  const parts: string[] = [];
  if (years > 0) parts.push(years === 1 ? "1 year" : `${years} years`);
  if (months > 0) parts.push(months === 1 ? "1 month" : `${months} months`);
  if (years === 0 && days > 0)
    parts.push(days === 1 ? "1 day" : `${days} days`);
  return parts.join(", ");
}

type LivestockChipProps = {
  item: Livestock;
  aquariumName: string;
  isSelected: boolean;
  onSelect: () => void;
};

const LivestockChip = memo(function LivestockChip({
  item,
  aquariumName,
  isSelected,
  onSelect,
}: LivestockChipProps) {
  const theme = useTheme();
  const status = item.status ?? "active";
  const statusColor = getStatusColor(status, theme);

  return (
    <Pressable onPress={onSelect}>
      <Surface
        elevation={isSelected ? 2 : 0}
        style={[
          styles.chipCard,
          {
            backgroundColor: isSelected
              ? theme.colors.primaryContainer
              : theme.colors.surface,
            borderColor: isSelected
              ? theme.colors.primary
              : withAlpha(theme.colors.onSurface, 0.12),
          },
        ]}
      >
        <View style={styles.chipContent}>
          <View style={styles.chipHeader}>
            <MaterialCommunityIcons
              name={getKindIcon(item.kind) as any}
              size={18}
              color={isSelected ? theme.colors.onPrimaryContainer : theme.colors.onSurface}
            />
            <Text
              variant="labelLarge"
              numberOfLines={1}
              style={[
                styles.chipName,
                {
                  color: isSelected
                    ? theme.colors.onPrimaryContainer
                    : theme.colors.onSurface,
                },
              ]}
            >
              {item.name}
            </Text>
          </View>
          <Text
            variant="bodySmall"
            numberOfLines={1}
            style={[
              styles.chipSpecies,
              {
                color: isSelected
                  ? theme.colors.onPrimaryContainer
                  : theme.colors.onSurfaceVariant,
              },
            ]}
          >
            {item.species} ({item.quantity})
          </Text>
          <View style={styles.chipFooter}>
            <View
                  style={[
                    styles.statusDot,
                    { backgroundColor: statusColor },
                  ]}
                />
                <Text
                  variant="labelSmall"
                  style={{ color: statusColor, textTransform: "capitalize" }}
            >
              {status}
            </Text>
          </View>
        </View>
      </Surface>
    </Pressable>
  );
});

type FormSectionProps = {
  icon: string;
  title: string;
  subtitle: string;
  isDirty?: boolean;
  defaultOpen?: boolean;
  badge?: string;
  badgeColor?: string;
  children: React.ReactNode;
};

function FormSection({
  icon,
  title,
  subtitle,
  isDirty,
  defaultOpen = false,
  badge,
  badgeColor,
  children,
}: FormSectionProps) {
  const theme = useTheme();
  const [isOpen, setOpen] = useState(defaultOpen);

  const toggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setOpen((prev) => !prev);
  }, []);

  return (
    <View
      style={[
        formSectionStyles.card,
        { backgroundColor: withAlpha(theme.colors.onSurface, 0.03) },
      ]}
    >
      <Pressable
        onPress={toggle}
        style={[formSectionStyles.header, { minHeight: 52 }]}
      >
        <View style={formSectionStyles.headerLeft}>
          <View
            style={[
              formSectionStyles.iconCircle,
              { backgroundColor: withAlpha(theme.colors.primary, 0.12) },
            ]}
          >
            <MaterialCommunityIcons
              name={icon as any}
              size={20}
              color={theme.colors.primary}
            />
          </View>
          <View style={formSectionStyles.headerText}>
            <View style={formSectionStyles.titleRow}>
              <Text variant="labelLarge" style={{ color: theme.colors.onSurface }}>
                {title}
              </Text>
              {badge ? (
                <View
                  style={[
                    formSectionStyles.miniBadge,
                    {
                      backgroundColor:
                        badgeColor ?? withAlpha(theme.colors.primary, 0.15),
                    },
                  ]}
                >
                  <Text
                    variant="labelSmall"
                    style={{
                      color: badgeColor
                        ? theme.colors.onPrimary
                        : theme.colors.primary,
                      fontSize: 10,
                    }}
                  >
                    {badge}
                  </Text>
                </View>
              ) : null}
              {isDirty && !badge ? (
                <View
                  style={[
                    formSectionStyles.dirtyDot,
                    { backgroundColor: theme.colors.primary },
                  ]}
                />
              ) : null}
            </View>
            <Text
              variant="bodySmall"
              style={{ color: theme.colors.onSurfaceVariant, lineHeight: 16 }}
              numberOfLines={isOpen ? 0 : 1}
            >
              {subtitle}
            </Text>
          </View>
        </View>
        <MaterialCommunityIcons
          name={isOpen ? "chevron-up" : "chevron-down"}
          size={22}
          color={theme.colors.onSurfaceVariant}
        />
      </Pressable>
      {isOpen ? (
        <View style={formSectionStyles.body}>{children}</View>
      ) : null}
    </View>
  );
}

const formSectionStyles = StyleSheet.create({
  card: {
    borderRadius: 20,
    overflow: "hidden",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerLeft: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  iconCircle: {
    width: 38,
    height: 38,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  headerText: {
    flex: 1,
    gap: 2,
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  miniBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 8,
  },
  dirtyDot: {
    width: 7,
    height: 7,
    borderRadius: 7,
  },
  body: {
    paddingHorizontal: 16,
    paddingBottom: 16,
    gap: 12,
  },
});

type LivestockDetailPanelProps = {
  item: Livestock;
  aquariumName: string;
  fallbackTargetId?: string;
  cardBackground: string;
  parentEntity?: { id: string; name: string; aquariumId: string };
  offspringEntities: { id: string; name: string; aquariumId: string }[];
  feedingTasks: TaskTemplate[];
  feedingNote: string;
  livestockStatus: NonNullable<Livestock["status"]>;
  livestockStatusNote: string;
  feedingTaskTitle: string;
  feedingTaskFrequency: FeedingFrequency;
  feedingTaskStartDate: string;
  feedingTaskStartDateValue: Date;
  feedingTaskTimesPerDay: string;
  feedingTaskCustomDays: string;
  isStartDatePickerOpen: boolean;
  openEntity: (ref: EntityRef) => void;
  setFeedingNoteDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setLivestockStatusDraft: Dispatch<
    SetStateAction<Record<string, NonNullable<Livestock["status"]>>>
  >;
  setLivestockStatusNoteDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setFeedingTaskTitleDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setFeedingTaskFrequencyDraft: Dispatch<
    SetStateAction<Record<string, FeedingFrequency>>
  >;
  setFeedingTaskStartDateDraft: Dispatch<
    SetStateAction<Record<string, string>>
  >;
  setFeedingTaskStartDateValueDraft: Dispatch<
    SetStateAction<Record<string, Date>>
  >;
  setFeedingTaskTimesPerDayDraft: Dispatch<
    SetStateAction<Record<string, string>>
  >;
  setFeedingTaskCustomDaysDraft: Dispatch<
    SetStateAction<Record<string, string>>
  >;
  setStartDatePickerOpenDraft: Dispatch<
    SetStateAction<Record<string, boolean>>
  >;
  setLivestockFeedingNotes: (livestockId: string, dietaryNotes: string) => void;
  setLivestockStatus: (
    livestockId: string,
    status: NonNullable<Livestock["status"]>,
    note?: string,
  ) => void;
  transferLivestock: (
    livestockId: string,
    targetAquariumId: string,
    note?: string,
  ) => void;
  addOffspring: (
    parentLivestockId: string,
    input: Omit<Livestock, "id" | "parentId" | "aquariumId"> & {
      aquariumId?: string;
    },
  ) => void;
  addLivestockFeedingTask: (input: {
    livestockId: string;
    title: string;
    frequency: TaskFrequency;
    description?: string;
    startDate?: string;
    timesPerDay?: number;
  }) => void;
  onCollapse: () => void;
};

const LivestockDetailPanel = memo(function LivestockDetailPanel({
  item,
  aquariumName,
  fallbackTargetId,
  cardBackground,
  parentEntity,
  offspringEntities,
  feedingTasks,
  feedingNote,
  livestockStatus,
  livestockStatusNote,
  feedingTaskTitle,
  feedingTaskFrequency,
  feedingTaskStartDate,
  feedingTaskStartDateValue,
  feedingTaskTimesPerDay,
  feedingTaskCustomDays,
  isStartDatePickerOpen,
  openEntity,
  setFeedingNoteDraft,
  setLivestockStatusDraft,
  setLivestockStatusNoteDraft,
  setFeedingTaskTitleDraft,
  setFeedingTaskFrequencyDraft,
  setFeedingTaskStartDateDraft,
  setFeedingTaskStartDateValueDraft,
  setFeedingTaskTimesPerDayDraft,
  setFeedingTaskCustomDaysDraft,
  setStartDatePickerOpenDraft,
  setLivestockFeedingNotes,
  setLivestockStatus,
  transferLivestock,
  addOffspring,
  addLivestockFeedingTask,
  onCollapse,
}: LivestockDetailPanelProps) {
  const theme = useTheme();
  const textColor = theme.colors.onSurface;

  return (
    <Card
      style={[styles.detailCard, { backgroundColor: cardBackground }]}
      mode="contained"
    >
      <Card.Content style={styles.detailContent}>
        <View style={styles.detailHeader}>
          <View style={styles.detailTitleRow}>
            <Text variant="titleLarge" style={{ color: textColor }}>
              {item.name}
            </Text>
            <Chip
              compact
              style={{ backgroundColor: theme.colors.surface }}
              textStyle={{ color: theme.colors.onSurface }}
              onPress={onCollapse}
            >
              Close
            </Chip>
          </View>
          <Text
            variant="bodyMedium"
            style={[styles.detailMeta, { color: textColor }]}
          >
            {item.species} &middot; Age: {formatAge(item.acquiredAt)} &middot;{" "}
            {item.quantity} &middot; {aquariumName}
          </Text>
        </View>

        <View style={styles.chipRow}>
          <Chip
            compact
            icon={getKindIcon(item.kind) as any}
            style={{ backgroundColor: theme.colors.surface }}
            textStyle={{ color: theme.colors.onSurface }}
          >
            {item.kind}
          </Chip>
          <Chip
            compact
            style={{ backgroundColor: theme.colors.surface }}
            textStyle={{ color: theme.colors.onSurface }}
          >
            {item.status ?? "active"}
          </Chip>
          <Chip
            compact
            icon="fishbowl"
            style={{ backgroundColor: theme.colors.surface }}
            textStyle={{ color: theme.colors.onSurface }}
            onPress={() =>
              openEntity(
                createEntityRef("aquarium", item.aquariumId, item.aquariumId),
              )
            }
          >
            {aquariumName}
          </Chip>
          {parentEntity ? (
            <Chip
              compact
              icon="family-tree"
              style={{ backgroundColor: theme.colors.surface }}
              textStyle={{ color: theme.colors.onSurface }}
              onPress={() =>
                openEntity(
                  createEntityRef(
                    "livestock",
                    parentEntity.id,
                    parentEntity.aquariumId,
                  ),
                )
              }
            >
              Parent: {parentEntity.name}
            </Chip>
          ) : null}
          {offspringEntities.map((offspring) => (
            <Chip
              key={offspring.id}
              compact
              icon="baby-face-outline"
              style={{ backgroundColor: theme.colors.surface }}
              textStyle={{ color: theme.colors.onSurface }}
              onPress={() =>
                openEntity(
                  createEntityRef(
                    "livestock",
                    offspring.id,
                    offspring.aquariumId,
                  ),
                )
              }
            >
              {offspring.name}
            </Chip>
          ))}
          {feedingTasks.map((task) => (
            <Chip
              key={task.id}
              compact
              icon="wrench"
              style={{ backgroundColor: theme.colors.surface }}
              textStyle={{ color: theme.colors.onSurface }}
              onPress={() =>
                openEntity(createEntityRef("task", task.id, item.aquariumId))
              }
            >
              {task.title}
            </Chip>
          ))}
        </View>

        {item.photoUri ? (
          <Image
            source={{ uri: item.photoUri }}
            style={styles.detailPhoto}
            contentFit="cover"
          />
        ) : null}

        <FormSection
          icon="food-drumstick"
          title="Feeding notes"
          subtitle={
            item.dietaryNotes
              ? `Current: "${item.dietaryNotes.length > 60 ? item.dietaryNotes.slice(0, 60) + "…" : item.dietaryNotes}"`
              : "Record dietary requirements and observations"
          }
          isDirty={
            feedingNote.trim() !== (item.dietaryNotes ?? "") &&
            feedingNote.trim().length > 0
          }
          defaultOpen={!item.dietaryNotes}
          badge={
            item.dietaryNotes
              ? undefined
              : undefined
          }
        >
          {item.dietaryNotes && feedingNote.trim() !== item.dietaryNotes ? (
            <View
              style={[
                detailStyles.savedBanner,
                { backgroundColor: withAlpha(theme.colors.tertiary, 0.08) },
              ]}
            >
              <MaterialCommunityIcons
                name="note-text"
                size={16}
                color={theme.colors.tertiary}
              />
              <Text
                variant="bodySmall"
                style={{ color: theme.colors.onTertiaryContainer, flex: 1 }}
                numberOfLines={2}
              >
                Saved: {item.dietaryNotes}
              </Text>
            </View>
          ) : null}
          <View style={detailStyles.inputWithCount}>
            <TextInput
              mode="outlined"
              label="Feeding notes"
              value={feedingNote}
              onChangeText={(value) =>
                setFeedingNoteDraft((prev) => ({
                  ...prev,
                  [item.id]: value,
                }))
              }
              multiline
              numberOfLines={3}
              outlineColor={theme.colors.outline}
              activeOutlineColor={theme.colors.primary}
              textColor={theme.colors.onSurface}
            />
            <Text
              variant="labelSmall"
              style={{
                color:
                  feedingNote.length > 500
                    ? theme.colors.error
                    : theme.colors.onSurfaceVariant,
                alignSelf: "flex-end",
                marginTop: 2,
              }}
            >
              {feedingNote.length}/500
            </Text>
          </View>
          <Button
            mode="contained"
            icon="content-save"
            onPress={() =>
              setLivestockFeedingNotes(item.id, feedingNote.trim())
            }
            disabled={!feedingNote.trim()}
            style={detailStyles.sectionAction}
          >
            Save feeding notes
          </Button>
        </FormSection>

        <FormSection
          icon="heart-pulse"
          title="Health status"
          subtitle={`Current: ${item.status ?? "active"}${getStatusColor(item.status ?? "active", theme) === theme.colors.error ? " — needs attention" : ""}`}
          isDirty={
            livestockStatus !== (item.status ?? "active") ||
            livestockStatusNote.trim().length > 0
          }
          badge={item.status === "ill" ? "Ill" : item.status === "deceased" ? "Lost" : undefined}
          badgeColor={
            item.status === "ill"
              ? withAlpha(theme.colors.error, 0.2)
              : item.status === "deceased"
                ? withAlpha(theme.colors.tertiary, 0.2)
                : undefined
          }
        >
          <View
            style={[
              detailStyles.currentStatusRow,
              { backgroundColor: withAlpha(theme.colors.surface, 0.6) },
            ]}
          >
            <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
              Current status
            </Text>
            <View style={detailStyles.statusChipRow}>
              <View
                style={[
                  detailStyles.statusIndicator,
                  { backgroundColor: getStatusColor(item.status ?? "active", theme) },
                ]}
              />
              <Text
                variant="labelLarge"
                style={{
                  color: getStatusColor(item.status ?? "active", theme),
                  textTransform: "capitalize",
                }}
              >
                {item.status ?? "active"}
              </Text>
            </View>
          </View>
          <ScrollableSegmentedButtons
            value={livestockStatus}
            onValueChange={(value) =>
              setLivestockStatusDraft((prev) => ({
                ...prev,
                [item.id]: value as NonNullable<Livestock["status"]>,
              }))
            }
            buttons={LIVESTOCK_STATUS_BUTTONS}
          />
          <TextInput
            mode="outlined"
            label="Status note (optional)"
            value={livestockStatusNote}
            onChangeText={(value) =>
              setLivestockStatusNoteDraft((prev) => ({
                ...prev,
                [item.id]: value,
              }))
            }
            multiline
            numberOfLines={2}
            outlineColor={theme.colors.outline}
            activeOutlineColor={theme.colors.primary}
            textColor={theme.colors.onSurface}
            placeholder="Reason for status change…"
          />
          <View style={detailStyles.actionRow}>
            <Button
              mode="contained"
              icon="check-circle"
              onPress={() =>
                setLivestockStatus(
                  item.id,
                  livestockStatus,
                  livestockStatusNote.trim() || undefined,
                )
              }
              style={detailStyles.actionPrimary}
            >
              Update status
            </Button>
            <Button
              mode="outlined"
              icon="swap-horizontal"
              disabled={
                !fallbackTargetId || fallbackTargetId === item.aquariumId
              }
              onPress={() =>
                fallbackTargetId
                  ? transferLivestock(
                      item.id,
                      fallbackTargetId,
                      "Manual transfer",
                    )
                  : undefined
              }
            >
              Transfer
            </Button>
            <Button
              mode="outlined"
              icon="baby-carriage"
              onPress={() =>
                addOffspring(item.id, {
                  kind: item.kind,
                  name: `${item.name} offspring`,
                  species: item.species,
                  quantity: 1,
                  acquiredAt: new Date().toISOString(),
                  status: "active",
                })
              }
            >
              Offspring
            </Button>
          </View>
        </FormSection>

        <FormSection
          icon="calendar-clock"
          title="Feeding schedule"
          subtitle={
            feedingTasks.length > 0
              ? `${feedingTasks.length} schedule${feedingTasks.length === 1 ? "" : "s"} configured`
              : "Create a recurring feeding task"
          }
          isDirty={
            feedingTaskTitle.trim().length > 0 ||
            feedingTaskTimesPerDay.trim().length > 0 ||
            feedingTaskStartDate.trim().length > 0
          }
          defaultOpen={feedingTasks.length === 0}
          badge={feedingTasks.length > 0 ? `${feedingTasks.length}` : undefined}
        >
          {feedingTasks.length > 0 ? (
            <View style={detailStyles.existingTasksList}>
              {feedingTasks.map((task) => (
                <Pressable
                  key={task.id}
                  onPress={() =>
                    openEntity(
                      createEntityRef("task", task.id, item.aquariumId),
                    )
                  }
                >
                  <View
                    style={[
                      detailStyles.taskRow,
                      { borderBottomColor: withAlpha(theme.colors.onSurface, 0.06) },
                    ]}
                  >
                    <View style={detailStyles.taskInfo}>
                      <MaterialCommunityIcons
                        name="clock-outline"
                        size={16}
                        color={theme.colors.onSurfaceVariant}
                      />
                      <View style={detailStyles.taskText}>
                        <Text variant="bodyMedium" style={{ color: theme.colors.onSurface }}>
                          {task.title}
                        </Text>
                        <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
                          {getFrequencyLabel(task.frequency)}
                          {task.timesPerDay && task.timesPerDay > 1
                            ? ` • ${task.timesPerDay}x/day`
                            : ""}
                        </Text>
                      </View>
                    </View>
                    <MaterialCommunityIcons
                      name="chevron-right"
                      size={20}
                      color={theme.colors.onSurfaceVariant}
                    />
                  </View>
                </Pressable>
              ))}
            </View>
          ) : null}

          <View
            style={[
              detailStyles.newTaskCard,
              { backgroundColor: withAlpha(theme.colors.surface, 0.6) },
            ]}
          >
            <Text
              variant="labelSmall"
              style={{
                color: theme.colors.primary,
                marginBottom: 8,
              }}
            >
              {feedingTasks.length > 0 ? "Add another schedule" : "New schedule"}
            </Text>
            <TextInput
              mode="outlined"
              label="Task title"
              value={feedingTaskTitle}
              onChangeText={(value) =>
                setFeedingTaskTitleDraft((prev) => ({
                  ...prev,
                  [item.id]: value,
                }))
              }
              placeholder={`Feed ${item.name}`}
              outlineColor={theme.colors.outline}
              activeOutlineColor={theme.colors.primary}
              textColor={theme.colors.onSurface}
              dense
            />
            <ScrollableSegmentedButtons
              value={feedingTaskFrequency}
              onValueChange={(value) =>
                setFeedingTaskFrequencyDraft((prev) => ({
                  ...prev,
                  [item.id]: value as FeedingFrequency,
                }))
              }
              buttons={FEEDING_TASK_FREQUENCY_BUTTONS}
            />
            {feedingTaskFrequency === "custom" ? (
              <TextInput
                mode="outlined"
                label="Repeat every N days"
                value={feedingTaskCustomDays}
                onChangeText={(value) =>
                  setFeedingTaskCustomDaysDraft((prev) => ({
                    ...prev,
                    [item.id]: value.replace(/[^0-9]/g, ""),
                  }))
                }
                keyboardType="numeric"
                placeholder="e.g. 3"
                outlineColor={theme.colors.outline}
                activeOutlineColor={theme.colors.primary}
                textColor={theme.colors.onSurface}
                dense
                left={<TextInput.Affix text="Every" />}
                right={<TextInput.Affix text="days" />}
              />
            ) : null}
            <View style={detailStyles.inlineInputs}>
              <TextInput
                mode="outlined"
                label="Times/day"
                value={feedingTaskTimesPerDay}
                onChangeText={(value) =>
                  setFeedingTaskTimesPerDayDraft((prev) => ({
                    ...prev,
                    [item.id]: value,
                  }))
                }
                keyboardType="numeric"
                style={{ flex: 1 }}
                placeholder="1"
                outlineColor={theme.colors.outline}
                activeOutlineColor={theme.colors.primary}
                textColor={theme.colors.onSurface}
                dense
              />
              <TouchableOpacity
                onPress={() =>
                  setStartDatePickerOpenDraft((prev) => ({
                    ...prev,
                    [item.id]: true,
                  }))
                }
                style={{ flex: 2 }}
              >
                <View pointerEvents="none">
                  <TextInput
                    mode="outlined"
                    label="Start date"
                    value={
                      feedingTaskStartDate
                        ? new Date(feedingTaskStartDate + "T00:00:00").toLocaleDateString()
                        : ""
                    }
                    outlineColor={theme.colors.outline}
                    activeOutlineColor={theme.colors.primary}
                    textColor={theme.colors.onSurface}
                    dense
                    editable={false}
                    right={
                      <TextInput.Icon icon="calendar" />
                    }
                  />
                </View>
              </TouchableOpacity>
            </View>
            <DatePickerModal
              locale="en"
              mode="single"
              visible={isStartDatePickerOpen}
              date={feedingTaskStartDateValue}
              onDismiss={() =>
                setStartDatePickerOpenDraft((prev) => ({
                  ...prev,
                  [item.id]: false,
                }))
              }
              onConfirm={({ date }) => {
                if (date) {
                  setFeedingTaskStartDateDraft((prev) => ({
                    ...prev,
                    [item.id]: toIsoDate(date),
                  }));
                  setFeedingTaskStartDateValueDraft((prev) => ({
                    ...prev,
                    [item.id]: date,
                  }));
                }
                setStartDatePickerOpenDraft((prev) => ({
                  ...prev,
                  [item.id]: false,
                }));
              }}
            />
            <Button
              mode="contained-tonal"
              icon="plus-circle"
              style={detailStyles.sectionAction}
              disabled={
                feedingTaskFrequency === "custom" &&
                (Number(feedingTaskCustomDays) < 1 ||
                  !Number.isFinite(Number(feedingTaskCustomDays)))
              }
              onPress={() => {
                const customTitle = feedingTaskTitle.trim();
                const timesPerDay = Math.max(
                  1,
                  Math.floor(Number(feedingTaskTimesPerDay) || 1),
                );
                const startDateValue =
                  feedingTaskStartDate.trim() || toIsoDate(new Date());

                let resolvedFrequency: TaskFrequency;
                if (feedingTaskFrequency === "custom") {
                  const customDays = Math.max(
                    1,
                    Math.floor(Number(feedingTaskCustomDays) || 1),
                  );
                  resolvedFrequency = `custom-${customDays}`;
                } else {
                  resolvedFrequency = feedingTaskFrequency;
                }

                addLivestockFeedingTask({
                  livestockId: item.id,
                  title: customTitle || `Feed ${item.name}`,
                  frequency: resolvedFrequency,
                  description:
                    feedingNote.trim() ||
                    item.dietaryNotes ||
                    `Targeted feeding regimen for ${item.name}`,
                  startDate: startDateValue,
                  timesPerDay:
                    resolvedFrequency === "daily" ? timesPerDay : undefined,
                });

                setFeedingTaskTitleDraft((prev) => ({
                  ...prev,
                  [item.id]: "",
                }));
                setFeedingTaskTimesPerDayDraft((prev) => ({
                  ...prev,
                  [item.id]: "",
                }));
                setFeedingTaskStartDateDraft((prev) => ({
                  ...prev,
                  [item.id]: "",
                }));
                setFeedingTaskCustomDaysDraft((prev) => ({
                  ...prev,
                  [item.id]: "",
                }));
                setFeedingTaskFrequencyDraft((prev) => ({
                  ...prev,
                  [item.id]: "daily",
                }));
              }}
            >
              Create feeding task
            </Button>
          </View>
        </FormSection>

        <Button
          mode="text"
          icon="open-in-new"
          onPress={() =>
            openEntity(createEntityRef("livestock", item.id, item.aquariumId))
          }
          style={styles.viewDetailButton}
        >
          View full details
        </Button>
      </Card.Content>
    </Card>
  );
});

export default function LivestockScreen() {
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  const router = useRouter();
  const {
    aquariums,
    livestock,
    taskTemplates,
    transferLivestock,
    addOffspring,
    addLivestockFeedingTask,
    setLivestockFeedingNotes,
    setLivestockStatus,
  } = useAquapt();

  const [selectedLivestockId, setSelectedLivestockId] = useState<string | null>(
    null,
  );
  const [filterAquariumId, setFilterAquariumId] = useState<string>("all");
  const [feedingNoteDraft, setFeedingNoteDraft] = useState<
    Record<string, string>
  >({});
  const [livestockStatusDraft, setLivestockStatusDraft] = useState<
    Record<string, NonNullable<Livestock["status"]>>
  >({});
  const [livestockStatusNoteDraft, setLivestockStatusNoteDraft] = useState<
    Record<string, string>
  >({});
  const [feedingTaskTitleDraft, setFeedingTaskTitleDraft] = useState<
    Record<string, string>
  >({});
  const [feedingTaskFrequencyDraft, setFeedingTaskFrequencyDraft] = useState<
    Record<string, FeedingFrequency>
  >({});
  const [feedingTaskStartDateDraft, setFeedingTaskStartDateDraft] = useState<
    Record<string, string>
  >({});
  const [feedingTaskTimesPerDayDraft, setFeedingTaskTimesPerDayDraft] =
    useState<Record<string, string>>({});
  const [feedingTaskCustomDaysDraft, setFeedingTaskCustomDaysDraft] = useState<
    Record<string, string>
  >({});
  const [feedingTaskStartDateValueDraft, setFeedingTaskStartDateValueDraft] =
    useState<Record<string, Date>>({});
  const [startDatePickerOpenDraft, setStartDatePickerOpenDraft] = useState<
    Record<string, boolean>
  >({});

  const openEntity = useCallback(
    (ref: EntityRef) => {
      router.push(getEntityHref(ref) as never);
    },
    [router],
  );

  const aquariumNameById = useMemo(
    () =>
      aquariums.reduce<Record<string, string>>((acc, aquarium) => {
        acc[aquarium.id] = aquarium.name;
        return acc;
      }, {}),
    [aquariums],
  );

  const aquariumIndexById = useMemo(
    () =>
      aquariums.reduce<Record<string, number>>((acc, aquarium, index) => {
        acc[aquarium.id] = index;
        return acc;
      }, {}),
    [aquariums],
  );

  const filteredLivestock = useMemo(() => {
    if (filterAquariumId === "all") return livestock;
    return livestock.filter((item) => item.aquariumId === filterAquariumId);
  }, [livestock, filterAquariumId]);

  const selectedLivestock = useMemo(
    () => livestock.find((item) => item.id === selectedLivestockId) ?? null,
    [livestock, selectedLivestockId],
  );

  const selectedLivestockParent = useMemo(() => {
    if (!selectedLivestock?.parentId) return undefined;
    const parent = livestock.find(
      (candidate) => candidate.id === selectedLivestock.parentId,
    );
    return parent
      ? {
          id: parent.id,
          name: parent.name,
          aquariumId: parent.aquariumId,
        }
      : undefined;
  }, [selectedLivestock, livestock]);

  const selectedLivestockOffspring = useMemo(() => {
    if (!selectedLivestock) return [];
    return livestock
      .filter((candidate) => candidate.parentId === selectedLivestock.id)
      .map((offspring) => ({
        id: offspring.id,
        name: offspring.name,
        aquariumId: offspring.aquariumId,
      }));
  }, [selectedLivestock, livestock]);

  const selectedFeedingTasks = useMemo(() => {
    if (!selectedLivestock) return [];
    return taskTemplates.filter(
      (task) => task.livestockId === selectedLivestock.id,
    );
  }, [selectedLivestock, taskTemplates]);

  const selectedFallbackTargetId = useMemo(() => {
    if (!selectedLivestock) return undefined;
    const currentIndex =
      aquariumIndexById[selectedLivestock.aquariumId] ?? -1;
    return aquariums[
      (currentIndex + 1 + aquariums.length) % aquariums.length
    ]?.id;
  }, [selectedLivestock, aquariumIndexById, aquariums]);

  const totalActive = useMemo(
    () =>
      livestock.filter((item) => (item.status ?? "active") === "active")
        .length,
    [livestock],
  );
  const totalIll = useMemo(
    () => livestock.filter((item) => item.status === "ill").length,
    [livestock],
  );
  const totalDeceased = useMemo(
    () => livestock.filter((item) => item.status === "deceased").length,
    [livestock],
  );

  const handleChipSelect = useCallback(
    (id: string) => {
      setSelectedLivestockId((prev) => (prev === id ? null : id));
    },
    [],
  );

  const handleCollapse = useCallback(() => {
    setSelectedLivestockId(null);
  }, []);

  const detailBackground = useMemo(() => {
    return theme.colors.surface;
  }, [theme]);

  return (
    <>
      <Stack.Screen options={{ title: "Livestock" }} />
      <ScrollView
        contentContainerStyle={[
          styles.container,
          { paddingTop: 16 + insets.top },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <Card
          mode="elevated"
          style={[
            styles.heroCard,
            { backgroundColor: theme.colors.surface },
          ]}
        >
          <Card.Content style={styles.heroContent}>
            <View style={styles.heroBackground}>
              <LivestockBackground
                tint={withAlpha(theme.colors.primary, 0.12)}
              />
            </View>
            <View style={styles.heroHeader}>
              <Text
                variant="headlineMedium"
                style={{ color: theme.colors.onSurface }}
              >
                Livestock
              </Text>
              <Text
                variant="bodyMedium"
                style={[
                  styles.heroSubtitle,
                  { color: theme.colors.onSurfaceVariant },
                ]}
              >
                {livestock.length > 0
                  ? `${livestock.length} record${livestock.length === 1 ? "" : "s"} across ${aquariums.length} tank${aquariums.length === 1 ? "" : "s"}`
                  : "Add your first residents to start tracking."}
              </Text>
            </View>
            <View style={styles.statRow}>
              <View
                style={[
                  styles.statBadge,
                  { backgroundColor: theme.colors.surface },
                ]}
              >
                <Text variant="labelSmall" style={{ color: theme.colors.onSurface }}>
                  {totalActive} active
                </Text>
              </View>
              {totalIll > 0 ? (
                <View
                  style={[
                    styles.statBadge,
                    { backgroundColor: withAlpha(theme.colors.error, 0.12) },
                  ]}
                >
                  <Text
                    variant="labelSmall"
                    style={{ color: theme.colors.error }}
                  >
                    {totalIll} ill
                  </Text>
                </View>
              ) : null}
              {totalDeceased > 0 ? (
                <View
                  style={[
                    styles.statBadge,
                    { backgroundColor: withAlpha(theme.colors.error, 0.08) },
                  ]}
                >
                  <Text
                    variant="labelSmall"
                    style={{ color: theme.colors.error }}
                  >
                    {totalDeceased} deceased
                  </Text>
                </View>
              ) : null}
            </View>
          </Card.Content>
        </Card>

        {aquariums.length > 1 ? (
          <ScrollableSegmentedButtons
            value={filterAquariumId}
            onValueChange={setFilterAquariumId}
            buttons={[
              { label: "All tanks", value: "all" },
              ...aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              })),
            ]}
          />
        ) : null}

        {livestock.length === 0 ? (
          <Card
            style={[
              styles.emptyCard,
              { backgroundColor: theme.colors.surfaceVariant },
            ]}
            mode="contained"
          >
            <Card.Content>
              <Text variant="bodyMedium">
                No livestock added yet. Head to the Tanks page to add your first
                residents.
              </Text>
            </Card.Content>
          </Card>
        ) : (
          <>
            <View style={styles.chipGrid}>
              {filteredLivestock.map((item) => (
                <LivestockChip
                  key={item.id}
                  item={item}
                  aquariumName={
                    aquariumNameById[item.aquariumId] ?? "Unknown tank"
                  }
                  isSelected={selectedLivestockId === item.id}
                  onSelect={() => handleChipSelect(item.id)}
                />
              ))}
            </View>

            {filteredLivestock.length === 0 && livestock.length > 0 ? (
              <Text
                variant="bodyMedium"
                style={[styles.emptyFilter, { color: theme.colors.onSurfaceVariant }]}
              >
                No livestock in this tank.
              </Text>
            ) : null}

            {selectedLivestock ? (
              <LivestockDetailPanel
                item={selectedLivestock}
                aquariumName={
                  aquariumNameById[selectedLivestock.aquariumId] ??
                  "Unknown tank"
                }
                fallbackTargetId={selectedFallbackTargetId}
                cardBackground={detailBackground}
                parentEntity={selectedLivestockParent}
                offspringEntities={selectedLivestockOffspring}
                feedingTasks={selectedFeedingTasks}
                feedingNote={
                  feedingNoteDraft[selectedLivestock.id] ??
                  selectedLivestock.dietaryNotes ??
                  ""
                }
                livestockStatus={
                  livestockStatusDraft[selectedLivestock.id] ??
                  selectedLivestock.status ??
                  "active"
                }
                livestockStatusNote={
                  livestockStatusNoteDraft[selectedLivestock.id] ?? ""
                }
                feedingTaskTitle={
                  feedingTaskTitleDraft[selectedLivestock.id] ?? ""
                }
                feedingTaskFrequency={
                  feedingTaskFrequencyDraft[selectedLivestock.id] ?? "daily"
                }
                feedingTaskStartDate={
                  feedingTaskStartDateDraft[selectedLivestock.id] ?? ""
                }
                feedingTaskStartDateValue={
                  feedingTaskStartDateValueDraft[selectedLivestock.id] ??
                  new Date()
                }
                feedingTaskTimesPerDay={
                  feedingTaskTimesPerDayDraft[selectedLivestock.id] ?? ""
                }
                feedingTaskCustomDays={
                  feedingTaskCustomDaysDraft[selectedLivestock.id] ?? ""
                }
                isStartDatePickerOpen={
                  startDatePickerOpenDraft[selectedLivestock.id] ?? false
                }
                openEntity={openEntity}
                setFeedingNoteDraft={setFeedingNoteDraft}
                setLivestockStatusDraft={setLivestockStatusDraft}
                setLivestockStatusNoteDraft={setLivestockStatusNoteDraft}
                setFeedingTaskTitleDraft={setFeedingTaskTitleDraft}
                setFeedingTaskFrequencyDraft={setFeedingTaskFrequencyDraft}
                setFeedingTaskStartDateDraft={setFeedingTaskStartDateDraft}
                setFeedingTaskStartDateValueDraft={setFeedingTaskStartDateValueDraft}
                setFeedingTaskTimesPerDayDraft={setFeedingTaskTimesPerDayDraft}
                setFeedingTaskCustomDaysDraft={setFeedingTaskCustomDaysDraft}
                setStartDatePickerOpenDraft={setStartDatePickerOpenDraft}
                setLivestockFeedingNotes={setLivestockFeedingNotes}
                setLivestockStatus={setLivestockStatus}
                transferLivestock={transferLivestock}
                addOffspring={addOffspring}
                addLivestockFeedingTask={addLivestockFeedingTask}
                onCollapse={handleCollapse}
              />
            ) : (
              <Card
                style={[
                  styles.selectPrompt,
                  { backgroundColor: theme.colors.surface },
                ]}
                mode="elevated"
              >
                <Card.Content style={styles.selectPromptContent}>
                  <MaterialCommunityIcons
                    name="gesture-tap"
                    size={28}
                    color={theme.colors.onSurfaceVariant}
                  />
                  <Text
                    variant="bodyMedium"
                    style={{ color: theme.colors.onSurfaceVariant }}
                  >
                    Select a resident above to view and edit details
                  </Text>
                </Card.Content>
              </Card>
            )}
          </>
        )}
      </ScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 132,
    gap: 12,
  },
  heroCard: {
    borderRadius: 24,
    marginVertical: 0,
    overflow: "hidden",
  },
  heroContent: {
    gap: 12,
  },
  heroBackground: {
    ...StyleSheet.absoluteFillObject,
    pointerEvents: "none",
  },
  heroHeader: {
    gap: 4,
  },
  heroSubtitle: {
    opacity: 0.82,
  },
  statRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  statBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  chipGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chipCard: {
    borderRadius: 16,
    borderWidth: 1.5,
    overflow: "hidden",
  },
  chipContent: {
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 3,
    minWidth: 120,
  },
  chipHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  chipName: {
    fontWeight: "600",
  },
  chipSpecies: {
    opacity: 0.8,
  },
  chipFooter: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    marginTop: 2,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 999,
  },
  emptyCard: {
    borderRadius: 18,
    marginTop: 4,
  },
  emptyFilter: {
    opacity: 0.7,
    marginTop: 4,
    textAlign: "center",
  },
  selectPrompt: {
    borderRadius: 24,
    marginTop: 4,
  },
  selectPromptContent: {
    alignItems: "center",
    gap: 10,
    paddingVertical: 8,
  },
  detailCard: {
    borderRadius: 24,
    marginTop: 4,
    overflow: "hidden",
  },
  detailContent: {
    gap: 12,
  },
  detailHeader: {
    gap: 6,
  },
  detailTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  detailMeta: {
    opacity: 0.8,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  detailPhoto: {
    width: "100%",
    height: 180,
    borderRadius: 18,
  },
  viewDetailButton: {
    alignSelf: "flex-start",
  },
});

const detailStyles = StyleSheet.create({
  savedBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 12,
  },
  inputWithCount: {
    gap: 2,
  },
  sectionAction: {
    alignSelf: "flex-start",
  },
  currentStatusRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 14,
  },
  statusChipRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  statusIndicator: {
    width: 10,
    height: 10,
    borderRadius: 10,
  },
  actionRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  actionPrimary: {
    flexGrow: 1,
  },
  existingTasksList: {
    borderRadius: 14,
    overflow: "hidden",
  },
  taskRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 10,
    paddingHorizontal: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  taskInfo: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    flex: 1,
  },
  taskText: {
    gap: 1,
  },
  newTaskCard: {
    padding: 14,
    borderRadius: 16,
    gap: 10,
  },
  inlineInputs: {
    flexDirection: "row",
    gap: 8,
  },
});
