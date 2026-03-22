import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import {
    Button,
    Card,
    Chip,
    HelperText,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import type {
    Consumable,
    DosingLog,
    Issue,
    Livestock,
    Memo,
    TaskExecution,
    TaskTemplate,
    WaterParameters,
} from "@/types/aquapt";

type SupportedFormKind =
  | "livestock"
  | "task-execution"
  | "parameter-log"
  | "dosing"
  | "memo"
  | "issue"
  | "consumable";

type PropertyType = "string" | "number" | "datetime" | "enum";
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

type LivestockFormModel = Pick<
  Livestock,
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

type TaskExecutionFormModel = Pick<
  TaskExecution,
  "taskTemplateId" | "completedAt" | "note"
>;

type DosingFormModel = Pick<DosingLog, "product" | "amountMl" | "note">;
type MemoFormModel = Pick<Memo, "content" | "photoUri" | "createdAt">;
type IssueFormModel = Pick<Issue, "title">;
type ConsumableFormModel = Pick<
  Consumable,
  "name" | "unit" | "remaining" | "reorderAt" | "photoUri"
>;
type ConsumableUseFormModel = {
  amountUsed: number;
  note?: string;
};

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

const LIVESTOCK_FIELDS: DynamicField<keyof LivestockFormModel>[] = [
  { key: "name", label: "Name", propertyType: "string", required: true },
  {
    key: "species",
    label: "Species",
    propertyType: "string",
    required: true,
  },
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
    helperText: "ISO date/time preferred (e.g. 2026-03-22T08:15:00.000Z)",
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
    label: "Photo URI",
    propertyType: "string",
    section: "optional",
  },
];

const DOSING_FIELDS: DynamicField<keyof DosingFormModel>[] = [
  {
    key: "product",
    label: "Product",
    propertyType: "string",
    required: true,
  },
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

const MEMO_FIELDS: DynamicField<keyof MemoFormModel>[] = [
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
    helperText: "Optional. If blank, current time is used.",
  },
  {
    key: "photoUri",
    label: "Photo URI",
    propertyType: "string",
    section: "optional",
  },
];

const ISSUE_FIELDS: DynamicField<keyof IssueFormModel>[] = [
  {
    key: "title",
    label: "Title",
    propertyType: "string",
    required: true,
  },
];

const CONSUMABLE_FIELDS: DynamicField<keyof ConsumableFormModel>[] = [
  {
    key: "name",
    label: "Name",
    propertyType: "string",
    required: true,
  },
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
    label: "Photo URI",
    propertyType: "string",
    section: "optional",
  },
];

const CONSUMABLE_USE_FIELDS: DynamicField<keyof ConsumableUseFormModel>[] = [
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

function parseOptionalNumber(value: string | undefined) {
  if (!value?.trim()) {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseNumberOrNaN(value: string | undefined) {
  const parsed = Number(value ?? "");
  return Number.isFinite(parsed) ? parsed : NaN;
}

function fieldKeyboardType(
  field: DynamicField,
): "default" | "decimal-pad" | "numeric" {
  if (field.propertyType === "number") {
    return "decimal-pad";
  }

  return "default";
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
): DynamicField<keyof TaskExecutionFormModel>[] {
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
      helperText: "Optional. If blank, current time is used.",
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
  return WATER_PARAMETER_KEYS.some((key) => {
    const numberValue = parseOptionalNumber(values[key]);
    return numberValue !== undefined;
  });
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
      return WATER_PARAMETER_KEYS.reduce<Record<string, string>>((acc, key) => {
        acc[key] = "";
        return acc;
      }, {});
    case "dosing":
      return {
        product: options.product ?? "",
        amountMl: "",
        note: "",
      };
    case "memo":
      return {
        content: options.content ?? "",
        createdAt: nowIso,
        photoUri: "",
      };
    case "issue":
      return {
        title: options.title ?? "",
      };
    case "consumable":
      if (options.targetConsumable) {
        return {
          amountUsed: "",
          note: "",
        };
      }

      return {
        name: "",
        unit: "ml",
        remaining: "",
        reorderAt: "",
        photoUri: "",
      };
    default:
      return {};
  }
}

export default function EntityFormScreen() {
  const router = useRouter();
  const theme = useTheme();
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
  const formKind =
    kindParam === "livestock" ||
    kindParam === "task-execution" ||
    kindParam === "parameter-log" ||
    kindParam === "dosing" ||
    kindParam === "memo" ||
    kindParam === "issue" ||
    kindParam === "consumable"
      ? kindParam
      : undefined;

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

  const tasksForAquarium = useMemo<TaskTemplate[]>(() => {
    if (!selectedAquariumId) {
      return taskTemplates;
    }

    return taskTemplates.filter((task) =>
      task.aquariumIds.includes(selectedAquariumId),
    );
  }, [selectedAquariumId, taskTemplates]);

  const taskOptions = useMemo<FieldOption[]>(
    () =>
      tasksForAquarium.map((task) => ({ label: task.title, value: task.id })),
    [tasksForAquarium],
  );

  const fields = useMemo<DynamicField[]>(() => {
    if (!formKind) {
      return [];
    }

    if (formKind === "livestock") {
      return LIVESTOCK_FIELDS;
    }

    if (formKind === "task-execution") {
      return buildTaskExecutionFields(taskOptions);
    }

    if (formKind === "parameter-log") {
      return buildParameterFields();
    }

    if (formKind === "dosing") {
      return DOSING_FIELDS;
    }

    if (formKind === "memo") {
      return MEMO_FIELDS;
    }

    if (formKind === "issue") {
      return ISSUE_FIELDS;
    }

    if (formKind === "consumable" && targetConsumable) {
      return CONSUMABLE_USE_FIELDS;
    }

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

  const setFieldValue = (key: string, value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }));
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

      if (parentId) {
        addOffspring(parentId, { ...input, aquariumId });
      } else {
        addLivestock({ ...input, aquariumId });
      }

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
          if (value !== undefined) {
            acc[key] = value;
          }
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

  if (!formKind) {
    return (
      <DashboardScrollView>
        <Stack.Screen options={{ title: "Entity form" }} />
        <DashboardHero
          title="Invalid action"
          subtitle="This action link is missing a valid form type."
          tone="error"
        />
      </DashboardScrollView>
    );
  }

  return (
    <DashboardScrollView>
      <Stack.Screen options={{ title: formTitle }} />
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
        description="Inputs are generated from each entity property's type."
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

            <View style={styles.actionsRow}>
              <Button mode="outlined" onPress={() => router.back()}>
                Cancel
              </Button>
              <Button mode="contained" onPress={submit}>
                Save
              </Button>
            </View>
          </Card.Content>
        </Card>
      </DashboardSection>
    </DashboardScrollView>
  );
}

const styles = StyleSheet.create({
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
  actionsRow: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 10,
    marginTop: 8,
  },
});
