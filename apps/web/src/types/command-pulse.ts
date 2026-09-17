export type CommandPressureBand =
  | "STABLE"
  | "GUARDED"
  | "ELEVATED"
  | "SEVERE"
  | "CRITICAL";

export type CommandBlocker = {
  code: string;
  label: string;
  value: string;
  weight: number;
};

export type IncidentCommandPulseResponse = {
  incidentId: string;

  score: number;
  band: CommandPressureBand;

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

  blockers: CommandBlocker[];

  generatedAt: string;
};

export type CommandPulseSnapshot = {
  id: string;
  incidentId: string;

  score: number;
  band: CommandPressureBand;
  blockerCount: number;

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

export type CommandPulseHistoryResponse = {
  incidentId: string;
  snapshots: CommandPulseSnapshot[];
};
