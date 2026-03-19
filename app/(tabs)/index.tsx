import { useForm } from "@tanstack/react-form";
import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useEffect, useMemo, useState } from "react";
import {
    Alert,
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
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { isTaskDue } from "@/services/scheduling";
import { evaluateParameterAlerts } from "@/services/water-alerts";
import { IssueStatus, Livestock, TaskFrequency } from "@/types/aquapt";

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

const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);
const parseIsoDate = (value: string) => {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
};

export default function HomeScreen() {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const theme = useTheme();
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
  const [newAssetModel, setNewAssetModel] = useState("");
  const [newAssetCategory, setNewAssetCategory] = useState<
    "filter" | "heater" | "light" | "co2" | "other"
  >("other");
  const [newAssetPurchasedAt, setNewAssetPurchasedAt] = useState(
    toIsoDate(new Date()),
  );
  const [newAssetPrice, setNewAssetPrice] = useState("");
  const [selectedAssetTaskTemplateIds, setSelectedAssetTaskTemplateIds] =
    useState<string[]>([]);
  const [newConsumableName, setNewConsumableName] = useState("");
  const [newConsumableRemaining, setNewConsumableRemaining] = useState("0");
  const [newConsumableUnit, setNewConsumableUnit] = useState<
    "pcs" | "ml" | "g"
  >("pcs");
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
  const [resolutionNoteDraft, setResolutionNoteDraft] = useState<
    Record<string, string>
  >({});
  const [isEditAquariumOpen, setEditAquariumOpen] = useState(false);
  const [isNewDatePickerOpen, setNewDatePickerOpen] = useState(false);
  const [isEditDatePickerOpen, setEditDatePickerOpen] = useState(false);
  const [selectedMetric, setSelectedMetric] =
    useState<AnalyticMetricKey>("nitrate");

  const addAquariumForm = useForm({
    defaultValues: {
      name: "",
      volume: "",
      dimensions: "",
      setupDate: toIsoDate(new Date()),
      setupDateValue: new Date(),
      investment: "",
      waterType: "freshwater" as "freshwater" | "marine" | "brackish",
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
      });

      addAquariumForm.setFieldValue("name", "");
      addAquariumForm.setFieldValue("volume", "");
      addAquariumForm.setFieldValue("dimensions", "");
      addAquariumForm.setFieldValue("setupDate", toIsoDate(new Date()));
      addAquariumForm.setFieldValue("setupDateValue", new Date());
      addAquariumForm.setFieldValue("investment", "");
      addAquariumForm.setFieldValue("waterType", "freshwater");
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
    void quickLogForm.handleSubmit();
  };

  const nitrateTrend = useMemo(() => {
    return aquariums.reduce<Record<string, string>>((acc, aquarium) => {
      const points = parameterLogs
        .filter(
          (entry) =>
            entry.aquariumId === aquarium.id &&
            entry.values.nitrate !== undefined,
        )
        .sort((a, b) => +new Date(a.createdAt) - +new Date(b.createdAt))
        .slice(-5)
        .map((entry) => entry.values.nitrate as number);

      if (points.length < 2) {
        acc[aquarium.id] = "Not enough data yet";
        return acc;
      }

      const first = points[0];
      const last = points[points.length - 1];
      const delta = Number((last - first).toFixed(2));
      const direction = delta > 0 ? "↑" : delta < 0 ? "↓" : "→";
      acc[aquarium.id] =
        `${direction} ${delta >= 0 ? "+" : ""}${delta} ppm (last ${points.length} logs)`;

      return acc;
    }, {});
  }, [aquariums, parameterLogs]);

  const pendingTasksToday = useMemo(() => {
    return taskTemplates.flatMap((task) =>
      task.aquariumIds
        .filter((aquariumId) =>
          isTaskDue(task, aquariumId, taskExecutions, new Date()),
        )
        .map((aquariumId) => ({
          taskId: task.id,
          taskTitle: task.title,
          aquariumId,
        })),
    );
  }, [taskTemplates, taskExecutions]);

  const availableTaskTemplatesForAsset = useMemo(() => {
    if (!selectedAquariumId) {
      return [];
    }

    return taskTemplates.filter((task) =>
      task.aquariumIds.includes(selectedAquariumId),
    );
  }, [selectedAquariumId, taskTemplates]);

  const parameterAlertsByAquarium = useMemo(() => {
    return aquariums.reduce<
      Record<string, ReturnType<typeof evaluateParameterAlerts>>
    >((acc, aquarium) => {
      const latest = parameterLogs
        .filter((entry) => entry.aquariumId === aquarium.id)
        .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))[0];

      if (!latest) {
        acc[aquarium.id] = [];
        return acc;
      }

      acc[aquarium.id] = evaluateParameterAlerts(aquarium, latest.values);
      return acc;
    }, {});
  }, [aquariums, parameterLogs]);

  const totalParameterAlerts = useMemo(
    () =>
      Object.values(parameterAlertsByAquarium).reduce(
        (sum, alerts) => sum + alerts.length,
        0,
      ),
    [parameterAlertsByAquarium],
  );

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

  const pickLivestockPhoto = async () => {
    const pickFromSource = async (source: "camera" | "library") => {
      setPickingPhoto(true);
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
          setNewLivestockPhotoUri(result.assets[0].uri);
        }
      } finally {
        setPickingPhoto(false);
      }
    };

    Alert.alert("Add livestock photo", "Choose photo source", [
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
          quickLogForm.setFieldValue("memo.photoUri", result.assets[0].uri);
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
    });
    setNewAssetModel("");
    setNewAssetCategory("other");
    setNewAssetPurchasedAt(toIsoDate(new Date()));
    setNewAssetPrice("");
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
    });
    setNewConsumableName("");
    setNewConsumableRemaining("0");
    setNewConsumableUnit("pcs");
    setAddConsumableOpen(false);
  };

  const totalOpenIssues = issues.filter(
    (issue) => issue.status !== "resolved",
  ).length;

  const chartAquariumId = selectedAquariumId || aquariums[0]?.id || "";
  const selectedMetricLabel =
    ANALYTIC_METRICS.find((metric) => metric.value === selectedMetric)?.label ??
    selectedMetric;
  const chartData = useMemo(() => {
    if (!chartAquariumId) {
      return [];
    }

    return parameterLogs
      .filter((entry) => {
        if (entry.aquariumId !== chartAquariumId) {
          return false;
        }

        const metricValue = entry.values[selectedMetric];
        return metricValue !== undefined;
      })
      .sort((a, b) => +new Date(a.createdAt) - +new Date(b.createdAt))
      .slice(-8)
      .map((entry) => ({
        value: entry.values[selectedMetric] as number,
        label: `${new Date(entry.createdAt).getDate()}`,
      }));
  }, [chartAquariumId, parameterLogs, selectedMetric]);

  const renderAquariumCard = (
    aquarium: (typeof aquariums)[number],
    cardIndex: number,
  ) => {
    const keepTones = [
      theme.colors.secondaryContainer,
      theme.colors.tertiaryContainer,
      theme.colors.surfaceVariant,
    ];
    const backgroundColor = keepTones[cardIndex % keepTones.length];

    return (
      <Card
        key={aquarium.id}
        style={[styles.keepCard, { backgroundColor }]}
        mode="contained"
      >
        <Card.Title
          title={aquarium.name}
          subtitle={`${aquarium.volumeLiters}L • ${aquarium.waterType}`}
        />
        <Card.Content>
          <Text variant="bodyMedium">
            Latest parameters: {latestParameterByAquarium[aquarium.id]}
          </Text>
          <Text variant="bodySmall" style={styles.issueMeta}>
            {aquarium.dimensions} • Setup {aquarium.setupDate}
            {aquarium.investmentCost !== undefined
              ? ` • $${aquarium.investmentCost}`
              : ""}
          </Text>
          <View style={styles.summaryRow}>
            <Chip compact icon="fish">
              {livestockCountByAquarium[aquarium.id] ?? 0} livestock
            </Chip>
            <Chip compact icon="alert-circle">
              {openIssuesByAquarium[aquarium.id] ?? 0} issues
            </Chip>
            <Chip compact icon="chart-line">
              NO3 trend: {nitrateTrend[aquarium.id]}
            </Chip>
            <Button
              mode="contained-tonal"
              onPress={() => openEditAquarium(aquarium.id)}
            >
              Edit specs
            </Button>
          </View>
        </Card.Content>
      </Card>
    );
  };

  const leftColumnAquariums = aquariums.filter((_, index) => index % 2 === 0);
  const rightColumnAquariums = aquariums.filter((_, index) => index % 2 === 1);

  return (
    <>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          { paddingTop: 16 + insets.top },
        ]}
      >
        <Text variant="headlineMedium">Aquapt Dashboard</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Monitor tank health, log key events, and catch issues early.
        </Text>

        <Card
          style={[
            styles.summaryCard,
            styles.keepCard,
            { backgroundColor: theme.colors.primaryContainer, marginBottom: 10 },
          ]}
          mode="elevated"
        >
          <Card.Content>
            <Text variant="titleMedium">Today at a glance</Text>
            <View style={styles.summaryRow}>
              <Chip compact icon="fish">
                {aquariums.length} Tanks
              </Chip>
              <Chip compact icon="alert">
                {totalOpenIssues} Active issues
              </Chip>
              <Chip compact icon="calendar-clock">
                {pendingTasksToday.length} Tasks due
              </Chip>
              <Chip compact icon="test-tube">
                {dosingLogs.length} Dosing logs
              </Chip>
              <Chip compact icon="shield-alert">
                {totalParameterAlerts} Safety alerts
              </Chip>
            </View>
          </Card.Content>
        </Card>

        <View style={styles.keepGrid}>
          <View style={styles.keepColumn}>
            {totalParameterAlerts > 0 ? (
              <Card
                style={[
                  styles.summaryCard,
                  styles.keepCard,
                  { backgroundColor: theme.colors.errorContainer },
                ]}
                mode="outlined"
              >
                <Card.Title title="Water safety alerts" />
                <Card.Content>
                  <View style={styles.summaryRow}>
                    {aquariums.flatMap((aquarium) =>
                      (parameterAlertsByAquarium[aquarium.id] ?? []).map(
                        (alert) => (
                          <Chip
                            key={`${aquarium.id}-${alert.key}-${alert.status}`}
                            icon={
                              alert.status === "high"
                                ? "arrow-up"
                                : "arrow-down"
                            }
                          >
                            {aquarium.name}: {alert.label} {alert.value}
                            {alert.unit ? ` ${alert.unit}` : ""}
                          </Chip>
                        ),
                      ),
                    )}
                  </View>
                </Card.Content>
              </Card>
            ) : null}

            {leftColumnAquariums.map((aquarium, index) =>
              renderAquariumCard(aquarium, index),
            )}
          </View>

          <View style={styles.keepColumn}>
            {pendingTasksToday.length > 0 ? (
              <Card
                style={[
                  styles.summaryCard,
                  styles.keepCard,
                  { backgroundColor: theme.colors.secondaryContainer },
                ]}
                mode="outlined"
              >
                <Card.Title title="Tasks due today" />
                <Card.Content>
                  <View style={styles.summaryRow}>
                    {pendingTasksToday.slice(0, 6).map((entry) => (
                      <Chip key={`${entry.taskId}-${entry.aquariumId}`} compact>
                        {entry.taskTitle}
                      </Chip>
                    ))}
                  </View>
                </Card.Content>
              </Card>
            ) : null}

            {rightColumnAquariums.map((aquarium, index) =>
              renderAquariumCard(aquarium, index + leftColumnAquariums.length),
            )}
          </View>
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
                { backgroundColor: theme.colors.tertiaryContainer, overflow: "hidden" },
              ]}
              mode="contained"
              onPress={() => setAddAquariumOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <AquariumBackground tint={theme.colors.onTertiaryContainer + "30"} />
                <Text variant="titleSmall" style={[styles.actionTitle, { color: theme.colors.onTertiaryContainer }]}>New aquarium</Text>
                <Text variant="bodySmall" style={[styles.actionDescription, { color: theme.colors.onTertiaryContainer }]}>
                  Add tank size, setup date, and water type.
                </Text>
              </View>
            </Card>

            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                { backgroundColor: theme.colors.secondaryContainer, overflow: "hidden" },
              ]}
              mode="contained"
              onPress={() => setAddAssetOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <AssetBackground tint={theme.colors.onSecondaryContainer + "30"} />
                <Text variant="titleSmall" style={[styles.actionTitle, { color: theme.colors.onSecondaryContainer }]}>New asset</Text>
                <Text variant="bodySmall" style={[styles.actionDescription, { color: theme.colors.onSecondaryContainer }]}>
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
                { backgroundColor: theme.colors.primaryContainer, overflow: "hidden" },
              ]}
              mode="contained"
              onPress={() => setAddLivestockOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <LivestockBackground tint={theme.colors.onPrimaryContainer + "30"} />
                <Text variant="titleSmall" style={[styles.actionTitle, { color: theme.colors.onPrimaryContainer }]}>New livestock</Text>
                <Text variant="bodySmall" style={[styles.actionDescription, { color: theme.colors.onPrimaryContainer }]}>
                  Log species, quantity, and optional photo.
                </Text>
              </View>
            </Card>

            <Card
              style={[
                styles.keepCard,
                styles.actionCard,
                { backgroundColor: theme.colors.surfaceVariant, overflow: "hidden" },
              ]}
              mode="contained"
              onPress={() => setAddConsumableOpen(true)}
            >
              <View style={styles.actionCardInner}>
                <ConsumableBackground tint={theme.colors.onSurfaceVariant + "30"} />
                <Text variant="titleSmall" style={[styles.actionTitle, { color: theme.colors.onSurfaceVariant }]}>New consumable</Text>
                <Text variant="bodySmall" style={[styles.actionDescription, { color: theme.colors.onSurfaceVariant }]}>
                  Track stock levels and reorder thresholds.
                </Text>
              </View>
            </Card>
          </View>
        </View>

        <Card style={styles.tankCard} mode="outlined">
          <Card.Title
            title="Parameter Analytics"
            subtitle={`${selectedMetricLabel} trend (recent logs)`}
          />
          <Card.Content>
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
                  yAxisTextStyle={styles.chartAxisLabel}
                  xAxisLabelTextStyle={styles.chartAxisLabel}
                  rulesColor="rgba(120,120,120,0.2)"
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

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Livestock tracking
        </Text>

        {livestock.map((item) => {
          const currentIndex = aquariums.findIndex(
            (aq) => aq.id === item.aquariumId,
          );
          const fallbackTarget =
            aquariums[(currentIndex + 1 + aquariums.length) % aquariums.length]
              ?.id;
          const feedingNote =
            feedingNoteDraft[item.id] ?? item.dietaryNotes ?? "";
          const livestockStatus =
            livestockStatusDraft[item.id] ?? item.status ?? "active";
          const livestockStatusNote = livestockStatusNoteDraft[item.id] ?? "";

          return (
            <Card key={item.id} style={styles.issueCard} mode="outlined">
              <Card.Content>
                <Text variant="titleSmall">
                  {item.name} ({item.quantity})
                </Text>
                <Text variant="bodySmall" style={styles.issueMeta}>
                  {item.species} •{" "}
                  {aquariums.find((aq) => aq.id === item.aquariumId)?.name}
                </Text>
                <View style={styles.summaryRow}>
                  <Chip compact>{item.kind}</Chip>
                  <Chip compact>{item.status ?? "active"}</Chip>
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
                />
                <ScrollableSegmentedButtons
                  value={livestockStatus}
                  onValueChange={(value) =>
                    setLivestockStatusDraft((prev) => ({
                      ...prev,
                      [item.id]: value as NonNullable<Livestock["status"]>,
                    }))
                  }
                  buttons={[
                    { label: "Active", value: "active" },
                    { label: "Ill", value: "ill" },
                    { label: "Deceased", value: "deceased" },
                  ]}
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
                />
                <View style={styles.summaryRow}>
                  <Button
                    mode="contained-tonal"
                    onPress={() =>
                      setLivestockFeedingNotes(item.id, feedingNote.trim())
                    }
                  >
                    Save feeding
                  </Button>
                  <Button
                    mode="contained-tonal"
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
                    mode="contained-tonal"
                    disabled={
                      !fallbackTarget || fallbackTarget === item.aquariumId
                    }
                    onPress={() =>
                      fallbackTarget
                        ? transferLivestock(
                            item.id,
                            fallbackTarget,
                            "Manual transfer",
                          )
                        : undefined
                    }
                  >
                    Transfer
                  </Button>
                  <Button
                    mode="contained-tonal"
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
                  value={feedingTaskTitleDraft[item.id] ?? ""}
                  onChangeText={(value) =>
                    setFeedingTaskTitleDraft((prev) => ({
                      ...prev,
                      [item.id]: value,
                    }))
                  }
                  style={styles.issueResolutionInput}
                  placeholder={`Feed ${item.name}`}
                />
                <ScrollableSegmentedButtons
                  value={feedingTaskFrequencyDraft[item.id] ?? "daily"}
                  onValueChange={(value) =>
                    setFeedingTaskFrequencyDraft((prev) => ({
                      ...prev,
                      [item.id]: value as TaskFrequency,
                    }))
                  }
                  buttons={[
                    { label: "Daily", value: "daily" },
                    { label: "Weekly", value: "weekly" },
                    { label: "Bi-weekly", value: "bi-weekly" },
                    { label: "Monthly", value: "monthly" },
                  ]}
                  style={styles.issueStatusSelector}
                />
                <Button
                  mode="contained-tonal"
                  style={styles.issueSaveButton}
                  onPress={() => {
                    const customTitle = feedingTaskTitleDraft[item.id]?.trim();
                    addLivestockFeedingTask({
                      livestockId: item.id,
                      title: customTitle || `Feed ${item.name}`,
                      frequency: feedingTaskFrequencyDraft[item.id] ?? "daily",
                      description:
                        feedingNote.trim() ||
                        item.dietaryNotes ||
                        `Targeted feeding regimen for ${item.name}`,
                    });

                    setFeedingTaskTitleDraft((prev) => ({
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
        })}

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Assets & consumables inventory
        </Text>

        {assets.map((asset) => (
          <Card key={asset.id} style={styles.issueCard} mode="contained">
            <Card.Content>
              <Text variant="titleSmall">{asset.brandModel}</Text>
              <Text variant="bodySmall" style={styles.issueMeta}>
                {asset.category} •{" "}
                {aquariums.find((aq) => aq.id === asset.aquariumId)?.name}
              </Text>
              <Text variant="bodySmall" style={styles.issueMeta}>
                Purchased: {asset.purchasedAt ?? "-"}
                {asset.price !== undefined ? ` • $${asset.price}` : ""}
              </Text>
              {asset.maintenanceTaskTemplateIds?.length ? (
                <Text variant="bodySmall" style={styles.issueMeta}>
                  Linked maintenance tasks:{" "}
                  {asset.maintenanceTaskTemplateIds.length}
                </Text>
              ) : null}
            </Card.Content>
          </Card>
        ))}

        {consumables.map((consumable) => {
          const low =
            consumable.reorderAt !== undefined &&
            consumable.remaining <= consumable.reorderAt;

          return (
            <Card key={consumable.id} style={styles.issueCard} mode="contained">
              <Card.Content>
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

                <ScrollableSegmentedButtons
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
                label="Investment cost"
                value={field.state.value}
                onChangeText={field.handleChange}
                keyboardType="numeric"
              />
            )}
          </editAquariumForm.Field>
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
                label="Investment cost (optional)"
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
            label="Purchase price (optional)"
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
              setNewAssetCategory(
                value as "filter" | "heater" | "light" | "co2" | "other",
              )
            }
            buttons={ASSET_CATEGORIES.map((category) => ({
              label: category,
              value: category,
            }))}
          />
          <TextInput
            mode="outlined"
            label="Purchased date (YYYY-MM-DD)"
            value={newAssetPurchasedAt}
            onChangeText={setNewAssetPurchasedAt}
          />
          <TextInput
            mode="outlined"
            label="Asset price (optional)"
            value={newAssetPrice}
            onChangeText={setNewAssetPrice}
            keyboardType="numeric"
          />
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
        </View>
      </BottomSheet>

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

      <FAB
        icon="plus"
        style={styles.fab}
        onPress={() => {
          quickLogForm.setFieldValue("selectedAquariumId", selectedAquariumId);
          setDialogOpen(true);
        }}
        label="Quick Log"
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
  keepCard: {
    borderRadius: 18,
    marginVertical: 0,
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
  subtitle: {
    opacity: 0.75,
    marginBottom: 4,
  },
  summaryCard: {
    marginVertical: 0,
    borderRadius: 24,
  },
  tankCard: {
    marginTop: 8,
    borderRadius: 24,
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
    borderRadius: 24,
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
  chartAxisLabel: {
    color: "rgba(120,120,120,0.9)",
    fontSize: 10,
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
});
