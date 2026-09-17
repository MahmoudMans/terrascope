export type RegionalActivityBand =
  | "QUIET"
  | "GUARDED"
  | "ELEVATED"
  | "HIGH"
  | "CRITICAL";

export type RegionalHazardType =
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

export type RegionalHazardPoint = {
  id: string;
  hazardType: RegionalHazardType;
  title: string;
  latitude: number;
  longitude: number;
  intensity: number | null;
  observedAt: string;
};

export type RegionalAnalysisRequest = {
  regionName: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
  points: RegionalHazardPoint[];
};

export type RegionalHazardBreakdown = {
  earthquakeCount: number;
  volcanoCount: number;
  wildfireCount: number;
  severeStormCount: number;
};

export type RegionalNearestEvent = {
  id: string;
  hazardType: RegionalHazardType;
  title: string;
  distanceKm: number;
};

export type RegionalAnalysisResult = {
  id: string;
  regionName: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
  sourcePointCount: number;
  matchedEventCount: number;
  breakdown: RegionalHazardBreakdown;
  nearestEvent: RegionalNearestEvent | null;
  averageDistanceKm: number;
  activityIndex: number;
  activityBand: RegionalActivityBand;
  analyzedAt: string;
};

export type RegionalAnalysisHistoryResponse = {
  totalCount: number;
  criticalCount: number;
  highCount: number;
  averageActivityIndex: number;
  runs: RegionalAnalysisResult[];
};
