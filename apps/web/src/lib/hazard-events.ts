import type { EarthquakeEvent } from "@/types/earthquake";
import type { NaturalHazardEvent } from "@/types/natural-hazard";
import type { VolcanoEvent } from "@/types/volcano";

export type HazardKind =
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

type UnifiedEventBase = {
  key: string;
  title: string;
  occurredAt: string;
  latitude: number;
  longitude: number;
  detail: string;
  intensity: number | null;
};

export type UnifiedHazardEvent =
  | (UnifiedEventBase & {
      kind: "earthquakes";
      source: EarthquakeEvent;
    })
  | (UnifiedEventBase & {
      kind: "volcanoes";
      source: VolcanoEvent;
    })
  | (UnifiedEventBase & {
      kind: "wildfires" | "severe-storms";
      source: NaturalHazardEvent;
    });

export const HAZARD_KINDS: HazardKind[] = [
  "earthquakes",
  "volcanoes",
  "wildfires",
  "severe-storms",
];

export const HAZARD_LABELS: Record<HazardKind, string> = {
  earthquakes: "Earthquake",
  volcanoes: "Volcano",
  wildfires: "Wildfire",
  "severe-storms": "Severe storm",
};

export const HAZARD_PLURAL_LABELS: Record<HazardKind, string> = {
  earthquakes: "Earthquakes",
  volcanoes: "Volcanoes",
  wildfires: "Wildfires",
  "severe-storms": "Storms",
};

export function timestampOf(isoValue: string): number {
  const value = new Date(isoValue).getTime();

  return Number.isNaN(value) ? 0 : value;
}

function naturalDetail(event: NaturalHazardEvent): string {
  if (event.magnitudeValue !== null) {
    return `${event.magnitudeValue} ${event.magnitudeUnit}`.trim();
  }

  return event.primarySource;
}

export function buildUnifiedEvents(
  earthquakes: EarthquakeEvent[],
  volcanoes: VolcanoEvent[],
  wildfires: NaturalHazardEvent[],
  storms: NaturalHazardEvent[],
): UnifiedHazardEvent[] {
  const events: UnifiedHazardEvent[] = [];

  for (const event of earthquakes) {
    events.push({
      key: `earthquakes:${event.id}`,
      kind: "earthquakes",
      title: event.place,
      occurredAt: event.occurredAt,
      latitude: event.latitude,
      longitude: event.longitude,
      detail: `M${event.magnitude.toFixed(1)} · ${event.depthKm.toFixed(1)} km deep`,
      intensity: event.magnitude,
      source: event,
    });
  }

  for (const event of volcanoes) {
    events.push({
      key: `volcanoes:${event.id}`,
      kind: "volcanoes",
      title: event.title,
      occurredAt: event.observedAt,
      latitude: event.latitude,
      longitude: event.longitude,
      detail: event.primarySource,
      intensity: null,
      source: event,
    });
  }

  for (const event of wildfires) {
    events.push({
      key: `wildfires:${event.id}`,
      kind: "wildfires",
      title: event.title,
      occurredAt: event.observedAt,
      latitude: event.latitude,
      longitude: event.longitude,
      detail: naturalDetail(event),
      intensity: event.magnitudeValue,
      source: event,
    });
  }

  for (const event of storms) {
    events.push({
      key: `severe-storms:${event.id}`,
      kind: "severe-storms",
      title: event.title,
      occurredAt: event.observedAt,
      latitude: event.latitude,
      longitude: event.longitude,
      detail: naturalDetail(event),
      intensity: event.magnitudeValue,
      source: event,
    });
  }

  return events.sort(
    (left, right) =>
      timestampOf(right.occurredAt) - timestampOf(left.occurredAt),
  );
}

export type DayGroup = {
  dayKey: string;
  label: string;
  items: UnifiedHazardEvent[];
};

const dayLabelFormat = new Intl.DateTimeFormat("en-GB", {
  weekday: "short",
  day: "numeric",
  month: "short",
});

