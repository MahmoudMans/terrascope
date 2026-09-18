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

import type {
  CreateLifelineAssetRequest,
  LifelineAssetStatus,
  LifelineOperationsBoardResponse,
} from "@/types/lifeline-operations";

import type {
  CreateDamageAssessmentRequest,
  DamageRecoveryBoardResponse,
  DamageSeverity,
} from "@/types/damage-recovery";

import type {
  CommandPulseHistoryResponse,
  CommandPulseSnapshot,
  IncidentCommandPulseResponse,
} from "@/types/command-pulse";

import type {
  IncidentSitrepHistoryResponse,
  IncidentSitrepPreview,
  IncidentSituationReport,
} from "@/types/sitrep";

import type {
  AfterActionReviewHistoryResponse,
  IncidentAfterActionReview,
  IncidentClosureReadinessResponse,
} from "@/types/closure-review";

import type {
  CreateImprovementActionRequest,
  ImprovementActionBoardResponse,
  ImprovementActionStatus,
} from "@/types/improvement-actions";

import type {
  AssessPreparednessExerciseRequest,
  CreatePreparednessExerciseRequest,
  PreparednessExerciseBoardResponse,
  PreparednessExerciseStatus,
} from "@/types/preparedness-exercises";

import type {
  CorrectiveActionStatus,
  CreatePreparednessCorrectiveActionRequest,
  ResilienceValidationBoardResponse,
} from "@/types/resilience-validation";

import type {
  IncidentResilienceCertification,
  ResilienceCertificationHistoryResponse,
  ResilienceCertificationReadinessResponse,
} from "@/types/resilience-certification";

import type {
  EvidencePackageVerificationResponse,
  ResilienceEvidencePackage,
  ResilienceEvidencePackageHistoryResponse,
  ResilienceEvidencePackagePreview,
} from "@/types/resilience-evidence";

import type {
  ResilienceAuditLedgerHistoryResponse,
  ResilienceAuditLedgerPreview,
  ResilienceAuditLedgerVerificationResponse,
  ResilienceAuditReceipt,
} from "@/types/resilience-audit-ledger";

import type {
  ResiliencePortfolioHistoryResponse,
  ResiliencePortfolioResponse,
  ResiliencePortfolioSnapshot,
} from "@/types/resilience-portfolio";

import type {
  ResiliencePortfolioReview,
  ResiliencePortfolioReviewHistoryResponse,
  ResiliencePortfolioReviewPreview,
} from "@/types/resilience-portfolio-review";

import type {
  CreatePortfolioObjectiveRequest,
  PortfolioObjectiveBoardResponse,
  PortfolioObjectiveHistoryResponse,
  PortfolioObjectiveStatus,
} from "@/types/resilience-portfolio-objectives";

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

