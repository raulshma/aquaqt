import { StyleSheet, View } from "react-native";
import { Card, Chip, Text, TextInput, useTheme } from "react-native-paper";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import type { AssistantDetectedAction } from "@/types/assistant";
import { useAquapt } from "@/context/aquapt-context";
import { HUMANIZED_TYPES } from "./conversation-drawer";
import {
  AQUARIUM_REQUIRING_ACTIONS,
  CONSUMABLE_UNITS,
  FREQUENCIES,
  ISSUE_STATUSES,
  LIVESTOCK_KINDS,
  LIVESTOCK_STATUSES,
  ASSET_CATEGORIES,
  WATER_TYPES,
} from "@/utils/assistant-constants";

interface ActionReviewFormProps {
  actions: AssistantDetectedAction[];
  updateAction: (actionId: string, updates: Record<string, unknown>) => void;
  updateParameterField: (actionId: string, key: string, value: string) => void;
}

export function ActionReviewForm({
  actions,
  updateAction,
  updateParameterField,
}: ActionReviewFormProps) {
  const { aquariums } = useAquapt();
  const theme = useTheme();

  return (
    <>
      {actions.map((action) => (
        <Card key={action.id} mode="outlined" style={styles.actionCard}>
          <Card.Content>
            <View style={styles.actionHeaderRow}>
              <Text variant="titleSmall">
                {HUMANIZED_TYPES[action.type] ?? action.type}
              </Text>
              <Chip
                selected={action.approved}
                onPress={() =>
                  updateAction(action.id, { approved: !action.approved })
                }
              >
                {action.approved ? "Approved" : "Not approved"}
              </Chip>
            </View>

            {AQUARIUM_REQUIRING_ACTIONS.includes(
              action.type as (typeof AQUARIUM_REQUIRING_ACTIONS)[number],
            ) ? (
              <ScrollableSegmentedButtons
                value={action.aquariumId ?? ""}
                onValueChange={(value: string) =>
                  updateAction(action.id, { aquariumId: value })
                }
                buttons={aquariums.map((aq) => ({
                  label: aq.name,
                  value: aq.id,
                }))}
                style={styles.inputSpacing}
                density="small"
              />
            ) : null}

            {action.type === "create_task_template" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Task title"
                  value={action.title ?? ""}
                  onChangeText={(v) => updateAction(action.id, { title: v })}
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.frequency ?? "weekly"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { frequency: v })
                  }
                  buttons={FREQUENCIES.map((i) => ({
                    label: i.label,
                    value: i.value,
                  }))}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Description (optional)"
                  value={action.description ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { description: v })
                  }
                  style={styles.inputSpacing}
                  multiline
                  numberOfLines={2}
                />
              </>
            )}

            {action.type === "complete_task" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Task title"
                  value={action.taskTitle ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { taskTitle: v })
                  }
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Completion note (optional)"
                  value={action.note ?? ""}
                  onChangeText={(v) => updateAction(action.id, { note: v })}
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "log_dosing" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Product"
                  value={action.product ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { product: v })
                  }
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Amount (ml)"
                  value={action.amountMl ? String(action.amountMl) : ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { amountMl: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Note (optional)"
                  value={action.note ?? ""}
                  onChangeText={(v) => updateAction(action.id, { note: v })}
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "log_parameters" && (
              <View style={styles.inputSpacing}>
                <Text variant="bodySmall">Water parameters</Text>
                <View style={styles.row}>
                  <TextInput
                    mode="outlined"
                    label="NO3"
                    value={
                      action.parameters?.nitrate !== undefined
                        ? String(action.parameters.nitrate)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateParameterField(action.id, "nitrate", v)
                    }
                    keyboardType="numeric"
                    style={styles.parameterInput}
                  />
                  <TextInput
                    mode="outlined"
                    label="pH"
                    value={
                      action.parameters?.ph !== undefined
                        ? String(action.parameters.ph)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateParameterField(action.id, "ph", v)
                    }
                    keyboardType="numeric"
                    style={styles.parameterInput}
                  />
                  <TextInput
                    mode="outlined"
                    label="Temp °C"
                    value={
                      action.parameters?.temperatureC !== undefined
                        ? String(action.parameters.temperatureC)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateParameterField(action.id, "temperatureC", v)
                    }
                    keyboardType="numeric"
                    style={styles.parameterInput}
                  />
                </View>
              </View>
            )}

            {action.type === "add_issue" && (
              <TextInput
                mode="outlined"
                label="Issue title"
                value={action.issueTitle ?? action.title ?? ""}
                onChangeText={(v) =>
                  updateAction(action.id, { issueTitle: v })
                }
                style={styles.inputSpacing}
              />
            )}

            {action.type === "add_memo" && (
              <TextInput
                mode="outlined"
                label="Memo content"
                value={action.memoContent ?? action.description ?? ""}
                onChangeText={(v) =>
                  updateAction(action.id, { memoContent: v })
                }
                style={styles.inputSpacing}
                multiline
                numberOfLines={2}
              />
            )}

            {action.type === "save_reminder_settings" && (
              <>
                <View style={styles.rowWrap}>
                  <Chip
                    selected={action.reminderEnabled === true}
                    onPress={() =>
                      updateAction(action.id, { reminderEnabled: true })
                    }
                  >
                    Reminders enabled
                  </Chip>
                  <Chip
                    selected={action.reminderEnabled === false}
                    onPress={() =>
                      updateAction(action.id, { reminderEnabled: false })
                    }
                  >
                    Reminders disabled
                  </Chip>
                </View>
                <TextInput
                  mode="outlined"
                  label="Reminder hour (0-23)"
                  value={
                    action.reminderHour !== undefined
                      ? String(action.reminderHour)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { reminderHour: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "add_aquarium" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Aquarium name"
                  value={action.title ?? ""}
                  onChangeText={(v) => updateAction(action.id, { title: v })}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Volume (L)"
                  value={
                    action.volumeLiters !== undefined
                      ? String(action.volumeLiters)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { volumeLiters: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Dimensions"
                  value={action.dimensions ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { dimensions: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.waterType ?? "freshwater"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { waterType: v })
                  }
                  buttons={WATER_TYPES}
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "edit_aquarium" && (
              <>
                <ScrollableSegmentedButtons
                  value={action.aquariumId ?? ""}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { aquariumId: v })
                  }
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                  style={styles.inputSpacing}
                  density="small"
                />
                <TextInput
                  mode="outlined"
                  label="Aquarium name (optional)"
                  value={action.aquariumName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { aquariumName: v })
                  }
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="New name (optional)"
                  value={action.title ?? ""}
                  onChangeText={(v) => updateAction(action.id, { title: v })}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="New volume (L)"
                  value={
                    action.volumeLiters !== undefined
                      ? String(action.volumeLiters)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { volumeLiters: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "add_livestock" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Livestock name"
                  value={action.livestockName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { livestockName: v })
                  }
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Species"
                  value={action.species ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { species: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.livestockKind ?? "fish"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { livestockKind: v })
                  }
                  buttons={LIVESTOCK_KINDS}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Quantity"
                  value={
                    action.quantity !== undefined
                      ? String(action.quantity)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { quantity: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "transfer_livestock" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Livestock name"
                  value={action.livestockName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { livestockName: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.targetAquariumId ?? ""}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { targetAquariumId: v })
                  }
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "set_livestock_status" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Livestock name"
                  value={action.livestockName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { livestockName: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.livestockStatus ?? "active"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { livestockStatus: v })
                  }
                  buttons={LIVESTOCK_STATUSES}
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "add_asset" && (
              <>
                <ScrollableSegmentedButtons
                  value={action.assetCategory ?? "other"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { assetCategory: v })
                  }
                  buttons={ASSET_CATEGORIES}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Brand/model"
                  value={action.brandModel ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { brandModel: v })
                  }
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "add_consumable" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Consumable name"
                  value={action.consumableName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { consumableName: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.consumableUnit ?? "ml"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { consumableUnit: v })
                  }
                  buttons={CONSUMABLE_UNITS}
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Remaining"
                  value={
                    action.remaining !== undefined
                      ? String(action.remaining)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { remaining: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "consume_consumable" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Consumable name"
                  value={action.consumableName ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { consumableName: v })
                  }
                  style={styles.inputSpacing}
                />
                <TextInput
                  mode="outlined"
                  label="Amount used"
                  value={
                    action.amountUsed !== undefined
                      ? String(action.amountUsed)
                      : ""
                  }
                  onChangeText={(v) =>
                    updateAction(action.id, { amountUsed: Number(v) })
                  }
                  keyboardType="numeric"
                  style={styles.inputSpacing}
                />
              </>
            )}

            {action.type === "set_issue_status" && (
              <>
                <TextInput
                  mode="outlined"
                  label="Issue title"
                  value={action.issueTitle ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { issueTitle: v })
                  }
                  style={styles.inputSpacing}
                />
                <ScrollableSegmentedButtons
                  value={action.issueStatus ?? "open"}
                  onValueChange={(v: string) =>
                    updateAction(action.id, { issueStatus: v })
                  }
                  buttons={ISSUE_STATUSES}
                  style={styles.inputSpacing}
                />
              </>
            )}

            <View style={styles.rowWrap}>
              <Chip compact>
                Confidence: {(action.confidence * 100).toFixed(0)}%
              </Chip>
              {action.validationErrors.length > 0 ? (
                <Chip compact>Needs edits</Chip>
              ) : (
                <Chip compact>Ready</Chip>
              )}
            </View>

            {action.validationErrors.length > 0 ? (
              <Text variant="bodySmall" style={[styles.errorText, { color: theme.colors.error }]}>
                {action.validationErrors.join("\n")}
              </Text>
            ) : null}
          </Card.Content>
        </Card>
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 10,
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8,
  },
  errorText: {
    marginTop: 10,
  },
  actionCard: {
    marginTop: 10,
    borderRadius: 16,
  },
  actionHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  inputSpacing: {
    marginTop: 10,
  },
  parameterInput: {
    minWidth: 88,
    flex: 1,
  },
});
