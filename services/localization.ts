import {
  type CountryCode,
  getAllCountries,
  getCountry,
  getCountryForTimezone,
} from "countries-and-timezones";

import { type AppSettings } from "@/types/aquapt";

const DEFAULT_COUNTRY_CODE: CountryCode = "US";
const DEFAULT_CURRENCY_CODE = "USD";
const DEFAULT_LOCALE = "en-US";
const DEFAULT_TIMEZONE = "UTC";
const DEFAULT_REGIONAL_PREFERENCES_MODE = "auto";
const FRANKFURTER_API_BASE_URL = "https://api.frankfurter.app";
const EXCHANGE_API_BASE_URL =
  "https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies";

const CURRENCY_COUNTRY_GROUPS: Record<string, CountryCode[]> = {
  AED: ["AE"],
  AFN: ["AF"],
  ALL: ["AL"],
  AMD: ["AM"],
  AOA: ["AO"],
  ARS: ["AR"],
  AUD: ["AU", "CC", "CX", "KI", "NF", "NR", "TV"],
  AWG: ["AW"],
  AZN: ["AZ"],
  BAM: ["BA"],
  BBD: ["BB"],
  BDT: ["BD"],
  BGN: ["BG"],
  BHD: ["BH"],
  BIF: ["BI"],
  BMD: ["BM"],
  BND: ["BN"],
  BOB: ["BO"],
  BRL: ["BR"],
  BSD: ["BS"],
  BTN: ["BT"],
  BWP: ["BW"],
  BYN: ["BY"],
  BZD: ["BZ"],
  CAD: ["CA"],
  CDF: ["CD"],
  CHF: ["CH", "LI"],
  CLP: ["CL"],
  CNY: ["CN"],
  COP: ["CO"],
  CRC: ["CR"],
  CUP: ["CU"],
  CVE: ["CV"],
  CZK: ["CZ"],
  DJF: ["DJ"],
  DKK: ["DK", "FO", "GL"],
  DOP: ["DO"],
  DZD: ["DZ"],
  EGP: ["EG"],
  ERN: ["ER"],
  ETB: ["ET"],
  EUR: [
    "AD",
    "AT",
    "AX",
    "BE",
    "BL",
    "CY",
    "DE",
    "EE",
    "ES",
    "FI",
    "FR",
    "GF",
    "GP",
    "GR",
    "HR",
    "IE",
    "IT",
    "LT",
    "LU",
    "LV",
    "MC",
    "ME",
    "MF",
    "MQ",
    "MT",
    "NL",
    "PM",
    "PT",
    "RE",
    "SI",
    "SK",
    "SM",
    "TF",
    "VA",
    "YT",
  ],
  FJD: ["FJ"],
  FKP: ["FK"],
  GBP: ["GB", "GG", "GS", "IM", "JE", "SH"],
  GEL: ["GE"],
  GHS: ["GH"],
  GIP: ["GI"],
  GMD: ["GM"],
  GNF: ["GN"],
  GTQ: ["GT"],
  GYD: ["GY"],
  HKD: ["HK"],
  HNL: ["HN"],
  HTG: ["HT"],
  HUF: ["HU"],
  IDR: ["ID"],
  ILS: ["IL", "PS"],
  INR: ["IN"],
  IQD: ["IQ"],
  IRR: ["IR"],
  ISK: ["IS"],
  JMD: ["JM"],
  JOD: ["JO"],
  JPY: ["JP"],
  KES: ["KE"],
  KGS: ["KG"],
  KHR: ["KH"],
  KMF: ["KM"],
  KPW: ["KP"],
  KRW: ["KR"],
  KWD: ["KW"],
  KYD: ["KY"],
  KZT: ["KZ"],
  LAK: ["LA"],
  LBP: ["LB"],
  LKR: ["LK"],
  LRD: ["LR"],
  LSL: ["LS"],
  LYD: ["LY"],
  MAD: ["EH", "MA"],
  MDL: ["MD"],
  MGA: ["MG"],
  MKD: ["MK"],
  MMK: ["MM"],
  MNT: ["MN"],
  MOP: ["MO"],
  MRU: ["MR"],
  MUR: ["MU"],
  MVR: ["MV"],
  MWK: ["MW"],
  MXN: ["MX"],
  MYR: ["MY"],
  MZN: ["MZ"],
  NAD: ["NA"],
  NGN: ["NG"],
  NIO: ["NI"],
  NOK: ["NO", "SJ"],
  NPR: ["NP"],
  NZD: ["CK", "NU", "NZ", "PN", "TK"],
  OMR: ["OM"],
  PEN: ["PE"],
  PGK: ["PG"],
  PHP: ["PH"],
  PKR: ["PK"],
  PLN: ["PL"],
  PYG: ["PY"],
  QAR: ["QA"],
  RON: ["RO"],
  RSD: ["RS"],
  RUB: ["RU"],
  RWF: ["RW"],
  SAR: ["SA"],
  SBD: ["SB"],
  SCR: ["SC"],
  SDG: ["SD"],
  SEK: ["SE"],
  SGD: ["SG"],
  SLE: ["SL"],
  SOS: ["SO"],
  SRD: ["SR"],
  SSP: ["SS"],
  STN: ["ST"],
  SYP: ["SY"],
  SZL: ["SZ"],
  THB: ["TH"],
  TJS: ["TJ"],
  TMT: ["TM"],
  TND: ["TN"],
  TOP: ["TO"],
  TRY: ["TR"],
  TTD: ["TT"],
  TWD: ["TW"],
  TZS: ["TZ"],
  UAH: ["UA"],
  UGX: ["UG"],
  USD: [
    "AS",
    "BQ",
    "EC",
    "FM",
    "GU",
    "IO",
    "MH",
    "MP",
    "PA",
    "PR",
    "PW",
    "SV",
    "TC",
    "TL",
    "UM",
    "US",
    "VG",
    "VI",
    "ZW",
  ],
  UYU: ["UY"],
  UZS: ["UZ"],
  VES: ["VE"],
  VND: ["VN"],
  VUV: ["VU"],
  WST: ["WS"],
  XAF: ["CF", "CG", "CM", "GA", "GQ", "TD"],
  XCD: ["AG", "AI", "DM", "GD", "KN", "LC", "MS", "VC"],
  XOF: ["BF", "BJ", "CI", "GW", "ML", "NE", "SN", "TG"],
  XPF: ["NC", "PF", "WF"],
  YER: ["YE"],
  ZAR: ["ZA"],
  ZMW: ["ZM"],
};

