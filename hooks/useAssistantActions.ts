import { useCallback, useMemo } from "react";
import type { AssistantConversation, AssistantDetectedAction } from "@/types/assistant";
import { useAquapt } from "@/context/aquapt-context";
import { executeApprovedActions } from "@/services/assistant-executor";
import { validateAction, applyActionDefaults } from "@/utils/assistant-validation";
import { nowId } from "@/utils/assistant-constants";
import type { TaskFrequency, IssueStatus, WaterType, LivestockKind, LivestockStatus, AssetCategory, ConsumableUnit } from "@/types/aquapt";

interface UseAssistantActionsProps {
  activeConversation: AssistantConversation | undefined;
  updateConversation: (
    convId: string,
    updater: (c: AssistantConversation) => AssistantConversation,
  ) => void;
}

interface ActionUpdatePayload {
  title?: string;
  frequency?: TaskFrequency;
  description?: string;
  aquariumId?: string;
  aquariumName?: string;
  approved?: boolean;
  taskTemplateId?: string;
  taskTitle?: string;
  product?: string;
  amountMl?: number;
  note?: string;
  issueTitle?: string;
  memoContent?: string;
  reminderEnabled?: boolean;
  reminderHour?: number;
  reminderHours?: number[];
  waterType?: WaterType;
  volumeLiters?: number;
  dimensions?: string;
  setupDate?: string;
  investmentCost?: number;
  targetAquariumId?: string;
  targetAquariumName?: string;
  livestockId?: string;
  livestockName?: string;
  species?: string;
  quantity?: number;
  livestockKind?: LivestockKind;
  livestockStatus?: LivestockStatus;
  issueId?: string;
  issueStatus?: IssueStatus;
  resolutionNote?: string;
  assetCategory?: AssetCategory;
  brandModel?: string;
  purchasedAt?: string;
  price?: number;
  consumableId?: string;
  consumableName?: string;
  consumableUnit?: ConsumableUnit;
  remaining?: number;
  reorderAt?: number;
  amountUsed?: number;
}

interface UseAssistantActionsReturn {
  activeDetectedActions: AssistantDetectedAction[];
  activeWarnings: string[];
  approvedActionCount: number;
  approvedSelectionCount: number;
  approvedInvalidActionCount: number;
  updateAction: (actionId: string, updates: ActionUpdatePayload) => void;
  updateParameterField: (
    actionId: string,
    key: string,
    value: string,
  ) => void;
  executeApprovedActionBatch: () => void;
}

export function useAssistantActions({
  activeConversation,
  updateConversation,
}: UseAssistantActionsProps): UseAssistantActionsReturn {
  const {
    aquariums,
    taskTemplates,
    livestock,
    issues,
    consumables,
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
  } = useAquapt();

  const hasMultipleAquariums = aquariums.length > 1;

  const withValidation = useCallback(
    (next: AssistantDetectedAction[]) =>
      next.map((action) => {
        const singleAquariumId =
          aquariums.length === 1 ? aquariums[0]?.id : undefined;
        const actionWithDefaults = applyActionDefaults(
          action,
          singleAquariumId,
        );
        return {
          ...actionWithDefaults,
          validationErrors: validateAction(
            actionWithDefaults,
            hasMultipleAquariums,
          ),
        };
      }),
    [aquariums, hasMultipleAquariums],
  );

  const activeDetectedActions = useMemo(
    () => activeConversation?.detectedActions ?? [],
    [activeConversation?.detectedActions],
  );

  const activeWarnings = useMemo(
    () => activeConversation?.warnings ?? [],
    [activeConversation?.warnings],
  );

  const approvedActionCount = useMemo(
    () =>
      activeDetectedActions.filter(
        (a) => a.approved && a.validationErrors.length === 0,
      ).length,
    [activeDetectedActions],
  );

  const approvedSelectionCount = useMemo(
    () => activeDetectedActions.filter((a) => a.approved).length,
    [activeDetectedActions],
  );

  const approvedInvalidActionCount = useMemo(
    () =>
      activeDetectedActions.filter(
        (a) => a.approved && a.validationErrors.length > 0,
      ).length,
    [activeDetectedActions],
  );

  const updateAction = useCallback(
    (actionId: string, updates: ActionUpdatePayload) => {
      if (!activeConversation) return;
      updateConversation(activeConversation.id, (c) => ({
        ...c,
        detectedActions: withValidation(
          c.detectedActions.map((a) =>
            a.id === actionId ? { ...a, ...updates } : a,
          ),
        ),
      }));
    },
    [activeConversation, updateConversation, withValidation],
  );

  const updateParameterField = useCallback(
    (actionId: string, key: string, value: string) => {
      if (!activeConversation) return;
      const parsed = Number(value);
      updateConversation(activeConversation.id, (c) => ({
        ...c,
        detectedActions: withValidation(
          c.detectedActions.map((action) => {
            if (action.id !== actionId) return action;
            const nextParameters = { ...(action.parameters ?? {}) };
            if (Number.isFinite(parsed)) {
              nextParameters[key as keyof typeof nextParameters] = parsed;
            } else {
              delete nextParameters[key as keyof typeof nextParameters];
            }
            return {
              ...action,
              parameters:
                Object.keys(nextParameters).length > 0
                  ? nextParameters
                  : undefined,
            };
          }),
        ),
      }));
    },
    [activeConversation, updateConversation, withValidation],
  );

  const executeApprovedActionBatch = useCallback(() => {
    if (!activeConversation) return;
    const result = executeApprovedActions({
      actions: activeDetectedActions,
      aquariums,
      existingTaskTemplates: taskTemplates,
      existingLivestock: livestock,
      existingIssues: issues,
      existingConsumables: consumables,
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
    });

    const feedbackParts: string[] = [];
    if (result.createdCount > 0)
      feedbackParts.push(`Executed ${result.createdCount} action(s).`);
    if (result.skippedCount > 0)
      feedbackParts.push(`Skipped ${result.skippedCount} action(s).`);

    const details = result.results
      .filter((item) => !item.created && item.reason)
      .slice(0, 5)
      .map((item) => `• ${item.actionType}: ${item.reason}`)
      .join("\n");

    const systemMsg = {
      id: nowId("msg"),
      role: "system" as const,
      content: [feedbackParts.join(" "), details].filter(Boolean).join("\n"),
      createdAt: new Date().toISOString(),
    };

    const createdIds = new Set(
      result.results.filter((e) => e.created).map((e) => e.actionId),
    );

    updateConversation(activeConversation.id, (c) => ({
      ...c,
      messages: [...c.messages, systemMsg],
      detectedActions: c.detectedActions.filter((a) => !createdIds.has(a.id)),
      updatedAt: new Date().toISOString(),
    }));
  }, [
    activeConversation,
    activeDetectedActions,
    aquariums,
    taskTemplates,
    livestock,
    issues,
    consumables,
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
    updateConversation,
  ]);

  return {
    activeDetectedActions,
    activeWarnings,
    approvedActionCount,
    approvedSelectionCount,
    approvedInvalidActionCount,
    updateAction,
    updateParameterField,
    executeApprovedActionBatch,
  };
}
