import type { AssistantDetectedAction } from "@/types/assistant";
import { AQUARIUM_REQUIRING_ACTIONS } from "./assistant-constants";

export const validateAction = (
  action: AssistantDetectedAction,
  hasMultipleAquariums: boolean,
) => {
  const errors: string[] = [];

  const requiresAquariumSelection = AQUARIUM_REQUIRING_ACTIONS.includes(
    action.type as (typeof AQUARIUM_REQUIRING_ACTIONS)[number],
  );

  if (
    requiresAquariumSelection &&
    hasMultipleAquariums &&
    !action.aquariumId &&
    !action.aquariumName?.trim()
  ) {
    errors.push("Pick an aquarium");
  }

  if (action.type === "create_task_template") {
    if (!action.title?.trim()) errors.push("Task title is required");
    if (!action.frequency) errors.push("Frequency is required");
  }
  if (action.type === "complete_task") {
    if (!action.taskTemplateId?.trim() && !action.taskTitle?.trim())
      errors.push("Task to complete is required");
  }
  if (action.type === "log_dosing") {
    if (!action.product?.trim()) errors.push("Dosing product is required");
    if (!action.amountMl || action.amountMl <= 0)
      errors.push("Dosing amount must be greater than 0");
  }
  if (action.type === "log_parameters") {
    if (!action.parameters || Object.keys(action.parameters).length === 0)
      errors.push("At least one water parameter is required");
  }
  if (action.type === "add_issue") {
    if (!action.issueTitle?.trim() && !action.title?.trim())
      errors.push("Issue title is required");
  }
  if (action.type === "add_memo") {
    if (!action.memoContent?.trim() && !action.description?.trim())
      errors.push("Memo content is required");
  }
  if (action.type === "save_reminder_settings") {
    if (typeof action.reminderEnabled !== "boolean")
      errors.push("Reminder enabled/disabled state is required");
    if (action.reminderEnabled && action.reminderHour === undefined)
      errors.push("Reminder hour is required when reminders are enabled");
  }
  if (action.type === "add_aquarium") {
    if (!action.title?.trim()) errors.push("Aquarium name is required");
    if (!action.volumeLiters || action.volumeLiters <= 0)
      errors.push("Aquarium volume must be greater than 0");
    if (!action.waterType) errors.push("Water type is required");
    if (!action.dimensions?.trim()) errors.push("Dimensions are required");
  }
  if (action.type === "edit_aquarium") {
    if (!action.aquariumId?.trim() && !action.aquariumName?.trim())
      errors.push("Aquarium to edit is required");
  }
  if (action.type === "add_livestock") {
    if (!action.livestockName?.trim() && !action.title?.trim())
      errors.push("Livestock name is required");
    if (!action.species?.trim()) errors.push("Species is required");
    if (!action.livestockKind) errors.push("Livestock kind is required");
    if (!action.quantity || action.quantity <= 0)
      errors.push("Quantity must be greater than 0");
  }
  if (action.type === "transfer_livestock") {
    if (!action.livestockId?.trim() && !action.livestockName?.trim())
      errors.push("Livestock to transfer is required");
    if (!action.targetAquariumId?.trim() && !action.targetAquariumName?.trim())
      errors.push("Target aquarium is required");
  }
  if (action.type === "set_livestock_status") {
    if (!action.livestockId?.trim() && !action.livestockName?.trim())
      errors.push("Livestock is required");
    if (!action.livestockStatus) errors.push("Livestock status is required");
  }
  if (action.type === "add_asset") {
    if (!action.assetCategory) errors.push("Asset category is required");
    if (!action.brandModel?.trim()) errors.push("Brand/model is required");
  }
  if (action.type === "add_consumable") {
    if (!action.consumableName?.trim())
      errors.push("Consumable name is required");
    if (!action.consumableUnit) errors.push("Consumable unit is required");
    if (action.remaining === undefined || action.remaining < 0)
      errors.push("Remaining amount must be 0 or greater");
  }
  if (action.type === "consume_consumable") {
    if (!action.consumableId?.trim() && !action.consumableName?.trim())
      errors.push("Consumable is required");
    if (!action.amountUsed || action.amountUsed <= 0)
      errors.push("Amount used must be greater than 0");
  }
  if (action.type === "set_issue_status") {
    if (!action.issueId?.trim() && !action.issueTitle?.trim())
      errors.push("Issue is required");
    if (!action.issueStatus) errors.push("Issue status is required");
  }
  return errors;
};

export const applyActionDefaults = (
  action: AssistantDetectedAction,
  singleAquariumId?: string,
): AssistantDetectedAction => {
  if (action.type === "create_task_template" && !action.frequency) {
    return { ...action, frequency: "weekly" };
  }

  if (action.type === "add_aquarium" && !action.waterType) {
    return { ...action, waterType: "freshwater" };
  }

  if (
    action.type === "edit_aquarium" &&
    singleAquariumId &&
    !action.aquariumId &&
    !action.aquariumName?.trim()
  ) {
    return { ...action, aquariumId: singleAquariumId };
  }

  if (action.type === "add_livestock" && !action.livestockKind) {
    return { ...action, livestockKind: "fish" };
  }

  if (action.type === "set_livestock_status" && !action.livestockStatus) {
    return { ...action, livestockStatus: "active" };
  }

  if (action.type === "add_asset" && !action.assetCategory) {
    return { ...action, assetCategory: "other" };
  }

  if (action.type === "add_consumable" && !action.consumableUnit) {
    return { ...action, consumableUnit: "ml" };
  }

  if (action.type === "set_issue_status" && !action.issueStatus) {
    return { ...action, issueStatus: "open" };
  }

  return action;
};
