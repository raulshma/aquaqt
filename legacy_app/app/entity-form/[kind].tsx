import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
  Button,
  Card,
  Chip,
  HelperText,
  IconButton,
  Text,
  TextInput,
  useTheme
} from "react-native-paper";
import { DatePickerModal, TimePickerModal } from "react-native-paper-dates";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  DashboardHero,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import {
  type PhotoSource,
  PhotoSourceDialog,
} from "@/components/ui/photo-source-dialog";
import { useAquapt } from "@/context/aquapt-context";
import type {
  Consumable,
  DosingLog,
  Issue,
  Livestock,
  Memo,
  TaskExecution,
  WaterParameters,
} from "@/types/aquapt";

const SUPPORTED_FORM_KINDS = [
  "livestock",
  "task-execution",
  "parameter-log",
  "dosing",
  "memo",
  "issue",
  "consumable",
] as const;

type SupportedFormKind = (typeof SUPPORTED_FORM_KINDS)[number];
type AquaptApi = ReturnType<typeof useAquapt>;
type AddLivestockInput = Parameters<AquaptApi["addLivestock"]>[0];
type AddConsumableInput = Parameters<AquaptApi["addConsumable"]>[0];

type PropertyType = "string" | "number" | "datetime" | "enum" | "photo";
type FieldSection = "core" | "optional";

interface FieldOption {
  label: string;
  value: string;
}

interface DynamicField<TFieldKey extends string = string> {
  key: TFieldKey;
  label: string;
  propertyType: PropertyType;
  required?: boolean;
  multiline?: boolean;
  section?: FieldSection;
  placeholder?: string;
  helperText?: string;
  options?: FieldOption[];
}

type LivestockFieldShape = Pick<
  AddLivestockInput,
  | "kind"
  | "name"
  | "species"
  | "quantity"
  | "acquiredAt"
  | "purchasePrice"
  | "dietaryNotes"
  | "status"
  | "photoUri"
>;

type TaskExecutionFieldShape = Pick<
  TaskExecution,
  "taskTemplateId" | "completedAt" | "note"
>;
type DosingFieldShape = Pick<DosingLog, "product" | "amountMl" | "note">;
type MemoFieldShape = Pick<Memo, "content" | "photoUri" | "createdAt">;
type IssueFieldShape = Pick<Issue, "title">;
type ConsumableFieldShape = Pick<
  AddConsumableInput,
  "name" | "unit" | "remaining" | "reorderAt" | "photoUri"
>;
type ConsumableUseFieldShape = { amountUsed: string; note?: string };

const LIVESTOCK_KIND_OPTIONS: FieldOption[] = [
  { label: "Fish", value: "fish" },
  { label: "Shrimp", value: "shrimp" },
  { label: "Snail", value: "snail" },
  { label: "Coral", value: "coral" },
  { label: "Plant", value: "plant" },
  { label: "Other", value: "other" },
];

const LIVESTOCK_STATUS_OPTIONS: FieldOption[] = [
  { label: "Active", value: "active" },
  { label: "Ill", value: "ill" },
  { label: "Deceased", value: "deceased" },
];

const CONSUMABLE_UNIT_OPTIONS: FieldOption[] = [
  { label: "g", value: "g" },
  { label: "ml", value: "ml" },
  { label: "pcs", value: "pcs" },
];

const WATER_PARAMETER_LABELS: Record<keyof WaterParameters, string> = {
  ammonia: "Ammonia (NH3)",
  nitrite: "Nitrite (NO2)",
  nitrate: "Nitrate (NO3)",
  ph: "pH",
  temperatureC: "Temperature (°C)",
  gh: "GH",
  kh: "KH",
  salinity: "Salinity",
  calcium: "Calcium",
  alkalinity: "Alkalinity",
};

const WATER_PARAMETER_KEYS: (keyof WaterParameters)[] = [
  "ammonia",
  "nitrite",
  "nitrate",
  "ph",
  "temperatureC",
  "gh",
  "kh",
  "salinity",
  "calcium",
  "alkalinity",
];