export async function fetchLifelineOperationsBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines`,
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
      `Lifeline operations returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function createLifelineAsset(
  incidentId: string,
  request: CreateLifelineAssetRequest,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines`,
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
      `Unable to create lifeline asset. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function updateLifelineStatus(
  incidentId: string,
  assetId: string,
  status: LifelineAssetStatus,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines/${assetId}/status`,
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
      `Unable to update lifeline status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function updateLifelineImpact(
  incidentId: string,
  assetId: string,
  affectedPopulation: number,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines/${assetId}/impact`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        affectedPopulation,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update lifeline impact. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function updateLifelineOwner(
  incidentId: string,
  assetId: string,
  owner: string,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines/${assetId}/owner`,
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
      `Unable to update lifeline owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function updateLifelineRestorationEta(
  incidentId: string,
  assetId: string,
  restorationEta: string | null,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines/${assetId}/restoration-eta`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        restorationEta,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update restoration ETA. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function deleteLifelineAsset(
  incidentId: string,
  assetId: string,
): Promise<LifelineOperationsBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/lifelines/${assetId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete lifeline asset. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<LifelineOperationsBoardResponse>;
}

export async function fetchDamageRecoveryBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage`,
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
      `Damage recovery board returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function createDamageAssessment(
  incidentId: string,
  request: CreateDamageAssessmentRequest,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage`,
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
      `Unable to create damage assessment. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function verifyDamageAssessment(
  incidentId: string,
  assessmentId: string,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}/verify`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to verify damage assessment. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function updateDamageProgress(
  incidentId: string,
  assessmentId: string,
  repairProgressPct: number,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}/progress`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        repairProgressPct,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update recovery progress. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function updateDamageSeverity(
  incidentId: string,
  assessmentId: string,
  severity: DamageSeverity,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}/severity`,
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
      `Unable to update damage severity. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function updateDamageOwner(
  incidentId: string,
  assessmentId: string,
  owner: string,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}/owner`,
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
      `Unable to update recovery owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function updateDamageRecoveryEta(
  incidentId: string,
  assessmentId: string,
  recoveryEta: string | null,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}/recovery-eta`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        recoveryEta,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update recovery ETA. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function deleteDamageAssessment(
  incidentId: string,
  assessmentId: string,
): Promise<DamageRecoveryBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/damage/${assessmentId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete damage assessment. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<DamageRecoveryBoardResponse>;
}

export async function fetchIncidentCommandPulse(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentCommandPulseResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/command-pulse`,
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
      `Command pulse returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentCommandPulseResponse>;
}

export async function fetchCommandPulseHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<CommandPulseHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/command-pulse/history`,
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
      `Command pulse history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<CommandPulseHistoryResponse>;
}

export async function createCommandPulseSnapshot(
  incidentId: string,
): Promise<CommandPulseSnapshot> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/command-pulse/snapshots`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create command snapshot. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<CommandPulseSnapshot>;
}

export async function fetchIncidentSitrepPreview(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentSitrepPreview> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/sitrep/preview`,
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
      `SITREP preview returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentSitrepPreview>;
}

export async function fetchIncidentSitrepHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentSitrepHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/sitrep/reports`,
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
      `SITREP history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentSitrepHistoryResponse>;
}

export async function createIncidentSituationReport(
  incidentId: string,
): Promise<IncidentSituationReport> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/sitrep/reports`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create situation report. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentSituationReport>;
}

export async function fetchIncidentClosureReadiness(
  incidentId: string,
  signal?: AbortSignal,
): Promise<IncidentClosureReadinessResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/closure-readiness`,
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
      `Closure readiness returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentClosureReadinessResponse>;
}

export async function fetchAfterActionReviewHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<AfterActionReviewHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/after-action-reviews`,
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
      `After-action review history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<AfterActionReviewHistoryResponse>;
}

export async function createAfterActionReview(
  incidentId: string,
): Promise<IncidentAfterActionReview> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/after-action-reviews`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create after-action review. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentAfterActionReview>;
}

export async function fetchIncidentImprovementBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements`,
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
      `Improvement register returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function createIncidentImprovementAction(
  incidentId: string,
  request: CreateImprovementActionRequest,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements`,
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
      `Unable to create improvement action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function updateImprovementActionStatus(
  incidentId: string,
  actionId: string,
  status: ImprovementActionStatus,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements/${actionId}/status`,
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
      `Unable to update improvement status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function updateImprovementActionPriority(
  incidentId: string,
  actionId: string,
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements/${actionId}/priority`,
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
      `Unable to update improvement priority. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function updateImprovementActionOwner(
  incidentId: string,
  actionId: string,
  owner: string,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements/${actionId}/owner`,
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
      `Unable to update improvement owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function updateImprovementActionDueAt(
  incidentId: string,
  actionId: string,
  dueAt: string | null,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements/${actionId}/due-at`,
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
      `Unable to update improvement due date. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function deleteIncidentImprovementAction(
  incidentId: string,
  actionId: string,
): Promise<ImprovementActionBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/improvements/${actionId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete improvement action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImprovementActionBoardResponse>;
}

export async function fetchPreparednessExerciseBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises`,
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
      `Preparedness exercise board returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function createPreparednessExercise(
  incidentId: string,
  request: CreatePreparednessExerciseRequest,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises`,
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
      `Unable to create preparedness exercise. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function updatePreparednessExerciseStatus(
  incidentId: string,
  exerciseId: string,
  status: PreparednessExerciseStatus,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises/${exerciseId}/status`,
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
      `Unable to update preparedness exercise status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function updatePreparednessExerciseOwner(
  incidentId: string,
  exerciseId: string,
  owner: string,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises/${exerciseId}/owner`,
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
      `Unable to update preparedness exercise owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function updatePreparednessExerciseSchedule(
  incidentId: string,
  exerciseId: string,
  scheduledAt: string | null,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises/${exerciseId}/schedule`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        scheduledAt,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update preparedness exercise schedule. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function assessPreparednessExercise(
  incidentId: string,
  exerciseId: string,
  request: AssessPreparednessExerciseRequest,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises/${exerciseId}/assess`,
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
      `Unable to assess preparedness exercise. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function deletePreparednessExercise(
  incidentId: string,
  exerciseId: string,
): Promise<PreparednessExerciseBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/preparedness-exercises/${exerciseId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete preparedness exercise. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PreparednessExerciseBoardResponse>;
}

export async function fetchResilienceValidationBoard(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation`,
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
      `Resilience validation returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function createPreparednessCorrectiveAction(
  incidentId: string,
  exerciseId: string,
  request: CreatePreparednessCorrectiveActionRequest,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation/exercises/${exerciseId}/corrective-actions`,
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
      `Unable to create corrective action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function updatePreparednessCorrectiveStatus(
  incidentId: string,
  correctiveActionId: string,
  status: CorrectiveActionStatus,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation/corrective-actions/${correctiveActionId}/status`,
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
      `Unable to update corrective status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function updatePreparednessCorrectiveOwner(
  incidentId: string,
  correctiveActionId: string,
  owner: string,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation/corrective-actions/${correctiveActionId}/owner`,
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
      `Unable to update corrective owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function updatePreparednessCorrectiveDueAt(
  incidentId: string,
  correctiveActionId: string,
  dueAt: string | null,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation/corrective-actions/${correctiveActionId}/due-at`,
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
      `Unable to update corrective due date. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function deletePreparednessCorrectiveAction(
  incidentId: string,
  correctiveActionId: string,
): Promise<ResilienceValidationBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-validation/corrective-actions/${correctiveActionId}`,
    {
      method: "DELETE",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete corrective action. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceValidationBoardResponse>;
}

export async function fetchResilienceCertificationReadiness(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceCertificationReadinessResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-certification/readiness`,
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
      `Resilience certification readiness returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceCertificationReadinessResponse>;
}

export async function fetchResilienceCertificationHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceCertificationHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-certification/history`,
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
      `Resilience certification history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceCertificationHistoryResponse>;
}

export async function createResilienceCertification(
  incidentId: string,
): Promise<IncidentResilienceCertification> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-certification`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create resilience certification. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<IncidentResilienceCertification>;
}

export async function fetchResilienceEvidencePreview(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceEvidencePackagePreview> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-evidence/preview`,
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
      `Evidence preview returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceEvidencePackagePreview>;
}

export async function fetchResilienceEvidenceHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceEvidencePackageHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-evidence/packages`,
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
      `Evidence history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceEvidencePackageHistoryResponse>;
}

export async function createResilienceEvidencePackage(
  incidentId: string,
): Promise<ResilienceEvidencePackage> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-evidence/packages`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create evidence package. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceEvidencePackage>;
}

export async function verifyResilienceEvidencePackage(
  incidentId: string,
  packageId: string,
): Promise<EvidencePackageVerificationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-evidence/packages/${packageId}/verify`,
    {
      method: "GET",
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Evidence verification returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EvidencePackageVerificationResponse>;
}

export async function fetchResilienceAuditLedgerPreview(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceAuditLedgerPreview> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-audit-ledger/preview`,
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
      `Audit ledger preview returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceAuditLedgerPreview>;
}

export async function fetchResilienceAuditLedgerHistory(
  incidentId: string,
  signal?: AbortSignal,
): Promise<ResilienceAuditLedgerHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-audit-ledger/receipts`,
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
      `Audit ledger history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceAuditLedgerHistoryResponse>;
}

export async function createResilienceAuditReceipt(
  incidentId: string,
): Promise<ResilienceAuditReceipt> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-audit-ledger/receipts`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create audit receipt. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceAuditReceipt>;
}

export async function verifyResilienceAuditLedger(
  incidentId: string,
): Promise<ResilienceAuditLedgerVerificationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/incidents/${incidentId}/resilience-audit-ledger/verify`,
    {
      method: "GET",
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Audit ledger verification returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResilienceAuditLedgerVerificationResponse>;
}

export async function fetchResiliencePortfolio(
  signal?: AbortSignal,
): Promise<ResiliencePortfolioResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio`,
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
      `Resilience portfolio returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioResponse>;
}

export async function fetchResiliencePortfolioHistory(
  signal?: AbortSignal,
): Promise<ResiliencePortfolioHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/history`,
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
      `Resilience portfolio history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioHistoryResponse>;
}

export async function createResiliencePortfolioSnapshot(): Promise<ResiliencePortfolioSnapshot> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/snapshots`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create resilience portfolio snapshot. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioSnapshot>;
}

export async function fetchResiliencePortfolioReviewPreview(
  signal?: AbortSignal,
): Promise<ResiliencePortfolioReviewPreview> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/review-preview`,
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
      `Portfolio review preview returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioReviewPreview>;
}

export async function fetchResiliencePortfolioReviewHistory(
  signal?: AbortSignal,
): Promise<ResiliencePortfolioReviewHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/reviews`,
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
      `Portfolio review history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioReviewHistoryResponse>;
}

export async function createResiliencePortfolioReview(): Promise<ResiliencePortfolioReview> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/reviews`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to create portfolio review. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ResiliencePortfolioReview>;
}

export async function fetchPortfolioObjectiveBoard(
  signal?: AbortSignal,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives`,
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
      `Portfolio objective board returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function fetchPortfolioObjectiveHistory(
  objectiveId: string,
): Promise<PortfolioObjectiveHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/history`,
    {
      method: "GET",
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Portfolio objective history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveHistoryResponse>;
}

export async function createPortfolioObjective(
  request: CreatePortfolioObjectiveRequest,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives`,
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
      `Unable to create portfolio objective. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function updatePortfolioObjectiveStatus(
  objectiveId: string,
  status: PortfolioObjectiveStatus,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/status`,
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
      `Unable to update portfolio objective status. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function updatePortfolioObjectiveOwner(
  objectiveId: string,
  owner: string,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/owner`,
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
      `Unable to update portfolio objective owner. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function updatePortfolioObjectiveTarget(
  objectiveId: string,
  targetAt: string | null,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/target`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        targetAt,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update portfolio objective target. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function updatePortfolioObjectiveProgress(
  objectiveId: string,
  progressPct: number,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/progress`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        progressPct,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update portfolio objective progress. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}

export async function completePortfolioObjective(
  objectiveId: string,
  completionEvidence: string,
): Promise<PortfolioObjectiveBoardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/resilience-portfolio/objectives/${objectiveId}/complete`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        completionEvidence,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to complete portfolio objective. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<PortfolioObjectiveBoardResponse>;
}
