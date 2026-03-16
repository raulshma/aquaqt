import { Image } from "expo-image";
import * as ImagePicker from "expo-image-picker";
import { useMemo, useState } from "react";
import {
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
    SegmentedButtons,
    Text,
    TextInput,
} from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { useAquapt } from "@/context/aquapt-context";
import { IssueStatus } from "@/types/aquapt";

const WATER_TYPES = ["freshwater", "marine", "brackish"] as const;

export default function HomeScreen() {
  const { width } = useWindowDimensions();
  const {
    aquariums,
    livestock,
    assets,
    consumables,
    dosingLogs,
    issues,
    parameterLogs,
    livestockCountByAquarium,
    openIssuesByAquarium,
    addAquarium,
    addMemo,
    addIssue,
    addLivestock,
    transferLivestock,
    addOffspring,
    setLivestockFeedingNotes,
    addAsset,
    addConsumable,
    consumeConsumable,
    logDosing,
    logParameters,
    setIssueStatus,
  } = useAquapt();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [selectedAquariumId, setSelectedAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [action, setAction] = useState<
    "parameter" | "memo" | "issue" | "dosing"
  >("parameter");
  const [newAquariumName, setNewAquariumName] = useState("");
  const [newAquariumVolume, setNewAquariumVolume] = useState("");
  const [newAquariumType, setNewAquariumType] = useState<
    "freshwater" | "marine" | "brackish"
  >("freshwater");
  const [newLivestockName, setNewLivestockName] = useState("");
  const [newLivestockSpecies, setNewLivestockSpecies] = useState("");
  const [newLivestockQty, setNewLivestockQty] = useState("1");
  const [newLivestockPhotoUri, setNewLivestockPhotoUri] = useState("");
  const [isPickingPhoto, setPickingPhoto] = useState(false);
  const [newAssetModel, setNewAssetModel] = useState("");
  const [newConsumableName, setNewConsumableName] = useState("");
  const [newConsumableRemaining, setNewConsumableRemaining] = useState("0");
  const [memo, setMemo] = useState("");
  const [issueTitle, setIssueTitle] = useState("");
  const [ammonia, setAmmonia] = useState("");
  const [nitrite, setNitrite] = useState("");
  const [nitrate, setNitrate] = useState("");
  const [ph, setPh] = useState("");
  const [temperature, setTemperature] = useState("");
  const [gh, setGh] = useState("");
  const [kh, setKh] = useState("");
  const [salinity, setSalinity] = useState("");
  const [calcium, setCalcium] = useState("");
  const [alkalinity, setAlkalinity] = useState("");
  const [doseProduct, setDoseProduct] = useState("");
  const [doseAmount, setDoseAmount] = useState("");
  const [issueStatusDraft, setIssueStatusDraft] = useState<
    Record<string, IssueStatus>
  >({});
  const [resolutionNoteDraft, setResolutionNoteDraft] = useState<
    Record<string, string>
  >({});

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
    if (!selectedAquariumId) {
      return;
    }

    if (action === "memo" && memo.trim()) {
      addMemo(selectedAquariumId, memo.trim());
      setMemo("");
    }

    if (action === "issue" && issueTitle.trim()) {
      addIssue(selectedAquariumId, issueTitle.trim());
      setIssueTitle("");
    }

    if (action === "parameter") {
      const ammoniaValue = Number(ammonia);
      const nitriteValue = Number(nitrite);
      const nitrateValue = Number(nitrate);
      const phValue = Number(ph);
      const temperatureValue = Number(temperature);
      const ghValue = Number(gh);
      const khValue = Number(kh);
      const salinityValue = Number(salinity);
      const calciumValue = Number(calcium);
      const alkalinityValue = Number(alkalinity);

      logParameters(selectedAquariumId, {
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

      setAmmonia("");
      setNitrite("");
      setNitrate("");
      setPh("");
      setTemperature("");
      setGh("");
      setKh("");
      setSalinity("");
      setCalcium("");
      setAlkalinity("");
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

  const createAquarium = () => {
    const volume = Number(newAquariumVolume);
    if (!newAquariumName.trim() || !Number.isFinite(volume) || volume <= 0) {
      return;
    }

    addAquarium({
      name: newAquariumName.trim(),
      volumeLiters: volume,
      dimensions: "-",
      waterType: newAquariumType,
      setupDate: new Date().toISOString().slice(0, 10),
    });
    setNewAquariumName("");
    setNewAquariumVolume("");
    setNewAquariumType("freshwater");
  };

  const createLivestock = () => {
    const quantity = Number(newLivestockQty);
    if (
      !selectedAquariumId ||
      !newLivestockName.trim() ||
      !newLivestockSpecies.trim()
    ) {
      return;
    }

    addLivestock({
      aquariumId: selectedAquariumId,
      kind: "other",
      name: newLivestockName.trim(),
      species: newLivestockSpecies.trim(),
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
      acquiredAt: new Date().toISOString(),
      photoUri: newLivestockPhotoUri || undefined,
      status: "active",
    });
    setNewLivestockName("");
    setNewLivestockSpecies("");
    setNewLivestockQty("1");
    setNewLivestockPhotoUri("");
  };

  const pickLivestockPhoto = async () => {
    setPickingPhoto(true);
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
        setNewLivestockPhotoUri(result.assets[0].uri);
      }
    } finally {
      setPickingPhoto(false);
    }
  };

  const createAsset = () => {
    if (!selectedAquariumId || !newAssetModel.trim()) {
      return;
    }

    addAsset({
      aquariumId: selectedAquariumId,
      category: "other",
      brandModel: newAssetModel.trim(),
    });
    setNewAssetModel("");
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
      unit: "pcs",
      remaining,
      reorderAt: Math.max(1, Math.floor(remaining * 0.25)),
    });
    setNewConsumableName("");
    setNewConsumableRemaining("0");
  };

  const totalOpenIssues = issues.filter(
    (issue) => issue.status !== "resolved",
  ).length;

  const chartAquariumId = selectedAquariumId || aquariums[0]?.id || "";
  const nitrateChartData = useMemo(() => {
    if (!chartAquariumId) {
      return [];
    }

    return parameterLogs
      .filter(
        (entry) =>
          entry.aquariumId === chartAquariumId &&
          entry.values.nitrate !== undefined,
      )
      .sort((a, b) => +new Date(a.createdAt) - +new Date(b.createdAt))
      .slice(-8)
      .map((entry) => ({
        value: entry.values.nitrate as number,
        label: `${new Date(entry.createdAt).getDate()}`,
      }));
  }, [chartAquariumId, parameterLogs]);

  return (
    <>
      <ScrollView contentContainerStyle={styles.container}>
        <Text variant="headlineMedium">Aquapt Dashboard</Text>
        <Text variant="bodyMedium" style={styles.subtitle}>
          Monitor tank health, log key events, and catch issues early.
        </Text>

        <Card style={styles.summaryCard} mode="elevated">
          <Card.Content>
            <Text variant="titleMedium">Today at a glance</Text>
            <View style={styles.summaryRow}>
              <Chip compact icon="fish">
                {aquariums.length} Tanks
              </Chip>
              <Chip compact icon="alert">
                {totalOpenIssues} Active issues
              </Chip>
              <Chip compact icon="test-tube">
                {dosingLogs.length} Dosing logs
              </Chip>
            </View>
          </Card.Content>
        </Card>

        <Card style={styles.tankCard} mode="outlined">
          <Card.Title title="Add Aquarium" subtitle="Multi-tank management" />
          <Card.Content style={styles.formStack}>
            <TextInput
              mode="outlined"
              label="Aquarium name"
              value={newAquariumName}
              onChangeText={setNewAquariumName}
            />
            <TextInput
              mode="outlined"
              label="Volume (L)"
              value={newAquariumVolume}
              onChangeText={setNewAquariumVolume}
              keyboardType="numeric"
            />
            <SegmentedButtons
              value={newAquariumType}
              onValueChange={(value) =>
                setNewAquariumType(
                  value as "freshwater" | "marine" | "brackish",
                )
              }
              buttons={WATER_TYPES.map((type) => ({
                label: type,
                value: type,
              }))}
            />
            <Button mode="contained" onPress={createAquarium}>
              Save aquarium
            </Button>
          </Card.Content>
        </Card>

        {aquariums.map((aquarium) => (
          <Card key={aquarium.id} style={styles.tankCard} mode="contained">
            <Card.Title
              title={aquarium.name}
              subtitle={`${aquarium.volumeLiters}L • ${aquarium.waterType}`}
            />
            <Card.Content>
              <Text variant="bodyMedium">
                Latest parameters: {latestParameterByAquarium[aquarium.id]}
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
              </View>
            </Card.Content>
          </Card>
        ))}

        <Card style={styles.tankCard} mode="outlined">
          <Card.Title
            title="Parameter Analytics"
            subtitle="Nitrate trend (recent logs)"
          />
          <Card.Content>
            <SegmentedButtons
              value={chartAquariumId}
              onValueChange={setSelectedAquariumId}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              }))}
            />
            {nitrateChartData.length > 1 ? (
              <View style={styles.chartWrap}>
                <LineChart
                  areaChart
                  data={nitrateChartData}
                  width={Math.max(220, width - 96)}
                  spacing={28}
                  color="#4caf50"
                  startFillColor="#4caf50"
                  endFillColor="#4caf50"
                  startOpacity={0.22}
                  endOpacity={0.04}
                  hideDataPoints={false}
                  dataPointsColor="#2e7d32"
                  yAxisTextStyle={styles.chartAxisLabel}
                  xAxisLabelTextStyle={styles.chartAxisLabel}
                  rulesColor="rgba(120,120,120,0.2)"
                />
              </View>
            ) : (
              <Text variant="bodyMedium" style={styles.chartEmpty}>
                Need at least 2 nitrate logs for charting.
              </Text>
            )}
          </Card.Content>
        </Card>

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Livestock tracking
        </Text>
        <Card mode="outlined">
          <Card.Content style={styles.formStack}>
            <SegmentedButtons
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
            <TextInput
              mode="outlined"
              label="Quantity"
              value={newLivestockQty}
              onChangeText={setNewLivestockQty}
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
            <Button mode="contained" onPress={createLivestock}>
              Add livestock
            </Button>
          </Card.Content>
        </Card>

        {livestock.map((item) => {
          const currentIndex = aquariums.findIndex(
            (aq) => aq.id === item.aquariumId,
          );
          const fallbackTarget =
            aquariums[(currentIndex + 1 + aquariums.length) % aquariums.length]
              ?.id;
          const resolutionNote =
            resolutionNoteDraft[item.id] ?? item.dietaryNotes ?? "";

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
                {item.photoUri ? (
                  <Image
                    source={{ uri: item.photoUri }}
                    style={styles.livestockPhoto}
                  />
                ) : null}
                <TextInput
                  mode="outlined"
                  label="Feeding notes"
                  value={resolutionNote}
                  onChangeText={(value) =>
                    setResolutionNoteDraft((prev) => ({
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
                      setLivestockFeedingNotes(item.id, resolutionNote.trim())
                    }
                  >
                    Save feeding
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
              </Card.Content>
            </Card>
          );
        })}

        <Text variant="titleMedium" style={styles.sectionTitle}>
          Assets & consumables inventory
        </Text>
        <Card mode="outlined">
          <Card.Content style={styles.formStack}>
            <TextInput
              mode="outlined"
              label="Asset model"
              value={newAssetModel}
              onChangeText={setNewAssetModel}
            />
            <Button mode="contained-tonal" onPress={createAsset}>
              Add asset
            </Button>

            <TextInput
              mode="outlined"
              label="Consumable name"
              value={newConsumableName}
              onChangeText={setNewConsumableName}
            />
            <TextInput
              mode="outlined"
              label="Remaining (pcs)"
              value={newConsumableRemaining}
              onChangeText={setNewConsumableRemaining}
              keyboardType="numeric"
            />
            <Button mode="contained-tonal" onPress={createConsumable}>
              Add consumable
            </Button>
          </Card.Content>
        </Card>

        {assets.map((asset) => (
          <Card key={asset.id} style={styles.issueCard} mode="contained">
            <Card.Content>
              <Text variant="titleSmall">{asset.brandModel}</Text>
              <Text variant="bodySmall" style={styles.issueMeta}>
                {asset.category} •{" "}
                {aquariums.find((aq) => aq.id === asset.aquariumId)?.name}
              </Text>
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

                <SegmentedButtons
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
        visible={isDialogOpen}
        onDismiss={() => setDialogOpen(false)}
        title="Quick Log"
        actions={
          <>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button onPress={handleSubmitQuickAction}>Save</Button>
          </>
        }
      >
        <SegmentedButtons
          value={selectedAquariumId}
          onValueChange={setSelectedAquariumId}
          buttons={aquariums.map((aq) => ({
            label: aq.name,
            value: aq.id,
            style: styles.segmentButton,
          }))}
          density="small"
        />

        <SegmentedButtons
          value={action}
          onValueChange={(value) =>
            setAction(value as "parameter" | "memo" | "issue")
          }
          buttons={[
            { label: "Parameters", value: "parameter" },
            { label: "Memo", value: "memo" },
            { label: "Issue", value: "issue" },
            { label: "Dosing", value: "dosing" },
          ]}
          style={styles.actionSelector}
        />

        {action === "parameter" ? (
          <View style={styles.inputsContainer}>
            <TextInput
              label="Ammonia (ppm)"
              mode="outlined"
              value={ammonia}
              onChangeText={setAmmonia}
              keyboardType="numeric"
            />
            <TextInput
              label="Nitrite (ppm)"
              mode="outlined"
              value={nitrite}
              onChangeText={setNitrite}
              keyboardType="numeric"
            />
            <TextInput
              label="Nitrate (ppm)"
              mode="outlined"
              value={nitrate}
              onChangeText={setNitrate}
              keyboardType="numeric"
            />
            <TextInput
              label="pH"
              mode="outlined"
              value={ph}
              onChangeText={setPh}
              keyboardType="numeric"
            />
            <TextInput
              label="Temperature °C"
              mode="outlined"
              value={temperature}
              onChangeText={setTemperature}
              keyboardType="numeric"
            />
            <TextInput
              label="GH"
              mode="outlined"
              value={gh}
              onChangeText={setGh}
              keyboardType="numeric"
            />
            <TextInput
              label="KH"
              mode="outlined"
              value={kh}
              onChangeText={setKh}
              keyboardType="numeric"
            />
            <TextInput
              label="Salinity"
              mode="outlined"
              value={salinity}
              onChangeText={setSalinity}
              keyboardType="numeric"
            />
            <TextInput
              label="Calcium"
              mode="outlined"
              value={calcium}
              onChangeText={setCalcium}
              keyboardType="numeric"
            />
            <TextInput
              label="Alkalinity"
              mode="outlined"
              value={alkalinity}
              onChangeText={setAlkalinity}
              keyboardType="numeric"
            />
          </View>
        ) : null}

        {action === "memo" ? (
          <TextInput
            label="Memo"
            mode="outlined"
            value={memo}
            onChangeText={setMemo}
            multiline
            numberOfLines={4}
            style={styles.inputTopSpacing}
          />
        ) : null}

        {action === "issue" ? (
          <TextInput
            label="Issue title"
            mode="outlined"
            value={issueTitle}
            onChangeText={setIssueTitle}
            style={styles.inputTopSpacing}
          />
        ) : null}

        {action === "dosing" ? (
          <View style={styles.inputsContainer}>
            <TextInput
              label="Product"
              mode="outlined"
              value={doseProduct}
              onChangeText={setDoseProduct}
            />
            <TextInput
              label="Amount (ml)"
              mode="outlined"
              value={doseAmount}
              onChangeText={setDoseAmount}
              keyboardType="numeric"
            />
          </View>
        ) : null}
      </BottomSheet>

      <FAB
        icon="plus"
        style={styles.fab}
        onPress={() => setDialogOpen(true)}
        label="Quick Log"
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    paddingBottom: 96,
    gap: 12,
  },
  subtitle: {
    opacity: 0.75,
    marginBottom: 4,
  },
  summaryCard: {
    marginVertical: 4,
  },
  tankCard: {
    marginTop: 8,
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
    borderRadius: 12,
  },
  livestockPhoto: {
    width: "100%",
    height: 150,
    borderRadius: 12,
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
  chartAxisLabel: {
    color: "rgba(120,120,120,0.9)",
    fontSize: 10,
  },
  fab: {
    position: "absolute",
    right: 16,
    bottom: 16,
  },
});
