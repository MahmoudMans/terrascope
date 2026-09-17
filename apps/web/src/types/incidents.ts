export type IncidentSeverity =
  | "LOW"
  | "MODERATE"
  | "HIGH"
  | "SEVERE"
  | "CRITICAL";

export type IncidentStatus =
  | "OPEN"
  | "MONITORING"
  | "RESPONDING"
  | "STABILIZED"
  | "CLOSED";

export type IncidentCase = {
  id: string;
  alertEventId: string | null;
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
  severity: IncidentSeverity;
  status: IncidentStatus;
  owner: string;
  timelineEventCount: number;
  openedAt: string;
  updatedAt: string;
  closedAt: string | null;
};

export type IncidentTimelineEvent = {
  id: string;
  incidentId: string;
  eventType:
    | "INCIDENT_OPENED"
    | "STATUS_CHANGED"
    | "SEVERITY_CHANGED"
    | "OWNER_CHANGED"
    | "NOTE_ADDED";
  message: string;
  createdAt: string;
};

export type IncidentDetail = {
  incident: IncidentCase;
  timeline: IncidentTimelineEvent[];
};

export type IncidentDashboardResponse = {
  totalCount: number;
  activeCount: number;
  respondingCount: number;
  criticalCount: number;
  closedCount: number;
  incidents: IncidentCase[];
};

export type CreateIncidentRequest = {
  alertEventId: string | null;
  sourceEventId: string;
  hazardType: IncidentCase["hazardType"];
  title: string;
  description: string;
  latitude: number;
  longitude: number;
  severity: IncidentSeverity;
  owner: string;
};
