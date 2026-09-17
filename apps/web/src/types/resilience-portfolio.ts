export type PortfolioAssuranceBand =
  | "FOUNDATIONAL"
  | "DEVELOPING"
  | "ASSURED"
  | "HIGH_ASSURANCE";

export type ResiliencePortfolioResponse = {
  score: number;
  band: PortfolioAssuranceBand;

  totalIncidentCount: number;
  openIncidentCount: number;
  closedIncidentCount: number;

  certifiedIncidentCount: number;
  evidencePackagedIncidentCount: number;
  auditLedgerIncidentCount: number;
  auditVerifiedIncidentCount: number;

  completedImprovementCount: number;
  validatedImprovementCount: number;
  untestedImprovementCount: number;
  needsRevalidationCount: number;
  failedImprovementCount: number;

  openCorrectiveActionCount: number;

  totalExerciseCount: number;
  completedExerciseCount: number;
  passedExerciseCount: number;

  averageLatestReadinessScore: number;

  generatedAt: string;
};

export type ResiliencePortfolioSnapshot = {
  id: string;
  snapshotNumber: number;

  score: number;
  band: PortfolioAssuranceBand;

  totalIncidentCount: number;
  openIncidentCount: number;
  closedIncidentCount: number;

  certifiedIncidentCount: number;
  evidencePackagedIncidentCount: number;
  auditLedgerIncidentCount: number;
  auditVerifiedIncidentCount: number;

  completedImprovementCount: number;
  validatedImprovementCount: number;
  untestedImprovementCount: number;
  needsRevalidationCount: number;
  failedImprovementCount: number;

  openCorrectiveActionCount: number;

  totalExerciseCount: number;
  completedExerciseCount: number;
  passedExerciseCount: number;

  averageLatestReadinessScore: number;

  createdAt: string;
};

export type ResiliencePortfolioHistoryResponse = {
  snapshots: ResiliencePortfolioSnapshot[];
};
