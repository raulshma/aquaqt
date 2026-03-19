import { describe, expect, test } from "bun:test";

import {
    applyRegionalDefaults,
    getCurrencyForCountry,
    listRegionalCountryOptions,
    listSupportedCurrencyCodes,
    resolveManualRegionalSettings,
} from "./localization";

describe("localization", () => {
  test("maps India to INR", () => {
    expect(getCurrencyForCountry("IN")).toBe("INR");
  });

  test("exposes country options for picker UI", () => {
    const options = listRegionalCountryOptions();

    expect(options.length).toBeGreaterThan(100);
    expect(options.some((option) => option.code === "IN")).toBe(true);
    expect(options.some((option) => option.currency === "USD")).toBe(true);
  });

  test("exposes currency options for picker UI", () => {
    const options = listSupportedCurrencyCodes();

    expect(options.includes("USD")).toBe(true);
    expect(options.includes("INR")).toBe(true);
    expect(options).toEqual([...options].sort());
  });

  test("resolves a manual India override from country name", () => {
    const resolved = resolveManualRegionalSettings({
      country: "India",
      currency: "",
    });

    expect(resolved.ok).toBe(true);
    if (!resolved.ok) {
      return;
    }

    expect(resolved.value).toEqual({
      defaultCountryCode: "IN",
      defaultCountryName: "India",
      defaultCurrency: "INR",
    });
  });

  test("keeps a manually chosen currency when country is overridden", () => {
    const resolved = resolveManualRegionalSettings({
      country: "India",
      currency: "usd",
    });

    expect(resolved.ok).toBe(true);
    if (!resolved.ok) {
      return;
    }

    expect(resolved.value.defaultCurrency).toBe("USD");
  });

  test("rejects invalid currency overrides", () => {
    const resolved = resolveManualRegionalSettings({
      country: "India",
      currency: "rupees",
    });

    expect(resolved.ok).toBe(false);
    if (resolved.ok) {
      return;
    }

    expect(resolved.message).toBe("Enter a valid 3-letter currency code.");
  });

  test("applies manual regional settings without clobbering them on hydrate", () => {
    const settings = applyRegionalDefaults({
      openRouterApiKey: "",
      aiModel: "test-model",
      regionalPreferencesMode: "manual",
      defaultCountryName: "India",
      defaultCurrency: "USD",
    });

    expect(settings.regionalPreferencesMode).toBe("manual");
    expect(settings.defaultCountryCode).toBe("IN");
    expect(settings.defaultCountryName).toBe("India");
    expect(settings.defaultCurrency).toBe("USD");
  });
});
