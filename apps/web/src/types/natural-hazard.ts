export type NaturalHazardType =
  | "wildfires"
  | "severe-storms";

export type NaturalHazardEvent = {
  id: string;
  hazardType: NaturalHazardType;
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
  magnitudeValue: number | null;
  magnitudeUnit: string;
  magnitudeDescription: string;
};

export type NaturalHazardSummary = {
  eventCount: number;
  totalObservationCount: number;
  sourceReferenceCount: number;
  magnitudeEventCount: number;
  latestObservationAt: string | null;
};

export type NaturalHazardFeedResponse = {
  hazardType: NaturalHazardType;
  source: string;
  live: boolean;
  generatedAt: string;
  status: string;
  days: number;
  search: string;
  sourceEventCount: number;
  matchedEventCount: number;
  returnedEventCount: number;
  summary: NaturalHazardSummary;
  events: NaturalHazardEvent[];
};

export type NaturalHazardQuery = {
  type: NaturalHazardType;
  status?: "open" | "closed" | "all";
  days?: number;
  limit?: number;
  search?: string;
};
