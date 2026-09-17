export type WatchPriority =
  | "LOW"
  | "MEDIUM"
  | "HIGH"
  | "CRITICAL";

export type WatchlistEntry = {
  id: string;
  sourceEventId: string;
  hazardType:
    | "earthquakes"
    | "volcanoes"
    | "wildfires"
    | "severe-storms";
  title: string;
  description: string;
  latitude: number;
  longitude: number;
  observedAt: string;
  sourceProvider: string;
  sourceUrl: string;
  magnitudeValue: number | null;
  magnitudeUnit: string;
  priority: WatchPriority;
  notes: string;
  createdAt: string;
  updatedAt: string;
};

export type WatchlistSummary = {
  totalCount: number;
  criticalCount: number;
  highCount: number;
  earthquakeCount: number;
  volcanoCount: number;
  wildfireCount: number;
  severeStormCount: number;
  latestSavedAt: string | null;
};

export type WatchlistFeedResponse = {
  summary: WatchlistSummary;
  entries: WatchlistEntry[];
};

export type SaveWatchlistEventRequest = {
  sourceEventId: string;
  hazardType:
    | "earthquakes"
    | "volcanoes"
    | "wildfires"
    | "severe-storms";
  title: string;
  description: string;
  latitude: number;
  longitude: number;
  observedAt: string;
  sourceProvider: string;
  sourceUrl: string;
  magnitudeValue: number | null;
  magnitudeUnit: string;
  priority: WatchPriority;
  notes: string;
};
