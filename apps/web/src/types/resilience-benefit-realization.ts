export type BenefitRealizationBand =
  | "UNPROVEN"
  | "EMERGING"
  | "REALIZING"
  | "REALIZED";

export type BenefitRealizationResponse = {
  band: BenefitRealizationBand;
  closureReady: boolean;

  totalObjectiveCount: number;

  plannedObjectiveCount: number;
  inProgressObjectiveCount: number;
  blockedObjectiveCount: number;
  completedObjectiveCount: number;
  cancelledObjectiveCount: number;

  validatedObjectiveCount: number;
  unvalidatedCompletedObjectiveCount: number;

  achievedCount: number;
  partialCount: number;
  notAchievedCount: number;

  validationCoveragePct: number;
  realizedBenefitRatePct: number;

  averageScoreDelta: number;
  averageAssuranceDebtDelta: number;

  generatedAt: string;
};

export type BenefitRealizationSnapshot = {
  id: string;
  snapshotNumber: number;

  band: BenefitRealizationBand;
  closureReady: boolean;

  totalObjectiveCount: number;

  plannedObjectiveCount: number;
  inProgressObjectiveCount: number;
  blockedObjectiveCount: number;
  completedObjectiveCount: number;
  cancelledObjectiveCount: number;

  validatedObjectiveCount: number;
  unvalidatedCompletedObjectiveCount: number;

  achievedCount: number;
  partialCount: number;
  notAchievedCount: number;

  validationCoveragePct: number;
  realizedBenefitRatePct: number;

  averageScoreDelta: number;
  averageAssuranceDebtDelta: number;

  createdAt: string;
};

export type BenefitRealizationHistoryResponse = {
  snapshots: BenefitRealizationSnapshot[];
};
