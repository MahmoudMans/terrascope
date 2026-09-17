export type VolcanoStatus =
  | "open"
  | "closed"
  | "all";

export type VolcanoEvent = {
  id: string;
  title: string;
  description: string;
  observedAt: string;
  longitude: number;
  latitude: number;
  observationCount: number;
  sourceCount: number;
  status: string;
  primarySource: string;
  sourceUrl: string;
  detailUrl: string;
};

export type VolcanoSummary = {
  eventCount: number;
  totalObservationCount: number;
  sourceReferenceCount: number;
  latestObservationAt: string | null;
};

export type VolcanoFeedResponse = {
  source: string;
  live: boolean;
  generatedAt: string;
  status: VolcanoStatus;
  days: number;
  search: string;
  sourceEventCount: number;
  matchedEventCount: number;
  returnedEventCount: number;
  summary: VolcanoSummary;
  events: VolcanoEvent[];
};

export type VolcanoQuery = {
  status: VolcanoStatus;
  days: number;
  limit?: number;
  search?: string;
};
