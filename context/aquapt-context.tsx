import {
    createContext,
    ReactNode,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";

import {
    aquariumsSeed,
    assetsSeed,
    consumablesSeed,
    dosingLogsSeed,
    issuesSeed,
    livestockSeed,
    memosSeed,
    parameterLogsSeed,
    taskExecutionsSeed,
    taskTemplatesSeed,
    timelineSeed,
} from "@/data/seed";
import {
    initPersistence,
    loadPersistedState,
    savePersistedState,
} from "@/services/persistence";
import {
    AppSettings,
    Aquarium,
    Asset,
    Consumable,
    DosingLog,
    Issue,
    Livestock,
    Memo,
    TaskExecution,
    TaskFrequency,
    TaskTemplate,
    TimelineEvent,
    WaterParameterLog,
    WaterParameters,
} from "@/types/aquapt";

const nowId = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

interface AquaptContextValue {
  isHydrated: boolean;
  aquariums: Aquarium[];
  livestock: Livestock[];
  taskTemplates: TaskTemplate[];
  taskExecutions: TaskExecution[];
  dosingLogs: DosingLog[];
  assets: Asset[];
  consumables: Consumable[];
  parameterLogs: WaterParameterLog[];
  issues: Issue[];
  memos: Memo[];
  timeline: TimelineEvent[];
  settings: AppSettings;
  livestockCountByAquarium: Record<string, number>;
  openIssuesByAquarium: Record<string, number>;
  addAquarium: (input: Omit<Aquarium, "id">) => void;
  editAquarium: (
    aquariumId: string,
    updates: Partial<Omit<Aquarium, "id">>,
  ) => void;
  addTaskTemplate: (input: {
    title: string;
    frequency: TaskFrequency;
    aquariumIds: string[];
    description?: string;
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
  logParameters: (aquariumId: string, values: WaterParameters) => void;
  addLivestock: (input: Omit<Livestock, "id">) => void;
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
  setLivestockFeedingNotes: (livestockId: string, dietaryNotes: string) => void;
  setLivestockStatus: (
    livestockId: string,
    status: NonNullable<Livestock["status"]>,
    note?: string,
  ) => void;
  addIssue: (aquariumId: string, title: string) => void;
  setIssueStatus: (
    issueId: string,
    status: Issue["status"],
    resolutionNote?: string,
  ) => void;
  addMemo: (
    aquariumId: string,
    content: string,
    photoUri?: string,
    createdAt?: string,
  ) => void;
  addAsset: (input: Omit<Asset, "id">) => void;
  addConsumable: (input: Omit<Consumable, "id" | "updatedAt">) => void;
  consumeConsumable: (
    consumableId: string,
    amountUsed: number,
    note?: string,
  ) => void;
  saveApiKey: (value: string) => void;
  saveAiModel: (value: string) => void;
}

const AquaptContext = createContext<AquaptContextValue | null>(null);

export function AquaptProvider({ children }: { children: ReactNode }) {
  const [isHydrated, setHydrated] = useState(false);
  const [aquariums, setAquariums] = useState(aquariumsSeed);
  const [livestock, setLivestock] = useState(livestockSeed);
  const [taskTemplates, setTaskTemplates] = useState(taskTemplatesSeed);
  const [taskExecutions, setTaskExecutions] = useState(taskExecutionsSeed);
  const [dosingLogs, setDosingLogs] = useState(dosingLogsSeed);
  const [assets, setAssets] = useState(assetsSeed);
  const [consumables, setConsumables] = useState(consumablesSeed);
  const [parameterLogs, setParameterLogs] = useState(parameterLogsSeed);
  const [issues, setIssues] = useState(issuesSeed);
  const [memos, setMemos] = useState(memosSeed);
  const [timeline, setTimeline] = useState(timelineSeed);
  const [settings, setSettings] = useState<AppSettings>({
    openRouterApiKey: "",
    aiModel: "openai/gpt-4o-mini",
  });
  const hasHydratedOnceRef = useRef(false);

  useEffect(() => {
    let isMounted = true;

    const bootstrap = async () => {
      try {
        await initPersistence();
        const persisted = await loadPersistedState();

        if (!isMounted || !persisted) {
          return;
        }

        setAquariums(persisted.aquariums ?? aquariumsSeed);
        setLivestock(persisted.livestock ?? livestockSeed);
        setTaskTemplates(persisted.taskTemplates ?? taskTemplatesSeed);
        setTaskExecutions(persisted.taskExecutions ?? taskExecutionsSeed);
        setDosingLogs(persisted.dosingLogs ?? dosingLogsSeed);
        setAssets(persisted.assets ?? assetsSeed);
        setConsumables(persisted.consumables ?? consumablesSeed);
        setParameterLogs(persisted.parameterLogs ?? parameterLogsSeed);
        setIssues(persisted.issues ?? issuesSeed);
        setMemos(persisted.memos ?? memosSeed);
        setTimeline(persisted.timeline ?? timelineSeed);
        setSettings(
          persisted.settings ?? {
            openRouterApiKey: "",
            aiModel: "openai/gpt-4o-mini",
          },
        );
      } catch (error) {
        console.warn("Persistence bootstrap failed", error);
      } finally {
        if (isMounted) {
          hasHydratedOnceRef.current = true;
          setHydrated(true);
        }
      }
    };

    bootstrap();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (!hasHydratedOnceRef.current) {
      return;
    }

    const persist = async () => {
      try {
        await savePersistedState({
          aquariums,
          livestock,
          taskTemplates,
          taskExecutions,
          dosingLogs,
          assets,
          consumables,
          parameterLogs,
          issues,
          memos,
          timeline,
          settings,
        });
      } catch (error) {
        console.warn("Persistence save failed", error);
      }
    };

    persist();
  }, [
    aquariums,
    livestock,
    taskTemplates,
    taskExecutions,
    dosingLogs,
    assets,
    consumables,
    issues,
    memos,
    parameterLogs,
    settings,
    timeline,
  ]);

  const livestockCountByAquarium = useMemo(() => {
    return livestock.reduce<Record<string, number>>((acc, item) => {
      acc[item.aquariumId] = (acc[item.aquariumId] ?? 0) + item.quantity;
      return acc;
    }, {});
  }, [livestock]);

  const openIssuesByAquarium = useMemo(() => {
    return issues.reduce<Record<string, number>>((acc, issue) => {
      if (issue.status !== "resolved") {
        acc[issue.aquariumId] = (acc[issue.aquariumId] ?? 0) + 1;
      }
      return acc;
    }, {});
  }, [issues]);

  const addAquarium = useCallback((input: Omit<Aquarium, "id">) => {
    setAquariums((prev) => [{ ...input, id: nowId("tank") }, ...prev]);
  }, []);

  const editAquarium = useCallback(
    (aquariumId: string, updates: Partial<Omit<Aquarium, "id">>) => {
      setAquariums((prev) =>
        prev.map((aq) => (aq.id === aquariumId ? { ...aq, ...updates } : aq)),
      );
    },
    [],
  );

  const addTaskTemplate = useCallback(
    (input: {
      title: string;
      frequency: TaskFrequency;
      aquariumIds: string[];
      description?: string;
    }) => {
      const task: TaskTemplate = {
        id: nowId("task"),
        title: input.title,
        frequency: input.frequency,
        aquariumIds: input.aquariumIds,
        description: input.description,
      };

      setTaskTemplates((prev) => [task, ...prev]);
    },
    [],
  );

  const completeTask = useCallback(
    (taskTemplateId: string, aquariumId: string, note?: string) => {
      const execution: TaskExecution = {
        id: nowId("exec"),
        taskTemplateId,
        aquariumId,
        completedAt: new Date().toISOString(),
        note,
      };

      const taskName =
        taskTemplates.find((task) => task.id === taskTemplateId)?.title ??
        "Task";

      setTaskExecutions((prev) => [execution, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId,
          type: "task",
          createdAt: execution.completedAt,
          title: `${taskName} completed`,
          description: note,
        },
        ...prev,
      ]);
    },
    [taskTemplates],
  );

  const logDosing = useCallback(
    (aquariumId: string, product: string, amountMl: number, note?: string) => {
      if (!product.trim() || !Number.isFinite(amountMl) || amountMl <= 0) {
        return;
      }

      const createdAt = new Date().toISOString();
      const dosing: DosingLog = {
        id: nowId("dose"),
        aquariumId,
        product: product.trim(),
        amountMl,
        createdAt,
        note,
      };

      setDosingLogs((prev) => [dosing, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId,
          type: "dosing",
          createdAt,
          title: `Dosed ${dosing.product}`,
          description: `${amountMl}ml${note ? ` • ${note}` : ""}`,
        },
        ...prev,
      ]);
    },
    [],
  );

  const logParameters = useCallback(
    (aquariumId: string, values: WaterParameters) => {
      const createdAt = new Date().toISOString();
      const log: WaterParameterLog = {
        id: nowId("param"),
        aquariumId,
        createdAt,
        values,
      };

      const summary = [
        values.ammonia !== undefined ? `NH3 ${values.ammonia}` : null,
        values.nitrite !== undefined ? `NO2 ${values.nitrite}` : null,
        values.nitrate !== undefined ? `NO3 ${values.nitrate}` : null,
        values.ph !== undefined ? `pH ${values.ph}` : null,
        values.temperatureC !== undefined
          ? `Temp ${values.temperatureC}°C`
          : null,
      ]
        .filter(Boolean)
        .join(", ");

      setParameterLogs((prev) => [log, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId,
          type: "parameter",
          createdAt,
          title: "Water parameters logged",
          description: summary,
        },
        ...prev,
      ]);
    },
    [],
  );

  const addIssue = useCallback((aquariumId: string, title: string) => {
    const createdAt = new Date().toISOString();
    const issue: Issue = {
      id: nowId("issue"),
      aquariumId,
      title,
      status: "open",
      createdAt,
    };

    setIssues((prev) => [issue, ...prev]);
    setTimeline((prev) => [
      {
        id: nowId("event"),
        aquariumId,
        type: "issue",
        createdAt,
        title: "Issue reported",
        description: title,
      },
      ...prev,
    ]);
  }, []);

  const setIssueStatus = useCallback(
    (issueId: string, status: Issue["status"], resolutionNote?: string) => {
      let affectedAquariumId = "";

      setIssues((prev) =>
        prev.map((issue) => {
          if (issue.id === issueId) {
            affectedAquariumId = issue.aquariumId;
            return {
              ...issue,
              status,
              resolutionNote: resolutionNote ?? issue.resolutionNote,
            };
          }

          return issue;
        }),
      );

      if (affectedAquariumId) {
        setTimeline((prev) => [
          {
            id: nowId("event"),
            aquariumId: affectedAquariumId,
            type: "issue",
            createdAt: new Date().toISOString(),
            title: `Issue moved to ${status}`,
            description: resolutionNote,
          },
          ...prev,
        ]);
      }
    },
    [],
  );

  const addMemo = useCallback(
    (
      aquariumId: string,
      content: string,
      photoUri?: string,
      createdAtInput?: string,
    ) => {
      const createdAt = createdAtInput ?? new Date().toISOString();
      const memo: Memo = {
        id: nowId("memo"),
        aquariumId,
        content,
        createdAt,
        photoUri,
      };

      setMemos((prev) => [memo, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId,
          type: "memo",
          createdAt,
          title: "Memo added",
          description: content,
          photoUri,
        },
        ...prev,
      ]);
    },
    [],
  );

  const saveApiKey = useCallback((value: string) => {
    setSettings((prev) => ({ ...prev, openRouterApiKey: value.trim() }));
  }, []);

  const saveAiModel = useCallback((value: string) => {
    setSettings((prev) => ({ ...prev, aiModel: value.trim() || prev.aiModel }));
  }, []);

  const addLivestock = useCallback((input: Omit<Livestock, "id">) => {
    const livestockItem: Livestock = {
      ...input,
      id: nowId("live"),
      status: input.status ?? "active",
    };

    setLivestock((prev) => [livestockItem, ...prev]);
    setTimeline((prev) => [
      {
        id: nowId("event"),
        aquariumId: livestockItem.aquariumId,
        type: "livestock",
        createdAt: new Date().toISOString(),
        title: "Livestock added",
        description: `${livestockItem.name} (${livestockItem.quantity})`,
      },
      ...prev,
    ]);
  }, []);

  const transferLivestock = useCallback(
    (livestockId: string, targetAquariumId: string, note?: string) => {
      let sourceAquariumId = "";
      let moved: Livestock | null = null;

      setLivestock((prev) =>
        prev.map((item) => {
          if (item.id !== livestockId) {
            return item;
          }

          sourceAquariumId = item.aquariumId;
          moved = { ...item, aquariumId: targetAquariumId };
          return moved;
        }),
      );

      if (moved && sourceAquariumId) {
        const sourceAquariumName =
          aquariums.find((aq) => aq.id === sourceAquariumId)?.name ??
          "source tank";
        const targetAquariumName =
          aquariums.find((aq) => aq.id === targetAquariumId)?.name ??
          "target tank";
        const createdAt = new Date().toISOString();

        setTimeline((prev) => [
          {
            id: nowId("event"),
            aquariumId: sourceAquariumId,
            type: "livestock",
            createdAt,
            title: `Transferred out ${moved?.name}`,
            description: `Moved to ${targetAquariumName}${note ? ` • ${note}` : ""}`,
          },
          {
            id: nowId("event"),
            aquariumId: targetAquariumId,
            type: "livestock",
            createdAt,
            title: `Transferred ${moved?.name}`,
            description: `From ${sourceAquariumName}${note ? ` • ${note}` : ""}`,
          },
          ...prev,
        ]);
      }
    },
    [aquariums],
  );

  const addOffspring = useCallback(
    (
      parentLivestockId: string,
      input: Omit<Livestock, "id" | "parentId" | "aquariumId"> & {
        aquariumId?: string;
      },
    ) => {
      const parent = livestock.find((item) => item.id === parentLivestockId);
      if (!parent) {
        return;
      }

      const createdAt = new Date().toISOString();
      const offspring: Livestock = {
        ...input,
        id: nowId("live"),
        parentId: parent.id,
        aquariumId: input.aquariumId ?? parent.aquariumId,
        acquiredAt: input.acquiredAt || createdAt,
      };

      setLivestock((prev) => [offspring, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId: offspring.aquariumId,
          type: "livestock",
          createdAt,
          title: "Offspring linked",
          description: `${offspring.name} linked to ${parent.name}`,
        },
        ...prev,
      ]);
    },
    [livestock],
  );

  const setLivestockFeedingNotes = useCallback(
    (livestockId: string, dietaryNotes: string) => {
      setLivestock((prev) =>
        prev.map((item) =>
          item.id === livestockId ? { ...item, dietaryNotes } : item,
        ),
      );
    },
    [],
  );

  const setLivestockStatus = useCallback(
    (
      livestockId: string,
      status: NonNullable<Livestock["status"]>,
      note?: string,
    ) => {
      let updatedAquariumId = "";
      let updatedName = "";

      setLivestock((prev) =>
        prev.map((item) => {
          if (item.id !== livestockId) {
            return item;
          }

          updatedAquariumId = item.aquariumId;
          updatedName = item.name;
          return { ...item, status };
        }),
      );

      if (updatedAquariumId) {
        setTimeline((prev) => [
          {
            id: nowId("event"),
            aquariumId: updatedAquariumId,
            type: "livestock",
            createdAt: new Date().toISOString(),
            title: `${updatedName} status: ${status}`,
            description: note,
          },
          ...prev,
        ]);
      }
    },
    [],
  );

  const addAsset = useCallback((input: Omit<Asset, "id">) => {
    const created: Asset = { ...input, id: nowId("asset") };
    setAssets((prev) => [created, ...prev]);
    setTimeline((prev) => [
      {
        id: nowId("event"),
        aquariumId: created.aquariumId,
        type: "asset",
        createdAt: new Date().toISOString(),
        title: "Asset registered",
        description: created.brandModel,
      },
      ...prev,
    ]);
  }, []);

  const addConsumable = useCallback(
    (input: Omit<Consumable, "id" | "updatedAt">) => {
      const createdAt = new Date().toISOString();
      const created: Consumable = {
        ...input,
        id: nowId("cons"),
        updatedAt: createdAt,
      };

      setConsumables((prev) => [created, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId: created.aquariumId,
          type: "consumable",
          createdAt,
          title: "Consumable tracked",
          description: `${created.name} (${created.remaining}${created.unit})`,
        },
        ...prev,
      ]);
    },
    [],
  );

  const consumeConsumable = useCallback(
    (consumableId: string, amountUsed: number, note?: string) => {
      if (!Number.isFinite(amountUsed) || amountUsed <= 0) {
        return;
      }

      let updated: Consumable | null = null;

      setConsumables((prev) =>
        prev.map((item) => {
          if (item.id !== consumableId) {
            return item;
          }

          updated = {
            ...item,
            remaining: Math.max(0, item.remaining - amountUsed),
            updatedAt: new Date().toISOString(),
          };

          return updated;
        }),
      );

      if (updated !== null) {
        const consumed = updated as Consumable;
        setTimeline((prev) => [
          {
            id: nowId("event"),
            aquariumId: consumed.aquariumId,
            type: "consumable",
            createdAt: new Date().toISOString(),
            title: `Used ${consumed.name}`,
            description: `${amountUsed}${consumed.unit}${note ? ` • ${note}` : ""}`,
          },
          ...prev,
        ]);
      }
    },
    [],
  );

  const value = useMemo<AquaptContextValue>(
    () => ({
      isHydrated,
      aquariums,
      livestock,
      taskTemplates,
      taskExecutions,
      dosingLogs,
      assets,
      consumables,
      parameterLogs,
      issues,
      memos,
      timeline,
      settings,
      livestockCountByAquarium,
      openIssuesByAquarium,
      addAquarium,
      editAquarium,
      addTaskTemplate,
      completeTask,
      logDosing,
      logParameters,
      addLivestock,
      transferLivestock,
      addOffspring,
      setLivestockFeedingNotes,
      setLivestockStatus,
      addIssue,
      setIssueStatus,
      addMemo,
      addAsset,
      addConsumable,
      consumeConsumable,
      saveApiKey,
      saveAiModel,
    }),
    [
      isHydrated,
      aquariums,
      livestock,
      taskTemplates,
      taskExecutions,
      dosingLogs,
      assets,
      consumables,
      parameterLogs,
      issues,
      memos,
      timeline,
      settings,
      livestockCountByAquarium,
      openIssuesByAquarium,
      addAquarium,
      editAquarium,
      addTaskTemplate,
      completeTask,
      logDosing,
      logParameters,
      addLivestock,
      transferLivestock,
      addOffspring,
      setLivestockFeedingNotes,
      setLivestockStatus,
      addIssue,
      setIssueStatus,
      addMemo,
      addAsset,
      addConsumable,
      consumeConsumable,
      saveApiKey,
      saveAiModel,
    ],
  );

  return (
    <AquaptContext.Provider value={value}>{children}</AquaptContext.Provider>
  );
}

export function useAquapt() {
  const context = useContext(AquaptContext);

  if (!context) {
    throw new Error("useAquapt must be used within AquaptProvider");
  }

  return context;
}