const COUNTRY_TO_CURRENCY = Object.entries(CURRENCY_COUNTRY_GROUPS).reduce<
  Partial<Record<CountryCode, string>>
>((map, [currency, countryCodes]) => {
  countryCodes.forEach((countryCode) => {
    map[countryCode] = currency;
  });

  return map;
}, {});

const exchangeRateCache = new Map<string, Promise<number>>();
const ALL_COUNTRIES = Object.values(getAllCountries());
const SUPPORTED_CURRENCY_CODES = Object.keys(CURRENCY_COUNTRY_GROUPS).sort();
const normalizeCountryName = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z]/g, "");

type RegionalDefaults = Pick<
  AppSettings,
  | "regionalPreferencesMode"
  | "defaultCountryCode"
  | "defaultCountryName"
  | "defaultCurrency"
  | "defaultLocale"
  | "defaultTimezone"
>;

export type RegionalCountryOption = {
  code: CountryCode;
  name: string;
  currency: string;
};

const normalizeCountryCode = (
  value?: string | null,
): CountryCode | undefined => {
  const normalized = value?.trim().toUpperCase();
  if (!normalized || normalized.length !== 2) {
    return undefined;
  }

  return normalized as CountryCode;
};

export const normalizeCurrencyCode = (value?: string | null) => {
  const normalized = value?.trim().toUpperCase();
  if (!normalized || normalized.length !== 3) {
    return undefined;
  }

  return normalized;
};

export function isSupportedCurrencyCode(value?: string | null) {
  const normalizedCurrencyCode = normalizeCurrencyCode(value);
  if (!normalizedCurrencyCode) {
    return false;
  }

  try {
    new Intl.NumberFormat(DEFAULT_LOCALE, {
      style: "currency",
      currency: normalizedCurrencyCode,
    }).format(1);
    return true;
  } catch {
    return false;
  }
}

