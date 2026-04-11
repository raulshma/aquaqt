import {
  Aquarium,
  Asset,
  Consumable,
  DosingLog,
  EntityKind,
  EntityRef,
  Issue,
  Livestock,
  Memo,
  TaskExecution,
  TaskTemplate,
  TimelineEvent,
  WaterParameterLog,
} from "@/types/aquapt";
import { isTaskDue } from "@/services/scheduling";

export interface AquaptEntityStore {
  aquariums: Aquarium[];
  taskTemplates: TaskTemplate[];
  taskExecutions: TaskExecution[];
  livestock: Livestock[];
  assets: Asset[];
  consumables: Consumable[];
  dosingLogs: DosingLog[];
  parameterLogs: WaterParameterLog[];
  issues: Issue[];
  memos: Memo[];
  timeline: TimelineEvent[];
}

export interface AquariumEntityCollections {
  aquarium: Aquarium;
  taskTemplates: TaskTemplate[];
  dueTaskTemplates: TaskTemplate[];
  taskExecutions: TaskExecution[];
  livestock: Livestock[];
  assets: Asset[];
  consumables: Consumable[];
  dosingLogs: DosingLog[];
  parameterLogs: WaterParameterLog[];
  issues: Issue[];
  memos: Memo[];
  timeline: TimelineEvent[];
}

type EntityItem =
  | Aquarium
  | TaskTemplate
  | Livestock
  | Asset
  | Consumable
  | Issue
  | Memo
  | DosingLog
  | WaterParameterLog;

export interface ResolvedEntityRef {
  ref: EntityRef;
  aquarium?: Aquarium;
  item?: EntityItem;
  title: string;
  subtitle?: string;
}

const ENTITY_KINDS: EntityKind[] = [
  "aquarium",
  "task",
  "livestock",
  "asset",
  "consumable",
  "issue",
  "memo",
  "dosing",
  "parameter-log",
];

export function createEntityRef(
  kind: EntityKind,
  id: string,
  aquariumId?: string,
): EntityRef {
  return aquariumId ? { kind, id, aquariumId } : { kind, id };
}

export function getEntityHref(ref: EntityRef) {
  return `/entity/${encodeURIComponent(ref.kind)}/${encodeURIComponent(ref.id)}`;
}

export function parseEntityKind(value: string | string[] | undefined) {
  const normalized = Array.isArray(value) ? value[0] : value;
  if (!normalized) {
    return null;
  }

  return ENTITY_KINDS.includes(normalized as EntityKind)
    ? (normalized as EntityKind)
    : null;
}

export function normalizeEntityRef(value: unknown): EntityRef | undefined {
  if (!value || typeof value !== "object") {
    return undefined;
  }

  const candidate = value as Partial<EntityRef>;
  if (
    typeof candidate.kind !== "string" ||
    !ENTITY_KINDS.includes(candidate.kind as EntityKind) ||
    typeof candidate.id !== "string" ||
    candidate.id.trim().length === 0
  ) {
    return undefined;
  }

  return {
    kind: candidate.kind as EntityKind,
    id: candidate.id,
    aquariumId:
      typeof candidate.aquariumId === "string" && candidate.aquariumId.trim()
        ? candidate.aquariumId
        : undefined,
  };
}

export function normalizeTimelineEvent(event: TimelineEvent): TimelineEvent {
  return {
    ...event,
    source: normalizeEntityRef(event.source),
    related: Array.isArray(event.related)
      ? event.related
          .map((entry) => normalizeEntityRef(entry))
          .filter((entry): entry is EntityRef => !!entry)
      : undefined,
  };
}

export function normalizeTimelineEvents(events: TimelineEvent[]) {
  return events.map((event) => normalizeTimelineEvent(event));
}

export function getTimelineEventTarget(event: TimelineEvent): EntityRef {
  return (
    normalizeEntityRef(event.source) ??
    createEntityRef("aquarium", event.aquariumId, event.aquariumId)
  );
}

export function entityRefEquals(left: EntityRef, right: EntityRef) {
  return left.kind === right.kind && left.id === right.id;
}

