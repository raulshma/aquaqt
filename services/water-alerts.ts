import { Aquarium, WaterParameters, WaterType } from "@/types/aquapt";

type NumericRange = {
  min?: number;
  max?: number;
};

export type ParameterAlert = {
  key: keyof WaterParameters;
  label: string;
  value: number;
  unit: string;
  status: "low" | "high";
  min?: number;
  max?: number;
};

const PARAMETER_LABELS: Record<keyof WaterParameters, string> = {
  ammonia: "Ammonia",
  nitrite: "Nitrite",
  nitrate: "Nitrate",
  ph: "pH",
  temperatureC: "Temperature",
  gh: "GH",
  kh: "KH",
  salinity: "Salinity",
  calcium: "Calcium",
  alkalinity: "Alkalinity",
};

const PARAMETER_UNITS: Record<keyof WaterParameters, string> = {
  ammonia: "ppm",
  nitrite: "ppm",
  nitrate: "ppm",
  ph: "",
  temperatureC: "°C",
  gh: "dGH",
  kh: "dKH",
  salinity: "sg",
  calcium: "ppm",
  alkalinity: "dKH",
};

const RANGES_BY_WATER_TYPE: Record<
  WaterType,
  Partial<Record<keyof WaterParameters, NumericRange>>
> = {
  freshwater: {
    ammonia: { max: 0 },
    nitrite: { max: 0 },
    nitrate: { max: 25 },
    ph: { min: 6.5, max: 7.8 },
    temperatureC: { min: 22, max: 27 },
    gh: { min: 4, max: 12 },
    kh: { min: 2, max: 8 },
  },
  brackish: {
    ammonia: { max: 0 },
    nitrite: { max: 0 },
    nitrate: { max: 30 },
    ph: { min: 7.2, max: 8.4 },
    temperatureC: { min: 24, max: 28 },
    salinity: { min: 1.005, max: 1.02 },
  },
  marine: {
    ammonia: { max: 0 },
    nitrite: { max: 0 },
    nitrate: { max: 15 },
    ph: { min: 7.9, max: 8.4 },
    temperatureC: { min: 24, max: 27 },
    salinity: { min: 1.023, max: 1.026 },
    calcium: { min: 380, max: 460 },
    alkalinity: { min: 7, max: 11 },
  },
};

export function evaluateParameterAlerts(
  aquarium: Aquarium,
  values: WaterParameters,
): ParameterAlert[] {
  const ranges = RANGES_BY_WATER_TYPE[aquarium.waterType];
  const alerts: ParameterAlert[] = [];

  (Object.keys(ranges) as (keyof WaterParameters)[]).forEach((key) => {
    const range = ranges[key];
    if (!range) {
      return;
    }

    const value = values[key];
    if (typeof value !== "number" || Number.isNaN(value)) {
      return;
    }

    if (range.min !== undefined && value < range.min) {
      alerts.push({
        key,
        label: PARAMETER_LABELS[key],
        value,
        unit: PARAMETER_UNITS[key],
        status: "low",
        min: range.min,
        max: range.max,
      });
      return;
    }

    if (range.max !== undefined && value > range.max) {
      alerts.push({
        key,
        label: PARAMETER_LABELS[key],
        value,
        unit: PARAMETER_UNITS[key],
        status: "high",
        min: range.min,
        max: range.max,
      });
    }
  });

  return alerts;
}