const LIVESTOCK_FIELDS: DynamicField<keyof LivestockFieldShape>[] = [
  { key: "name", label: "Name", propertyType: "string", required: true },
  { key: "species", label: "Species", propertyType: "string", required: true },
  {
    key: "quantity",
    label: "Quantity",
    propertyType: "number",
    required: true,
  },
  {
    key: "kind",
    label: "Kind",
    propertyType: "enum",
    required: true,
    options: LIVESTOCK_KIND_OPTIONS,
  },
  {
    key: "status",
    label: "Status",
    propertyType: "enum",
    required: true,
    options: LIVESTOCK_STATUS_OPTIONS,
  },
  {
    key: "acquiredAt",
    label: "Acquired at",
    propertyType: "datetime",
    required: true,
    helperText: "Choose date and time",
  },
  {
    key: "purchasePrice",
    label: "Purchase price",
    propertyType: "number",
    section: "optional",
  },
  {
    key: "dietaryNotes",
    label: "Dietary notes",
    propertyType: "string",
    multiline: true,
    section: "optional",
  },
  {
    key: "photoUri",
    label: "Photo",
    propertyType: "photo",
    section: "optional",
  },
];

const DOSING_FIELDS: DynamicField<keyof DosingFieldShape>[] = [
  { key: "product", label: "Product", propertyType: "string", required: true },
  {
    key: "amountMl",
    label: "Amount (ml)",
    propertyType: "number",
    required: true,
  },
  {
    key: "note",
    label: "Note",
    propertyType: "string",
    multiline: true,
    section: "optional",
  },
];

const MEMO_FIELDS: DynamicField<keyof MemoFieldShape>[] = [
  {
    key: "content",
    label: "Content",
    propertyType: "string",
    multiline: true,
    required: true,
  },
  {
    key: "createdAt",
    label: "Created at",
    propertyType: "datetime",
    section: "optional",
    helperText: "Optional; defaults to now",
  },
  {
    key: "photoUri",
    label: "Photo",
    propertyType: "photo",
    section: "optional",
  },
];

const ISSUE_FIELDS: DynamicField<keyof IssueFieldShape>[] = [
  { key: "title", label: "Title", propertyType: "string", required: true },
];

const CONSUMABLE_FIELDS: DynamicField<keyof ConsumableFieldShape>[] = [
  { key: "name", label: "Name", propertyType: "string", required: true },
  {
    key: "unit",
    label: "Unit",
    propertyType: "enum",
    required: true,
    options: CONSUMABLE_UNIT_OPTIONS,
  },
  {
    key: "remaining",
    label: "Remaining",
    propertyType: "number",
    required: true,
  },
  {
    key: "reorderAt",
    label: "Reorder threshold",
    propertyType: "number",
    section: "optional",
  },
  {
    key: "photoUri",
    label: "Photo",
    propertyType: "photo",
    section: "optional",
  },
];

const CONSUMABLE_USE_FIELDS: DynamicField<keyof ConsumableUseFieldShape>[] = [
  {
    key: "amountUsed",
    label: "Amount used",
    propertyType: "number",
    required: true,
  },
  {
    key: "note",
    label: "Note",
    propertyType: "string",
    multiline: true,
    section: "optional",
  },
];

function getSingleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function isSupportedFormKind(
  value: string | undefined,
): value is SupportedFormKind {
  return !!value && (SUPPORTED_FORM_KINDS as readonly string[]).includes(value);
}