export function resolveEntityRef(
  store: AquaptEntityStore,
  ref: EntityRef,
): ResolvedEntityRef | null {
  const aquariumById = store.aquariums.find((entry) => entry.id === ref.id);

  if (ref.kind === "aquarium") {
    if (!aquariumById) {
      return null;
    }

    return {
      ref,
      aquarium: aquariumById,
      item: aquariumById,
      title: aquariumById.name,
      subtitle: `${aquariumById.volumeLiters}L • ${aquariumById.waterType}`,
    };
  }

  const fallbackAquariumId = ref.aquariumId;
  const aquarium =
    (fallbackAquariumId
      ? store.aquariums.find((entry) => entry.id === fallbackAquariumId)
      : undefined) ?? undefined;

  switch (ref.kind) {
    case "task": {
      const item = store.taskTemplates.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium,
        item,
        title: item.title,
        subtitle: item.frequency,
      };
    }
    case "livestock": {
      const item = store.livestock.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.name,
        subtitle: item.species,
      };
    }
    case "asset": {
      const item = store.assets.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.brandModel,
        subtitle: item.category,
      };
    }
    case "consumable": {
      const item = store.consumables.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.name,
        subtitle: `${item.remaining}${item.unit} remaining`,
      };
    }
    case "issue": {
      const item = store.issues.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.title,
        subtitle: item.status,
      };
    }
    case "memo": {
      const item = store.memos.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.content.slice(0, 60) || "Memo",
        subtitle: new Date(item.createdAt).toLocaleString(),
      };
    }
    case "dosing": {
      const item = store.dosingLogs.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: item.product,
        subtitle: `${item.amountMl}ml`,
      };
    }
    case "parameter-log": {
      const item = store.parameterLogs.find((entry) => entry.id === ref.id);
      if (!item) {
        return null;
      }

      return {
        ref,
        aquarium:
          aquarium ??
          store.aquariums.find((entry) => entry.id === item.aquariumId),
        item,
        title: "Water parameters",
        subtitle: new Date(item.createdAt).toLocaleString(),
      };
    }
    default:
      return null;
  }
}

export function getAquariumCollections(
  store: AquaptEntityStore,
  aquariumId: string,
  now = new Date(),
): AquariumEntityCollections | null {
  const aquarium = store.aquariums.find((entry) => entry.id === aquariumId);
  if (!aquarium) {
    return null;
  }

  const taskTemplates = store.taskTemplates.filter((task) =>
    task.aquariumIds.includes(aquariumId),
  );

  return {
    aquarium,
    taskTemplates,
    dueTaskTemplates: taskTemplates.filter((task) =>
      isTaskDue(task, aquariumId, store.taskExecutions, now),
    ),
    taskExecutions: store.taskExecutions.filter(
      (entry) => entry.aquariumId === aquariumId,
    ),
    livestock: store.livestock.filter((entry) => entry.aquariumId === aquariumId),
    assets: store.assets.filter((entry) => entry.aquariumId === aquariumId),
    consumables: store.consumables.filter(
      (entry) => entry.aquariumId === aquariumId,
    ),
    dosingLogs: store.dosingLogs.filter((entry) => entry.aquariumId === aquariumId),
    parameterLogs: store.parameterLogs.filter(
      (entry) => entry.aquariumId === aquariumId,
    ),
    issues: store.issues.filter((entry) => entry.aquariumId === aquariumId),
    memos: store.memos.filter((entry) => entry.aquariumId === aquariumId),
    timeline: store.timeline.filter((entry) => entry.aquariumId === aquariumId),
  };
}

export function getRelatedTimelineEvents(
  store: AquaptEntityStore,
  ref: EntityRef,
) {
  return store.timeline.filter((event) => {
    const source = normalizeEntityRef(event.source);
    if (source && entityRefEquals(source, ref)) {
      return true;
    }

    return (
      Array.isArray(event.related) &&
      event.related.some((candidate) => entityRefEquals(candidate, ref))
    );
  });
}

export function getTaskExecutionHistory(
  store: AquaptEntityStore,
  taskTemplateId: string,
) {
  return store.taskExecutions
    .filter((entry) => entry.taskTemplateId === taskTemplateId)
    .sort((left, right) => +new Date(right.completedAt) - +new Date(left.completedAt));
}

export function getLivestockOffspring(
  store: AquaptEntityStore,
  parentId: string,
) {
  return store.livestock.filter((entry) => entry.parentId === parentId);
}

export function getLivestockFeedingTasks(
  store: AquaptEntityStore,
  livestockId: string,
) {
  return store.taskTemplates.filter((entry) => entry.livestockId === livestockId);
}
