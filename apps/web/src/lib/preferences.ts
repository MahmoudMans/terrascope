import type { EarthquakePeriod } from "@/types/earthquake";

import { THEME_CHOICES, applyTheme, type ThemeChoice } from "@/lib/theme";

export type TextSize = "default" | "large" | "xlarge";

export type AutoRefreshMinutes = 0 | 1 | 5 | 15;

export type LayerPreferences = {
  earthquakes: boolean;
  volcanoes: boolean;
  wildfires: boolean;
  storms: boolean;
};

export type Preferences = {
  period: EarthquakePeriod;
  minMagnitude: number;
  maxDepth: number;
  layers: LayerPreferences;
  autoRefreshMinutes: AutoRefreshMinutes;
  textSize: TextSize;
  reduceMotion: boolean;
  liveUpdates: boolean;
  theme: ThemeChoice;
  shortcuts: boolean;
};

export type PreferencesPatch = Partial<Omit<Preferences, "layers">> & {
  layers?: Partial<LayerPreferences>;
};

export const PREFERENCES_STORAGE_KEY = "terrascope.preferences.v1";

export const PERIOD_CHOICES: Array<{
  value: EarthquakePeriod;
  label: string;
}> = [
  { value: "hour", label: "Past hour" },
  { value: "day", label: "Past 24h" },
  { value: "week", label: "Past 7d" },
  { value: "month", label: "Past 30d" },
];

export const MAGNITUDE_CHOICES: Array<{
  value: number;
  label: string;
}> = [
  { value: 0, label: "All magnitudes" },
  { value: 1, label: "M1.0 and above" },
  { value: 2.5, label: "M2.5 and above" },
  { value: 4, label: "M4.0 and above" },
  { value: 4.5, label: "M4.5 and above" },
  { value: 5, label: "M5.0 and above" },
  { value: 6, label: "M6.0 and above" },
];

export const DEPTH_CHOICES: Array<{
  value: number;
  label: string;
}> = [
  { value: 50, label: "Up to 50 km" },
  { value: 100, label: "Up to 100 km" },
  { value: 300, label: "Up to 300 km" },
  { value: 700, label: "Up to 700 km" },
  { value: 1000, label: "Up to 1000 km" },
];

export const AUTO_REFRESH_CHOICES: Array<{
  value: AutoRefreshMinutes;
  label: string;
}> = [
  { value: 0, label: "Off" },
  { value: 1, label: "1 min" },
  { value: 5, label: "5 min" },
  { value: 15, label: "15 min" },
];

export const TEXT_SIZE_CHOICES: Array<{
  value: TextSize;
  label: string;
}> = [
  { value: "default", label: "Default" },
  { value: "large", label: "Large" },
  { value: "xlarge", label: "Extra large" },
];

function prefersReducedMotion(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }

  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function createDefaultPreferences(): Preferences {
  return {
    period: "day",
    minMagnitude: 2.5,
    maxDepth: 700,
    layers: {
      earthquakes: true,
      volcanoes: true,
      wildfires: true,
      storms: true,
    },
    autoRefreshMinutes: 0,
    textSize: "default",
    reduceMotion: prefersReducedMotion(),
    liveUpdates: true,
    theme: "dark",
    shortcuts: true,
  };
}

function pick<T>(
  value: unknown,
  allowed: readonly T[],
  fallback: T,
): T {
  return allowed.includes(value as T) ? (value as T) : fallback;
}

function flag(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

export function sanitizePreferences(raw: unknown): Preferences {
  const defaults = createDefaultPreferences();

  if (typeof raw !== "object" || raw === null) {
    return defaults;
  }

  const record = raw as Record<string, unknown>;
  const layers =
    typeof record.layers === "object" && record.layers !== null
      ? (record.layers as Record<string, unknown>)
      : {};

  return {
    period: pick(
      record.period,
      PERIOD_CHOICES.map((choice) => choice.value),
      defaults.period,
    ),
    minMagnitude: pick(
      record.minMagnitude,
      MAGNITUDE_CHOICES.map((choice) => choice.value),
      defaults.minMagnitude,
    ),
    maxDepth: pick(
      record.maxDepth,
      DEPTH_CHOICES.map((choice) => choice.value),
      defaults.maxDepth,
    ),
    layers: {
      earthquakes: flag(layers.earthquakes, defaults.layers.earthquakes),
      volcanoes: flag(layers.volcanoes, defaults.layers.volcanoes),
      wildfires: flag(layers.wildfires, defaults.layers.wildfires),
      storms: flag(layers.storms, defaults.layers.storms),
    },
    autoRefreshMinutes: pick(
      record.autoRefreshMinutes,
      AUTO_REFRESH_CHOICES.map((choice) => choice.value),
      defaults.autoRefreshMinutes,
    ),
    textSize: pick(
      record.textSize,
      TEXT_SIZE_CHOICES.map((choice) => choice.value),
      defaults.textSize,
    ),
    reduceMotion: flag(record.reduceMotion, defaults.reduceMotion),
    liveUpdates: flag(record.liveUpdates, defaults.liveUpdates),
    theme: pick(
      record.theme,
      THEME_CHOICES.map((choice) => choice.value),
      defaults.theme,
    ),
    shortcuts: flag(record.shortcuts, defaults.shortcuts),
  };
}

export function readPreferences(): Preferences {
  try {
    const stored = window.localStorage.getItem(PREFERENCES_STORAGE_KEY);

    if (stored === null) {
      return createDefaultPreferences();
    }

    return sanitizePreferences(JSON.parse(stored));
  } catch {
    return createDefaultPreferences();
  }
}

export function writePreferences(preferences: Preferences): void {
  try {
    window.localStorage.setItem(
      PREFERENCES_STORAGE_KEY,
      JSON.stringify(preferences),
    );
  } catch {
    return;
  }
}

export function mergePreferences(
  current: Preferences,
  patch: PreferencesPatch,
): Preferences {
  return {
    ...current,
    ...patch,
    layers: { ...current.layers, ...patch.layers },
  };
}

export function applyDisplayPreferences(preferences: Preferences): void {
  const root = document.documentElement;

  root.dataset.textSize = preferences.textSize;
  root.dataset.motion = preferences.reduceMotion ? "reduced" : "full";

  applyTheme(preferences.theme);
}
