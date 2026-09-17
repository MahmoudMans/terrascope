import type {
  RegionalHazardPoint,
} from "@/types/regional-intelligence";

export type AlertRuleHazardType =
  | "all"
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

export type AlertEventStatus =
  | "NEW"
  | "ACKNOWLEDGED"
  | "DISMISSED";

export type AlertRule = {
  id: string;
  name: string;
  hazardType: AlertRuleHazardType;
  latitude: number;
  longitude: number;
  radiusKm: number;
  minimumIntensity: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AlertEvent = {
  id: string;
  ruleId: string;
  ruleName: string;
  sourceEventId: string;
  hazardType:
    | "earthquakes"
    | "volcanoes"
    | "wildfires"
    | "severe-storms";
  title: string;
  latitude: number;
  longitude: number;
  intensity: number | null;
  observedAt: string;
  distanceKm: number;
  status: AlertEventStatus;
  triggeredAt: string;
  updatedAt: string;
};

export type AlertDashboardResponse = {
  ruleCount: number;
  enabledRuleCount: number;
  alertCount: number;
  newAlertCount: number;
  acknowledgedAlertCount: number;
  rules: AlertRule[];
  alerts: AlertEvent[];
};

export type CreateAlertRuleRequest = {
  name: string;
  hazardType: AlertRuleHazardType;
  latitude: number;
  longitude: number;
  radiusKm: number;
  minimumIntensity: number;
};

export type EvaluateAlertRulesRequest = {
  points: RegionalHazardPoint[];
};

export type AlertEvaluationResponse = {
  evaluatedRuleCount: number;
  evaluatedPointCount: number;
  newlyTriggeredCount: number;
  dashboard: AlertDashboardResponse;
};
