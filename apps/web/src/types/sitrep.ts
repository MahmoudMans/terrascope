import type {
  CommandPressureBand,
} from "@/types/command-pulse";

import type {
  IncidentSeverity,
  IncidentStatus,
} from "@/types/incidents";

export type IncidentSitrepPreview = {
  incidentId: string;

  incidentTitle: string;
  incidentStatus: IncidentStatus;
  incidentSeverity: IncidentSeverity;

  commandScore: number;
  commandBand: CommandPressureBand;

  executiveSummary: string;

  priorities: string[];

  openActionCount: number;
  overdueActionCount: number;
  blockedActionCount: number;

  resourceShortageCount: number;

  evacuationRemaining: number;
  shelterRemainingCapacity: number;

  lifelineDisruptedCount: number;
  criticalLifelineDisruptionCount: number;
  lifelineAffectedPopulation: number;

  unresolvedDamageLossMusd: number;
  criticalDamageCount: number;
  averageRepairProgressPct: number;

  generatedAt: string;
};

export type IncidentSituationReport = {
  id: string;
  incidentId: string;
  reportNumber: number;

  incidentTitle: string;
  incidentStatus: IncidentStatus;
  incidentSeverity: IncidentSeverity;

  commandScore: number;
  commandBand: CommandPressureBand;

  executiveSummary: string;

  priorities: string[];

  openActionCount: number;
  overdueActionCount: number;
  blockedActionCount: number;

  resourceShortageCount: number;

  evacuationRemaining: number;
  shelterRemainingCapacity: number;

  lifelineDisruptedCount: number;
  criticalLifelineDisruptionCount: number;
  lifelineAffectedPopulation: number;

  unresolvedDamageLossMusd: number;
  criticalDamageCount: number;
  averageRepairProgressPct: number;

  createdAt: string;
};

export type IncidentSitrepHistoryResponse = {
  incidentId: string;
  reports: IncidentSituationReport[];
};
