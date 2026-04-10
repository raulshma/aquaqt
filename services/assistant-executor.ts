import {
    Aquarium,
    AssetCategory,
    Consumable,
    ConsumableUnit,
    Issue,
    Livestock,
    TaskCategory,
    TaskFrequency,
    TaskTemplate,
    WaterParameters,
} from "@/types/aquapt";
import {
    AssistantDetectedAction,
    AssistantTaskExecutionResult,
} from "@/types/assistant";

interface ExecuteApprovedActionsOptions {
  actions: AssistantDetectedAction[];
  aquariums: Aquarium[];
  existingTaskTemplates: TaskTemplate[];
  existingLivestock: Livestock[];
  existingIssues: Issue[];
  existingConsumables: Consumable[];
  addTaskTemplate: (input: {
    title: string;
    frequency: TaskFrequency;
    aquariumIds: string[];
    description?: string;
    category?: TaskCategory;
    livestockId?: string;
    reminderHours?: number[];
  }) => void;
  completeTask: (
    taskTemplateId: string,
    aquariumId: string,
    note?: string,
  ) => void;
  logDosing: (
    aquariumId: string,
    product: string,
    amountMl: number,
    note?: string,
  ) => void;
  logParameters: (
    aquariumId: string,
    values: WaterParameters,
  ) => void;
  addIssue: (aquariumId: string, title: string) => void;
  addMemo: (
    aquariumId: string,
    content: string,
    photoUri?: string,
    createdAt?: string,
  ) => void;
  saveReminderSettings: (input: {
    notificationsEnabled: boolean;
    reminderHour: number;
    reminderHours?: number[];
  }) => void;
  addAquarium: (input: Omit<Aquarium, "id">) => void;
  editAquarium: (
    aquariumId: string,
    updates: Partial<Omit<Aquarium, "id">>,
  ) => void;
  addLivestock: (input: Omit<Livestock, "id">) => void;
  transferLivestock: (
    livestockId: string,
    targetAquariumId: string,
    note?: string,
  ) => void;
  setLivestockStatus: (
    livestockId: string,
    status: NonNullable<Livestock["status"]>,
    note?: string,
  ) => void;
  addAsset: (input: {
    aquariumId: string;
    category: AssetCategory;
    brandModel: string;
    purchasedAt?: string;
    price?: number;
    maintenanceTaskTemplateIds?: string[];
  }) => void;
  addConsumable: (input: {
    aquariumId: string;
    name: string;
    unit: ConsumableUnit;
    remaining: number;
    reorderAt?: number;
  }) => void;
  consumeConsumable: (
    consumableId: string,
    amountUsed: number,
    note?: string,
  ) => void;
  setIssueStatus: (
    issueId: string,
    status: Issue["status"],
    resolutionNote?: string,
  ) => void;
}

const normalizeLabel = (value: string) => value.trim().toLowerCase();

const resolveAquariumId = (
  action: AssistantDetectedAction,
  aquariums: Aquarium[],
) => {
  if (
    action.aquariumId &&
    aquariums.some((aq) => aq.id === action.aquariumId)
  ) {
    return action.aquariumId;
  }

  const byName = action.aquariumName
    ? aquariums.find(
        (aq) =>
          normalizeLabel(aq.name) === normalizeLabel(action.aquariumName!),
      )
    : undefined;

  if (byName) {
    return byName.id;
  }

  if (aquariums.length === 1) {
    return aquariums[0].id;
  }

  return "";
};

const resolveTargetAquariumId = (
  action: AssistantDetectedAction,
  aquariums: Aquarium[],
) => {
  if (
    action.targetAquariumId &&
    aquariums.some((aq) => aq.id === action.targetAquariumId)
  ) {
    return action.targetAquariumId;
  }

  if (action.targetAquariumName) {
    const byName = aquariums.find(
      (aq) =>
        normalizeLabel(aq.name) === normalizeLabel(action.targetAquariumName!),
    );
    if (byName) {
      return byName.id;
    }
  }

  return "";
};

