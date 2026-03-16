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
    Issue,
    Memo,
    TaskExecution,
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
  taskTemplates: TaskTemplate[];
  taskExecutions: TaskExecution[];
  parameterLogs: WaterParameterLog[];
  issues: Issue[];
  memos: Memo[];
  timeline: TimelineEvent[];
  settings: AppSettings;
  livestockCountByAquarium: Record<string, number>;
  openIssuesByAquarium: Record<string, number>;
  completeTask: (
    taskTemplateId: string,
    aquariumId: string,
    note?: string,
  ) => void;
  logParameters: (aquariumId: string, values: WaterParameters) => void;
  addIssue: (aquariumId: string, title: string) => void;
  setIssueStatus: (
    issueId: string,
    status: Issue["status"],
    resolutionNote?: string,
  ) => void;
  addMemo: (aquariumId: string, content: string) => void;
  saveApiKey: (value: string) => void;
}

const AquaptContext = createContext<AquaptContextValue | null>(null);

export function AquaptProvider({ children }: { children: ReactNode }) {
  const [isHydrated, setHydrated] = useState(false);
  const [aquariums] = useState(aquariumsSeed);
  const [taskTemplates] = useState(taskTemplatesSeed);
  const [taskExecutions, setTaskExecutions] = useState(taskExecutionsSeed);
  const [parameterLogs, setParameterLogs] = useState(parameterLogsSeed);
  const [issues, setIssues] = useState(issuesSeed);
  const [memos, setMemos] = useState(memosSeed);
  const [timeline, setTimeline] = useState(timelineSeed);
  const [settings, setSettings] = useState<AppSettings>({
    openRouterApiKey: "",
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

        setTaskExecutions(persisted.taskExecutions ?? taskExecutionsSeed);
        setParameterLogs(persisted.parameterLogs ?? parameterLogsSeed);
        setIssues(persisted.issues ?? issuesSeed);
        setMemos(persisted.memos ?? memosSeed);
        setTimeline(persisted.timeline ?? timelineSeed);
        setSettings(persisted.settings ?? { openRouterApiKey: "" });
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
          taskExecutions,
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
  }, [issues, memos, parameterLogs, settings, taskExecutions, timeline]);

  const livestockCountByAquarium = useMemo(() => {
    return livestockSeed.reduce<Record<string, number>>((acc, item) => {
      acc[item.aquariumId] = (acc[item.aquariumId] ?? 0) + item.quantity;
      return acc;
    }, {});
  }, []);

  const openIssuesByAquarium = useMemo(() => {
    return issues.reduce<Record<string, number>>((acc, issue) => {
      if (issue.status !== "resolved") {
        acc[issue.aquariumId] = (acc[issue.aquariumId] ?? 0) + 1;
      }
      return acc;
    }, {});
  }, [issues]);

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

  const addMemo = useCallback((aquariumId: string, content: string) => {
    const createdAt = new Date().toISOString();
    const memo: Memo = {
      id: nowId("memo"),
      aquariumId,
      content,
      createdAt,
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
      },
      ...prev,
    ]);
  }, []);

  const saveApiKey = useCallback((value: string) => {
    setSettings((prev) => ({ ...prev, openRouterApiKey: value.trim() }));
  }, []);

  const value = useMemo<AquaptContextValue>(
    () => ({
      isHydrated,
      aquariums,
      taskTemplates,
      taskExecutions,
      parameterLogs,
      issues,
      memos,
      timeline,
      settings,
      livestockCountByAquarium,
      openIssuesByAquarium,
      completeTask,
      logParameters,
      addIssue,
      setIssueStatus,
      addMemo,
      saveApiKey,
    }),
    [
      isHydrated,
      aquariums,
      taskTemplates,
      taskExecutions,
      parameterLogs,
      issues,
      memos,
      timeline,
      settings,
      livestockCountByAquarium,
      openIssuesByAquarium,
      completeTask,
      logParameters,
      addIssue,
      setIssueStatus,
      addMemo,
      saveApiKey,
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
