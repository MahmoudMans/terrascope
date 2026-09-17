import type {
  CommandPressureBand,
} from "@/types/command-pulse";

import type {
  IncidentSeverity,
} from "@/types/incidents";

export type ClosureReadinessStatus =
  | "READY"
  | "BLOCKED";

export type ClosureBlocker = {
  code: string;
  label: string;
  value: string;
};

export type IncidentClosureReadinessResponse = {
  incidentId: string;

  status: ClosureReadinessStatus;
  ready: boolean;

  commandScore: number;
  commandBand: CommandPressureBand;

  openActionCount: number;
  resourceShortageCount: number;
  activeEvacuationZoneCount: number;
  activeShelterCount: number;
  disruptedLifelineCount: number;
  unresolvedDamageCount: number;

  unresolvedItemCount: number;

  blockers: ClosureBlocker[];

  generatedAt: string;
};

export type IncidentAfterActionReview = {
  id: string;
  incidentId: string;
  reviewNumber: number;

  incidentTitle: string;
  hazardType: string;
  incidentSeverity: IncidentSeverity;

  openedAt: string;
  closedAt: string;
  durationMinutes: number;

  closureReadinessStatus:
    ClosureReadinessStatus;

  closureReady: boolean;

  finalCommandScore: number;
  finalCommandBand:
    CommandPressureBand;

  peakCommandScore: number;

  sitrepCount: number;
  timelineEventCount: number;

  actionCount: number;
  resourceRequestCount: number;
  evacuationZoneCount: number;
  shelterCount: number;
  lifelineAssetCount: number;
  damageAssessmentCount: number;

  unresolvedItemCount: number;

  executiveSummary: string;

  createdAt: string;
};

export type AfterActionReviewHistoryResponse = {
  incidentId: string;
  reviews: IncidentAfterActionReview[];
};