function localDayKey(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${date.getFullYear()}-${month}-${day}`;
}

export function groupByDay(
  events: UnifiedHazardEvent[],
  now: Date = new Date(),
): DayGroup[] {
  const today = localDayKey(now);
  const yesterdayDate = new Date(now);
  yesterdayDate.setDate(yesterdayDate.getDate() - 1);
  const yesterday = localDayKey(yesterdayDate);

  const groups: DayGroup[] = [];
  const byKey = new Map<string, DayGroup>();

  for (const event of events) {
    const timestamp = timestampOf(event.occurredAt);
    const date = new Date(timestamp);
    const dayKey = timestamp === 0 ? "unknown" : localDayKey(date);

    let group = byKey.get(dayKey);

    if (group === undefined) {
      const label =
        dayKey === "unknown"
          ? "Unknown date"
          : dayKey === today
            ? "Today"
            : dayKey === yesterday
              ? "Yesterday"
              : dayLabelFormat.format(date);

      group = { dayKey, label, items: [] };
      byKey.set(dayKey, group);
      groups.push(group);
    }

    group.items.push(event);
  }

  return groups;
}

type Bounds = {
  south: number;
  north: number;
  west: number;
  east: number;
};

export type HazardRegion = {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
};

type BoundedRegion = HazardRegion & {
  bounds: Bounds | null;
};

const REGION_TABLE: BoundedRegion[] = [
  {
    id: "antarctica",
    name: "Antarctica & Southern Ocean",
    latitude: -78,
    longitude: 0,
    radiusKm: 3500,
    bounds: { south: -90, north: -60, west: -180, east: 180 },
  },
  {
    id: "arctic",
    name: "Arctic",
    latitude: 76,
    longitude: 0,
    radiusKm: 3000,
    bounds: { south: 66, north: 90, west: -180, east: 180 },
  },
  {
    id: "south-america",
    name: "South America",
    latitude: -15,
    longitude: -60,
    radiusKm: 3500,
    bounds: { south: -57, north: 13, west: -81, east: -34 },
  },
  {
    id: "central-america",
    name: "Central America & Caribbean",
    latitude: 15,
    longitude: -80,
    radiusKm: 2200,
    bounds: { south: 5, north: 24, west: -118, east: -58 },
  },
  {
    id: "north-america-west",
    name: "Western North America",
    latitude: 42,
    longitude: -118,
    radiusKm: 2200,
    bounds: { south: 24, north: 66, west: -170, east: -100 },
  },
  {
    id: "north-america-east",
    name: "Eastern North America",
    latitude: 40,
    longitude: -85,
    radiusKm: 2200,
    bounds: { south: 24, north: 66, west: -100, east: -50 },
  },
  {
    id: "mediterranean",
    name: "Mediterranean & North Africa",
    latitude: 31,
    longitude: 10,
    radiusKm: 2500,
    bounds: { south: 18, north: 38, west: -18, east: 36 },
  },
  {
    id: "europe",
    name: "Europe",
    latitude: 50,
    longitude: 12,
    radiusKm: 2200,
    bounds: { south: 36, north: 66, west: -25, east: 40 },
  },
  {
    id: "sub-saharan-africa",
    name: "Sub-Saharan Africa",
    latitude: 0,
    longitude: 22,
    radiusKm: 3500,
    bounds: { south: -36, north: 18, west: -20, east: 52 },
  },
  {
    id: "middle-east",
    name: "Middle East",
    latitude: 30,
    longitude: 48,
    radiusKm: 2200,
    bounds: { south: 12, north: 42, west: 36, east: 63 },
  },
  {
    id: "south-asia",
    name: "South Asia",
    latitude: 23,
    longitude: 79,
    radiusKm: 2200,
    bounds: { south: 5, north: 36, west: 63, east: 93 },
  },
  {
    id: "southeast-asia",
    name: "Southeast Asia",
    latitude: 3,
    longitude: 112,
    radiusKm: 2800,
    bounds: { south: -11, north: 20, west: 93, east: 141 },
  },
  {
    id: "east-asia",
    name: "East Asia",
    latitude: 35,
    longitude: 112,
    radiusKm: 3000,
    bounds: { south: 20, north: 54, west: 93, east: 146 },
  },
  {
    id: "northern-asia",
    name: "Russia & Central Asia",
    latitude: 55,
    longitude: 90,
    radiusKm: 3500,
    bounds: { south: 36, north: 66, west: 40, east: 180 },
  },
  {
    id: "oceania",
    name: "Australia & Oceania",
    latitude: -25,
    longitude: 135,
    radiusKm: 3500,
    bounds: { south: -50, north: 0, west: 110, east: 180 },
  },
];

const PACIFIC_OCEAN: BoundedRegion = {
  id: "pacific-ocean",
  name: "Pacific Ocean",
  latitude: 0,
  longitude: -150,
  radiusKm: 5000,
  bounds: null,
};

const ATLANTIC_OCEAN: BoundedRegion = {
  id: "atlantic-ocean",
  name: "Atlantic Ocean",
  latitude: 0,
  longitude: -30,
  radiusKm: 4500,
  bounds: null,
};

const INDIAN_OCEAN: BoundedRegion = {
  id: "indian-ocean",
  name: "Indian Ocean",
  latitude: -15,
  longitude: 75,
  radiusKm: 4000,
  bounds: null,
};

function stripBounds(region: BoundedRegion): HazardRegion {
  return {
    id: region.id,
    name: region.name,
    latitude: region.latitude,
    longitude: region.longitude,
    radiusKm: region.radiusKm,
  };
}

export function regionOf(
  latitude: number,
  longitude: number,
): HazardRegion {
  for (const region of REGION_TABLE) {
    const bounds = region.bounds;

    if (
      bounds !== null
      && latitude >= bounds.south
      && latitude < bounds.north
      && longitude >= bounds.west
      && longitude < bounds.east
    ) {
      return stripBounds(region);
    }
  }

  if (longitude >= 140 || longitude <= -100) {
    return stripBounds(PACIFIC_OCEAN);
  }

  if (longitude > -100 && longitude < 20) {
    return stripBounds(ATLANTIC_OCEAN);
  }

  return stripBounds(INDIAN_OCEAN);
}

export type RegionSummary = {
  region: HazardRegion;
  events: UnifiedHazardEvent[];
  counts: Record<HazardKind, number>;
  strongestMagnitude: number | null;
  latestAt: string | null;
};

export function summarizeRegions(
  events: UnifiedHazardEvent[],
): RegionSummary[] {
  const byRegion = new Map<string, RegionSummary>();

  for (const event of events) {
    const region = regionOf(event.latitude, event.longitude);

    let summary = byRegion.get(region.id);

    if (summary === undefined) {
      summary = {
        region,
        events: [],
        counts: {
          earthquakes: 0,
          volcanoes: 0,
          wildfires: 0,
          "severe-storms": 0,
        },
        strongestMagnitude: null,
        latestAt: null,
      };

      byRegion.set(region.id, summary);
    }

    summary.events.push(event);
    summary.counts[event.kind] += 1;

    if (
      event.kind === "earthquakes"
      && event.intensity !== null
      && (
        summary.strongestMagnitude === null
        || event.intensity > summary.strongestMagnitude
      )
    ) {
      summary.strongestMagnitude = event.intensity;
    }

    if (
      summary.latestAt === null
      || timestampOf(event.occurredAt) > timestampOf(summary.latestAt)
    ) {
      summary.latestAt = event.occurredAt;
    }
  }

  return Array.from(byRegion.values());
}
