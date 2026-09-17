export type EarthquakePeriod =
  | "hour"
  | "day"
  | "week"
  | "month";

export type EarthquakeSort =
  | "time"
  | "magnitude"
  | "depth";

export type EarthquakeEvent = {
  id: string;
  magnitude: number;
  place: string;
  occurredAt: string;
  updatedAt: string;
  longitude: number;
  latitude: number;
  depthKm: number;
  significance: number;
  feltReports: number | null;
  tsunamiPotential: boolean;
  alertLevel: string;
  reviewStatus: string;
  detailUrl: string;
};

export type EarthquakeSummary = {
  eventCount: number;
  strongestMagnitude: number;
  averageDepthKm: number;
  significantEventCount: number;
  tsunamiPotentialCount: number;
  latestEventAt: string | null;
};

export type EarthquakeFeedResponse = {
  source: string;
  live: boolean;
  generatedAt: string;
  period: EarthquakePeriod;
  filters: {
    minMagnitude: number;
    maxDepthKm: number;
    limit: number;
    search: string;
    sort: EarthquakeSort;
  };
  sourceEventCount: number;
  matchedEventCount: number;
  returnedEventCount: number;
  summary: EarthquakeSummary;
  events: EarthquakeEvent[];
};

export type EarthquakeQuery = {
  period: EarthquakePeriod;
  minMagnitude: number;
  maxDepth: number;
  limit?: number;
  search?: string;
  sort: EarthquakeSort;
};