const isDuplicate = (
  action: AssistantDetectedAction,
  aquariumId: string,
  existingTaskTemplates: TaskTemplate[],
) => {
  const normalizedTitle = normalizeLabel(action.title ?? "");

  return existingTaskTemplates.some((task) => {
    return (
      normalizeLabel(task.title) === normalizedTitle &&
      task.frequency === action.frequency &&
      task.aquariumIds.includes(aquariumId)
    );
  });
};

const resolveTaskTemplateId = (
  action: AssistantDetectedAction,
  aquariumId: string,
  existingTaskTemplates: TaskTemplate[],
) => {
  if (
    action.taskTemplateId &&
    existingTaskTemplates.some((task) => task.id === action.taskTemplateId)
  ) {
    return action.taskTemplateId;
  }

  if (!action.taskTitle?.trim()) {
    return "";
  }

  const byTitle = existingTaskTemplates.find(
    (task) =>
      normalizeLabel(task.title) === normalizeLabel(action.taskTitle ?? "") &&
      task.aquariumIds.includes(aquariumId),
  );

  return byTitle?.id ?? "";
};

const resolveLivestockId = (
  action: AssistantDetectedAction,
  existingLivestock: Livestock[],
  aquariumId?: string,
) => {
  if (
    action.livestockId &&
    existingLivestock.some((item) => item.id === action.livestockId)
  ) {
    return action.livestockId;
  }

  if (!action.livestockName?.trim()) {
    return "";
  }

  const byName = existingLivestock.find((item) => {
    const nameMatch =
      normalizeLabel(item.name) === normalizeLabel(action.livestockName ?? "");
    if (!nameMatch) {
      return false;
    }

    if (aquariumId) {
      return item.aquariumId === aquariumId;
    }

    return true;
  });

  return byName?.id ?? "";
};

const resolveIssueId = (
  action: AssistantDetectedAction,
  existingIssues: Issue[],
) => {
  if (
    action.issueId &&
    existingIssues.some((item) => item.id === action.issueId)
  ) {
    return action.issueId;
  }

  if (!action.issueTitle?.trim()) {
    return "";
  }

  const byTitle = existingIssues.find(
    (item) =>
      normalizeLabel(item.title) === normalizeLabel(action.issueTitle ?? ""),
  );
  return byTitle?.id ?? "";
};

const resolveConsumableId = (
  action: AssistantDetectedAction,
  existingConsumables: Consumable[],
  aquariumId?: string,
) => {
  if (
    action.consumableId &&
    existingConsumables.some((item) => item.id === action.consumableId)
  ) {
    return action.consumableId;
  }

  if (!action.consumableName?.trim()) {
    return "";
  }

  const byName = existingConsumables.find((item) => {
    const nameMatch =
      normalizeLabel(item.name) === normalizeLabel(action.consumableName ?? "");
    if (!nameMatch) {
      return false;
    }

    if (aquariumId) {
      return item.aquariumId === aquariumId;
    }

    return true;
  });

  return byName?.id ?? "";
};

