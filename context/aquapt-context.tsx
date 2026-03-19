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
    initPersistence,
    loadPersistedState,
    PersistedAppState,
    savePersistedState,
} from "@/services/persistence";
import {
    createEntityRef,
    entityRefEquals,
    normalizeTimelineEvents,
} from "@/services/entity-links";
import {
  applyRegionalDefaults,
  resolveManualRegionalSettings,
} from "@/services/localization";
import {
    AppThemePreference,
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

const dedupeEntityRefs = (...refs: Array<ReturnType<typeof createEntityRef> | undefined>) =>
  refs.filter((ref): ref is ReturnType<typeof createEntityRef> => !!ref).reduce<
    ReturnType<typeof createEntityRef>[]
  >((acc, ref) => {
    if (acc.some((entry) => entityRefEquals(entry, ref))) {
      return acc;
    }

    acc.push(ref);
    return acc;
  }, []);

const aquariumRelatedRefs = (
  aquariumId: string,
  ...refs: Array<ReturnType<typeof createEntityRef> | undefined>
) => dedupeEntityRefs(createEntityRef("aquarium", aquariumId, aquariumId), ...refs);

const createDefaultSettings = (): AppSettings =>
  applyRegionalDefaults({
    openRouterApiKey: "",
    aiModel: "nvidia/nemotron-3-super-120b-a12b:free",
    notificationsEnabled: false,
    reminderHour: 8,
    assistantMemoryEnabled: true,
    themePreference: "system",
  });

const buildAppSettings = (persisted?: Partial<AppSettings>): AppSettings =>
  applyRegionalDefaults({
    openRouterApiKey: persisted?.openRouterApiKey ?? "",
    aiModel:
      persisted?.aiModel ?? "nvidia/nemotron-3-super-120b-a12b:free",
    notificationsEnabled: persisted?.notificationsEnabled ?? false,
    reminderHour: persisted?.reminderHour ?? 8,
    assistantMemoryEnabled: persisted?.assistantMemoryEnabled ?? true,
    themePreference: persisted?.themePreference ?? "system",
    regionalPreferencesMode: persisted?.regionalPreferencesMode,
    defaultLocale: persisted?.defaultLocale,
    defaultTimezone: persisted?.defaultTimezone,
    defaultCountryCode: persisted?.defaultCountryCode,
    defaultCountryName: persisted?.defaultCountryName,
    defaultCurrency: persisted?.defaultCurrency,
  });

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
    category?: "maintenance" | "feeding";
    livestockId?: string;
  }) => void;
  addLivestockFeedingTask: (input: {
    livestockId: string;
    title: string;
    frequency: TaskFrequency;
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
  exportAppState: () => string;
  importAppStateFromJson: (payload: string) => {
    ok: boolean;
    message: string;
  };
  saveReminderSettings: (input: {
    notificationsEnabled: boolean;
    reminderHour: number;
  }) => void;
  saveApiKey: (value: string) => void;
  saveAiModel: (value: string) => void;
  saveAssistantMemoryEnabled: (value: boolean) => void;
  saveThemePreference: (value: AppThemePreference) => void;
  saveRegionalPreferences: (input: {
    country: string;
    currency: string;
  }) => {
    ok: boolean;
    message: string;
  };
  resetRegionalPreferences: () => void;
}

const AquaptContext = createContext<AquaptContextValue | null>(null);

export function AquaptProvider({ children }: { children: ReactNode }) {
  const [isHydrated, setHydrated] = useState(false);
  const [aquariums, setAquariums] = useState<Aquarium[]>([]);
  const [livestock, setLivestock] = useState<Livestock[]>([]);
  const [taskTemplates, setTaskTemplates] = useState<TaskTemplate[]>([]);
  const [taskExecutions, setTaskExecutions] = useState<TaskExecution[]>([]);
  const [dosingLogs, setDosingLogs] = useState<DosingLog[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [consumables, setConsumables] = useState<Consumable[]>([]);
  const [parameterLogs, setParameterLogs] = useState<WaterParameterLog[]>([]);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [memos, setMemos] = useState<Memo[]>([]);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [settings, setSettings] = useState<AppSettings>(createDefaultSettings);
  const hasHydratedOnceRef = useRef(false);
  const persistTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let isMounted = true;

    const bootstrap = async () => {
      try {
        await initPersistence();
        const persisted = await loadPersistedState();

        if (!isMounted) {
          return;
        }

        if (!persisted) {
          return;
        }

        setAquariums(persisted.aquariums ?? []);
        setLivestock(persisted.livestock ?? []);
        setTaskTemplates(persisted.taskTemplates ?? []);
        setTaskExecutions(persisted.taskExecutions ?? []);
        setDosingLogs(persisted.dosingLogs ?? []);
        setAssets(persisted.assets ?? []);
        setConsumables(persisted.consumables ?? []);
        setParameterLogs(persisted.parameterLogs ?? []);
        setIssues(persisted.issues ?? []);
        setMemos(persisted.memos ?? []);
        setTimeline(normalizeTimelineEvents(persisted.timeline ?? []));
        setSettings(buildAppSettings(persisted.settings));
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

    if (persistTimeoutRef.current) {
      clearTimeout(persistTimeoutRef.current);
    }

    const stateSnapshot = {
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
    };

    persistTimeoutRef.current = setTimeout(() => {
      void (async () => {
        try {
          await savePersistedState(stateSnapshot);
        } catch (error) {
          console.warn("Persistence save failed", error);
        }
      })();
    }, 250);

    return () => {
      if (persistTimeoutRef.current) {
        clearTimeout(persistTimeoutRef.current);
        persistTimeoutRef.current = null;
      }
    };
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
      category?: "maintenance" | "feeding";
      livestockId?: string;
    }) => {
      const task: TaskTemplate = {
        id: nowId("task"),
        title: input.title,
        frequency: input.frequency,
        aquariumIds: input.aquariumIds,
        description: input.description,
        category: input.category,
        livestockId: input.livestockId,
      };

      setTaskTemplates((prev) => [task, ...prev]);
    },
    [],
  );

  const addLivestockFeedingTask = useCallback(
    (input: {
      livestockId: string;
      title: string;
      frequency: TaskFrequency;
      description?: string;
    }) => {
      const livestockItem = livestock.find(
        (item) => item.id === input.livestockId,
      );
      if (!livestockItem) {
        return;
      }

      addTaskTemplate({
        title: input.title,
        frequency: input.frequency,
        aquariumIds: [livestockItem.aquariumId],
        description: input.description,
        category: "feeding",
        livestockId: livestockItem.id,
      });
    },
    [addTaskTemplate, livestock],
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

      const taskTemplate = taskTemplates.find((task) => task.id === taskTemplateId);
      const taskName = taskTemplate?.title ?? "Task";

      setTaskExecutions((prev) => [execution, ...prev]);
      setTimeline((prev) => [
        {
          id: nowId("event"),
          aquariumId,
          type: "task",
          createdAt: execution.completedAt,
          title: `${taskName} completed`,
          description: note,
          source: createEntityRef("task", taskTemplateId, aquariumId),
          related: aquariumRelatedRefs(
            aquariumId,
            taskTemplate?.livestockId
              ? createEntityRef("livestock", taskTemplate.livestockId, aquariumId)
              : undefined,
          ),
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
          source: createEntityRef("dosing", dosing.id, aquariumId),
          related: aquariumRelatedRefs(aquariumId),
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
          source: createEntityRef("parameter-log", log.id, aquariumId),
          related: aquariumRelatedRefs(aquariumId),
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
        source: createEntityRef("issue", issue.id, aquariumId),
        related: aquariumRelatedRefs(aquariumId),
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
            source: createEntityRef("issue", issueId, affectedAquariumId),
            related: aquariumRelatedRefs(affectedAquariumId),
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
          source: createEntityRef("memo", memo.id, aquariumId),
          related: aquariumRelatedRefs(aquariumId),
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

  const saveReminderSettings = useCallback(
    (input: { notificationsEnabled: boolean; reminderHour: number }) => {
      const normalizedHour = Math.min(23, Math.max(0, input.reminderHour));

      setSettings((prev) => ({
        ...prev,
        notificationsEnabled: input.notificationsEnabled,
        reminderHour: normalizedHour,
      }));
    },
    [],
  );

  const saveAssistantMemoryEnabled = useCallback((value: boolean) => {
    setSettings((prev) => ({ ...prev, assistantMemoryEnabled: value }));
  }, []);

  const saveThemePreference = useCallback((value: AppThemePreference) => {
    setSettings((prev) => ({ ...prev, themePreference: value }));
  }, []);

  const saveRegionalPreferences = useCallback(
    (input: { country: string; currency: string }) => {
      const resolved = resolveManualRegionalSettings({
        country: input.country,
        currency: input.currency,
        fallbackCountryCode: settings.defaultCountryCode,
      });

      if (!resolved.ok) {
        return resolved;
      }

      setSettings((prev) =>
        applyRegionalDefaults({
          ...prev,
          ...resolved.value,
          regionalPreferencesMode: "manual",
        }),
      );

      return {
        ok: true,
        message: `Regional override saved: ${resolved.value.defaultCountryName} • ${resolved.value.defaultCurrency}.`,
      };
    },
    [settings.defaultCountryCode],
  );

  const resetRegionalPreferences = useCallback(() => {
    setSettings((prev) =>
      applyRegionalDefaults({
        ...prev,
        regionalPreferencesMode: "auto",
      }),
    );
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
        photoUri: livestockItem.photoUri,
        source: createEntityRef(
          "livestock",
          livestockItem.id,
          livestockItem.aquariumId,
        ),
        related: aquariumRelatedRefs(livestockItem.aquariumId),
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
            photoUri: moved?.photoUri,
            source: moved
              ? createEntityRef("livestock", moved.id, sourceAquariumId)
              : undefined,
            related: aquariumRelatedRefs(
              sourceAquariumId,
              createEntityRef("aquarium", targetAquariumId, targetAquariumId),
            ),
          },
          {
            id: nowId("event"),
            aquariumId: targetAquariumId,
            type: "livestock",
            createdAt,
            title: `Transferred ${moved?.name}`,
            description: `From ${sourceAquariumName}${note ? ` • ${note}` : ""}`,
            photoUri: moved?.photoUri,
            source: moved
              ? createEntityRef("livestock", moved.id, targetAquariumId)
              : undefined,
            related: aquariumRelatedRefs(
              targetAquariumId,
              createEntityRef("aquarium", sourceAquariumId, sourceAquariumId),
            ),
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
          photoUri: offspring.photoUri,
          source: createEntityRef("livestock", offspring.id, offspring.aquariumId),
          related: aquariumRelatedRefs(
            offspring.aquariumId,
            createEntityRef("livestock", parent.id, parent.aquariumId),
          ),
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
      let updatedPhotoUri = "";

      setLivestock((prev) =>
        prev.map((item) => {
          if (item.id !== livestockId) {
            return item;
          }

          updatedAquariumId = item.aquariumId;
          updatedName = item.name;
          updatedPhotoUri = item.photoUri ?? "";
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
            photoUri: updatedPhotoUri || undefined,
            source: createEntityRef("livestock", livestockId, updatedAquariumId),
            related: aquariumRelatedRefs(updatedAquariumId),
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
        photoUri: created.photoUri,
        source: createEntityRef("asset", created.id, created.aquariumId),
        related: aquariumRelatedRefs(
          created.aquariumId,
          ...(created.maintenanceTaskTemplateIds ?? []).map((taskTemplateId) =>
            createEntityRef("task", taskTemplateId, created.aquariumId),
          ),
        ),
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
          photoUri: created.photoUri,
          source: createEntityRef("consumable", created.id, created.aquariumId),
          related: aquariumRelatedRefs(created.aquariumId),
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
      let updatedPhotoUri = "";

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
          updatedPhotoUri = item.photoUri ?? "";

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
            photoUri: updatedPhotoUri || undefined,
            source: createEntityRef(
              "consumable",
              consumed.id,
              consumed.aquariumId,
            ),
            related: aquariumRelatedRefs(consumed.aquariumId),
          },
          ...prev,
        ]);
      }
    },
    [],
  );

  const exportAppState = useCallback(() => {
    const snapshot: PersistedAppState = {
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
    };

    return JSON.stringify(snapshot, null, 2);
  }, [
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
  ]);

  const importAppStateFromJson = useCallback((payload: string) => {
    const trimmed = payload.trim();
    if (!trimmed) {
      return { ok: false, message: "Backup payload is empty." };
    }

    try {
      const parsed = JSON.parse(trimmed) as Partial<PersistedAppState>;

      const nextState: PersistedAppState = {
        aquariums: Array.isArray(parsed.aquariums) ? parsed.aquariums : [],
        livestock: Array.isArray(parsed.livestock) ? parsed.livestock : [],
        taskTemplates: Array.isArray(parsed.taskTemplates)
          ? parsed.taskTemplates
          : [],
        taskExecutions: Array.isArray(parsed.taskExecutions)
          ? parsed.taskExecutions
          : [],
        dosingLogs: Array.isArray(parsed.dosingLogs) ? parsed.dosingLogs : [],
        assets: Array.isArray(parsed.assets) ? parsed.assets : [],
        consumables: Array.isArray(parsed.consumables)
          ? parsed.consumables
          : [],
        parameterLogs: Array.isArray(parsed.parameterLogs)
          ? parsed.parameterLogs
          : [],
        issues: Array.isArray(parsed.issues) ? parsed.issues : [],
        memos: Array.isArray(parsed.memos) ? parsed.memos : [],
        timeline: Array.isArray(parsed.timeline)
          ? normalizeTimelineEvents(parsed.timeline)
          : [],
        settings:
          parsed.settings && typeof parsed.settings === "object"
            ? buildAppSettings(parsed.settings as AppSettings)
            : createDefaultSettings(),
      };

      setAquariums(nextState.aquariums);
      setLivestock(nextState.livestock);
      setTaskTemplates(nextState.taskTemplates);
      setTaskExecutions(nextState.taskExecutions);
      setDosingLogs(nextState.dosingLogs);
      setAssets(nextState.assets);
      setConsumables(nextState.consumables);
      setParameterLogs(nextState.parameterLogs);
      setIssues(nextState.issues);
      setMemos(nextState.memos);
      setTimeline(normalizeTimelineEvents(nextState.timeline));
      setSettings(nextState.settings);

      return {
        ok: true,
        message: "Backup imported successfully. App state has been restored.",
      };
    } catch {
      return {
        ok: false,
        message: "Invalid JSON backup payload. Please check and try again.",
      };
    }
  }, []);

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
      addLivestockFeedingTask,
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
      exportAppState,
      importAppStateFromJson,
      saveReminderSettings,
      saveApiKey,
      saveAiModel,
      saveAssistantMemoryEnabled,
      saveThemePreference,
      saveRegionalPreferences,
      resetRegionalPreferences,
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
      addLivestockFeedingTask,
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
      exportAppState,
      importAppStateFromJson,
      saveReminderSettings,
      saveApiKey,
      saveAiModel,
      saveAssistantMemoryEnabled,
      saveThemePreference,
      saveRegionalPreferences,
      resetRegionalPreferences,
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