export function findCountry(input?: string | null) {
  const normalizedCountryCode = normalizeCountryCode(input);
  if (normalizedCountryCode) {
    return getCountry(normalizedCountryCode) ?? null;
  }

  const normalizedName = input ? normalizeCountryName(input) : "";
  if (!normalizedName) {
    return null;
  }

  return (
    ALL_COUNTRIES.find(
      (country) => normalizeCountryName(country.name) === normalizedName,
    ) ?? null
  );
}

const getDeviceLocale = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().locale || DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
};

const getDeviceTimezone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIMEZONE;
  } catch {
    return DEFAULT_TIMEZONE;
  }
};

const getCountryCodeFromLocale = (locale: string) => {
  const match = locale.replace(/_/g, "-").match(/-([A-Za-z]{2})(?:-|$)/);
  return normalizeCountryCode(match?.[1]);
};

export function getCurrencyForCountry(countryCode?: string | null) {
  const normalizedCountryCode = normalizeCountryCode(countryCode);
  if (!normalizedCountryCode) {
    return DEFAULT_CURRENCY_CODE;
  }

  return COUNTRY_TO_CURRENCY[normalizedCountryCode] ?? DEFAULT_CURRENCY_CODE;
}

const REGIONAL_COUNTRY_OPTIONS = ALL_COUNTRIES.map((country) => ({
  code: country.id,
  name: country.name,
  currency: getCurrencyForCountry(country.id),
})).sort((a, b) => a.name.localeCompare(b.name));

export function listRegionalCountryOptions(): RegionalCountryOption[] {
  return REGIONAL_COUNTRY_OPTIONS;
}

export function listSupportedCurrencyCodes(): string[] {
  return SUPPORTED_CURRENCY_CODES;
}

export function resolveRegionalDefaults(): RegionalDefaults {
  const locale = getDeviceLocale();
  const timezone = getDeviceTimezone();
  const timezoneCountryCode = getCountryForTimezone(timezone)?.id;
  const localeCountryCode = getCountryCodeFromLocale(locale);
  const countryCode =
    normalizeCountryCode(timezoneCountryCode) ??
    localeCountryCode ??
    DEFAULT_COUNTRY_CODE;
  const countryName =
    getCountry(countryCode)?.name ??
    getCountry(DEFAULT_COUNTRY_CODE)?.name ??
    "United States of America";

  return {
    regionalPreferencesMode: DEFAULT_REGIONAL_PREFERENCES_MODE,
    defaultLocale: locale,
    defaultTimezone: timezone,
    defaultCountryCode: countryCode,
    defaultCountryName: countryName,
    defaultCurrency: getCurrencyForCountry(countryCode),
  };
}

export function resolveManualRegionalSettings(input: {
  country?: string | null;
  currency?: string | null;
  fallbackCountryCode?: string | null;
}) {
  const detected = resolveRegionalDefaults();
  const countryInput = input.country?.trim();
  const matchedCountry = countryInput
    ? findCountry(countryInput)
    : (findCountry(input.fallbackCountryCode) ??
      getCountry(detected.defaultCountryCode ?? DEFAULT_COUNTRY_CODE));

  if (countryInput && !matchedCountry) {
    return {
      ok: false as const,
      message: "Enter a valid country name or 2-letter country code.",
    };
  }

  const currencyInput = input.currency?.trim();
  if (currencyInput && !isSupportedCurrencyCode(currencyInput)) {
    return {
      ok: false as const,
      message: "Enter a valid 3-letter currency code.",
    };
  }

  const countryCode = matchedCountry?.id ?? detected.defaultCountryCode;
  const countryName = matchedCountry?.name ?? detected.defaultCountryName;

  return {
    ok: true as const,
    value: {
      defaultCountryCode: countryCode,
      defaultCountryName: countryName,
      defaultCurrency:
        normalizeCurrencyCode(currencyInput) ??
        getCurrencyForCountry(countryCode),
    },
  };
}

export function applyRegionalDefaults(settings: AppSettings): AppSettings {
  const detected = resolveRegionalDefaults();

  if (settings.regionalPreferencesMode !== "manual") {
    return {
      ...settings,
      ...detected,
      regionalPreferencesMode: DEFAULT_REGIONAL_PREFERENCES_MODE,
    };
  }

  const manual = resolveManualRegionalSettings({
    country: settings.defaultCountryCode ?? settings.defaultCountryName,
    currency: settings.defaultCurrency,
    fallbackCountryCode: detected.defaultCountryCode,
  });

  return {
    ...settings,
    ...detected,
    ...(manual.ok ? manual.value : {}),
    regionalPreferencesMode: "manual",
  };
}