function parseOptionalNumber(value: string | undefined) {
  if (!value?.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseNumberOrNaN(value: string | undefined) {
  const parsed = Number(value ?? "");
  return Number.isFinite(parsed) ? parsed : NaN;
}

function parseDateValue(value: string | undefined) {
  if (!value?.trim()) return new Date();
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
}

function formatDateTimeLabel(value: string | undefined) {
  if (!value?.trim()) return "Select date & time";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? "Select date & time"
    : parsed.toLocaleString();
}

function fieldKeyboardType(
  field: DynamicField,
): "default" | "decimal-pad" | "numeric" {
  return field.propertyType === "number" ? "decimal-pad" : "default";
}

function buildParameterFields(): DynamicField<keyof WaterParameters>[] {
  return WATER_PARAMETER_KEYS.map((key) => ({
    key,
    label: WATER_PARAMETER_LABELS[key],
    propertyType: "number",
    section: "optional",
  }));
}

function buildTaskExecutionFields(
  taskOptions: FieldOption[],
): DynamicField<keyof TaskExecutionFieldShape>[] {
  return [
    {
      key: "taskTemplateId",
      label: "Task",
      propertyType: "enum",
      required: true,
      options: taskOptions,
    },
    {
      key: "completedAt",
      label: "Completed at",
      propertyType: "datetime",
      section: "optional",
      helperText: "Optional; defaults to now",
    },
    {
      key: "note",
      label: "Note",
      propertyType: "string",
      multiline: true,
      section: "optional",
    },
  ];
}

function hasAnyWaterParameter(values: Record<string, string>) {
  return WATER_PARAMETER_KEYS.some(
    (key) => parseOptionalNumber(values[key]) !== undefined,
  );
}

function buildInitialValues(
  formKind: SupportedFormKind,
  options: {
    taskTemplateId?: string;
    product?: string;
    content?: string;
    title?: string;
    targetConsumable?: Consumable;
  },
): Record<string, string> {
  const nowIso = new Date().toISOString();

  switch (formKind) {
    case "livestock":
      return {
        name: "",
        species: "",
        quantity: "1",
        kind: "fish",
        status: "active",
        acquiredAt: nowIso,
        purchasePrice: "",
        dietaryNotes: "",
        photoUri: "",
      };
    case "task-execution":
      return {
        taskTemplateId: options.taskTemplateId ?? "",
        completedAt: nowIso,
        note: "",
      };
    case "parameter-log":
      return WATER_PARAMETER_KEYS.reduce<Record<string, string>>(
        (acc, key) => ({ ...acc, [key]: "" }),
        {},
      );
    case "dosing":
      return { product: options.product ?? "", amountMl: "", note: "" };
    case "memo":
      return {
        content: options.content ?? "",
        createdAt: nowIso,
        photoUri: "",
      };
    case "issue":
      return { title: options.title ?? "" };
    case "consumable":
      return options.targetConsumable
        ? { amountUsed: "", note: "" }
        : { name: "", unit: "ml", remaining: "", reorderAt: "", photoUri: "" };
  }
}

export default function EntityFormScreen() {
  const router = useRouter();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{
    kind?: string;
    aquariumId?: string;
    id?: string;
    parentId?: string;
    product?: string;
    content?: string;
    title?: string;
    taskTemplateId?: string;
  }>();

  const {
    aquariums,
    taskTemplates,
    consumables,
    addLivestock,
    addOffspring,
    completeTask,
    logParameters,
    logDosing,
    addMemo,
    addIssue,
    consumeConsumable,
    addConsumable,
  } = useAquapt();

  const kindParam = getSingleParam(params.kind);
  const formKind = isSupportedFormKind(kindParam) ? kindParam : undefined;
  const parentId = getSingleParam(params.parentId);
  const consumableId = getSingleParam(params.id);
  const targetConsumable = consumableId
    ? consumables.find((entry) => entry.id === consumableId)
    : undefined;

  const [selectedAquariumId, setSelectedAquariumId] = useState(
    getSingleParam(params.aquariumId) ?? aquariums[0]?.id ?? "",
  );
  const [errorText, setErrorText] = useState("");
  const [values, setValues] = useState<Record<string, string>>({});
  const [activeDateTimeField, setActiveDateTimeField] = useState<string | null>(
    null,
  );
  const [isDatePickerOpen, setDatePickerOpen] = useState(false);
  const [isTimePickerOpen, setTimePickerOpen] = useState(false);
  const [dateTimeDraft, setDateTimeDraft] = useState(new Date());
  const [photoDialogField, setPhotoDialogField] = useState<string | null>(null);
  const [isPhotoLoading, setPhotoLoading] = useState(false);

  useEffect(() => {
    const selectedExists = aquariums.some((aq) => aq.id === selectedAquariumId);
    if (!selectedExists && aquariums.length > 0 && !targetConsumable) {
      setSelectedAquariumId(aquariums[0].id);
    }
    if (
      targetConsumable &&
      selectedAquariumId !== targetConsumable.aquariumId
    ) {
      setSelectedAquariumId(targetConsumable.aquariumId);
    }
  }, [aquariums, selectedAquariumId, targetConsumable]);

  useEffect(() => {
    if (!formKind) {
      setValues({});
      return;
    }

    setValues(
      buildInitialValues(formKind, {
        taskTemplateId: getSingleParam(params.taskTemplateId),
        product: getSingleParam(params.product),
        content: getSingleParam(params.content),
        title: getSingleParam(params.title),
        targetConsumable,
      }),
    );
  }, [
    formKind,
    params.taskTemplateId,
    params.product,
    params.content,
    params.title,
    targetConsumable,
  ]);

  const tasksForAquarium = useMemo(() => {
    if (!selectedAquariumId) return taskTemplates;
    return taskTemplates.filter((task) =>
      task.aquariumIds.includes(selectedAquariumId),
    );
  }, [selectedAquariumId, taskTemplates]);

  const taskOptions = useMemo(
    () =>
      tasksForAquarium.map((task) => ({ label: task.title, value: task.id })),
    [tasksForAquarium],
  );

  const fields = useMemo<DynamicField[]>(() => {
    if (!formKind) return [];
    if (formKind === "livestock") return LIVESTOCK_FIELDS;
    if (formKind === "task-execution")
      return buildTaskExecutionFields(taskOptions);
    if (formKind === "parameter-log") return buildParameterFields();
    if (formKind === "dosing") return DOSING_FIELDS;
    if (formKind === "memo") return MEMO_FIELDS;
    if (formKind === "issue") return ISSUE_FIELDS;
    if (formKind === "consumable" && targetConsumable)
      return CONSUMABLE_USE_FIELDS;
    return CONSUMABLE_FIELDS;
  }, [formKind, targetConsumable, taskOptions]);

  const formTitle =
    formKind === "livestock"
      ? parentId
        ? "Add offspring"
        : "Add livestock"
      : formKind === "task-execution"
        ? "Log task execution"
        : formKind === "parameter-log"
          ? "Log water parameters"
          : formKind === "dosing"
            ? "Log dosing"
            : formKind === "memo"
              ? "Add memo"
              : formKind === "issue"
                ? "Report issue"
                : formKind === "consumable"
                  ? targetConsumable
                    ? "Use consumable"
                    : "Track consumable"
                  : "Entity form";

  const aquariumName =
    aquariums.find((entry) => entry.id === selectedAquariumId)?.name ?? "";
  const coreFields = fields.filter((field) => field.section !== "optional");
  const optionalFields = fields.filter((field) => field.section === "optional");

  const setFieldValue = (key: string, value: string) =>
    setValues((prev) => ({ ...prev, [key]: value }));

  const openDateTimePicker = (fieldKey: string) => {
    setActiveDateTimeField(fieldKey);
    setDateTimeDraft(parseDateValue(values[fieldKey]));
    setDatePickerOpen(true);
  };

  const closeDateTimePicker = () => {
    setDatePickerOpen(false);
    setTimePickerOpen(false);
    setActiveDateTimeField(null);
  };

  const pickPhotoFromSource = async (source: PhotoSource) => {
    if (!photoDialogField) return;

    setPhotoDialogField(null);
    setPhotoLoading(true);

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
        setFieldValue(photoDialogField, result.assets[0].uri);
      }
    } finally {
      setPhotoLoading(false);
    }
  };

  const openPhotoDialog = (fieldKey: string) => {
    setPhotoDialogField(fieldKey);
  };

  const closePhotoDialog = () => {
    setPhotoDialogField(null);
  };

  const removePhoto = () => {
    if (photoDialogField) {
      setFieldValue(photoDialogField, "");
    }
    setPhotoDialogField(null);
  };

  const renderField = (field: DynamicField) => {
    const currentValue = values[field.key] ?? "";

    if (field.propertyType === "enum") {
      return (
        <View key={field.key} style={styles.fieldBlock}>
          <Text variant="labelLarge">{field.label}</Text>
          <View style={styles.chipRow}>
            {(field.options ?? []).map((option) => (
              <Chip
                key={option.value}
                compact
                selected={currentValue === option.value}
                onPress={() => setFieldValue(field.key, option.value)}
              >
                {option.label}
              </Chip>
            ))}
          </View>
          {field.helperText ? (
            <Text variant="bodySmall" style={styles.helperMuted}>
              {field.helperText}
            </Text>
          ) : null}
        </View>
      );
    }

    if (field.propertyType === "photo") {
      const hasPhoto = !!currentValue;
      return (
        <View key={field.key} style={styles.fieldBlock}>
          <Text variant="labelLarge">{field.label}</Text>
          {hasPhoto ? (
            <View style={styles.photoContainer}>
              <Image
                source={{ uri: currentValue }}
                style={styles.photoPreview}
                contentFit="cover"
              />
              <View style={styles.photoActions}>
                <IconButton
                  icon="camera"
                  mode="contained-tonal"
                  size={20}
                  onPress={() => openPhotoDialog(field.key)}
                />
                <IconButton
                  icon="delete-outline"
                  mode="contained-tonal"
                  size={20}
                  iconColor={theme.colors.error}
                  onPress={() => setFieldValue(field.key, "")}
                />
              </View>
            </View>
          ) : (
            <Button
              mode="outlined"
              icon="camera"
              onPress={() => openPhotoDialog(field.key)}
              style={styles.photoButton}
            >
              Add photo
            </Button>
          )}
          {field.helperText ? (
            <Text variant="bodySmall" style={styles.helperMuted}>
              {field.helperText}
            </Text>
          ) : null}
        </View>
      );
    }

    if (field.propertyType === "datetime") {
      return (
        <View key={field.key} style={styles.fieldBlock}>
          <Text variant="labelLarge">
            {field.label}
            {field.required ? " *" : ""}
          </Text>
          <Button
            mode="outlined"
            icon="calendar-clock"
            onPress={() => openDateTimePicker(field.key)}
            contentStyle={styles.dateButtonContent}
          >
            {formatDateTimeLabel(currentValue)}
          </Button>
          {field.helperText ? (
            <Text variant="bodySmall" style={styles.helperMuted}>
              {field.helperText}
            </Text>
          ) : null}
        </View>
      );
    }

    return (
      <View key={field.key} style={styles.fieldBlock}>
        <TextInput
          label={`${field.label}${field.required ? " *" : ""}`}
          value={currentValue}
          onChangeText={(text) => setFieldValue(field.key, text)}
          mode="outlined"
          multiline={field.multiline}
          placeholder={field.placeholder}
          keyboardType={fieldKeyboardType(field)}
        />
        {field.helperText ? (
          <Text variant="bodySmall" style={styles.helperMuted}>
            {field.helperText}
          </Text>
        ) : null}
      </View>
    );
  };

  const submit = () => {
    setErrorText("");
    if (!formKind) {
      setErrorText("Unknown form type.");
      return;
    }

    const aquariumId = targetConsumable?.aquariumId ?? selectedAquariumId;
    if (!aquariumId) {
      setErrorText("Please select an aquarium first.");
      return;
    }

    if (formKind === "livestock") {
      const quantity = parseNumberOrNaN(values.quantity);
      const purchasePrice = parseOptionalNumber(values.purchasePrice);
      if (!values.name?.trim() || !values.species?.trim() || quantity <= 0) {
        setErrorText(
          "Name, species, and quantity greater than 0 are required.",
        );
        return;
      }

      const input = {
        kind: (values.kind || "fish") as Livestock["kind"],
        name: values.name.trim(),
        species: values.species.trim(),
        quantity,
        acquiredAt: values.acquiredAt?.trim() || new Date().toISOString(),
        status: (values.status || "active") as NonNullable<Livestock["status"]>,
        purchasePrice,
        dietaryNotes: values.dietaryNotes?.trim() || undefined,
        photoUri: values.photoUri?.trim() || undefined,
      };

      if (parentId) addOffspring(parentId, { ...input, aquariumId });
      else addLivestock({ ...input, aquariumId });
      router.back();
      return;
    }

    if (formKind === "task-execution") {
      const taskTemplateId = values.taskTemplateId?.trim();
      if (!taskTemplateId) {
        setErrorText("Task selection is required.");
        return;
      }
      completeTask(
        taskTemplateId,
        aquariumId,
        values.note?.trim() || undefined,
        values.completedAt?.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "parameter-log") {
      if (!hasAnyWaterParameter(values)) {
        setErrorText("Add at least one water parameter value.");
        return;
      }
      const parameters = WATER_PARAMETER_KEYS.reduce<WaterParameters>(
        (acc, key) => {
          const value = parseOptionalNumber(values[key]);
          if (value !== undefined) acc[key] = value;
          return acc;
        },
        {},
      );
      logParameters(aquariumId, parameters);
      router.back();
      return;
    }

    if (formKind === "dosing") {
      const amountMl = parseNumberOrNaN(values.amountMl);
      if (!values.product?.trim() || amountMl <= 0) {
        setErrorText("Product and amount greater than 0 are required.");
        return;
      }
      logDosing(
        aquariumId,
        values.product.trim(),
        amountMl,
        values.note?.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "memo") {
      if (!values.content?.trim()) {
        setErrorText("Memo content is required.");
        return;
      }
      addMemo(
        aquariumId,
        values.content.trim(),
        values.photoUri?.trim() || undefined,
        values.createdAt?.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "issue") {
      if (!values.title?.trim()) {
        setErrorText("Issue title is required.");
        return;
      }
      addIssue(aquariumId, values.title.trim());
      router.back();
      return;
    }

    if (targetConsumable) {
      const amountUsed = parseNumberOrNaN(values.amountUsed);
      if (amountUsed <= 0) {
        setErrorText("Amount used must be greater than 0.");
        return;
      }
      consumeConsumable(
        targetConsumable.id,
        amountUsed,
        values.note?.trim() || undefined,
      );
      router.back();
      return;
    }

    const remaining = parseNumberOrNaN(values.remaining);
    const reorderAt = parseOptionalNumber(values.reorderAt);
    if (!values.name?.trim() || remaining < 0 || Number.isNaN(remaining)) {
      setErrorText("Name and remaining amount (0 or more) are required.");
      return;
    }

    addConsumable({
      aquariumId,
      name: values.name.trim(),
      unit: (values.unit || "ml") as Consumable["unit"],
      remaining,
      reorderAt,
      photoUri: values.photoUri?.trim() || undefined,
    });
    router.back();
  };

  const renderBody = () => (
    <>
      <DashboardHero
        title={formTitle}
        subtitle={
          aquariumName
            ? `Aquarium: ${aquariumName}`
            : "Select values and save your entry."
        }
        tone="primary"
      />

      <DashboardSection
        title="Form"
        description="Inputs are generated from each entity property type, including native date-time pickers."
      >
        <Card
          mode="contained"
          style={[
            styles.card,
            { backgroundColor: theme.colors.surfaceVariant },
          ]}
        >
          <Card.Content style={styles.cardContent}>
            {!targetConsumable ? (
              <View style={styles.fieldBlock}>
                <Text variant="labelLarge">Aquarium</Text>
                <View style={styles.chipRow}>
                  {aquariums.map((aq) => (
                    <Chip
                      key={aq.id}
                      compact
                      selected={selectedAquariumId === aq.id}
                      onPress={() => setSelectedAquariumId(aq.id)}
                    >
                      {aq.name}
                    </Chip>
                  ))}
                </View>
              </View>
            ) : (
              <View style={styles.fieldBlock}>
                <Text variant="labelLarge">Consumable</Text>
                <Text variant="bodyMedium">{targetConsumable.name}</Text>
                <Text variant="bodySmall" style={styles.helperMuted}>
                  Remaining: {targetConsumable.remaining}
                  {targetConsumable.unit}
                </Text>
              </View>
            )}

            {coreFields.length > 0 ? (
              <>
                <Text variant="titleSmall" style={styles.sectionLabel}>
                  Required fields
                </Text>
                {coreFields.map((field) => renderField(field))}
              </>
            ) : null}

            {optionalFields.length > 0 ? (
              <>
                <Text variant="titleSmall" style={styles.sectionLabel}>
                  Optional fields
                </Text>
                {optionalFields.map((field) => renderField(field))}
              </>
            ) : null}

            {formKind === "task-execution" && taskOptions.length === 0 ? (
              <Text variant="bodySmall" style={styles.helperMuted}>
                No tasks found for the selected aquarium.
              </Text>
            ) : null}

            <HelperText type="error" visible={!!errorText}>
              {errorText}
            </HelperText>
          </Card.Content>
        </Card>
      </DashboardSection>
    </>
  );

  if (!formKind) {
    return (
      <View style={styles.screen}>
        <Stack.Screen options={{ headerShown: false }} />
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={[
            styles.scrollContent,
            { paddingTop: 16 + insets.top, paddingBottom: 24 + insets.bottom },
          ]}
          showsVerticalScrollIndicator={false}
        >
          <DashboardHero
            title="Invalid action"
            subtitle="This action link is missing a valid form type."
            tone="error"
          />
        </ScrollView>
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.scrollContent,
          { paddingTop: 16 + insets.top, paddingBottom: 144 + insets.bottom },
        ]}
        showsVerticalScrollIndicator={false}
      >
        {renderBody()}
      </ScrollView>

      <DatePickerModal
        locale="en"
        mode="single"
        visible={isDatePickerOpen}
        date={dateTimeDraft}
        onDismiss={closeDateTimePicker}
        onConfirm={({ date }: { date: Date | undefined }) => {
          if (date) {
            setDateTimeDraft(date);
            setDatePickerOpen(false);
            setTimePickerOpen(true);
            return;
          }
          closeDateTimePicker();
        }}
      />

      <TimePickerModal
        locale="en"
        visible={isTimePickerOpen}
        onDismiss={closeDateTimePicker}
        onConfirm={({ hours, minutes }: { hours: number; minutes: number }) => {
          const nextDate = new Date(dateTimeDraft);
          nextDate.setHours(hours);
          nextDate.setMinutes(minutes);
          nextDate.setSeconds(0);
          nextDate.setMilliseconds(0);
          if (activeDateTimeField) {
            setFieldValue(activeDateTimeField, nextDate.toISOString());
          }
          closeDateTimePicker();
        }}
        hours={dateTimeDraft.getHours()}
        minutes={dateTimeDraft.getMinutes()}
      />

      <PhotoSourceDialog
        visible={photoDialogField !== null}
        title={`${fields.find((f) => f.key === photoDialogField)?.label ?? "Photo"}`}
        hasCurrentPhoto={!!(photoDialogField && values[photoDialogField])}
        loading={isPhotoLoading}
        onDismiss={closePhotoDialog}
        onPickSource={pickPhotoFromSource}
        onRemovePhoto={removePhoto}
      />

      <View
        style={[
          styles.footer,
          {
            paddingBottom: Math.max(insets.bottom, 16),
            backgroundColor: theme.colors.surface,
            borderTopColor: theme.colors.outlineVariant,
          },
        ]}
      >
        <View style={styles.footerButtons}>
          <Button
            mode="outlined"
            onPress={() => router.back()}
            style={styles.footerButton}
          >
            Cancel
          </Button>
          <Button mode="contained" onPress={submit} style={styles.footerButton}>
            Save
          </Button>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  scrollView: { flex: 1 },
  scrollContent: {
    paddingHorizontal: 16,
    gap: 12,
  },
  card: {
    borderRadius: 24,
  },
  cardContent: {
    gap: 10,
  },
  fieldBlock: {
    gap: 8,
  },
  helperMuted: {
    opacity: 0.7,
  },
  sectionLabel: {
    marginTop: 4,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  dateButtonContent: {
    justifyContent: "flex-start",
  },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 16,
    paddingTop: 12,
    elevation: 8,
  },
  footerButtons: {
    flexDirection: "row",
    gap: 10,
  },
  footerButton: {
    flex: 1,
  },
  photoContainer: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  photoPreview: {
    width: 80,
    height: 80,
    borderRadius: 8,
  },
  photoActions: {
    flexDirection: "row",
    gap: 8,
  },
  photoButton: {
    alignSelf: "flex-start",
  },
});
