import { useForm } from "@tanstack/react-form";
import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useRouter } from "expo-router";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import {
    type Dispatch,
    memo,
    type SetStateAction,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import {
    Pressable,
    ScrollView,
    StyleSheet,
    useWindowDimensions,
    View,
} from "react-native";
import { LineChart } from "react-native-gifted-charts";
import {
    Button,
    Card,
    Chip,
    FAB,
    IconButton,
    Portal,
    Surface,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";
import { DatePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    AquariumBackground,
    AssetBackground,
    ConsumableBackground,
    LivestockBackground,
} from "@/components/illustrations/AnimatedCardBackgrounds";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { getCardTextColorForBackground } from "@/components/ui/card-tone";
import {
    type PhotoSource,
    PhotoSourceDialog,
} from "@/components/ui/photo-source-dialog";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { withAlpha } from "@/constants/theme";
import { createEntityRef, getEntityHref } from "@/services/entity-links";
import { formatCurrencyAmount } from "@/services/localization";
import { isTaskDue, toIsoDate } from "@/services/scheduling";
import { evaluateParameterAlerts } from "@/services/water-alerts";
import {
    type Aquarium,
    type EntityRef,
    type Issue,
    AssetCategory,
    IssueStatus,
    type Livestock,
    TaskFrequency,
    type TaskTemplate,
    type WaterParameterLog,
    WaterType,
} from "@/types/aquapt";

const WATER_TYPES = ["freshwater", "marine", "brackish"] as const;
const LIVESTOCK_KINDS = [
  "fish",
  "shrimp",
  "snail",
  "coral",
  "plant",
  "other",
] as const;
const ASSET_CATEGORIES = ["filter", "heater", "light", "co2", "other"] as const;
const CONSUMABLE_UNITS = ["pcs", "ml", "g"] as const;
const ANALYTIC_METRICS = [
  { label: "NH3", value: "ammonia" },
  { label: "NO2", value: "nitrite" },
  { label: "NO3", value: "nitrate" },
  { label: "pH", value: "ph" },
  { label: "Temp", value: "temperatureC" },
  { label: "GH", value: "gh" },
  { label: "KH", value: "kh" },
  { label: "Sal", value: "salinity" },
  { label: "Ca", value: "calcium" },
  { label: "Alk", value: "alkalinity" },
] as const;
type AnalyticMetricKey = (typeof ANALYTIC_METRICS)[number]["value"];

const METRIC_UNITS: Record<AnalyticMetricKey, string> = {
  ammonia: "ppm",
  nitrite: "ppm",
  nitrate: "ppm",
  ph: "",
  temperatureC: "°C",
  gh: "",
  kh: "",
  salinity: "",
  calcium: "ppm",
  alkalinity: "dKH",
};

const METRIC_COLORS: Record<AnalyticMetricKey, string> = {
  ammonia: "#ef4444",
  nitrite: "#f97316",
  nitrate: "#22c55e",
  ph: "#0ea5e9",
  temperatureC: "#8b5cf6",
  gh: "#14b8a6",
  kh: "#06b6d4",
  salinity: "#0d9488",
  calcium: "#2563eb",
  alkalinity: "#9333ea",
};

const parseIsoDate = (value: string) => {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
};

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
];

const ISSUE_STATUS_BUTTONS = [
  { label: "Open", value: "open" },
  { label: "Monitoring", value: "monitoring" },
  { label: "Resolved", value: "resolved" },
];

type AquariumInsights = {
  latestParameterSummary: string;
  nitrateTrend: string;
  alerts: ReturnType<typeof evaluateParameterAlerts>;
};

type DueTaskEntry = {
  taskId: string;
  taskTitle: string;
  aquariumId: string;
};

type AquariumAlertEntry = ReturnType<typeof evaluateParameterAlerts>[number] & {
  aquariumId: string;
  aquariumName: string;
};

type PhotoDialogConfig = {
  title: string;
  currentUri?: string;
  setLoading: Dispatch<SetStateAction<boolean>>;
  onPicked: (uri: string) => void;
  onCleared: () => void;
};

type AquariumOverviewCardProps = {
  aquarium: Aquarium;
  backgroundColor: string;
  latestParameterSummary: string;
  investmentCostText?: string;
  livestockCount: number;
  openIssueCount: number;
  nitrateTrend: string;
  onEdit: (aquariumId: string) => void;
  onOpenDetails: (aquariumId: string) => void;
};

const AquariumOverviewCard = memo(function AquariumOverviewCard({
  aquarium,
  backgroundColor,
  latestParameterSummary,
  investmentCostText,
  livestockCount,
  openIssueCount,
  nitrateTrend,
  onEdit,
  onOpenDetails,
}: AquariumOverviewCardProps) {
  const theme = useTheme();
  const textColor = getCardTextColorForBackground(theme, backgroundColor);
  const surfaceBg = theme.colors.surface;
  const onSurface = theme.colors.onSurface;

  const nitrateShort = nitrateTrend.startsWith("Not enough")
    ? "—"
    : nitrateTrend.split(" ").slice(0, 2).join(" ");

  return (
    <Pressable onPress={() => onOpenDetails(aquarium.id)}>
      <Surface
        elevation={1}
        style={[styles.aquariumCard, { backgroundColor }]}
      >
        {aquarium.photoUri ? (
          <Image
            source={{ uri: aquarium.photoUri }}
            style={styles.aquariumThumb}
            contentFit="cover"
          />
        ) : (
          <View
            style={[
              styles.aquariumThumbPlaceholder,
              { backgroundColor: surfaceBg },
            ]}
          >
            <IconButton
              icon="fishbowl-outline"
              size={22}
              iconColor={textColor}
            />
          </View>
        )}

        <View style={styles.aquariumCardBody}>
          <View style={styles.aquariumCardHeader}>
            <View style={styles.aquariumCardTitleRow}>
              <Text
                variant="titleSmall"
                numberOfLines={1}
                style={{ color: textColor }}
              >
                {aquarium.name}
              </Text>
              <View
                style={[
                  styles.aquariumBadge,
                  { backgroundColor: surfaceBg },
                ]}
              >
                <Text
                  variant="labelSmall"
                  style={{ color: onSurface }}
                >
                  {aquarium.volumeLiters}L
                </Text>
              </View>
            </View>
            <Text
              variant="bodySmall"
              numberOfLines={1}
              style={{ color: textColor, opacity: 0.78 }}
            >
              {aquarium.waterType}
              {investmentCostText ? ` • ${investmentCostText}` : ""}
            </Text>
          </View>

          <Text
            variant="bodySmall"
            numberOfLines={1}
            style={{ color: textColor, opacity: 0.88 }}
          >
            {latestParameterSummary}
          </Text>

          <View style={styles.aquariumCardFooter}>
            <View style={styles.aquariumStatRow}>
              <Text
                variant="labelSmall"
                style={[styles.aquariumStat, { color: textColor }]}
              >
                {aquarium.dimensions}
              </Text>
              <Text
                variant="labelSmall"
                style={[styles.aquariumStat, { color: textColor }]}
              >
                Setup {aquarium.setupDate}
              </Text>
            </View>

            <View style={styles.aquariumCardActions}>
              <Text
                variant="labelSmall"
                style={[styles.aquariumStat, { color: textColor }]}
              >
                {livestockCount}
                <Text style={{ fontSize: 10 }}> {"\uD83D\uDC1F"}</Text>
                {"  "}
                {openIssueCount}
                <Text style={{ fontSize: 10 }}> {"\u26A0"}</Text>
                {"  "}NO3 {nitrateShort}
              </Text>
              <IconButton
                icon="pencil"
                mode="contained"
                size={16}
                onPress={() => onEdit(aquarium.id)}
                accessibilityLabel="Edit aquarium specs"
                style={styles.aquariumCardIconButton}
              />
            </View>
          </View>
        </View>
      </Surface>
    </Pressable>
  );
});

type LivestockCardProps = {
  item: Livestock;
  aquariumName: string;
  fallbackTargetId?: string;
  feedingNote: string;
  livestockStatus: NonNullable<Livestock["status"]>;
  livestockStatusNote: string;
  feedingTaskTitle: string;
  feedingTaskFrequency: TaskFrequency;
  feedingTaskStartDate: string;
  feedingTaskTimesPerDay: string;
  cardBackground: string;
  parentEntity?: { id: string; name: string; aquariumId: string };
  offspringEntities: { id: string; name: string; aquariumId: string }[];
  feedingTasks: TaskTemplate[];
  openEntity: (ref: EntityRef) => void;
  setFeedingNoteDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setLivestockStatusDraft: Dispatch<
    SetStateAction<Record<string, NonNullable<Livestock["status"]>>>
  >;
  setLivestockStatusNoteDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setFeedingTaskTitleDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setFeedingTaskFrequencyDraft: Dispatch<
    SetStateAction<Record<string, TaskFrequency>>
  >;
  setFeedingTaskStartDateDraft: Dispatch<
    SetStateAction<Record<string, string>>
  >;
  setFeedingTaskTimesPerDayDraft: Dispatch<
    SetStateAction<Record<string, string>>
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
};

const LivestockCard = memo(function LivestockCard({
  item,
  aquariumName,
  fallbackTargetId,
  feedingNote,
  livestockStatus,
  livestockStatusNote,
  feedingTaskTitle,
  feedingTaskFrequency,
  feedingTaskStartDate,
  feedingTaskTimesPerDay,
  cardBackground,
  parentEntity,
  offspringEntities,
  feedingTasks,
  openEntity,
  setFeedingNoteDraft,
  setLivestockStatusDraft,
  setLivestockStatusNoteDraft,
  setFeedingTaskTitleDraft,
  setFeedingTaskFrequencyDraft,
  setFeedingTaskStartDateDraft,
  setFeedingTaskTimesPerDayDraft,
  setLivestockFeedingNotes,
  setLivestockStatus,
  transferLivestock,
  addOffspring,
  addLivestockFeedingTask,
}: LivestockCardProps) {
  const theme = useTheme();
  const textColor = getCardTextColorForBackground(theme, cardBackground);

  return (
    <Card
      style={[
        styles.issueCard,
        styles.sectionItemCard,
        styles.keepCard,
        { backgroundColor: cardBackground },
      ]}
      mode="contained"
      onPress={() =>
        openEntity(createEntityRef("livestock", item.id, item.aquariumId))
      }
    >
      <Card.Content>
        <Text variant="titleSmall" style={{ color: textColor }}>
          {item.name} ({item.quantity})
        </Text>
        <Text
          variant="bodySmall"
          style={[styles.issueMeta, { color: textColor }]}
        >
          {item.species} • {aquariumName}
        </Text>
        <View style={styles.summaryRow}>
          <Chip
            compact
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
            style={styles.livestockPhoto}
          />
        ) : null}
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
          numberOfLines={2}
          style={styles.issueResolutionInput}
          outlineColor={theme.colors.outline}
          activeOutlineColor={theme.colors.primary}
          textColor={theme.colors.onSurface}
        />
        <ScrollableSegmentedButtons
          value={livestockStatus}
          onValueChange={(value) =>
            setLivestockStatusDraft((prev) => ({
              ...prev,
              [item.id]: value as NonNullable<Livestock["status"]>,
            }))
          }
          buttons={LIVESTOCK_STATUS_BUTTONS}
          style={styles.issueStatusSelector}
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
          style={styles.issueResolutionInput}
          outlineColor={theme.colors.outline}
          activeOutlineColor={theme.colors.primary}
          textColor={theme.colors.onSurface}
        />
        <View style={styles.summaryRow}>
          <Button
            mode="contained"
            onPress={() =>
              setLivestockFeedingNotes(item.id, feedingNote.trim())
            }
          >
            Save feeding
          </Button>
          <Button
            mode="contained"
            onPress={() =>
              setLivestockStatus(
                item.id,
                livestockStatus,
                livestockStatusNote.trim() || undefined,
              )
            }
          >
            Save status
          </Button>
          <Button
            mode="contained"
            disabled={!fallbackTargetId || fallbackTargetId === item.aquariumId}
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
            mode="contained"
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
            Add offspring
          </Button>
        </View>
        <TextInput
          mode="outlined"
          label="Feeding task title"
          value={feedingTaskTitle}
          onChangeText={(value) =>
            setFeedingTaskTitleDraft((prev) => ({
              ...prev,
              [item.id]: value,
            }))
          }
          style={styles.issueResolutionInput}
          placeholder={`Feed ${item.name}`}
          outlineColor={theme.colors.outline}
          activeOutlineColor={theme.colors.primary}
          textColor={theme.colors.onSurface}
        />
        <ScrollableSegmentedButtons
          value={feedingTaskFrequency}
          onValueChange={(value) =>
            setFeedingTaskFrequencyDraft((prev) => ({
              ...prev,
              [item.id]: value as TaskFrequency,
            }))
          }
          buttons={FEEDING_TASK_FREQUENCY_BUTTONS}
          style={styles.issueStatusSelector}
        />
        <View style={styles.summaryRow}>
          <TextInput
            mode="outlined"
            label="Times per day"
            value={feedingTaskTimesPerDay}
            onChangeText={(value) =>
              setFeedingTaskTimesPerDayDraft((prev) => ({
                ...prev,
                [item.id]: value,
              }))
            }
            keyboardType="numeric"
            style={[styles.issueResolutionInput, { flex: 1 }]}
            placeholder="1"
            outlineColor={theme.colors.outline}
            activeOutlineColor={theme.colors.primary}
            textColor={theme.colors.onSurface}
          />
          <TextInput
            mode="outlined"
            label="Start date (YYYY-MM-DD)"
            value={feedingTaskStartDate}
            onChangeText={(value) =>
              setFeedingTaskStartDateDraft((prev) => ({
                ...prev,
                [item.id]: value,
              }))
            }
            style={[styles.issueResolutionInput, { flex: 2 }]}
            placeholder={toIsoDate(new Date())}
            outlineColor={theme.colors.outline}
            activeOutlineColor={theme.colors.primary}
            textColor={theme.colors.onSurface}
          />
        </View>
        <Button
          mode="contained-tonal"
          style={styles.issueSaveButton}
          onPress={() => {
            const customTitle = feedingTaskTitle.trim();
            const timesPerDay = Math.max(
              1,
              Math.floor(Number(feedingTaskTimesPerDay) || 1),
            );
            const startDateValue =
              feedingTaskStartDate.trim() || toIsoDate(new Date());
            addLivestockFeedingTask({
              livestockId: item.id,
              title: customTitle || `Feed ${item.name}`,
              frequency: feedingTaskFrequency,
              description:
                feedingNote.trim() ||
                item.dietaryNotes ||
                `Targeted feeding regimen for ${item.name}`,
              startDate: startDateValue,
              timesPerDay:
                feedingTaskFrequency === "daily" ? timesPerDay : undefined,
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
          }}
        >
          Create feeding task
        </Button>
      </Card.Content>
    </Card>
  );
});

type IssueCardProps = {
  issue: Issue;
  aquariumName: string;
  currentStatus: IssueStatus;
  resolutionNote: string;
  setIssueStatusDraft: Dispatch<SetStateAction<Record<string, IssueStatus>>>;
  setResolutionNoteDraft: Dispatch<SetStateAction<Record<string, string>>>;
  setIssueStatus: (
    issueId: string,
    status: Issue["status"],
    resolutionNote?: string,
  ) => void;
  backgroundColor: string;
  onOpenDetails: (issueId: string, aquariumId: string) => void;
};

const IssueCard = memo(function IssueCard({
  issue,
  aquariumName,
  currentStatus,
  resolutionNote,
  setIssueStatusDraft,
  setResolutionNoteDraft,
  setIssueStatus,
  backgroundColor,
  onOpenDetails,
}: IssueCardProps) {
  const theme = useTheme();
  const textColor = getCardTextColorForBackground(theme, backgroundColor);

  return (
    <Card
      style={[
        styles.issueCard,
        styles.sectionItemCard,
        styles.keepCard,
        { backgroundColor },
      ]}
      mode="contained"
      onPress={() => onOpenDetails(issue.id, issue.aquariumId)}
    >
      <Card.Content>
        <Text variant="titleSmall" style={{ color: textColor }}>
          {issue.title}
        </Text>
        <Text
          variant="bodySmall"
          style={[styles.issueMeta, { color: textColor }]}
        >
          {aquariumName} • Logged {new Date(issue.createdAt).toLocaleString()}
        </Text>
        <View style={styles.summaryRow}>
          <Chip
            compact
            icon="fishbowl"
            onPress={() => onOpenDetails(issue.id, issue.aquariumId)}
          >
            Open issue
          </Chip>
        </View>

        <ScrollableSegmentedButtons
          value={currentStatus}
          onValueChange={(value) =>
            setIssueStatusDraft((prev) => ({
              ...prev,
              [issue.id]: value as IssueStatus,
            }))
          }
          buttons={ISSUE_STATUS_BUTTONS}
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
});

type TodayMetricCardProps = {
  label: string;
  value: string;
  detail: string;
  accentColor: string;
  backgroundColor: string;
  textColor: string;
};

const TodayMetricCard = memo(function TodayMetricCard({
  label,
  value,
  detail,
  accentColor,
  backgroundColor,
  textColor,
}: TodayMetricCardProps) {
  return (
    <Surface
      elevation={1}
      style={[styles.todayMetricCard, { backgroundColor }]}
    >
      <View style={styles.todayMetricHeader}>
        <View
          style={[styles.todayMetricAccent, { backgroundColor: accentColor }]}
        />
        <Text
          variant="labelMedium"
          style={[styles.todayMetricLabel, { color: textColor }]}
        >
          {label}
        </Text>
      </View>
      <Text variant="headlineSmall" style={{ color: textColor }}>
        {value}
      </Text>
      <Text
        variant="bodySmall"
        numberOfLines={1}
        style={[styles.todayMetricDetail, { color: textColor }]}
      >
        {detail}
      </Text>
    </Surface>
  );
});

type TodayFocusPanelItem = {
  id: string;
  title: string;
  caption: string;
  accentColor: string;
  onPress?: () => void;
  onComplete?: () => void;
};

type TodayFocusPanelProps = {
  eyebrow: string;
  title: string;
  summary: string;
  items: TodayFocusPanelItem[];
  emptyState: string;
  backgroundColor: string;
  textColor: string;
  emptyAccentColor: string;
};

const TodayFocusPanel = memo(function TodayFocusPanel({
  eyebrow,
  title,
  summary,
  items,
  emptyState,
  backgroundColor,
  textColor,
  emptyAccentColor,
}: TodayFocusPanelProps) {
  return (
    <Surface
      elevation={1}
      style={[styles.todayFocusPanel, { backgroundColor }]}
    >
      <View style={styles.todayFocusHeader}>
        <Text
          variant="labelMedium"
          style={[styles.todayFocusEyebrow, { color: textColor }]}
        >
          {eyebrow}
        </Text>
        <Text variant="titleSmall" style={{ color: textColor }}>
          {title}
        </Text>
        <Text
          variant="bodySmall"
          numberOfLines={2}
          style={[styles.todayFocusSummary, { color: textColor }]}
        >
          {summary}
        </Text>
      </View>

      {items.length > 0 ? (
        <View style={styles.todayFocusList}>
          {items.map((item) => {
            const itemContent = (
              <View style={styles.todayFocusItem}>
                <View
                  style={[
                    styles.todayFocusItemAccent,
                    { backgroundColor: item.accentColor },
                  ]}
                />
                <View style={styles.todayFocusItemCopy}>
                  <Text
                    variant="bodyMedium"
                    numberOfLines={1}
                    style={{ color: textColor }}
                  >
                    {item.title}
                  </Text>
                  <Text
                    variant="bodySmall"
                    numberOfLines={1}
                    style={[styles.todayFocusItemCaption, { color: textColor }]}
                  >
                    {item.caption}
                  </Text>
                </View>
                {item.onComplete && (
                  <Pressable
                    onPress={(e) => {
                      e.stopPropagation();
                      item.onComplete?.();
                    }}
                    hitSlop={4}
                    style={({ pressed }) => [
                      styles.todayFocusCompleteBtn,
                      {
                        borderColor: item.accentColor,
                        backgroundColor: pressed
                          ? item.accentColor
                          : "transparent",
                      },
                    ]}
                  >
                    {({ pressed }) => (
                      <MaterialCommunityIcons
                        name="check"
                        size={13}
                        color={pressed ? "#fff" : item.accentColor}
                      />
                    )}
                  </Pressable>
                )}
              </View>
            );

            if (!item.onPress) {
              return <View key={item.id}>{itemContent}</View>;
            }

            return (
              <Pressable key={item.id} onPress={item.onPress}>
                {itemContent}
              </Pressable>
            );
          })}
        </View>
      ) : (
        <View style={styles.todayFocusEmpty}>
          <View
            style={[
              styles.todayFocusItemAccent,
              { backgroundColor: emptyAccentColor },
            ]}
          />
          <Text variant="bodySmall" style={{ color: textColor }}>
            {emptyState}
          </Text>
        </View>
      )}
    </Surface>
  );
});

export default function HomeScreen() {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  const router = useRouter();
  const {
    aquariums,
    livestock,
    assets,
    consumables,
    dosingLogs,
    issues,
    parameterLogs,
    taskTemplates,
    taskExecutions,
    settings,
    livestockCountByAquarium,
    openIssuesByAquarium,
    addAquarium,
    editAquarium,
    addMemo,
    addIssue,
    addLivestock,
    transferLivestock,
    addOffspring,
    addLivestockFeedingTask,
    setLivestockFeedingNotes,
    setLivestockStatus,
    addAsset,
    addConsumable,
    consumeConsumable,
    logDosing,
    logParameters,
    completeTask,
    setIssueStatus,
  } = useAquapt();
  const userCurrencyCode = settings.defaultCurrency ?? "USD";
  const userLocale = settings.defaultLocale;
  const getCurrencyFieldLabel = (baseLabel: string, optional = false) => {
    if (!settings.defaultCurrency) {
      return optional ? `${baseLabel} (optional)` : baseLabel;
    }

    return optional
      ? `${baseLabel} (${settings.defaultCurrency}, optional)`
      : `${baseLabel} (${settings.defaultCurrency})`;
  };
  const openEntity = (ref: EntityRef) => {
    router.push(getEntityHref(ref) as never);
  };
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [isAddAquariumOpen, setAddAquariumOpen] = useState(false);
  const [isAddLivestockOpen, setAddLivestockOpen] = useState(false);
  const [isAddAssetOpen, setAddAssetOpen] = useState(false);
  const [isAddConsumableOpen, setAddConsumableOpen] = useState(false);
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [newLivestockName, setNewLivestockName] = useState("");
  const [newLivestockSpecies, setNewLivestockSpecies] = useState("");
  const [newLivestockQty, setNewLivestockQty] = useState("1");
  const [newLivestockKind, setNewLivestockKind] =
    useState<Livestock["kind"]>("other");
  const [newLivestockPrice, setNewLivestockPrice] = useState("");
  const [newLivestockPhotoUri, setNewLivestockPhotoUri] = useState("");
  const [isPickingPhoto, setPickingPhoto] = useState(false);
  const [isPickingMemoPhoto, setPickingMemoPhoto] = useState(false);
  const [isPickingAquariumPhoto, setPickingAquariumPhoto] = useState(false);
  const [isPickingAssetPhoto, setPickingAssetPhoto] = useState(false);
  const [isPickingConsumablePhoto, setPickingConsumablePhoto] = useState(false);
  const [newAssetModel, setNewAssetModel] = useState("");
  const [newAssetCategory, setNewAssetCategory] = useState<AssetCategory>("other");
  const [newAssetPurchasedAt, setNewAssetPurchasedAt] = useState(
    toIsoDate(new Date()),
  );
  const [newAssetPurchasedAtValue, setNewAssetPurchasedAtValue] = useState(
    new Date(),
  );
  const [isAssetDatePickerOpen, setAssetDatePickerOpen] = useState(false);
  const [newAssetPrice, setNewAssetPrice] = useState("");
  const [newAssetPhotoUri, setNewAssetPhotoUri] = useState("");
  const [selectedAssetTaskTemplateIds, setSelectedAssetTaskTemplateIds] =
    useState<string[]>([]);
  const [newConsumableName, setNewConsumableName] = useState("");
  const [newConsumableRemaining, setNewConsumableRemaining] = useState("0");
  const [newConsumableUnit, setNewConsumableUnit] = useState<
    "pcs" | "ml" | "g"
  >("pcs");
  const [newConsumablePhotoUri, setNewConsumablePhotoUri] = useState("");
  const [issueStatusDraft, setIssueStatusDraft] = useState<
    Record<string, IssueStatus>
  >({});
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
    Record<string, TaskFrequency>
  >({});
  const [feedingTaskStartDateDraft, setFeedingTaskStartDateDraft] = useState<
    Record<string, string>
  >({});
  const [feedingTaskTimesPerDayDraft, setFeedingTaskTimesPerDayDraft] =
    useState<Record<string, string>>({});
  const [resolutionNoteDraft, setResolutionNoteDraft] = useState<
    Record<string, string>
  >({});
  const [isEditAquariumOpen, setEditAquariumOpen] = useState(false);
  const [isNewDatePickerOpen, setNewDatePickerOpen] = useState(false);
  const [isEditDatePickerOpen, setEditDatePickerOpen] = useState(false);
  const [selectedMetric, setSelectedMetric] =
    useState<AnalyticMetricKey>("nitrate");
  const [isFabTooltipVisible, setFabTooltipVisible] = useState(false);
  const [photoDialogConfig, setPhotoDialogConfig] =
    useState<PhotoDialogConfig | null>(null);
  const fabTooltipTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );
  const lastFabLongPressRef = useRef(0);

  const addAquariumForm = useForm({
    defaultValues: {
      name: "",
      volume: "",
      dimensions: "",
      setupDate: toIsoDate(new Date()),
      setupDateValue: new Date(),
      investment: "",
      waterType: "freshwater" as WaterType,
      photoUri: "",
    },
    onSubmit: ({ value }) => {
      const volume = Number(value.volume);
      const investment = Number(value.investment);
      if (!value.name.trim() || !Number.isFinite(volume) || volume <= 0) {
        return;
      }

      addAquarium({
        name: value.name.trim(),
        volumeLiters: volume,
        dimensions: value.dimensions.trim() || "-",
        waterType: value.waterType,
        setupDate: value.setupDate.trim() || toIsoDate(new Date()),
        investmentCost:
          Number.isFinite(investment) && investment >= 0
            ? investment
            : undefined,
        photoUri: value.photoUri || undefined,
      });

      addAquariumForm.setFieldValue("name", "");
      addAquariumForm.setFieldValue("volume", "");
      addAquariumForm.setFieldValue("dimensions", "");
      addAquariumForm.setFieldValue("setupDate", toIsoDate(new Date()));
      addAquariumForm.setFieldValue("setupDateValue", new Date());
      addAquariumForm.setFieldValue("investment", "");
      addAquariumForm.setFieldValue("waterType", "freshwater");
      addAquariumForm.setFieldValue("photoUri", "");
      setAddAquariumOpen(false);
    },
  });

  const editAquariumForm = useForm({
    defaultValues: {
      id: "",
      name: "",
      volume: "",
      dimensions: "",
      setupDate: "",
      setupDateValue: new Date(),
      investment: "",
      photoUri: "",
    },
    onSubmit: ({ value }) => {
      if (!value.id || !value.name.trim()) {
        return;
      }

      const volume = Number(value.volume);
      const investment = Number(value.investment);
      if (!Number.isFinite(volume) || volume <= 0) {
        return;
      }

      editAquarium(value.id, {
        name: value.name.trim(),
        volumeLiters: volume,
        dimensions: value.dimensions.trim() || "-",
        setupDate: value.setupDate.trim() || toIsoDate(new Date()),
        investmentCost:
          Number.isFinite(investment) && investment >= 0
            ? investment
            : undefined,
        photoUri: value.photoUri || undefined,
      });

      setEditAquariumOpen(false);
      editAquariumForm.setFieldValue("id", "");
    },
  });

  const quickLogForm = useForm({
    defaultValues: {
      action: "parameter" as "parameter" | "memo" | "issue" | "dosing" | "task",
      selectedAquariumId: aquariums[0]?.id ?? "",
      memo: {
        text: "",
        photoUri: "",
      },
      issue: {
        title: "",
      },
      parameter: {
        ammonia: "",
        nitrite: "",
        nitrate: "",
        ph: "",
        temperature: "",
        gh: "",
        kh: "",
        salinity: "",
        calcium: "",
        alkalinity: "",
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
        );
        quickLogForm.setFieldValue("memo.text", "");
        quickLogForm.setFieldValue("memo.photoUri", "");
      }

      if (value.action === "issue" && value.issue.title.trim()) {
        addIssue(value.selectedAquariumId, value.issue.title.trim());
        quickLogForm.setFieldValue("issue.title", "");
      }

      if (value.action === "parameter") {
        const ammoniaValue = Number(value.parameter.ammonia);
        const nitriteValue = Number(value.parameter.nitrite);
        const nitrateValue = Number(value.parameter.nitrate);
        const phValue = Number(value.parameter.ph);
        const temperatureValue = Number(value.parameter.temperature);
        const ghValue = Number(value.parameter.gh);
        const khValue = Number(value.parameter.kh);
        const salinityValue = Number(value.parameter.salinity);
        const calciumValue = Number(value.parameter.calcium);
        const alkalinityValue = Number(value.parameter.alkalinity);

        logParameters(value.selectedAquariumId, {
          ammonia: Number.isFinite(ammoniaValue) ? ammoniaValue : undefined,
          nitrite: Number.isFinite(nitriteValue) ? nitriteValue : undefined,
          nitrate: Number.isFinite(nitrateValue) ? nitrateValue : undefined,
          ph: Number.isFinite(phValue) ? phValue : undefined,
          temperatureC: Number.isFinite(temperatureValue)
            ? temperatureValue
            : undefined,
          gh: Number.isFinite(ghValue) ? ghValue : undefined,
          kh: Number.isFinite(khValue) ? khValue : undefined,
          salinity: Number.isFinite(salinityValue) ? salinityValue : undefined,
          calcium: Number.isFinite(calciumValue) ? calciumValue : undefined,
          alkalinity: Number.isFinite(alkalinityValue)
            ? alkalinityValue
            : undefined,
        });

        quickLogForm.setFieldValue("parameter.ammonia", "");
        quickLogForm.setFieldValue("parameter.nitrite", "");
        quickLogForm.setFieldValue("parameter.nitrate", "");
        quickLogForm.setFieldValue("parameter.ph", "");
        quickLogForm.setFieldValue("parameter.temperature", "");
        quickLogForm.setFieldValue("parameter.gh", "");
        quickLogForm.setFieldValue("parameter.kh", "");
        quickLogForm.setFieldValue("parameter.salinity", "");
        quickLogForm.setFieldValue("parameter.calcium", "");
        quickLogForm.setFieldValue("parameter.alkalinity", "");
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
          quickLogForm.setFieldValue("dosing.product", "");
          quickLogForm.setFieldValue("dosing.amount", "");
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
        quickLogForm.setFieldValue("task.templateId", "");
        quickLogForm.setFieldValue("task.note", "");
      }

      setDialogOpen(false);
    },
  });

  const pickPhotoFromSource = async (source: PhotoSource) => {
    if (!photoDialogConfig) {
      return;
    }

    const { setLoading, onPicked } = photoDialogConfig;
    setPhotoDialogConfig(null);
    setLoading(true);

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
        onPicked(result.assets[0].uri);
      }
    } finally {
      setLoading(false);
    }
  };

  const promptForPhoto = ({
    title,
    currentUri,
    setLoading,
    onPicked,
    onCleared,
  }: {
    title: string;
    currentUri?: string;
    setLoading: Dispatch<SetStateAction<boolean>>;
    onPicked: (uri: string) => void;
    onCleared: () => void;
  }) => {
    setPhotoDialogConfig({
      title,
      currentUri,
      setLoading,
      onPicked,
      onCleared,
    });
  };

  useEffect(() => {
    if (aquariums.length === 0) {
      if (selectedAquariumId !== "") {
        setSelectedAquariumId("");
      }
      return;
    }

    const selectedExists = aquariums.some((aq) => aq.id === selectedAquariumId);
    if (!selectedExists) {
      setSelectedAquariumId(aquariums[0].id);
    }
  }, [aquariums, selectedAquariumId]);

  useEffect(() => {
    return () => {
      if (fabTooltipTimeoutRef.current) {
        clearTimeout(fabTooltipTimeoutRef.current);
      }
    };
  }, []);

  const parameterLogsByAquarium = useMemo(() => {
    const groupedLogs: Record<string, WaterParameterLog[]> = {};

    for (const entry of parameterLogs) {
      if (!groupedLogs[entry.aquariumId]) {
        groupedLogs[entry.aquariumId] = [];
      }

      groupedLogs[entry.aquariumId].push(entry);
    }

    for (const aquariumLogs of Object.values(groupedLogs)) {
      aquariumLogs.sort(
        (a, b) => +new Date(a.createdAt) - +new Date(b.createdAt),
      );
    }

    return groupedLogs;
  }, [parameterLogs]);

  const aquariumInsightsById = useMemo<Record<string, AquariumInsights>>(() => {
    const insights: Record<string, AquariumInsights> = {};

    for (const aquarium of aquariums) {
      const aquariumLogs = parameterLogsByAquarium[aquarium.id] ?? [];
      const latestLog = aquariumLogs[aquariumLogs.length - 1];

      const latestParameterSummary = latestLog
        ? `NO3 ${latestLog.values.nitrate ?? "-"} • pH ${latestLog.values.ph ?? "-"} • ${latestLog.values.temperatureC ?? "-"}°C`
        : "No measurements logged yet";

      const nitratePoints = aquariumLogs
        .filter((entry) => entry.values.nitrate !== undefined)
        .slice(-5)
        .map((entry) => entry.values.nitrate as number);

      let nitrateTrend = "Not enough data yet";
      if (nitratePoints.length >= 2) {
        const first = nitratePoints[0];
        const last = nitratePoints[nitratePoints.length - 1];
        const delta = Number((last - first).toFixed(2));
        const direction = delta > 0 ? "↑" : delta < 0 ? "↓" : "→";
        nitrateTrend = `${direction} ${delta >= 0 ? "+" : ""}${delta} ppm (last ${nitratePoints.length} logs)`;
      }

      insights[aquarium.id] = {
        latestParameterSummary,
        nitrateTrend,
        alerts: latestLog
          ? evaluateParameterAlerts(aquarium, latestLog.values)
          : [],
      };
    }

    return insights;
  }, [aquariums, parameterLogsByAquarium]);

  const handleSubmitQuickAction = () => {
    void quickLogForm.handleSubmit();
  };

  const { pendingTasksToday, dueTasksByAquarium } = useMemo(() => {
    const dueTasks: Record<string, TaskTemplate[]> = {};
    const pendingEntries: DueTaskEntry[] = [];
    const now = new Date();

    for (const task of taskTemplates) {
      for (const aquariumId of task.aquariumIds) {
        if (!isTaskDue(task, aquariumId, taskExecutions, now)) {
          continue;
        }

        if (!dueTasks[aquariumId]) {
          dueTasks[aquariumId] = [];
        }

        dueTasks[aquariumId].push(task);
        pendingEntries.push({
          taskId: task.id,
          taskTitle: task.title,
          aquariumId,
        });
      }
    }

    return {
      pendingTasksToday: pendingEntries,
      dueTasksByAquarium: dueTasks,
    };
  }, [taskExecutions, taskTemplates]);

  const availableTaskTemplatesForAsset = useMemo(() => {
    if (!selectedAquariumId) {
      return [];
    }

    return taskTemplates.filter((task) =>
      task.aquariumIds.includes(selectedAquariumId),
    );
  }, [selectedAquariumId, taskTemplates]);

  const totalParameterAlerts = useMemo(
    () =>
      Object.values(aquariumInsightsById).reduce(
        (sum, insight) => sum + insight.alerts.length,
        0,
      ),
    [aquariumInsightsById],
  );
  const todayAlertEntries = useMemo<AquariumAlertEntry[]>(
    () =>
      aquariums.flatMap((aquarium) =>
        (aquariumInsightsById[aquarium.id]?.alerts ?? []).map((alert) => ({
          ...alert,
          aquariumId: aquarium.id,
          aquariumName: aquarium.name,
        })),
      ),
    [aquariums, aquariumInsightsById],
  );

  const assistantHeaderSubtitle =
    totalParameterAlerts > 0
      ? `${totalParameterAlerts} water alerts need attention across ${aquariums.length || 1} tank${aquariums.length === 1 ? "" : "s"}.`
      : pendingTasksToday.length > 0
        ? `${pendingTasksToday.length} task${pendingTasksToday.length === 1 ? "" : "s"} are due today. Ask the assistant to help you prioritize them.`
        : aquariums.length > 0
          ? `Everything looks steady across ${aquariums.length} tank${aquariums.length === 1 ? "" : "s"}. Ask for a quick care review anytime.`
          : "Add your first tank and let the assistant help you build a care routine.";

  const clearFabTooltipTimer = () => {
    if (fabTooltipTimeoutRef.current) {
      clearTimeout(fabTooltipTimeoutRef.current);
      fabTooltipTimeoutRef.current = null;
    }
  };

  const showFabTooltip = () => {
    clearFabTooltipTimer();
    setFabTooltipVisible(true);
    fabTooltipTimeoutRef.current = setTimeout(() => {
      setFabTooltipVisible(false);
      fabTooltipTimeoutRef.current = null;
    }, 1800);
  };

  const hideFabTooltip = () => {
    clearFabTooltipTimer();
    setFabTooltipVisible(false);
  };

  const openQuickLog = () => {
    hideFabTooltip();
    quickLogForm.setFieldValue("selectedAquariumId", selectedAquariumId);
    setDialogOpen(true);
  };

  const handleQuickLogFabLongPress = () => {
    lastFabLongPressRef.current = Date.now();
    showFabTooltip();
  };

  const handleQuickLogFabPress = () => {
    if (Date.now() - lastFabLongPressRef.current < 700) {
      return;
    }

    openQuickLog();
  };

  const createLivestock = () => {
    const quantity = Number(newLivestockQty);
    const purchasePrice = Number(newLivestockPrice);
    if (
      !selectedAquariumId ||
      !newLivestockName.trim() ||
      !newLivestockSpecies.trim()
    ) {
      return;
    }

    addLivestock({
      aquariumId: selectedAquariumId,
      kind: newLivestockKind,
      name: newLivestockName.trim(),
      species: newLivestockSpecies.trim(),
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
      acquiredAt: new Date().toISOString(),
      purchasePrice:
        Number.isFinite(purchasePrice) && purchasePrice >= 0
          ? purchasePrice
          : undefined,
      photoUri: newLivestockPhotoUri || undefined,
      status: "active",
    });
    setNewLivestockName("");
    setNewLivestockSpecies("");
    setNewLivestockQty("1");
    setNewLivestockKind("other");
    setNewLivestockPrice("");
    setNewLivestockPhotoUri("");
    setAddLivestockOpen(false);
  };

  const pickLivestockPhoto = () => {
    promptForPhoto({
      title: "Add livestock photo",
      currentUri: newLivestockPhotoUri,
      setLoading: setPickingPhoto,
      onPicked: (uri) => setNewLivestockPhotoUri(uri),
      onCleared: () => {
        setNewLivestockPhotoUri("");
      },
    });
  };

  const pickMemoPhoto = () => {
    promptForPhoto({
      title: "Add memo photo",
      currentUri: quickLogForm.state.values.memo.photoUri,
      setLoading: setPickingMemoPhoto,
      onPicked: (uri) => quickLogForm.setFieldValue("memo.photoUri", uri),
      onCleared: () => quickLogForm.setFieldValue("memo.photoUri", ""),
    });
  };

  const pickAquariumPhoto = (target: "add" | "edit") => {
    const currentUri =
      target === "add"
        ? addAquariumForm.state.values.photoUri
        : editAquariumForm.state.values.photoUri;

    promptForPhoto({
      title: target === "add" ? "Add aquarium photo" : "Update aquarium photo",
      currentUri,
      setLoading: setPickingAquariumPhoto,
      onPicked: (uri) => {
        if (target === "add") {
          addAquariumForm.setFieldValue("photoUri", uri);
          return;
        }

        editAquariumForm.setFieldValue("photoUri", uri);
      },
      onCleared: () => {
        if (target === "add") {
          addAquariumForm.setFieldValue("photoUri", "");
          return;
        }

        editAquariumForm.setFieldValue("photoUri", "");
      },
    });
  };

  const pickAssetPhoto = () => {
    promptForPhoto({
      title: "Add asset photo",
      currentUri: newAssetPhotoUri,
      setLoading: setPickingAssetPhoto,
      onPicked: setNewAssetPhotoUri,
      onCleared: () => setNewAssetPhotoUri(""),
    });
  };

  const pickConsumablePhoto = () => {
    promptForPhoto({
      title: "Add consumable photo",
      currentUri: newConsumablePhotoUri,
      setLoading: setPickingConsumablePhoto,
      onPicked: setNewConsumablePhotoUri,
      onCleared: () => setNewConsumablePhotoUri(""),
    });
  };

  const createAsset = () => {
    if (!selectedAquariumId || !newAssetModel.trim()) {
      return;
    }

    const price = Number(newAssetPrice);

    addAsset({
      aquariumId: selectedAquariumId,
      category: newAssetCategory,
      brandModel: newAssetModel.trim(),
      purchasedAt: newAssetPurchasedAt,
      price: Number.isFinite(price) && price >= 0 ? price : undefined,
      maintenanceTaskTemplateIds:
        selectedAssetTaskTemplateIds.length > 0
          ? selectedAssetTaskTemplateIds
          : undefined,
      photoUri: newAssetPhotoUri || undefined,
    });
    setNewAssetModel("");
    setNewAssetCategory("other");
    setNewAssetPurchasedAt(toIsoDate(new Date()));
    setNewAssetPurchasedAtValue(new Date());
    setNewAssetPrice("");
    setNewAssetPhotoUri("");
    setSelectedAssetTaskTemplateIds([]);
    setAddAssetOpen(false);
  };

  const openEditAquarium = (aquariumId: string) => {
    const aquarium = aquariums.find((item) => item.id === aquariumId);
    if (!aquarium) {
      return;
    }

    editAquariumForm.setFieldValue("id", aquarium.id);
    editAquariumForm.setFieldValue("name", aquarium.name);
    editAquariumForm.setFieldValue("volume", String(aquarium.volumeLiters));
    editAquariumForm.setFieldValue("dimensions", aquarium.dimensions);
    editAquariumForm.setFieldValue("setupDate", aquarium.setupDate);
    editAquariumForm.setFieldValue(
      "setupDateValue",
      parseIsoDate(aquarium.setupDate),
    );
    editAquariumForm.setFieldValue(
      "investment",
      aquarium.investmentCost !== undefined
        ? String(aquarium.investmentCost)
        : "",
    );
    editAquariumForm.setFieldValue("photoUri", aquarium.photoUri ?? "");
    setEditAquariumOpen(true);
  };

  const saveAquariumEdit = () => {
    void editAquariumForm.handleSubmit();
  };

  const createConsumable = () => {
    const remaining = Number(newConsumableRemaining);
    if (
      !selectedAquariumId ||
      !newConsumableName.trim() ||
      !Number.isFinite(remaining)
    ) {
      return;
    }

    addConsumable({
      aquariumId: selectedAquariumId,
      name: newConsumableName.trim(),
      unit: newConsumableUnit,
      remaining,
      reorderAt: Math.max(1, Math.floor(remaining * 0.25)),
      photoUri: newConsumablePhotoUri || undefined,
    });
    setNewConsumableName("");
    setNewConsumableRemaining("0");
    setNewConsumableUnit("pcs");
    setNewConsumablePhotoUri("");
    setAddConsumableOpen(false);
  };

  const aquariumNameById = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      acc[aquarium.id] = aquarium.name;
      return acc;
    }, {});
  }, [aquariums]);

  const aquariumIndexById = useMemo(() => {
    return aquariums.reduce<Record<string, number>>((acc, aquarium, index) => {
      acc[aquarium.id] = index;
      return acc;
    }, {});
  }, [aquariums]);
  const totalLivestock = useMemo(
    () => livestock.reduce((sum, item) => sum + item.quantity, 0),
    [livestock],
  );

  const totalOpenIssues = useMemo(
    () =>
      Object.values(openIssuesByAquarium).reduce(
        (sum, issueCount) => sum + issueCount,
        0,
      ),
    [openIssuesByAquarium],
  );

  const chartAquariumId = selectedAquariumId || aquariums[0]?.id || "";
  const selectedMetricLabel =
    ANALYTIC_METRICS.find((metric) => metric.value === selectedMetric)?.label ??
    selectedMetric;
  const chartData = useMemo(() => {
    if (!chartAquariumId) {
      return [];
    }

    return (parameterLogsByAquarium[chartAquariumId] ?? [])
      .filter((entry) => entry.values[selectedMetric] !== undefined)
      .slice(-8)
      .map((entry) => ({
        value: entry.values[selectedMetric] as number,
        label: `${new Date(entry.createdAt).getDate()}`,
      }));
  }, [chartAquariumId, parameterLogsByAquarium, selectedMetric]);



  const getAquariumCardBackgroundColor = (cardIndex: number) => {
    const keepTones = [
      theme.colors.secondaryContainer,
      theme.colors.tertiaryContainer,
      theme.colors.surfaceVariant,
    ];
    return keepTones[cardIndex % keepTones.length];
  };
  const primarySummaryTextColor = getCardTextColorForBackground(
    theme,
    theme.colors.primaryContainer,
  );
  const todayGlanceTextColor = getCardTextColorForBackground(
    theme,
    theme.colors.primaryContainer,
  );
  const todaySummaryDescription =
    totalParameterAlerts > 0
      ? `${totalParameterAlerts} safety alert${totalParameterAlerts === 1 ? "" : "s"} need review today.`
      : pendingTasksToday.length > 0
        ? `${pendingTasksToday.length} routine task${pendingTasksToday.length === 1 ? "" : "s"} are queued for today.`
        : aquariums.length > 0
          ? `Everything looks steady across ${aquariums.length} tank${aquariums.length === 1 ? "" : "s"}.`
          : "Start by adding a tank and building your care routine.";
  const todayMetrics = [
    {
      label: "Tanks",
      value: `${aquariums.length}`,
      detail:
        aquariums.length === 1 ? "single active system" : "active systems",
      accentColor: theme.colors.primary,
    },
    {
      label: "Residents",
      value: `${totalLivestock}`,
      detail: `${livestock.length} livestock record${livestock.length === 1 ? "" : "s"}`,
      accentColor: theme.colors.tertiary,
    },
    {
      label: "Open issues",
      value: `${totalOpenIssues}`,
      detail: totalOpenIssues > 0 ? "follow-up needed" : "all clear",
      accentColor:
        totalOpenIssues > 0 ? theme.colors.error : theme.colors.primary,
    },
    {
      label: "Dosing logs",
      value: `${dosingLogs.length}`,
      detail:
        parameterLogs.length > 0
          ? `${parameterLogs.length} water test${parameterLogs.length === 1 ? "" : "s"} logged`
          : "no chemistry logs yet",
      accentColor: theme.colors.secondary,
    },
  ];
  const todayAlertItems = todayAlertEntries.slice(0, 3).map((entry) => ({
    id: `${entry.aquariumId}-${entry.key}-${entry.status}`,
    title: `${entry.aquariumName} · ${entry.label}`,
    caption: `${entry.status === "high" ? "High" : "Low"} at ${entry.value}${entry.unit ? ` ${entry.unit}` : ""}`,
    accentColor:
      entry.status === "high" ? theme.colors.error : theme.colors.tertiary,
    onPress: () =>
      router.push(
        getEntityHref(createEntityRef("aquarium", entry.aquariumId)) as never,
      ),
  }));
  const todayTaskItems = pendingTasksToday.slice(0, 3).map((entry) => ({
    id: `${entry.taskId}-${entry.aquariumId}`,
    title: entry.taskTitle,
    caption: aquariumNameById[entry.aquariumId] ?? "Unknown tank",
    accentColor: theme.colors.secondary,
    onPress: () =>
      router.push(
        getEntityHref(createEntityRef("task", entry.taskId)) as never,
      ),
    onComplete: () => completeTask(entry.taskId, entry.aquariumId),
  }));
  const isWideTodayGlanceLayout = width >= 720;
  const todayMetricWidth = isWideTodayGlanceLayout ? "24%" : "48.5%";
  const todayPanelWidth = isWideTodayGlanceLayout ? "49%" : "100%";

  return (
    <>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          { paddingTop: 16 + insets.top },
        ]}
      >
        <View
          style={[
            styles.assistantHeader,
            {
              backgroundColor: theme.colors.surface,
              borderColor: theme.colors.outlineVariant,
            },
          ]}
        >
          <View
            style={[
              styles.assistantHeaderBadge,
              { backgroundColor: theme.colors.primaryContainer },
            ]}
          >
            <Text
              variant="labelLarge"
              style={{ color: theme.colors.onPrimaryContainer }}
            >
              AI
            </Text>
          </View>
          <View style={styles.assistantHeaderCopy}>
            <Text variant="titleMedium">Aquapt Dashboard</Text>
            <Text variant="bodySmall" style={styles.assistantHeaderSubtitle}>
              {assistantHeaderSubtitle}
            </Text>
          </View>
          <IconButton
            icon="message-text-outline"
            onPress={() => router.push("/(tabs)/assistant")}
          ></IconButton>
        </View>

        <Card
          style={[
            styles.summaryCard,
            styles.keepCard,
            {
              backgroundColor: theme.colors.primaryContainer,
              marginBottom: 6,
            },
          ]}
          mode="elevated"
        >
          <Card.Content style={styles.todayGlanceCardContent}>
            <View style={styles.todayGlanceHeader}>
              <View style={styles.todayGlanceHeaderCopy}>
                <Text
                  variant="titleMedium"
                  style={{ color: primarySummaryTextColor }}
                >
                  Today at a glance
                </Text>
                <Text
                  variant="bodySmall"
                  style={[
                    styles.todayGlanceDescription,
                    { color: todayGlanceTextColor },
                  ]}
                >
                  {todaySummaryDescription}
                </Text>
              </View>
              <View
                style={[
                  styles.todayGlanceBadge,
                  {
                    backgroundColor:
                      totalParameterAlerts > 0
                        ? theme.colors.errorContainer
                        : theme.colors.surface,
                  },
                ]}
              >
                <Text
                  variant="labelMedium"
                  style={{
                    color:
                      totalParameterAlerts > 0
                        ? theme.colors.onErrorContainer
                        : theme.colors.onSurface,
                  }}
                >
                  {totalParameterAlerts > 0
                    ? `${totalParameterAlerts} alert${totalParameterAlerts === 1 ? "" : "s"}`
                    : pendingTasksToday.length > 0
                      ? `${pendingTasksToday.length} due`
                      : "Calm"}
                </Text>
              </View>
            </View>

            <View style={styles.todayMetricsGrid}>
              {todayMetrics.map((metric) => (
                <View
                  key={metric.label}
                  style={[styles.todayMetricSlot, { width: todayMetricWidth }]}
                >
                  <TodayMetricCard
                    label={metric.label}
                    value={metric.value}
                    detail={metric.detail}
                    accentColor={metric.accentColor}
                    backgroundColor={theme.colors.surface}
                    textColor={theme.colors.onSurface}
                  />
                </View>
              ))}
            </View>

            <View style={styles.todayFocusGrid}>
              <View style={[styles.todayFocusSlot, { width: todayPanelWidth }]}>
                <TodayFocusPanel
                  eyebrow="Water safety"
                  title={
                    totalParameterAlerts > 0
                      ? `${totalParameterAlerts} alert${totalParameterAlerts === 1 ? "" : "s"} need attention`
                      : "No chemistry alerts"
                  }
                  summary={
                    totalParameterAlerts > 0
                      ? "Recent test values are outside your expected range."
                      : "Latest readings are sitting inside a safer range."
                  }
                  items={todayAlertItems}
                  emptyState="No active water safety warnings right now."
                  backgroundColor={theme.colors.surface}
                  textColor={theme.colors.onSurface}
                  emptyAccentColor={theme.colors.primary}
                />
              </View>
              <View style={[styles.todayFocusSlot, { width: todayPanelWidth }]}>
                <TodayFocusPanel
                  eyebrow="Due today"
                  title={
                    pendingTasksToday.length > 0
                      ? `${pendingTasksToday.length} task${pendingTasksToday.length === 1 ? "" : "s"} queued`
                      : "Nothing due today"
                  }
                  summary={
                    pendingTasksToday.length > 0
                      ? "Knock these out to keep routines current across your tanks."
                      : "Your recurring care schedule is clear for the day."
                  }
                  items={todayTaskItems}
                  emptyState="No recurring tasks need action today."
                  backgroundColor={theme.colors.surface}
                  textColor={theme.colors.onSurface}
                  emptyAccentColor={theme.colors.secondary}
                />
              </View>
            </View>
          </Card.Content>
        </Card>

        <View style={styles.aquariumList}>
          {aquariums.map((aquarium, index) => {
            const insight = aquariumInsightsById[aquarium.id];

            return (
              <AquariumOverviewCard
                key={aquarium.id}
                aquarium={aquarium}
                backgroundColor={getAquariumCardBackgroundColor(index)}
                latestParameterSummary={
                  insight?.latestParameterSummary ??
                  "No measurements logged yet"
                }
                investmentCostText={
                  aquarium.investmentCost !== undefined
                    ? formatCurrencyAmount(
                        aquarium.investmentCost,
                        userCurrencyCode,
                        userLocale,
                      )
                    : undefined
                }
                livestockCount={livestockCountByAquarium[aquarium.id] ?? 0}
                openIssueCount={openIssuesByAquarium[aquarium.id] ?? 0}
                nitrateTrend={insight?.nitrateTrend ?? "Not enough data yet"}
                onEdit={openEditAquarium}
                onOpenDetails={(aquariumId) =>
                  openEntity(
                    createEntityRef("aquarium", aquariumId, aquariumId),
                  )
                }
              />
            );
          })}
        </View>

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Create something new
        </Text>
        <View style={styles.keepGrid}>
          <View style={styles.keepColumn}>
            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                {
                  backgroundColor: theme.colors.tertiaryContainer,
                  overflow: "hidden",
                },
              ]}
              mode="contained"
              onPress={() => setAddAquariumOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <AquariumBackground
                  tint={theme.colors.onTertiaryContainer + "30"}
                />
                <Text
                  variant="titleSmall"
                  style={[
                    styles.actionTitle,
                    { color: theme.colors.onTertiaryContainer },
                  ]}
                >
                  New aquarium
                </Text>
                <Text
                  variant="bodySmall"
                  style={[
                    styles.actionDescription,
                    { color: theme.colors.onTertiaryContainer },
                  ]}
                >
                  Add tank size, setup date, and water type.
                </Text>
              </View>
            </Card>

            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                {
                  backgroundColor: theme.colors.secondaryContainer,
                  overflow: "hidden",
                },
              ]}
              mode="contained"
              onPress={() => setAddAssetOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <AssetBackground
                  tint={theme.colors.onSecondaryContainer + "30"}
                />
                <Text
                  variant="titleSmall"
                  style={[
                    styles.actionTitle,
                    { color: theme.colors.onSecondaryContainer },
                  ]}
                >
                  New asset
                </Text>
                <Text
                  variant="bodySmall"
                  style={[
                    styles.actionDescription,
                    { color: theme.colors.onSecondaryContainer },
                  ]}
                >
                  Register equipment and maintenance links.
                </Text>
              </View>
            </Card>
          </View>

          <View style={styles.keepColumn}>
            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                {
                  backgroundColor: theme.colors.primaryContainer,
                  overflow: "hidden",
                },
              ]}
              mode="contained"
              onPress={() => setAddLivestockOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <LivestockBackground
                  tint={theme.colors.onPrimaryContainer + "30"}
                />
                <Text
                  variant="titleSmall"
                  style={[
                    styles.actionTitle,
                    { color: theme.colors.onPrimaryContainer },
                  ]}
                >
                  New livestock
                </Text>
                <Text
                  variant="bodySmall"
                  style={[
                    styles.actionDescription,
                    { color: theme.colors.onPrimaryContainer },
                  ]}
                >
                  Log species, quantity, and optional photo.
                </Text>
              </View>
            </Card>

            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                {
                  backgroundColor: theme.colors.surfaceVariant,
                  overflow: "hidden",
                },
              ]}
              mode="contained"
              onPress={() => setAddConsumableOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <ConsumableBackground
                  tint={theme.colors.onSurfaceVariant + "30"}
                />
                <Text
                  variant="titleSmall"
                  style={[
                    styles.actionTitle,
                    { color: theme.colors.onSurfaceVariant },
                  ]}
                >
                  New consumable
                </Text>
                <Text
                  variant="bodySmall"
                  style={[
                    styles.actionDescription,
                    { color: theme.colors.onSurfaceVariant },
                  ]}
                >
                  Track stock levels and reorder thresholds.
                </Text>
              </View>
            </Card>
          </View>
        </View>

        <Card
          style={[
            styles.sectionShell,
            styles.keepCard,
            { backgroundColor: theme.colors.surface },
          ]}
          mode="elevated"
        >
          <Card.Content style={styles.sectionShellContent}>
            <View style={styles.sectionHeader}>
              <Text variant="titleMedium">Parameter analytics</Text>
              <Text variant="bodySmall" style={styles.sectionDescription}>
                Track recent chemistry shifts and compare trends across tanks.
              </Text>
            </View>

            <Card
              style={[
                styles.tankCard,
                styles.keepCard,
                styles.sectionFeatureCard,
                { backgroundColor: theme.colors.surfaceVariant },
              ]}
              mode="contained"
            >
              <Card.Content>
                <View style={styles.sectionChipRow}>
                  <Chip compact icon="chart-line">
                    {selectedMetricLabel} trend
                  </Chip>
                  <Chip compact icon="timeline-clock">
                    Last 8 logs
                  </Chip>
                </View>
                <ScrollableSegmentedButtons
                  value={chartAquariumId}
                  onValueChange={setSelectedAquariumId}
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                />
                <ScrollableSegmentedButtons
                  value={selectedMetric}
                  onValueChange={(value) =>
                    setSelectedMetric(value as AnalyticMetricKey)
                  }
                  buttons={ANALYTIC_METRICS.map((metric) => ({
                    label: metric.label,
                    value: metric.value,
                  }))}
                  style={styles.metricSelector}
                />
                {chartData.length > 1 ? (
                  <View style={styles.chartWrap}>
                    <LineChart
                      areaChart
                      data={chartData}
                      width={Math.max(220, width - 96)}
                      spacing={28}
                      color={METRIC_COLORS[selectedMetric]}
                      startFillColor={METRIC_COLORS[selectedMetric]}
                      endFillColor={METRIC_COLORS[selectedMetric]}
                      startOpacity={0.22}
                      endOpacity={0.04}
                      hideDataPoints={false}
                      dataPointsColor={METRIC_COLORS[selectedMetric]}
                      yAxisTextStyle={{ color: theme.colors.onSurfaceVariant, fontSize: 10 }}
                      xAxisLabelTextStyle={{ color: theme.colors.onSurfaceVariant, fontSize: 10 }}
                      rulesColor={withAlpha(theme.colors.onSurfaceVariant, 0.15)}
                    />
                    <Text variant="bodySmall" style={styles.chartUnitLabel}>
                      Unit: {METRIC_UNITS[selectedMetric] || "value"}
                    </Text>
                  </View>
                ) : (
                  <Text variant="bodyMedium" style={styles.chartEmpty}>
                    Need at least 2 {selectedMetricLabel} logs for charting.
                  </Text>
                )}
              </Card.Content>
            </Card>
          </Card.Content>
        </Card>

        <Card
          style={[
            styles.sectionShell,
            styles.keepCard,
            { backgroundColor: theme.colors.surface },
          ]}
          mode="elevated"
        >
          <Card.Content style={styles.sectionShellContent}>
            <View style={styles.sectionHeader}>
              <Text variant="titleMedium">Livestock tracking</Text>
              <Text variant="bodySmall" style={styles.sectionDescription}>
                Keep feeding, status, and breeding actions close to each record.
              </Text>
            </View>

            <View style={styles.sectionStack}>
              {livestock.length === 0 ? (
                <Card
                  style={[
                    styles.keepCard,
                    styles.sectionFeatureCard,
                    { backgroundColor: theme.colors.surfaceVariant },
                  ]}
                  mode="contained"
                >
                  <Card.Content>
                    <Text variant="bodyMedium">
                      Add livestock to start tracking feeding notes, health, and
                      transfers.
                    </Text>
                  </Card.Content>
                </Card>
              ) : null}

              {livestock.map((item, index) => {
                const currentIndex = aquariumIndexById[item.aquariumId] ?? -1;
                const fallbackTarget =
                  aquariums[
                    (currentIndex + 1 + aquariums.length) % aquariums.length
                  ]?.id;
                const feedingNote =
                  feedingNoteDraft[item.id] ?? item.dietaryNotes ?? "";
                const livestockStatus =
                  livestockStatusDraft[item.id] ?? item.status ?? "active";
                const livestockStatusNote =
                  livestockStatusNoteDraft[item.id] ?? "";
                const parentEntity = item.parentId
                  ? livestock.find(
                      (candidate) => candidate.id === item.parentId,
                    )
                  : undefined;
                const offspringEntities = livestock.filter(
                  (candidate) => candidate.parentId === item.id,
                );
                const feedingTasks = taskTemplates.filter(
                  (task) => task.livestockId === item.id,
                );

                const cardBackground =
                  index % 2 === 0
                    ? theme.colors.tertiaryContainer
                    : theme.colors.secondaryContainer;

                return (
                  <LivestockCard
                    key={item.id}
                    item={item}
                    aquariumName={
                      aquariumNameById[item.aquariumId] ?? "Unknown tank"
                    }
                    fallbackTargetId={fallbackTarget}
                    feedingNote={feedingNote}
                    livestockStatus={livestockStatus}
                    livestockStatusNote={livestockStatusNote}
                    feedingTaskTitle={feedingTaskTitleDraft[item.id] ?? ""}
                    feedingTaskFrequency={
                      feedingTaskFrequencyDraft[item.id] ?? "daily"
                    }
                    feedingTaskStartDate={
                      feedingTaskStartDateDraft[item.id] ?? ""
                    }
                    feedingTaskTimesPerDay={
                      feedingTaskTimesPerDayDraft[item.id] ?? ""
                    }
                    cardBackground={cardBackground}
                    parentEntity={
                      parentEntity
                        ? {
                            id: parentEntity.id,
                            name: parentEntity.name,
                            aquariumId: parentEntity.aquariumId,
                          }
                        : undefined
                    }
                    offspringEntities={offspringEntities.map((offspring) => ({
                      id: offspring.id,
                      name: offspring.name,
                      aquariumId: offspring.aquariumId,
                    }))}
                    feedingTasks={feedingTasks}
                    openEntity={openEntity}
                    setFeedingNoteDraft={setFeedingNoteDraft}
                    setLivestockStatusDraft={setLivestockStatusDraft}
                    setLivestockStatusNoteDraft={setLivestockStatusNoteDraft}
                    setFeedingTaskTitleDraft={setFeedingTaskTitleDraft}
                    setFeedingTaskFrequencyDraft={setFeedingTaskFrequencyDraft}
                    setFeedingTaskStartDateDraft={setFeedingTaskStartDateDraft}
                    setFeedingTaskTimesPerDayDraft={
                      setFeedingTaskTimesPerDayDraft
                    }
                    setLivestockFeedingNotes={setLivestockFeedingNotes}
                    setLivestockStatus={setLivestockStatus}
                    transferLivestock={transferLivestock}
                    addOffspring={addOffspring}
                    addLivestockFeedingTask={addLivestockFeedingTask}
                  />
                );
              })}
            </View>
          </Card.Content>
        </Card>

        <Card
          style={[
            styles.sectionShell,
            styles.keepCard,
            { backgroundColor: theme.colors.surface },
          ]}
          mode="elevated"
        >
          <Card.Content style={styles.sectionShellContent}>
            <View style={styles.sectionHeader}>
              <Text variant="titleMedium">Assets & consumables inventory</Text>
              <Text variant="bodySmall" style={styles.sectionDescription}>
                See equipment coverage and stock usage in the same visual rhythm
                as the rest of the dashboard.
              </Text>
            </View>

            <View style={styles.sectionStack}>
              <View style={styles.sectionSubgroup}>
                <Text variant="labelLarge" style={styles.sectionLabel}>
                  Assets
                </Text>
                {assets.length === 0 ? (
                  <Card
                    style={[
                      styles.keepCard,
                      styles.sectionFeatureCard,
                      { backgroundColor: theme.colors.surfaceVariant },
                    ]}
                    mode="contained"
                  >
                    <Card.Content>
                      <Text variant="bodyMedium">
                        No equipment registered yet.
                      </Text>
                    </Card.Content>
                  </Card>
                ) : null}
                {assets.map((asset) => (
                  <Card
                    key={asset.id}
                    style={[
                      styles.issueCard,
                      styles.sectionItemCard,
                      styles.keepCard,
                      { backgroundColor: theme.colors.secondaryContainer },
                    ]}
                    mode="contained"
                    onPress={() =>
                      openEntity(
                        createEntityRef("asset", asset.id, asset.aquariumId),
                      )
                    }
                  >
                    <Card.Content>
                      {asset.photoUri ? (
                        <Image
                          source={{ uri: asset.photoUri }}
                          style={styles.entityPhoto}
                          contentFit="cover"
                        />
                      ) : null}
                      <Text variant="titleSmall">{asset.brandModel}</Text>
                      <Text variant="bodySmall" style={styles.issueMeta}>
                        {asset.category} •{" "}
                        {aquariumNameById[asset.aquariumId] ?? "Unknown tank"}
                      </Text>
                      <Text variant="bodySmall" style={styles.issueMeta}>
                        Purchased: {asset.purchasedAt ?? "-"}
                        {asset.price !== undefined
                          ? ` • ${formatCurrencyAmount(
                              asset.price,
                              userCurrencyCode,
                              userLocale,
                            )}`
                          : ""}
                      </Text>
                      {asset.maintenanceTaskTemplateIds?.length ? (
                        <View style={styles.summaryRow}>
                          {asset.maintenanceTaskTemplateIds
                            .map((taskId) =>
                              taskTemplates.find((task) => task.id === taskId),
                            )
                            .filter((task): task is TaskTemplate => !!task)
                            .map((task) => (
                              <Chip
                                key={task.id}
                                compact
                                icon="wrench"
                                onPress={() =>
                                  openEntity(
                                    createEntityRef(
                                      "task",
                                      task.id,
                                      asset.aquariumId,
                                    ),
                                  )
                                }
                              >
                                {task.title}
                              </Chip>
                            ))}
                        </View>
                      ) : null}
                    </Card.Content>
                  </Card>
                ))}
              </View>

              <View style={styles.sectionSubgroup}>
                <Text variant="labelLarge" style={styles.sectionLabel}>
                  Consumables
                </Text>
                {consumables.length === 0 ? (
                  <Card
                    style={[
                      styles.keepCard,
                      styles.sectionFeatureCard,
                      { backgroundColor: theme.colors.surfaceVariant },
                    ]}
                    mode="contained"
                  >
                    <Card.Content>
                      <Text variant="bodyMedium">
                        No consumables added yet.
                      </Text>
                    </Card.Content>
                  </Card>
                ) : null}
                {consumables.map((consumable) => {
                  const low =
                    consumable.reorderAt !== undefined &&
                    consumable.remaining <= consumable.reorderAt;

                  return (
                    <Card
                      key={consumable.id}
                      style={[
                        styles.issueCard,
                        styles.sectionItemCard,
                        styles.keepCard,
                        { backgroundColor: theme.colors.primaryContainer },
                      ]}
                      mode="contained"
                      onPress={() =>
                        openEntity(
                          createEntityRef(
                            "consumable",
                            consumable.id,
                            consumable.aquariumId,
                          ),
                        )
                      }
                    >
                      <Card.Content>
                        {consumable.photoUri ? (
                          <Image
                            source={{ uri: consumable.photoUri }}
                            style={styles.entityPhoto}
                            contentFit="cover"
                          />
                        ) : null}
                        <Text variant="titleSmall">{consumable.name}</Text>
                        <Text variant="bodySmall" style={styles.issueMeta}>
                          {consumable.remaining}
                          {consumable.unit} remaining •{" "}
                          {low ? "Reorder soon" : "Stock OK"}
                        </Text>
                        <Button
                          mode="contained"
                          style={styles.issueSaveButton}
                          onPress={() =>
                            consumeConsumable(consumable.id, 1, "Daily usage")
                          }
                        >
                          Use 1 {consumable.unit}
                        </Button>
                      </Card.Content>
                    </Card>
                  );
                })}
              </View>
            </View>
          </Card.Content>
        </Card>

        <Card
          style={[
            styles.sectionShell,
            styles.keepCard,
            { backgroundColor: theme.colors.surface },
          ]}
          mode="elevated"
        >
          <Card.Content style={styles.sectionShellContent}>
            <View style={styles.sectionHeader}>
              <Text variant="titleMedium">Issue tracking</Text>
              <Text variant="bodySmall" style={styles.sectionDescription}>
                Manage open problems with the same quick-edit flow, now in a
                layout that matches the top-of-page cards.
              </Text>
            </View>

            <View style={styles.sectionStack}>
              {issues.length === 0 ? (
                <Card
                  style={[
                    styles.keepCard,
                    styles.sectionFeatureCard,
                    { backgroundColor: theme.colors.surfaceVariant },
                  ]}
                  mode="contained"
                >
                  <Card.Content>
                    <Text variant="bodyMedium">
                      No issues logged yet. Great news for your tanks.
                    </Text>
                  </Card.Content>
                </Card>
              ) : null}

              {issues.map((issue) => {
                const currentStatus =
                  issueStatusDraft[issue.id] ?? issue.status;
                const resolutionNote =
                  resolutionNoteDraft[issue.id] ?? issue.resolutionNote ?? "";

                return (
                  <IssueCard
                    key={issue.id}
                    issue={issue}
                    aquariumName={
                      aquariumNameById[issue.aquariumId] ?? "Unknown tank"
                    }
                    currentStatus={currentStatus}
                    resolutionNote={resolutionNote}
                    setIssueStatusDraft={setIssueStatusDraft}
                    setResolutionNoteDraft={setResolutionNoteDraft}
                    setIssueStatus={setIssueStatus}
                    backgroundColor={theme.colors.surfaceVariant}
                    onOpenDetails={(issueId, aquariumId) =>
                      openEntity(createEntityRef("issue", issueId, aquariumId))
                    }
                  />
                );
              })}
            </View>
          </Card.Content>
        </Card>
      </ScrollView>

      <BottomSheet
        visible={isEditAquariumOpen}
        onDismiss={() => {
          setEditAquariumOpen(false);
          editAquariumForm.setFieldValue("id", "");
        }}
        title="Edit aquarium"
        actions={
          <>
            <Button
              onPress={() => {
                setEditAquariumOpen(false);
                editAquariumForm.setFieldValue("id", "");
              }}
            >
              Cancel
            </Button>
            <Button onPress={saveAquariumEdit}>Save</Button>
          </>
        }
      >
        <View style={styles.inputsContainer}>
          <editAquariumForm.Field name="name">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Name"
                value={field.state.value}
                onChangeText={field.handleChange}
              />
            )}
          </editAquariumForm.Field>
          <editAquariumForm.Field name="volume">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Volume (L)"
                value={field.state.value}
                onChangeText={field.handleChange}
                keyboardType="numeric"
              />
            )}
          </editAquariumForm.Field>
          <editAquariumForm.Field name="dimensions">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Dimensions"
                value={field.state.value}
                onChangeText={field.handleChange}
              />
            )}
          </editAquariumForm.Field>
          <editAquariumForm.Subscribe
            selector={(state) => state.values.setupDate}
          >
            {(setupDate) => (
              <Button
                mode="outlined"
                icon="calendar"
                onPress={() => setEditDatePickerOpen(true)}
              >
                Setup date: {setupDate}
              </Button>
            )}
          </editAquariumForm.Subscribe>
          <editAquariumForm.Field name="investment">
            {(field) => (
              <TextInput
                mode="outlined"
                label={getCurrencyFieldLabel("Investment cost")}
                value={field.state.value}
                onChangeText={field.handleChange}
                keyboardType="numeric"
              />
            )}
          </editAquariumForm.Field>
          <editAquariumForm.Subscribe
            selector={(state) => state.values.photoUri}
          >
            {(photoUri) => (
              <>
                <Button
                  mode="contained-tonal"
                  onPress={() => {
                    void pickAquariumPhoto("edit");
                  }}
                  loading={isPickingAquariumPhoto}
                >
                  {photoUri ? "Change photo" : "Add aquarium photo"}
                </Button>
                {photoUri ? (
                  <Image
                    source={{ uri: photoUri }}
                    style={styles.photoPreview}
                    contentFit="cover"
                  />
                ) : null}
              </>
            )}
          </editAquariumForm.Subscribe>
        </View>
      </BottomSheet>

      <BottomSheet
        visible={isAddAquariumOpen}
        onDismiss={() => setAddAquariumOpen(false)}
        title="Add aquarium"
        actions={
          <>
            <Button onPress={() => setAddAquariumOpen(false)}>Cancel</Button>
            <Button onPress={() => void addAquariumForm.handleSubmit()}>
              Save
            </Button>
          </>
        }
      >
        <View style={styles.inputsContainer}>
          <addAquariumForm.Field name="name">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Aquarium name"
                value={field.state.value}
                onChangeText={field.handleChange}
              />
            )}
          </addAquariumForm.Field>
          <addAquariumForm.Field name="volume">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Volume (L)"
                value={field.state.value}
                onChangeText={field.handleChange}
                keyboardType="numeric"
              />
            )}
          </addAquariumForm.Field>
          <addAquariumForm.Field name="dimensions">
            {(field) => (
              <TextInput
                mode="outlined"
                label="Dimensions"
                value={field.state.value}
                onChangeText={field.handleChange}
                placeholder="90 x 45 x 45 cm"
              />
            )}
          </addAquariumForm.Field>
          <addAquariumForm.Subscribe
            selector={(state) => state.values.setupDate}
          >
            {(setupDate) => (
              <Button
                mode="outlined"
                icon="calendar"
                onPress={() => setNewDatePickerOpen(true)}
              >
                Setup date: {setupDate}
              </Button>
            )}
          </addAquariumForm.Subscribe>
          <addAquariumForm.Field name="investment">
            {(field) => (
              <TextInput
                mode="outlined"
                label={getCurrencyFieldLabel("Investment cost", true)}
                value={field.state.value}
                onChangeText={field.handleChange}
                keyboardType="numeric"
              />
            )}
          </addAquariumForm.Field>
          <addAquariumForm.Field name="waterType">
            {(field) => (
              <ScrollableSegmentedButtons
                value={field.state.value}
                onValueChange={(value) =>
                  field.handleChange(
                    value as "freshwater" | "marine" | "brackish",
                  )
                }
                buttons={WATER_TYPES.map((type) => ({
                  label: type,
                  value: type,
                }))}
              />
            )}
          </addAquariumForm.Field>
          <addAquariumForm.Subscribe
            selector={(state) => state.values.photoUri}
          >
            {(photoUri) => (
              <>
                <Button
                  mode="contained-tonal"
                  onPress={() => {
                    void pickAquariumPhoto("add");
                  }}
                  loading={isPickingAquariumPhoto}
                >
                  {photoUri ? "Change photo" : "Add aquarium photo"}
                </Button>
                {photoUri ? (
                  <Image
                    source={{ uri: photoUri }}
                    style={styles.photoPreview}
                    contentFit="cover"
                  />
                ) : null}
              </>
            )}
          </addAquariumForm.Subscribe>
        </View>
      </BottomSheet>

      <BottomSheet
        visible={isAddLivestockOpen}
        onDismiss={() => setAddLivestockOpen(false)}
        title="Add livestock"
        actions={
          <>
            <Button onPress={() => setAddLivestockOpen(false)}>Cancel</Button>
            <Button onPress={createLivestock}>Save</Button>
          </>
        }
      >
        <View style={styles.inputsContainer}>
          <ScrollableSegmentedButtons
            value={selectedAquariumId}
            onValueChange={setSelectedAquariumId}
            buttons={aquariums.map((aq) => ({
              label: aq.name,
              value: aq.id,
            }))}
          />
          <TextInput
            mode="outlined"
            label="Name"
            value={newLivestockName}
            onChangeText={setNewLivestockName}
          />
          <TextInput
            mode="outlined"
            label="Species"
            value={newLivestockSpecies}
            onChangeText={setNewLivestockSpecies}
          />
          <ScrollableSegmentedButtons
            value={newLivestockKind}
            onValueChange={(value) =>
              setNewLivestockKind(value as Livestock["kind"])
            }
            buttons={LIVESTOCK_KINDS.map((kind) => ({
              label: kind,
              value: kind,
            }))}
          />
          <TextInput
            mode="outlined"
            label="Quantity"
            value={newLivestockQty}
            onChangeText={setNewLivestockQty}
            keyboardType="numeric"
          />
          <TextInput
            mode="outlined"
            label={getCurrencyFieldLabel("Purchase price", true)}
            value={newLivestockPrice}
            onChangeText={setNewLivestockPrice}
            keyboardType="numeric"
          />
          <Button
            mode="contained-tonal"
            onPress={pickLivestockPhoto}
            loading={isPickingPhoto}
          >
            {newLivestockPhotoUri ? "Change photo" : "Select photo"}
          </Button>
          {newLivestockPhotoUri ? (
            <Image
              source={{ uri: newLivestockPhotoUri }}
              style={styles.photoPreview}
            />
          ) : null}
        </View>
      </BottomSheet>

      <BottomSheet
        visible={isAddAssetOpen}
        onDismiss={() => setAddAssetOpen(false)}
        title="Add asset"
        actions={
          <>
            <Button onPress={() => setAddAssetOpen(false)}>Cancel</Button>
            <Button onPress={createAsset}>Save</Button>
          </>
        }
      >
        <View style={styles.inputsContainer}>
          <ScrollableSegmentedButtons
            value={selectedAquariumId}
            onValueChange={setSelectedAquariumId}
            buttons={aquariums.map((aq) => ({
              label: aq.name,
              value: aq.id,
            }))}
          />
          <TextInput
            mode="outlined"
            label="Asset model"
            value={newAssetModel}
            onChangeText={setNewAssetModel}
          />
          <ScrollableSegmentedButtons
            value={newAssetCategory}
            onValueChange={(value) =>
              setNewAssetCategory(value as AssetCategory)
            }
            buttons={ASSET_CATEGORIES.map((category) => ({
              label: category,
              value: category,
            }))}
          />
          <Button
            mode="outlined"
            icon="calendar"
            onPress={() => setAssetDatePickerOpen(true)}
          >
            Purchased: {newAssetPurchasedAt}
          </Button>
          <TextInput
            mode="outlined"
            label={getCurrencyFieldLabel("Asset price", true)}
            value={newAssetPrice}
            onChangeText={setNewAssetPrice}
            keyboardType="numeric"
          />
          <Button
            mode="contained-tonal"
            onPress={() => {
              void pickAssetPhoto();
            }}
            loading={isPickingAssetPhoto}
          >
            {newAssetPhotoUri ? "Change photo" : "Add asset photo"}
          </Button>
          {newAssetPhotoUri ? (
            <Image
              source={{ uri: newAssetPhotoUri }}
              style={styles.photoPreview}
              contentFit="cover"
            />
          ) : null}
          {availableTaskTemplatesForAsset.length > 0 ? (
            <View style={styles.summaryRow}>
              {availableTaskTemplatesForAsset.map((task) => {
                const selected = selectedAssetTaskTemplateIds.includes(task.id);
                return (
                  <Chip
                    key={task.id}
                    selected={selected}
                    onPress={() =>
                      setSelectedAssetTaskTemplateIds((prev) =>
                        selected
                          ? prev.filter((id) => id !== task.id)
                          : [...prev, task.id],
                      )
                    }
                  >
                    {task.title}
                  </Chip>
                );
              })}
            </View>
          ) : null}
        </View>
      </BottomSheet>

      <BottomSheet
        visible={isAddConsumableOpen}
        onDismiss={() => setAddConsumableOpen(false)}
        title="Add consumable"
        actions={
          <>
            <Button onPress={() => setAddConsumableOpen(false)}>Cancel</Button>
            <Button onPress={createConsumable}>Save</Button>
          </>
        }
      >
        <View style={styles.inputsContainer}>
          <ScrollableSegmentedButtons
            value={selectedAquariumId}
            onValueChange={setSelectedAquariumId}
            buttons={aquariums.map((aq) => ({
              label: aq.name,
              value: aq.id,
            }))}
          />
          <TextInput
            mode="outlined"
            label="Consumable name"
            value={newConsumableName}
            onChangeText={setNewConsumableName}
          />
          <TextInput
            mode="outlined"
            label={`Remaining (${newConsumableUnit})`}
            value={newConsumableRemaining}
            onChangeText={setNewConsumableRemaining}
            keyboardType="numeric"
          />
          <ScrollableSegmentedButtons
            value={newConsumableUnit}
            onValueChange={(value) =>
              setNewConsumableUnit(value as "pcs" | "ml" | "g")
            }
            buttons={CONSUMABLE_UNITS.map((unit) => ({
              label: unit,
              value: unit,
            }))}
          />
          <Button
            mode="contained-tonal"
            onPress={() => {
              void pickConsumablePhoto();
            }}
            loading={isPickingConsumablePhoto}
          >
            {newConsumablePhotoUri ? "Change photo" : "Add consumable photo"}
          </Button>
          {newConsumablePhotoUri ? (
            <Image
              source={{ uri: newConsumablePhotoUri }}
              style={styles.photoPreview}
              contentFit="cover"
            />
          ) : null}
        </View>
      </BottomSheet>

      <PhotoSourceDialog
        visible={Boolean(photoDialogConfig)}
        title={photoDialogConfig?.title ?? "Select photo"}
        description="Choose photo source"
        hasCurrentPhoto={Boolean(photoDialogConfig?.currentUri)}
        loading={
          isPickingPhoto ||
          isPickingMemoPhoto ||
          isPickingAquariumPhoto ||
          isPickingAssetPhoto ||
          isPickingConsumablePhoto
        }
        onDismiss={() => setPhotoDialogConfig(null)}
        onPickSource={(source) => {
          void pickPhotoFromSource(source);
        }}
        onRemovePhoto={
          photoDialogConfig
            ? () => {
                photoDialogConfig.onCleared();
                setPhotoDialogConfig(null);
              }
            : undefined
        }
      />

      <addAquariumForm.Subscribe
        selector={(state) => state.values.setupDateValue}
      >
        {(setupDateValue) => (
          <DatePickerModal
            locale="en"
            mode="single"
            visible={isNewDatePickerOpen}
            date={setupDateValue}
            onDismiss={() => setNewDatePickerOpen(false)}
            onConfirm={({ date }) => {
              if (date) {
                addAquariumForm.setFieldValue("setupDateValue", date);
                addAquariumForm.setFieldValue("setupDate", toIsoDate(date));
              }
              setNewDatePickerOpen(false);
            }}
          />
        )}
      </addAquariumForm.Subscribe>

      <editAquariumForm.Subscribe
        selector={(state) => state.values.setupDateValue}
      >
        {(setupDateValue) => (
          <DatePickerModal
            locale="en"
            mode="single"
            visible={isEditDatePickerOpen}
            date={setupDateValue}
            onDismiss={() => setEditDatePickerOpen(false)}
            onConfirm={({ date }) => {
              if (date) {
                editAquariumForm.setFieldValue("setupDateValue", date);
                editAquariumForm.setFieldValue("setupDate", toIsoDate(date));
              }
              setEditDatePickerOpen(false);
            }}
          />
        )}
      </editAquariumForm.Subscribe>

      <DatePickerModal
        locale="en"
        mode="single"
        visible={isAssetDatePickerOpen}
        date={newAssetPurchasedAtValue}
        onDismiss={() => setAssetDatePickerOpen(false)}
        onConfirm={({ date }) => {
          if (date) {
            setNewAssetPurchasedAtValue(date);
            setNewAssetPurchasedAt(toIsoDate(date));
          }
          setAssetDatePickerOpen(false);
        }}
      />

      <BottomSheet
        visible={isDialogOpen}
        onDismiss={() => {
          setDialogOpen(false);
          quickLogForm.setFieldValue("selectedAquariumId", selectedAquariumId);
          quickLogForm.setFieldValue("action", "parameter");
          quickLogForm.setFieldValue("memo.text", "");
          quickLogForm.setFieldValue("memo.photoUri", "");
          quickLogForm.setFieldValue("issue.title", "");
          quickLogForm.setFieldValue("dosing.product", "");
          quickLogForm.setFieldValue("dosing.amount", "");
          quickLogForm.setFieldValue("task.templateId", "");
          quickLogForm.setFieldValue("task.note", "");
        }}
        title="Quick Log"
        actions={
          <>
            <Button
              onPress={() => {
                setDialogOpen(false);
                quickLogForm.setFieldValue(
                  "selectedAquariumId",
                  selectedAquariumId,
                );
              }}
            >
              Cancel
            </Button>
            <quickLogForm.Subscribe selector={(state) => state.values}>
              {(values) => {
                const canSaveByAction: Record<typeof values.action, boolean> = {
                  task: values.task.templateId.length > 0,
                  parameter: true,
                  memo: values.memo.text.trim().length > 0,
                  issue: values.issue.title.trim().length > 0,
                  dosing:
                    values.dosing.product.trim().length > 0 &&
                    Number.isFinite(Number(values.dosing.amount)) &&
                    Number(values.dosing.amount) > 0,
                };

                return (
                  <Button
                    onPress={handleSubmitQuickAction}
                    disabled={
                      !values.selectedAquariumId ||
                      !canSaveByAction[values.action]
                    }
                  >
                    Save
                  </Button>
                );
              }}
            </quickLogForm.Subscribe>
          </>
        }
      >
        <quickLogForm.Field name="selectedAquariumId">
          {(field) => (
            <ScrollableSegmentedButtons
              value={field.state.value}
              onValueChange={(value) => {
                field.handleChange(value);
                setSelectedAquariumId(value);
              }}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
                style: styles.segmentButton,
              }))}
              density="small"
            />
          )}
        </quickLogForm.Field>

        <quickLogForm.Field name="action">
          {(field) => (
            <ScrollableSegmentedButtons
              value={field.state.value}
              onValueChange={(value) =>
                field.handleChange(
                  value as "parameter" | "memo" | "issue" | "dosing" | "task",
                )
              }
              buttons={[
                { label: "Task", value: "task" },
                { label: "Parameters", value: "parameter" },
                { label: "Memo", value: "memo" },
                { label: "Issue", value: "issue" },
                { label: "Dosing", value: "dosing" },
              ]}
              style={styles.actionSelector}
            />
          )}
        </quickLogForm.Field>

        <quickLogForm.Subscribe selector={(state) => state.values}>
          {(values) => {
            const dueTasksForSelectedAquarium =
              dueTasksByAquarium[values.selectedAquariumId] ?? [];

            return (
              <>
                {values.action === "task" ? (
                  <View style={styles.inputsContainer}>
                    <quickLogForm.Field name="task.templateId">
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
                    </quickLogForm.Field>
                    {dueTasksForSelectedAquarium.length === 0 ? (
                      <Text variant="bodySmall" style={styles.issueMeta}>
                        No due tasks for this aquarium.
                      </Text>
                    ) : null}
                    <quickLogForm.Field name="task.note">
                      {(field) => (
                        <TextInput
                          label="Completion note (optional)"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          multiline
                          numberOfLines={2}
                        />
                      )}
                    </quickLogForm.Field>
                  </View>
                ) : null}

                {values.action === "parameter" ? (
                  <View style={styles.inputsContainer}>
                    <quickLogForm.Field name="parameter.ammonia">
                      {(field) => (
                        <TextInput
                          label="Ammonia (ppm)"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.nitrite">
                      {(field) => (
                        <TextInput
                          label="Nitrite (ppm)"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.nitrate">
                      {(field) => (
                        <TextInput
                          label="Nitrate (ppm)"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.ph">
                      {(field) => (
                        <TextInput
                          label="pH"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.temperature">
                      {(field) => (
                        <TextInput
                          label="Temperature °C"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.gh">
                      {(field) => (
                        <TextInput
                          label="GH"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.kh">
                      {(field) => (
                        <TextInput
                          label="KH"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.salinity">
                      {(field) => (
                        <TextInput
                          label="Salinity"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.calcium">
                      {(field) => (
                        <TextInput
                          label="Calcium"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="parameter.alkalinity">
                      {(field) => (
                        <TextInput
                          label="Alkalinity"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                  </View>
                ) : null}

                {values.action === "memo" ? (
                  <View style={styles.inputsContainer}>
                    <quickLogForm.Field name="memo.text">
                      {(field) => (
                        <TextInput
                          label="Memo"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          multiline
                          numberOfLines={4}
                        />
                      )}
                    </quickLogForm.Field>
                    <Button
                      mode="contained-tonal"
                      onPress={pickMemoPhoto}
                      loading={isPickingMemoPhoto}
                    >
                      {values.memo.photoUri
                        ? "Change memo photo"
                        : "Attach memo photo"}
                    </Button>
                    {values.memo.photoUri ? (
                      <Image
                        source={{ uri: values.memo.photoUri }}
                        style={styles.photoPreview}
                      />
                    ) : null}
                  </View>
                ) : null}

                {values.action === "issue" ? (
                  <quickLogForm.Field name="issue.title">
                    {(field) => (
                      <TextInput
                        label="Issue title"
                        mode="outlined"
                        value={field.state.value}
                        onChangeText={field.handleChange}
                        style={styles.inputTopSpacing}
                      />
                    )}
                  </quickLogForm.Field>
                ) : null}

                {values.action === "dosing" ? (
                  <View style={styles.inputsContainer}>
                    <quickLogForm.Field name="dosing.product">
                      {(field) => (
                        <TextInput
                          label="Product"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                        />
                      )}
                    </quickLogForm.Field>
                    <quickLogForm.Field name="dosing.amount">
                      {(field) => (
                        <TextInput
                          label="Amount (ml)"
                          mode="outlined"
                          value={field.state.value}
                          onChangeText={field.handleChange}
                          keyboardType="numeric"
                        />
                      )}
                    </quickLogForm.Field>
                  </View>
                ) : null}
              </>
            );
          }}
        </quickLogForm.Subscribe>
      </BottomSheet>

      {isFabTooltipVisible ? (
        <Portal>
          <View
            pointerEvents="none"
            style={[styles.fabTooltipWrap, { bottom: 160 + insets.bottom }]}
          >
            <Surface
              elevation={3}
              style={[
                styles.fabTooltip,
                { backgroundColor: theme.colors.inverseSurface },
              ]}
            >
              <Text
                variant="labelMedium"
                style={{ color: theme.colors.inverseOnSurface }}
              >
                Quick Log
              </Text>
            </Surface>
          </View>
        </Portal>
      ) : null}

      <FAB
        icon="plus"
        style={[styles.fab, { bottom: 88 + insets.bottom }]}
        onPress={handleQuickLogFabPress}
        onLongPress={handleQuickLogFabLongPress}
        delayLongPress={250}
        accessibilityLabel="Quick log"
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 132,
    gap: 12,
  },
  keepGrid: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
  },
  keepColumn: {
    flex: 1,
    gap: 10,
  },
  aquariumList: {
    gap: 8,
  },
  aquariumCard: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 16,
    overflow: "hidden",
  },
  aquariumThumb: {
    width: 88,
    height: 88,
  },
  aquariumThumbPlaceholder: {
    width: 88,
    height: 88,
    alignItems: "center",
    justifyContent: "center",
  },
  aquariumCardBody: {
    flex: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 3,
    minWidth: 0,
  },
  aquariumCardHeader: {
    gap: 1,
  },
  aquariumCardTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  aquariumBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 999,
  },
  aquariumCardFooter: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 4,
  },
  aquariumStatRow: {
    flexDirection: "row",
    gap: 8,
    flex: 1,
    minWidth: 0,
  },
  aquariumStat: {
    opacity: 0.72,
    lineHeight: 14,
  },
  aquariumCardActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
  },
  aquariumCardIconButton: {
    margin: 0,
  },
  keepCard: {
    borderRadius: 18,
    marginVertical: 0,
    overflow: "hidden",
  },
  actionCard: {
    minHeight: 140,
  },
  actionCardInner: {
    minHeight: 140,
    padding: 16,
    justifyContent: "flex-end",
  },
  actionTitle: {
    fontWeight: "700",
  },
  actionIllustration: {
    fontSize: 34,
    marginBottom: 10,
  },
  actionDescription: {
    marginTop: 4,
    opacity: 0.82,
  },
  assistantHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: 24,
    borderWidth: StyleSheet.hairlineWidth,
    marginBottom: 2,
  },
  assistantHeaderBadge: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: "center",
    justifyContent: "center",
  },
  assistantHeaderCopy: {
    flex: 1,
    gap: 4,
  },
  assistantHeaderSubtitle: {
    opacity: 0.74,
    lineHeight: 18,
  },
  summaryCard: {
    marginVertical: 0,
    borderRadius: 24,
  },
  todayGlanceCardContent: {
    gap: 12,
  },
  todayGlanceHeader: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 12,
  },
  todayGlanceHeaderCopy: {
    flex: 1,
    gap: 4,
  },
  todayGlanceDescription: {
    opacity: 0.76,
    lineHeight: 18,
  },
  todayGlanceBadge: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    alignSelf: "flex-start",
  },
  todayMetricsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  todayMetricSlot: {
    minWidth: 0,
  },
  todayMetricCard: {
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 6,
  },
  todayMetricHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  todayMetricAccent: {
    width: 8,
    height: 8,
    borderRadius: 999,
  },
  todayMetricLabel: {
    opacity: 0.72,
  },
  todayMetricDetail: {
    opacity: 0.68,
  },
  todayFocusGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  todayFocusSlot: {
    minWidth: 0,
  },
  todayFocusPanel: {
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 12,
  },
  todayFocusHeader: {
    gap: 3,
  },
  todayFocusEyebrow: {
    opacity: 0.7,
    textTransform: "uppercase",
    letterSpacing: 0.6,
  },
  todayFocusSummary: {
    opacity: 0.72,
    lineHeight: 18,
  },
  todayFocusList: {
    gap: 10,
  },
  todayFocusItem: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
  },
  todayFocusItemAccent: {
    width: 8,
    height: 8,
    borderRadius: 999,
    marginTop: 6,
  },
  todayFocusItemCopy: {
    flex: 1,
    gap: 2,
  },
  todayFocusItemCaption: {
    opacity: 0.66,
  },
  todayFocusCompleteBtn: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  todayFocusEmpty: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  sectionShell: {
    marginTop: 8,
    borderRadius: 28,
  },
  sectionShellContent: {
    gap: 14,
  },
  sectionHeader: {
    gap: 4,
  },
  sectionDescription: {
    opacity: 0.74,
    lineHeight: 18,
  },
  sectionFeatureCard: {
    marginTop: 0,
  },
  tankCard: {
    marginTop: 8,
    borderRadius: 24,
  },
  sectionChipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 12,
  },
  sectionStack: {
    gap: 12,
  },
  sectionSubgroup: {
    gap: 10,
  },
  sectionLabel: {
    opacity: 0.72,
    letterSpacing: 0.4,
  },
  summaryRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 10,
  },
  aquariumCardContent: {
    gap: 4,
  },
  actionSelector: {
    marginTop: 12,
  },
  sectionTitle: {
    marginTop: 16,
  },
  issueCard: {
    marginTop: 8,
    borderRadius: 24,
  },
  sectionItemCard: {
    marginTop: 0,
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
  formStack: {
    gap: 10,
  },
  inputTopSpacing: {
    marginTop: 12,
  },
  photoPreview: {
    width: "100%",
    height: 160,
    borderRadius: 18,
  },
  entityPhoto: {
    width: "100%",
    height: 140,
    borderRadius: 18,
    marginBottom: 10,
  },
  livestockPhoto: {
    width: "100%",
    height: 150,
    borderRadius: 18,
    marginTop: 10,
  },
  chartWrap: {
    marginTop: 14,
    alignItems: "center",
  },
  chartEmpty: {
    marginTop: 12,
    opacity: 0.75,
  },
  metricSelector: {
    marginTop: 12,
  },
  chartUnitLabel: {
    marginTop: 8,
    opacity: 0.7,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 88,
  },
  fabTooltipWrap: {
    position: "absolute",
    right: 16,
    alignItems: "flex-end",
  },
  fabTooltip: {
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
});