export function formatCurrencyAmount(
  value: number,
  currencyCode = DEFAULT_CURRENCY_CODE,
  locale = DEFAULT_LOCALE,
  options: Intl.NumberFormatOptions = {},
) {
  try {
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: currencyCode.toUpperCase(),
      ...options,
    }).format(value);
  } catch {
    const maximumFractionDigits =
      typeof options.maximumFractionDigits === "number"
        ? options.maximumFractionDigits
        : 2;

    return `${currencyCode.toUpperCase()} ${value.toFixed(maximumFractionDigits)}`;
  }
}

const parseExchangeRate = (
  payload: unknown,
  fromCurrency: string,
  toCurrency: string,
) => {
  if (!payload || typeof payload !== "object") {
    throw new Error("Invalid exchange rate response");
  }

  const normalizedFrom = fromCurrency.toLowerCase();
  const normalizedTo = toCurrency.toLowerCase();
  const record = payload as Record<string, unknown>;
  const directRate = record[normalizedTo];

  if (typeof directRate === "number" && Number.isFinite(directRate)) {
    return directRate;
  }

  const nested = record[normalizedFrom];
  if (nested && typeof nested === "object") {
    const nestedRate = (nested as Record<string, unknown>)[normalizedTo];
    if (typeof nestedRate === "number" && Number.isFinite(nestedRate)) {
      return nestedRate;
    }
  }

  throw new Error(`Exchange rate not found for ${fromCurrency}/${toCurrency}`);
};

const parseFrankfurterRate = (payload: unknown, toCurrency: string) => {
  if (!payload || typeof payload !== "object") {
    throw new Error("Invalid Frankfurter exchange rate response");
  }

  const record = payload as Record<string, unknown>;
  const rates = record.rates;
  if (!rates || typeof rates !== "object") {
    throw new Error("Frankfurter exchange rates missing");
  }

  const normalizedTo = toCurrency.trim().toUpperCase();
  const rate = (rates as Record<string, unknown>)[normalizedTo];
  if (typeof rate === "number" && Number.isFinite(rate)) {
    return rate;
  }

  throw new Error(`Frankfurter rate not found for ${toCurrency}`);
};

async function getExchangeRate(fromCurrency: string, toCurrency: string) {
  const normalizedFrom = fromCurrency.trim().toLowerCase();
  const normalizedTo = toCurrency.trim().toLowerCase();
  const cacheKey = `${normalizedFrom}-${normalizedTo}`;

  if (!exchangeRateCache.has(cacheKey)) {
    exchangeRateCache.set(
      cacheKey,
      (async () => {
        try {
          const frankfurterResponse = await fetch(
            `${FRANKFURTER_API_BASE_URL}/latest?from=${normalizedFrom.toUpperCase()}&to=${normalizedTo.toUpperCase()}`,
          );

          if (!frankfurterResponse.ok) {
            throw new Error(
              `Failed to load Frankfurter exchange rate (${frankfurterResponse.status})`,
            );
          }

          const frankfurterPayload =
            (await frankfurterResponse.json()) as unknown;
          return parseFrankfurterRate(frankfurterPayload, normalizedTo);
        } catch {
          const fallbackResponse = await fetch(
            `${EXCHANGE_API_BASE_URL}/${normalizedFrom}/${normalizedTo}.json`,
          );

          if (!fallbackResponse.ok) {
            throw new Error(
              `Failed to load exchange rate (${fallbackResponse.status})`,
            );
          }

          const fallbackPayload = (await fallbackResponse.json()) as unknown;
          return parseExchangeRate(
            fallbackPayload,
            normalizedFrom,
            normalizedTo,
          );
        }
      })(),
    );
  }

  return exchangeRateCache.get(cacheKey)!;
}

export async function convertCurrencyAmount(
  value: number,
  fromCurrency: string,
  toCurrency: string,
) {
  const normalizedFrom = fromCurrency.trim().toUpperCase();
  const normalizedTo = toCurrency.trim().toUpperCase();

  if (normalizedFrom === normalizedTo) {
    return value;
  }

  const exchangeRate = await getExchangeRate(normalizedFrom, normalizedTo);
  return value * exchangeRate;
}
