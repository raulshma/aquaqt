import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { useMemo, useState } from "react";
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
import type { Consumable, Livestock, TaskTemplate } from "@/types/aquapt";

type SupportedFormKind =
  | "livestock"
  | "task-execution"
  | "parameter-log"
  | "dosing"
  | "memo"
  | "issue"
  | "consumable";

const LIVESTOCK_KINDS: Livestock["kind"][] = [
  "fish",
  "shrimp",
  "snail",
  "coral",
  "plant",
  "other",
];

const CONSUMABLE_UNITS: Consumable["unit"][] = ["g", "ml", "pcs"];
const LIVESTOCK_STATUSES: NonNullable<Livestock["status"]>[] = [
  "active",
  "ill",
  "deceased",
];

function getSingleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function parseNumber(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function hasWaterParameterValues(values: {
  ammonia?: number;
  nitrite?: number;
  nitrate?: number;
  ph?: number;
  temperatureC?: number;
  gh?: number;
  kh?: number;
  salinity?: number;
  calcium?: number;
  alkalinity?: number;
}) {
  return Object.values(values).some((value) => value !== undefined);
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

  const formKind = getSingleParam(params.kind) as SupportedFormKind | undefined;
  const defaultAquariumId =
    getSingleParam(params.aquariumId) ?? aquariums[0]?.id ?? "";
  const parentId = getSingleParam(params.parentId);
  const consumableId = getSingleParam(params.id);
  const targetConsumable = consumableId
    ? consumables.find((entry) => entry.id === consumableId)
    : undefined;

  const [selectedAquariumId, setSelectedAquariumId] =
    useState(defaultAquariumId);
  const [errorText, setErrorText] = useState("");

  const [livestockName, setLivestockName] = useState("");
  const [livestockSpecies, setLivestockSpecies] = useState("");
  const [livestockQuantity, setLivestockQuantity] = useState("1");
  const [livestockKind, setLivestockKind] = useState<Livestock["kind"]>("fish");
  const [livestockStatus, setLivestockStatus] =
    useState<NonNullable<Livestock["status"]>>("active");
  const [livestockAcquiredAt, setLivestockAcquiredAt] = useState(
    new Date().toISOString(),
  );
  const [livestockPurchasePrice, setLivestockPurchasePrice] = useState("");
  const [livestockDietaryNotes, setLivestockDietaryNotes] = useState("");
  const [livestockPhotoUri, setLivestockPhotoUri] = useState("");

  const [selectedTaskTemplateId, setSelectedTaskTemplateId] = useState(
    getSingleParam(params.taskTemplateId) ?? "",
  );
  const [taskNote, setTaskNote] = useState("");
  const [taskCompletedAt, setTaskCompletedAt] = useState(
    new Date().toISOString(),
  );

  const [dosingProduct, setDosingProduct] = useState(
    getSingleParam(params.product) ?? "",
  );
  const [dosingAmount, setDosingAmount] = useState("");
  const [dosingNote, setDosingNote] = useState("");

  const [memoContent, setMemoContent] = useState(
    getSingleParam(params.content) ?? "",
  );
  const [memoPhotoUri, setMemoPhotoUri] = useState("");
  const [memoCreatedAt, setMemoCreatedAt] = useState(new Date().toISOString());

  const [issueTitle, setIssueTitle] = useState(
    getSingleParam(params.title) ?? "",
  );

  const [ammonia, setAmmonia] = useState("");
  const [nitrite, setNitrite] = useState("");
  const [nitrate, setNitrate] = useState("");
  const [ph, setPh] = useState("");
  const [temperatureC, setTemperatureC] = useState("");
  const [gh, setGh] = useState("");
  const [kh, setKh] = useState("");
  const [salinity, setSalinity] = useState("");
  const [calcium, setCalcium] = useState("");
  const [alkalinity, setAlkalinity] = useState("");

  const [consumableName, setConsumableName] = useState("");
  const [consumableUnit, setConsumableUnit] =
    useState<Consumable["unit"]>("ml");
  const [consumableRemaining, setConsumableRemaining] = useState("");
  const [consumableReorderAt, setConsumableReorderAt] = useState("");
  const [consumablePhotoUri, setConsumablePhotoUri] = useState("");
  const [consumableUseAmount, setConsumableUseAmount] = useState("");
  const [consumableUseNote, setConsumableUseNote] = useState("");

  const tasksForAquarium = useMemo<TaskTemplate[]>(() => {
    if (!selectedAquariumId) {
      return taskTemplates;
    }

    return taskTemplates.filter((task) =>
      task.aquariumIds.includes(selectedAquariumId),
    );
  }, [selectedAquariumId, taskTemplates]);

  const aquariumName =
    aquariums.find((entry) => entry.id === selectedAquariumId)?.name ?? "";

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

  const canSelectAquarium = aquariums.length > 1;

  const onSubmit = () => {
    setErrorText("");

    if (!formKind) {
      setErrorText("Unknown form type.");
      return;
    }

    if (!selectedAquariumId && formKind !== "consumable") {
      setErrorText("Select an aquarium first.");
      return;
    }

    if (formKind === "livestock") {
      const quantity = parseNumber(livestockQuantity);
      const purchasePrice = parseNumber(livestockPurchasePrice);
      if (
        !livestockName.trim() ||
        !livestockSpecies.trim() ||
        !quantity ||
        quantity <= 0
      ) {
        setErrorText("Provide name, species, and a quantity greater than 0.");
        return;
      }

      if (parentId) {
        addOffspring(parentId, {
          kind: livestockKind,
          name: livestockName.trim(),
          species: livestockSpecies.trim(),
          quantity,
          acquiredAt: livestockAcquiredAt.trim() || new Date().toISOString(),
          status: livestockStatus,
          aquariumId: selectedAquariumId,
          purchasePrice,
          dietaryNotes: livestockDietaryNotes.trim() || undefined,
          photoUri: livestockPhotoUri.trim() || undefined,
        });
      } else {
        addLivestock({
          aquariumId: selectedAquariumId,
          kind: livestockKind,
          name: livestockName.trim(),
          species: livestockSpecies.trim(),
          quantity,
          acquiredAt: livestockAcquiredAt.trim() || new Date().toISOString(),
          status: livestockStatus,
          purchasePrice,
          dietaryNotes: livestockDietaryNotes.trim() || undefined,
          photoUri: livestockPhotoUri.trim() || undefined,
        });
      }

      router.back();
      return;
    }

    if (formKind === "task-execution") {
      if (!selectedTaskTemplateId) {
        setErrorText("Pick a task template to execute.");
        return;
      }

      completeTask(
        selectedTaskTemplateId,
        selectedAquariumId,
        taskNote.trim() || undefined,
        taskCompletedAt.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "parameter-log") {
      const parameterValues = {
        ammonia: parseNumber(ammonia),
        nitrite: parseNumber(nitrite),
        nitrate: parseNumber(nitrate),
        ph: parseNumber(ph),
        temperatureC: parseNumber(temperatureC),
        gh: parseNumber(gh),
        kh: parseNumber(kh),
        salinity: parseNumber(salinity),
        calcium: parseNumber(calcium),
        alkalinity: parseNumber(alkalinity),
      };

      if (!hasWaterParameterValues(parameterValues)) {
        setErrorText("Add at least one water parameter value.");
        return;
      }

      logParameters(selectedAquariumId, parameterValues);
      router.back();
      return;
    }

    if (formKind === "dosing") {
      const amount = parseNumber(dosingAmount);
      if (!dosingProduct.trim() || !amount || amount <= 0) {
        setErrorText("Provide product and amount greater than 0.");
        return;
      }

      logDosing(
        selectedAquariumId,
        dosingProduct.trim(),
        amount,
        dosingNote.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "memo") {
      if (!memoContent.trim()) {
        setErrorText("Memo content cannot be empty.");
        return;
      }

      addMemo(
        selectedAquariumId,
        memoContent.trim(),
        memoPhotoUri.trim() || undefined,
        memoCreatedAt.trim() || undefined,
      );
      router.back();
      return;
    }

    if (formKind === "issue") {
      if (!issueTitle.trim()) {
        setErrorText("Issue title cannot be empty.");
        return;
      }

      addIssue(selectedAquariumId, issueTitle.trim());
      router.back();
      return;
    }

    if (formKind === "consumable") {
      if (targetConsumable) {
        const amountUsed = parseNumber(consumableUseAmount);
        if (!amountUsed || amountUsed <= 0) {
          setErrorText("Usage amount must be greater than 0.");
          return;
        }

        consumeConsumable(
          targetConsumable.id,
          amountUsed,
          consumableUseNote.trim() || undefined,
        );
        router.back();
        return;
      }

      if (!selectedAquariumId) {
        setErrorText("Select an aquarium first.");
        return;
      }

      const remaining = parseNumber(consumableRemaining);
      const reorderAt = parseNumber(consumableReorderAt);

      if (!consumableName.trim() || remaining === undefined || remaining < 0) {
        setErrorText("Provide a name and a remaining amount of 0 or more.");
        return;
      }

      addConsumable({
        aquariumId: selectedAquariumId,
        name: consumableName.trim(),
        unit: consumableUnit,
        remaining,
        reorderAt,
        photoUri: consumablePhotoUri.trim() || undefined,
      });
      router.back();
      return;
    }
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
            : "Choose details and save."
        }
        tone="primary"
      />

      <DashboardSection
        title="Details"
        description="Fill out required and optional fields."
      >
        <Card
          mode="contained"
          style={[
            styles.card,
            { backgroundColor: theme.colors.surfaceVariant },
          ]}
        >
          <Card.Content style={styles.cardContent}>
            {canSelectAquarium || !selectedAquariumId ? (
              <View style={styles.chipRow}>
                {aquariums.map((aq) => (
                  <Chip
                    key={aq.id}
                    selected={selectedAquariumId === aq.id}
                    onPress={() => setSelectedAquariumId(aq.id)}
                    compact
                  >
                    {aq.name}
                  </Chip>
                ))}
              </View>
            ) : null}

            {formKind === "livestock" ? (
              <>
                <TextInput
                  label="Name"
                  value={livestockName}
                  onChangeText={setLivestockName}
                  mode="outlined"
                />
                <TextInput
                  label="Species"
                  value={livestockSpecies}
                  onChangeText={setLivestockSpecies}
                  mode="outlined"
                />
                <TextInput
                  label="Quantity"
                  value={livestockQuantity}
                  onChangeText={setLivestockQuantity}
                  mode="outlined"
                  keyboardType="numeric"
                />
                <TextInput
                  label="Acquired at (ISO/date)"
                  value={livestockAcquiredAt}
                  onChangeText={setLivestockAcquiredAt}
                  mode="outlined"
                />
                <TextInput
                  label="Purchase price (optional)"
                  value={livestockPurchasePrice}
                  onChangeText={setLivestockPurchasePrice}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Dietary notes (optional)"
                  value={livestockDietaryNotes}
                  onChangeText={setLivestockDietaryNotes}
                  mode="outlined"
                  multiline
                />
                <TextInput
                  label="Photo URI (optional)"
                  value={livestockPhotoUri}
                  onChangeText={setLivestockPhotoUri}
                  mode="outlined"
                />
                <View style={styles.chipRow}>
                  {LIVESTOCK_KINDS.map((entry) => (
                    <Chip
                      key={entry}
                      compact
                      selected={livestockKind === entry}
                      onPress={() => setLivestockKind(entry)}
                    >
                      {entry}
                    </Chip>
                  ))}
                </View>
                <Text variant="bodyMedium">Status</Text>
                <View style={styles.chipRow}>
                  {LIVESTOCK_STATUSES.map((entry) => (
                    <Chip
                      key={entry}
                      compact
                      selected={livestockStatus === entry}
                      onPress={() => setLivestockStatus(entry)}
                    >
                      {entry}
                    </Chip>
                  ))}
                </View>
              </>
            ) : null}

            {formKind === "task-execution" ? (
              <>
                <Text variant="bodyMedium">Task</Text>
                <View style={styles.chipRow}>
                  {tasksForAquarium.map((task) => (
                    <Chip
                      key={task.id}
                      selected={selectedTaskTemplateId === task.id}
                      onPress={() => setSelectedTaskTemplateId(task.id)}
                      compact
                    >
                      {task.title}
                    </Chip>
                  ))}
                </View>
                <TextInput
                  label="Note (optional)"
                  value={taskNote}
                  onChangeText={setTaskNote}
                  mode="outlined"
                  multiline
                />
                <TextInput
                  label="Completed at (ISO/date, optional)"
                  value={taskCompletedAt}
                  onChangeText={setTaskCompletedAt}
                  mode="outlined"
                />
              </>
            ) : null}

            {formKind === "parameter-log" ? (
              <>
                <TextInput
                  label="Ammonia (NH3)"
                  value={ammonia}
                  onChangeText={setAmmonia}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Nitrite (NO2)"
                  value={nitrite}
                  onChangeText={setNitrite}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Nitrate (NO3)"
                  value={nitrate}
                  onChangeText={setNitrate}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="pH"
                  value={ph}
                  onChangeText={setPh}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Temperature (°C)"
                  value={temperatureC}
                  onChangeText={setTemperatureC}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="GH"
                  value={gh}
                  onChangeText={setGh}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="KH"
                  value={kh}
                  onChangeText={setKh}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Salinity"
                  value={salinity}
                  onChangeText={setSalinity}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Calcium"
                  value={calcium}
                  onChangeText={setCalcium}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Alkalinity"
                  value={alkalinity}
                  onChangeText={setAlkalinity}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
              </>
            ) : null}

            {formKind === "dosing" ? (
              <>
                <TextInput
                  label="Product"
                  value={dosingProduct}
                  onChangeText={setDosingProduct}
                  mode="outlined"
                />
                <TextInput
                  label="Amount (ml)"
                  value={dosingAmount}
                  onChangeText={setDosingAmount}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Note (optional)"
                  value={dosingNote}
                  onChangeText={setDosingNote}
                  mode="outlined"
                  multiline
                />
              </>
            ) : null}

            {formKind === "memo" ? (
              <>
                <TextInput
                  label="Memo"
                  value={memoContent}
                  onChangeText={setMemoContent}
                  mode="outlined"
                  multiline
                />
                <TextInput
                  label="Photo URI (optional)"
                  value={memoPhotoUri}
                  onChangeText={setMemoPhotoUri}
                  mode="outlined"
                />
                <TextInput
                  label="Created at (ISO/date, optional)"
                  value={memoCreatedAt}
                  onChangeText={setMemoCreatedAt}
                  mode="outlined"
                />
              </>
            ) : null}

            {formKind === "issue" ? (
              <TextInput
                label="Issue title"
                value={issueTitle}
                onChangeText={setIssueTitle}
                mode="outlined"
              />
            ) : null}

            {formKind === "consumable" && targetConsumable ? (
              <>
                <Text variant="titleSmall">{targetConsumable.name}</Text>
                <Text variant="bodySmall">
                  Remaining: {targetConsumable.remaining}
                  {targetConsumable.unit}
                </Text>
                <TextInput
                  label={`Amount used (${targetConsumable.unit})`}
                  value={consumableUseAmount}
                  onChangeText={setConsumableUseAmount}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Note (optional)"
                  value={consumableUseNote}
                  onChangeText={setConsumableUseNote}
                  mode="outlined"
                  multiline
                />
              </>
            ) : null}

            {formKind === "consumable" && !targetConsumable ? (
              <>
                <TextInput
                  label="Name"
                  value={consumableName}
                  onChangeText={setConsumableName}
                  mode="outlined"
                />
                <View style={styles.chipRow}>
                  {CONSUMABLE_UNITS.map((entry) => (
                    <Chip
                      key={entry}
                      compact
                      selected={consumableUnit === entry}
                      onPress={() => setConsumableUnit(entry)}
                    >
                      {entry}
                    </Chip>
                  ))}
                </View>
                <TextInput
                  label={`Remaining (${consumableUnit})`}
                  value={consumableRemaining}
                  onChangeText={setConsumableRemaining}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label={`Reorder at (${consumableUnit}, optional)`}
                  value={consumableReorderAt}
                  onChangeText={setConsumableReorderAt}
                  mode="outlined"
                  keyboardType="decimal-pad"
                />
                <TextInput
                  label="Photo URI (optional)"
                  value={consumablePhotoUri}
                  onChangeText={setConsumablePhotoUri}
                  mode="outlined"
                />
              </>
            ) : null}

            <HelperText type="error" visible={!!errorText}>
              {errorText}
            </HelperText>

            <View style={styles.actionsRow}>
              <Button mode="outlined" onPress={() => router.back()}>
                Cancel
              </Button>
              <Button mode="contained" onPress={onSubmit}>
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
