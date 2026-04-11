import { describe, expect, test } from "bun:test";

import {
  createEntityRef,
  getAquariumCollections,
  getEntityHref,
  getRelatedTimelineEvents,
  getTimelineEventTarget,
  normalizeTimelineEvent,
  resolveEntityRef,
  type AquaptEntityStore,
} from "./entity-links";

const store: AquaptEntityStore = {
  aquariums: [
    {
      id: "tank-1",
      name: "Living Room",
      volumeLiters: 120,
      dimensions: "90 x 45 x 45 cm",
      waterType: "freshwater",
      setupDate: "2025-08-20",
    },
  ],
  taskTemplates: [
    {
      id: "task-1",
      title: "Water change",
      frequency: "weekly",
      aquariumIds: ["tank-1"],
      livestockId: "live-1",
    },
  ],
  taskExecutions: [
    {
      id: "exec-1",
      taskTemplateId: "task-1",
      aquariumId: "tank-1",
      completedAt: "2026-03-01T10:00:00.000Z",
    },
  ],
  livestock: [
    {
      id: "live-1",
      aquariumId: "tank-1",
      kind: "fish",
      name: "Rummy Nose Tetra",
      species: "Hemigrammus bleheri",
      quantity: 10,
      acquiredAt: "2025-09-01",
      parentId: "live-parent",
      status: "active",
    },
    {
      id: "live-parent",
      aquariumId: "tank-1",
      kind: "fish",
      name: "Parent Fish",
      species: "Test species",
      quantity: 1,
      acquiredAt: "2025-08-01",
      status: "active",
    },
  ],
  assets: [
    {
      id: "asset-1",
      aquariumId: "tank-1",
      category: "filter",
      brandModel: "Oase Biomaster",
      maintenanceTaskTemplateIds: ["task-1"],
    },
  ],
  consumables: [
    {
      id: "cons-1",
      aquariumId: "tank-1",
      name: "Filter floss",
      unit: "pcs",
      remaining: 2,
      reorderAt: 4,
      updatedAt: "2026-03-10T00:00:00.000Z",
    },
  ],
  dosingLogs: [
    {
      id: "dose-1",
      aquariumId: "tank-1",
      product: "All in one",
      amountMl: 5,
      createdAt: "2026-03-16T06:30:00.000Z",
    },
  ],
  parameterLogs: [
    {
      id: "param-1",
      aquariumId: "tank-1",
      createdAt: "2026-03-16T07:00:00.000Z",
      values: { nitrate: 10, ph: 7.2 },
    },
  ],
  issues: [
    {
      id: "issue-1",
      aquariumId: "tank-1",
      title: "Cloudy water",
      status: "open",
      createdAt: "2026-03-14T08:00:00.000Z",
    },
  ],
  memos: [
    {
      id: "memo-1",
      aquariumId: "tank-1",
      content: "Observed healthy appetite.",
      createdAt: "2026-03-15T20:00:00.000Z",
    },
  ],
  timeline: [
    {
      id: "event-1",
      aquariumId: "tank-1",
      type: "task",
      createdAt: "2026-03-16T06:30:00.000Z",
      title: "Water change completed",
      source: createEntityRef("task", "task-1", "tank-1"),
      related: [createEntityRef("livestock", "live-1", "tank-1")],
    },
  ],
};

describe("entity-links", () => {
  test("builds stable entity hrefs", () => {
    expect(getEntityHref(createEntityRef("task", "task-1", "tank-1"))).toBe(
      "/entity/task/task-1",
    );
  });

  test("falls back legacy timeline events to aquarium detail", () => {
    const legacyEvent = normalizeTimelineEvent({
      id: "legacy",
      aquariumId: "tank-1",
      type: "memo",
      createdAt: "2026-03-16T06:30:00.000Z",
      title: "Legacy memo",
      source: { kind: "unknown" as never, id: "bad" },
      related: [{ kind: "unknown" as never, id: "bad" }],
    });

    expect(legacyEvent.source).toBeUndefined();
    expect(legacyEvent.related).toEqual([]);
    expect(getTimelineEventTarget(legacyEvent)).toEqual({
      kind: "aquarium",
      id: "tank-1",
      aquariumId: "tank-1",
    });
  });

  test("resolves refs and related timeline events", () => {
    const resolved = resolveEntityRef(
      store,
      createEntityRef("livestock", "live-1", "tank-1"),
    );

    expect(resolved?.title).toBe("Rummy Nose Tetra");
    expect(getRelatedTimelineEvents(store, createEntityRef("livestock", "live-1"))).toHaveLength(1);
  });

  test("computes aquarium-centered collections and due tasks", () => {
    const collections = getAquariumCollections(
      store,
      "tank-1",
      new Date("2026-03-19T00:00:00.000Z"),
    );

    expect(collections?.livestock).toHaveLength(2);
    expect(collections?.assets).toHaveLength(1);
    expect(collections?.dueTaskTemplates.map((task) => task.id)).toEqual(["task-1"]);
  });
});
