import type {
  EarthquakeFeedResponse,
  EarthquakeQuery,
} from "@/types/earthquake";

import type {
  NaturalHazardFeedResponse,
  NaturalHazardQuery,
} from "@/types/natural-hazard";

import type {
  VolcanoFeedResponse,
  VolcanoQuery,
} from "@/types/volcano";

import type {
  WatchlistEntry,
  WatchlistFeedResponse,
  WatchPriority,
  SaveWatchlistEventRequest,
} from "@/types/watchlist";

import type {
  ImpactSimulationRequest,
  ImpactSimulationRunResponse,
  SimulationHistoryResponse,
} from "@/types/simulation";

import type {
  RegionalAnalysisHistoryResponse,
  RegionalAnalysisRequest,
  RegionalAnalysisResult,
} from "@/types/regional-intelligence";

import type {
  AlertDashboardResponse,
  AlertEvaluationResponse,
  AlertEvent,
  AlertEventStatus,
  AlertRule,
  CreateAlertRuleRequest,
  EvaluateAlertRulesRequest,
} from "@/types/alerts";

import type {
  CreateIncidentRequest,
  IncidentDashboardResponse,
  IncidentDetail,
  IncidentSeverity,
  IncidentStatus,
} from "@/types/incidents";

import type {
  CreateIncidentActionRequest,
  IncidentActionBoardResponse,
  IncidentActionPriority,
  IncidentActionStatus,
} from "@/types/incident-actions";

import type {
  CreateIncidentResourceRequest,
  IncidentResourceBoardResponse,
} from "@/types/incident-resources";

import type {
  CreateEvacuationZoneRequest,
  CreateShelterRequest,
  EvacuationOperationsBoardResponse,
  EvacuationZoneStatus,
  ShelterStatus,
} from "@/types/evacuation-operations";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8087/api/v1";

export async function fetchEarthquakes(
  query: EarthquakeQuery,
  signal?: AbortSignal,
): Promise<EarthquakeFeedResponse> {
  const params = new URLSearchParams({
    period: query.period,
    minMagnitude:
      query.minMagnitude.toString(),
    maxDepth:
      query.maxDepth.toString(),
    limit:
      (query.limit ?? 300).toString(),
    search:
      query.search ?? "",
    sort:
      query.sort,
  });

  const response = await fetch(
    `${API_BASE_URL}/earthquakes?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope earthquake API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EarthquakeFeedResponse>;
}

export async function fetchVolcanoes(
  query: VolcanoQuery,
  signal?: AbortSignal,
): Promise<VolcanoFeedResponse> {
  const params = new URLSearchParams({
    status: query.status,
    days: query.days.toString(),
    limit:
      (query.limit ?? 120).toString(),
    search:
      query.search ?? "",
  });

  const response = await fetch(
    `${API_BASE_URL}/volcanoes?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope volcano API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<VolcanoFeedResponse>;
}

export async function fetchNaturalHazards(
  query: NaturalHazardQuery,
  signal?: AbortSignal,
): Promise<NaturalHazardFeedResponse> {
  const params = new URLSearchParams({
    type: query.type,
    status: query.status ?? "open",
    days:
      (query.days ?? 90).toString(),
    limit:
      (query.limit ?? 120).toString(),
    search:
      query.search ?? "",
  });

  const response = await fetch(
    `${API_BASE_URL}/hazards/events?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope ${query.type} API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<NaturalHazardFeedResponse>;
}

export async function fetchWatchlist(
  signal?: AbortSignal,
): Promise<WatchlistFeedResponse> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope watchlist API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistFeedResponse>;
}

export async function saveWatchlistEvent(
  request: SaveWatchlistEventRequest,
): Promise<WatchlistEntry> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to save event. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistEntry>;
}

export async function updateWatchlistPriority(
  id: string,
  priority: WatchPriority,
): Promise<WatchlistEntry> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist/${id}/priority`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        priority,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update watch priority. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistEntry>;
}

export async function deleteWatchlistEntry(
  id: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist/${id}`,
    {
      method: "DELETE",
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete watchlist entry. HTTP ${response.status}`,
    );
  }
}

export async function fetchSimulationHistory(
  signal?: AbortSignal,
): Promise<SimulationHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/simulations`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope simulation history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<SimulationHistoryResponse>;
}

export async function runImpactSimulation(
  request: ImpactSimulationRequest,
): Promise<ImpactSimulationRunResponse> {
  const response = await fetch(
    `${API_BASE_URL}/simulations/run`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to run simulation. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImpactSimulationRunResponse>;
}

export async function analyzeRegionalIntelligence(
  request: RegionalAnalysisRequest,
): Promise<RegionalAnalysisResult> {
  const response = await fetch(
    `${API_BASE_URL}/regional-intelligence/analyze`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Regional analysis returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<RegionalAnalysisResult>;
}

export async function fetchRegionalAnalysisHistory(
  signal?: AbortSignal,
): Promise<RegionalAnalysisHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/regional-intelligence/history`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Regional history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<RegionalAnalysisHistoryResponse>;
}