export function executeApprovedActions({
  actions,
  aquariums,
  existingTaskTemplates,
  existingLivestock,
  existingIssues,
  existingConsumables,
  addTaskTemplate,
  completeTask,
  logDosing,
  logParameters,
  addIssue,
  addMemo,
  saveReminderSettings,
  addAquarium,
  editAquarium,
  addLivestock,
  transferLivestock,
  setLivestockStatus,
  addAsset,
  addConsumable,
  consumeConsumable,
  setIssueStatus,
}: ExecuteApprovedActionsOptions): AssistantTaskExecutionResult {
  const results: AssistantTaskExecutionResult["results"] = [];
  let createdCount = 0;

  for (const action of actions) {
    if (!action.approved) {
      continue;
    }

    if (action.validationErrors.length > 0) {
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: false,
        reason: action.validationErrors.join(", "),
      });
      continue;
    }

    if (action.type === "save_reminder_settings") {
      const hours = action.reminderHours ?? (action.reminderHour !== undefined ? [action.reminderHour] : [8]);
      saveReminderSettings({
        notificationsEnabled: !!action.reminderEnabled,
        reminderHour: Math.min(23, Math.max(0, action.reminderHour ?? hours[0] ?? 8)),
        reminderHours: hours.map((h) => Math.min(23, Math.max(0, h))),
      });
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Reminder settings updated",
      });
      continue;
    }

    if (action.type === "add_aquarium") {
      if (
        !action.title?.trim() ||
        !action.volumeLiters ||
        !action.waterType ||
        !action.dimensions?.trim()
      ) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Aquarium fields are incomplete.",
        });
        continue;
      }

      addAquarium({
        name: action.title.trim(),
        volumeLiters: action.volumeLiters,
        dimensions: action.dimensions.trim(),
        waterType: action.waterType,
        setupDate: action.setupDate?.trim() || new Date().toISOString(),
        investmentCost: action.investmentCost,
      });
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Aquarium added",
      });
      continue;
    }

    if (action.type === "edit_aquarium") {
      const editAquariumId = resolveAquariumId(action, aquariums);
      if (!editAquariumId) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "No aquarium found to edit.",
        });
        continue;
      }

      const updates: Partial<Omit<Aquarium, "id">> = {};
      if (action.title?.trim()) {
        updates.name = action.title.trim();
      }
      if (action.volumeLiters && action.volumeLiters > 0) {
        updates.volumeLiters = action.volumeLiters;
      }
      if (action.dimensions?.trim()) {
        updates.dimensions = action.dimensions.trim();
      }
      if (action.waterType) {
        updates.waterType = action.waterType;
      }
      if (action.setupDate?.trim()) {
        updates.setupDate = action.setupDate.trim();
      }
      if (action.investmentCost !== undefined) {
        updates.investmentCost = action.investmentCost;
      }

      if (Object.keys(updates).length === 0) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "No aquarium updates provided.",
        });
        continue;
      }

      editAquarium(editAquariumId, updates);
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Aquarium updated",
      });
      continue;
    }

    if (action.type === "set_issue_status") {
      const issueId = resolveIssueId(action, existingIssues);
      if (!issueId || !action.issueStatus) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Could not resolve issue or status.",
        });
        continue;
      }

      setIssueStatus(
        issueId,
        action.issueStatus,
        action.resolutionNote ?? action.note,
      );
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Issue status updated",
      });
      continue;
    }

    if (action.type === "consume_consumable") {
      const sourceAquariumId =
        resolveAquariumId(action, aquariums) || undefined;
      const consumableId = resolveConsumableId(
        action,
        existingConsumables,
        sourceAquariumId,
      );
      if (!consumableId || !action.amountUsed || action.amountUsed <= 0) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Could not resolve consumable or amountUsed.",
        });
        continue;
      }

      consumeConsumable(
        consumableId,
        action.amountUsed,
        action.note ?? action.description,
      );
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Consumable usage logged",
      });
      continue;
    }

    if (action.type === "transfer_livestock") {
      const sourceAquariumId =
        resolveAquariumId(action, aquariums) || undefined;
      const livestockId = resolveLivestockId(
        action,
        existingLivestock,
        sourceAquariumId,
      );
      const targetAquariumId = resolveTargetAquariumId(action, aquariums);
      if (!livestockId || !targetAquariumId) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Could not resolve livestock or target aquarium.",
        });
        continue;
      }

      transferLivestock(
        livestockId,
        targetAquariumId,
        action.note ?? action.description,
      );
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Livestock transferred",
      });
      continue;
    }

    if (action.type === "set_livestock_status") {
      const sourceAquariumId =
        resolveAquariumId(action, aquariums) || undefined;
      const livestockId = resolveLivestockId(
        action,
        existingLivestock,
        sourceAquariumId,
      );
      if (!livestockId || !action.livestockStatus) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Could not resolve livestock or status.",
        });
        continue;
      }

      setLivestockStatus(
        livestockId,
        action.livestockStatus,
        action.note ?? action.description,
      );
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Livestock status updated",
      });
      continue;
    }

    const aquariumId = resolveAquariumId(action, aquariums);
    if (!aquariumId) {
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: false,
        reason: "No aquarium could be resolved for this action.",
      });
      continue;
    }

    if (action.type === "create_task_template") {
      if (!action.title || !action.frequency) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Task title/frequency missing",
        });
        continue;
      }

      if (isDuplicate(action, aquariumId, existingTaskTemplates)) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Skipped duplicate task.",
        });
        continue;
      }

      addTaskTemplate({
        title: action.title,
        frequency: action.frequency,
        aquariumIds: [aquariumId],
        description: action.description,
        category: "maintenance",
        reminderHours: action.reminderHours,
      });

      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: `Task created: ${action.title}`,
      });
      continue;
    }

    if (action.type === "complete_task") {
      const taskTemplateId = resolveTaskTemplateId(
        action,
        aquariumId,
        existingTaskTemplates,
      );

      if (!taskTemplateId) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Task template for completion was not found.",
        });
        continue;
      }

      completeTask(
        taskTemplateId,
        aquariumId,
        action.note ?? action.description,
      );
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Task marked complete",
      });
      continue;
    }

    if (action.type === "log_dosing") {
      if (!action.product || !action.amountMl || action.amountMl <= 0) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Dosing product/amount is invalid.",
        });
        continue;
      }

      logDosing(aquariumId, action.product, action.amountMl, action.note);
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Dosing logged",
      });
      continue;
    }

    if (action.type === "log_parameters") {
      if (!action.parameters) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "No parameter values were provided.",
        });
        continue;
      }

      logParameters(aquariumId, action.parameters);
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Water parameters logged",
      });
      continue;
    }

    if (action.type === "add_issue") {
      const issueTitle = action.issueTitle ?? action.title;
      if (!issueTitle?.trim()) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Issue title is missing.",
        });
        continue;
      }

      addIssue(aquariumId, issueTitle.trim());
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Issue created",
      });
      continue;
    }

    if (action.type === "add_memo") {
      const memoContent = action.memoContent ?? action.description;
      if (!memoContent?.trim()) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Memo content is missing.",
        });
        continue;
      }

      addMemo(aquariumId, memoContent.trim());
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Memo added",
      });
      continue;
    }

    if (action.type === "add_livestock") {
      const livestockName = action.livestockName ?? action.title;
      if (
        !action.livestockKind ||
        !livestockName?.trim() ||
        !action.species?.trim() ||
        !action.quantity ||
        action.quantity <= 0
      ) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Livestock fields are incomplete.",
        });
        continue;
      }

      addLivestock({
        aquariumId,
        kind: action.livestockKind,
        name: livestockName.trim(),
        species: action.species.trim(),
        quantity: action.quantity,
        acquiredAt: action.setupDate?.trim() || new Date().toISOString(),
        purchasePrice: action.price,
        dietaryNotes: action.description,
      });
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Livestock added",
      });
      continue;
    }

    if (action.type === "add_asset") {
      if (!action.assetCategory || !action.brandModel?.trim()) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Asset category/brandModel is missing.",
        });
        continue;
      }

      addAsset({
        aquariumId,
        category: action.assetCategory,
        brandModel: action.brandModel.trim(),
        purchasedAt: action.purchasedAt?.trim() || undefined,
        price: action.price,
      });
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Asset added",
      });
      continue;
    }

    if (action.type === "add_consumable") {
      if (
        !action.consumableName?.trim() ||
        !action.consumableUnit ||
        action.remaining === undefined ||
        action.remaining < 0
      ) {
        results.push({
          actionId: action.id,
          actionType: action.type,
          created: false,
          reason: "Consumable fields are incomplete.",
        });
        continue;
      }

      addConsumable({
        aquariumId,
        name: action.consumableName.trim(),
        unit: action.consumableUnit,
        remaining: action.remaining,
        reorderAt: action.reorderAt,
      });
      createdCount += 1;
      results.push({
        actionId: action.id,
        actionType: action.type,
        created: true,
        summary: "Consumable added",
      });
      continue;
    }

    results.push({
      actionId: action.id,
      actionType: action.type,
      created: false,
      reason: "Unsupported action.",
    });
  }

  return {
    createdCount,
    skippedCount: results.filter((item) => !item.created).length,
    results,
  };
}
