export type WaterType = "freshwater" | "marine" | "brackish";

export type IssueStatus = "open" | "monitoring" | "resolved";

export type TaskFrequency = "daily" | "weekly" | "bi-weekly" | "monthly";

export type TimelineEventType =
  | "task"
  | "parameter"
  | "issue"
  | "livestock"
  | "memo"
  | "dosing"
  | "asset"
  | "consumable";

export interface Aquarium {
  id: string;
  name: string;
  volumeLiters: number;
  dimensions: string;
  waterType: WaterType;
  setupDate: string;
  investmentCost?: number;
}

export interface WaterParameters {
  ammonia?: number;
  nitrite?: number;
  nitrate?: number;
  ph?: number;
  temperatureC?: number;
  gh?: number;
  kh?: number;
  salinity?: number;
  calcium?: number;
  alkalinity?: number;
}

export interface WaterParameterLog {
  id: string;
  aquariumId: string;
  createdAt: string;
  values: WaterParameters;
}

export interface Livestock {
  id: string;
  aquariumId: string;
  kind: "fish" | "shrimp" | "snail" | "coral" | "plant" | "other";
  name: string;
  species: string;
  quantity: number;
  acquiredAt: string;
  purchasePrice?: number;
  photoUri?: string;
  dietaryNotes?: string;
  parentId?: string;
  status?: "active" | "ill" | "deceased";
}

export interface DosingLog {
  id: string;
  aquariumId: string;
  product: string;
  amountMl: number;
  createdAt: string;
  note?: string;
}

export interface Asset {
  id: string;
  aquariumId: string;
  category: "filter" | "heater" | "light" | "co2" | "other";
  brandModel: string;
  purchasedAt?: string;
  price?: number;
  maintenanceTaskTemplateIds?: string[];
}

export interface Consumable {
  id: string;
  aquariumId: string;
  name: string;
  unit: "g" | "ml" | "pcs";
  remaining: number;
  reorderAt?: number;
  updatedAt: string;
}

export interface TaskTemplate {
  id: string;
  title: string;
  description?: string;
  frequency: TaskFrequency;
  aquariumIds: string[];
}

export interface TaskExecution {
  id: string;
  taskTemplateId: string;
  aquariumId: string;
  completedAt: string;
  note?: string;
}

export interface Issue {
  id: string;
  aquariumId: string;
  title: string;
  status: IssueStatus;
  createdAt: string;
  resolutionNote?: string;
}

export interface Memo {
  id: string;
  aquariumId: string;
  content: string;
  createdAt: string;
}

export interface TimelineEvent {
  id: string;
  aquariumId: string;
  type: TimelineEventType;
  createdAt: string;
  title: string;
  description?: string;
}

export interface AppSettings {
  openRouterApiKey: string;
  aiModel: string;
}
