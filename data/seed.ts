import {
    Aquarium,
    Issue,
    Livestock,
    Memo,
    TaskExecution,
    TaskTemplate,
    TimelineEvent,
    WaterParameterLog,
} from "@/types/aquapt";

export const aquariumsSeed: Aquarium[] = [
  {
    id: "tank-1",
    name: "Living Room Planted",
    volumeLiters: 120,
    dimensions: "90 x 45 x 45 cm",
    waterType: "freshwater",
    setupDate: "2025-08-20",
    investmentCost: 980,
  },
  {
    id: "tank-2",
    name: "Nano Reef",
    volumeLiters: 60,
    dimensions: "60 x 35 x 35 cm",
    waterType: "marine",
    setupDate: "2025-11-04",
    investmentCost: 1450,
  },
];

export const livestockSeed: Livestock[] = [
  {
    id: "live-1",
    aquariumId: "tank-1",
    name: "Red Cherry Shrimp Colony",
    species: "Neocaridina davidi",
    quantity: 18,
    acquiredAt: "2025-09-10",
  },
  {
    id: "live-2",
    aquariumId: "tank-2",
    name: "Clownfish Pair",
    species: "Amphiprion ocellaris",
    quantity: 2,
    acquiredAt: "2025-11-10",
    purchasePrice: 90,
  },
];

export const taskTemplatesSeed: TaskTemplate[] = [
  {
    id: "task-1",
    title: "50% Water Change",
    description: "Siphon substrate and dose dechlorinator.",
    frequency: "weekly",
    aquariumIds: ["tank-1"],
  },
  {
    id: "task-2",
    title: "Clean Filter Intake",
    description: "Rinse intake sponge with tank water.",
    frequency: "bi-weekly",
    aquariumIds: ["tank-1", "tank-2"],
  },
  {
    id: "task-3",
    title: "Dose Trace Elements",
    description: "5ml all-in-one reef supplement.",
    frequency: "daily",
    aquariumIds: ["tank-2"],
  },
];

export const taskExecutionsSeed: TaskExecution[] = [
  {
    id: "exec-1",
    taskTemplateId: "task-1",
    aquariumId: "tank-1",
    completedAt: "2026-03-15T10:10:00.000Z",
  },
  {
    id: "exec-2",
    taskTemplateId: "task-3",
    aquariumId: "tank-2",
    completedAt: "2026-03-16T06:30:00.000Z",
  },
];

export const parameterLogsSeed: WaterParameterLog[] = [
  {
    id: "param-1",
    aquariumId: "tank-1",
    createdAt: "2026-03-16T07:00:00.000Z",
    values: {
      ammonia: 0,
      nitrite: 0,
      nitrate: 18,
      ph: 7.1,
      temperatureC: 24.8,
      gh: 7,
      kh: 4,
    },
  },
  {
    id: "param-2",
    aquariumId: "tank-2",
    createdAt: "2026-03-16T07:20:00.000Z",
    values: {
      ammonia: 0,
      nitrite: 0,
      nitrate: 6,
      ph: 8.1,
      temperatureC: 25.4,
      salinity: 1.025,
      calcium: 420,
      alkalinity: 8.3,
    },
  },
];

export const issuesSeed: Issue[] = [
  {
    id: "issue-1",
    aquariumId: "tank-1",
    title: "Green spot algae on front glass",
    status: "monitoring",
    createdAt: "2026-03-14T08:00:00.000Z",
  },
  {
    id: "issue-2",
    aquariumId: "tank-2",
    title: "Skimmer overflowing after dosing",
    status: "open",
    createdAt: "2026-03-16T08:30:00.000Z",
  },
];

export const memosSeed: Memo[] = [
  {
    id: "memo-1",
    aquariumId: "tank-1",
    content: "Observed berried female shrimp near moss wall.",
    createdAt: "2026-03-15T20:00:00.000Z",
  },
];

export const timelineSeed: TimelineEvent[] = [
  {
    id: "event-1",
    aquariumId: "tank-1",
    type: "parameter",
    createdAt: "2026-03-16T07:00:00.000Z",
    title: "Water parameters logged",
    description: "NH3 0, NO2 0, NO3 18, pH 7.1",
  },
  {
    id: "event-2",
    aquariumId: "tank-2",
    type: "task",
    createdAt: "2026-03-16T06:30:00.000Z",
    title: "Dose Trace Elements completed",
  },
  {
    id: "event-3",
    aquariumId: "tank-2",
    type: "issue",
    createdAt: "2026-03-16T08:30:00.000Z",
    title: "Issue reported",
    description: "Skimmer overflowing after dosing",
  },
];