export async function fetchAlertDashboard(
  signal?: AbortSignal,
): Promise<AlertDashboardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/alerts`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Alert dashboard returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AlertDashboardResponse>;
}

export async function createAlertRule(
  request: CreateAlertRuleRequest,
): Promise<AlertRule> {
  const response = await fetch(
    `${API_BASE_URL}/alerts/rules`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create alert rule. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AlertRule>;
}

export async function setAlertRuleEnabled(
  id: string,
  enabled: boolean,
): Promise<AlertRule> {
  const response = await fetch(
    `${API_BASE_URL}/alerts/rules/${id}/enabled?enabled=${enabled}`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update alert rule. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AlertRule>;
}

export async function deleteAlertRule(
  id: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/alerts/rules/${id}`,
    {
      method: "DELETE",
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete alert rule. HTTP ${response.status}`,
    );
  }
}

export async function evaluateAlertRules(
  request: EvaluateAlertRulesRequest,
): Promise<AlertEvaluationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/alerts/evaluate`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to evaluate alert rules. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AlertEvaluationResponse>;
}

export async function updateAlertEventStatus(
  id: string,
  status: AlertEventStatus,
): Promise<AlertEvent> {
  const response = await fetch(
    `${API_BASE_URL}/alerts/events/${id}/status`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update alert event. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AlertEvent>;
}

export async function fetchIncidentDashboard(
  signal?: AbortSignal,
): Promise<IncidentDashboardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Incident dashboard returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDashboardResponse>;
}

export async function fetchIncidentDetail(
  id: string,
  signal?: AbortSignal,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${id}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Incident detail returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function createIncident(
  request: CreateIncidentRequest,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create incident. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function updateIncidentStatus(
  id: string,
  status: IncidentStatus,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${id}/status`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update incident status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function updateIncidentSeverity(
  id: string,
  severity: IncidentSeverity,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${id}/severity`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        severity,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update incident severity. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function updateIncidentOwner(
  id: string,
  owner: string,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${id}/owner`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        owner,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update incident owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function addIncidentNote(
  id: string,
  message: string,
): Promise<IncidentDetail> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${id}/notes`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to add incident note. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentDetail>;
}

export async function fetchIncidentActionBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Incident action board returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function createIncidentAction(
  incidentId: string,
  request: CreateIncidentActionRequest,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create response action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function updateIncidentActionStatus(
  incidentId: string,
  actionId: string,
  status: IncidentActionStatus,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions/${actionId}/status`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update action status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function updateIncidentActionPriority(
  incidentId: string,
  actionId: string,
  priority: IncidentActionPriority,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions/${actionId}/priority`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        priority,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update action priority. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function updateIncidentActionOwner(
  incidentId: string,
  actionId: string,
  owner: string,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions/${actionId}/owner`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        owner,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update action owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function updateIncidentActionDueAt(
  incidentId: string,
  actionId: string,
  dueAt: string | null,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions/${actionId}/due-at`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        dueAt,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update action due date. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function deleteIncidentAction(
  incidentId: string,
  actionId: string,
): Promise<IncidentActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/actions/${actionId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete response action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentActionBoardResponse>;
}

export async function fetchIncidentResourceBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Incident resource board returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function createIncidentResource(
  incidentId: string,
  request: CreateIncidentResourceRequest,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create resource request. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function updateIncidentResourceAllocation(
  incidentId: string,
  resourceId: string,
  allocatedQuantity: number,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources/${resourceId}/allocation`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        allocatedQuantity,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update resource allocation. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function updateIncidentResourcePriority(
  incidentId: string,
  resourceId: string,
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources/${resourceId}/priority`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        priority,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update resource priority. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function updateIncidentResourceOwner(
  incidentId: string,
  resourceId: string,
  owner: string,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources/${resourceId}/owner`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        owner,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update resource owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function cancelIncidentResource(
  incidentId: string,
  resourceId: string,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources/${resourceId}/cancel`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to cancel resource request. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function deleteIncidentResource(
  incidentId: string,
  resourceId: string,
): Promise<IncidentResourceBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resources/${resourceId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete resource request. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResourceBoardResponse>;
}

export async function fetchEvacuationOperationsBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Evacuation operations returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function createEvacuationZone(
  incidentId: string,
  request: CreateEvacuationZoneRequest,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/zones`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create evacuation zone. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateEvacuationProgress(
  incidentId: string,
  zoneId: string,
  evacuatedPopulation: number,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/zones/${zoneId}/progress`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        evacuatedPopulation,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update evacuation progress. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateEvacuationZoneStatus(
  incidentId: string,
  zoneId: string,
  status: EvacuationZoneStatus,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/zones/${zoneId}/status`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update evacuation zone status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateEvacuationZoneOwner(
  incidentId: string,
  zoneId: string,
  owner: string,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/zones/${zoneId}/owner`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        owner,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update evacuation owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function deleteEvacuationZone(
  incidentId: string,
  zoneId: string,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/zones/${zoneId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete evacuation zone. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function createIncidentShelter(
  incidentId: string,
  request: CreateShelterRequest,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/shelters`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create shelter. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateShelterOccupancy(
  incidentId: string,
  shelterId: string,
  occupancy: number,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/shelters/${shelterId}/occupancy`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        occupancy,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update shelter occupancy. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateShelterStatus(
  incidentId: string,
  shelterId: string,
  status: ShelterStatus,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/shelters/${shelterId}/status`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update shelter status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function updateShelterOwner(
  incidentId: string,
  shelterId: string,
  owner: string,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/shelters/${shelterId}/owner`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        owner,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update shelter owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}

export async function deleteIncidentShelter(
  incidentId: string,
  shelterId: string,
): Promise<EvacuationOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/evacuation/shelters/${shelterId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete shelter. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvacuationOperationsBoardResponse>;
}
