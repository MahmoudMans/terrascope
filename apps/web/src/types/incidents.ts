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
    | "NOTE_ADDED"
    | "ACTION_CREATED"
    | "ACTION_STATUS_CHANGED"
    | "ACTION_PRIORITY_CHANGED"
    | "ACTION_OWNER_CHANGED"
    | "ACTION_DUE_CHANGED"
    | "ACTION_DELETED"
    | "RESOURCE_REQUESTED"
    | "RESOURCE_ALLOCATION_CHANGED"
    | "RESOURCE_PRIORITY_CHANGED"
    | "RESOURCE_OWNER_CHANGED"
    | "RESOURCE_CANCELLED"
    | "RESOURCE_DELETED"
    | "EVACUATION_ZONE_CREATED"
    | "EVACUATION_PROGRESS_CHANGED"
    | "EVACUATION_STATUS_CHANGED"
    | "EVACUATION_OWNER_CHANGED"
    | "EVACUATION_ZONE_DELETED"
    | "SHELTER_CREATED"
    | "SHELTER_OCCUPANCY_CHANGED"
    | "SHELTER_STATUS_CHANGED"
    | "SHELTER_OWNER_CHANGED"
    | "SHELTER_DELETED"
    | "LIFELINE_ASSET_CREATED"
    | "LIFELINE_STATUS_CHANGED"
    | "LIFELINE_IMPACT_CHANGED"
    | "LIFELINE_OWNER_CHANGED"
    | "LIFELINE_ETA_CHANGED"
    | "LIFELINE_ASSET_DELETED"
    | "DAMAGE_ASSESSMENT_CREATED"
    | "DAMAGE_VERIFIED"
    | "DAMAGE_SEVERITY_CHANGED"
    | "DAMAGE_PROGRESS_CHANGED"
    | "DAMAGE_OWNER_CHANGED"
    | "DAMAGE_ETA_CHANGED"
    | "DAMAGE_ASSESSMENT_DELETED";
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
