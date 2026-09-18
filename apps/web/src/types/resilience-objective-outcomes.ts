export type PortfolioObjectiveOutcome =
  | "ACHIEVED"
  | "PARTIAL"
  | "NOT_ACHIEVED";

export type PortfolioObjectiveOutcomePreview = {
  objectiveId: string;
  objectiveTitle: string;

  baselineSnapshotId: string;
  baselineSnapshotNumber: number;

  outcome: PortfolioObjectiveOutcome;

  baselineScore: number;
  currentScore: number;
  scoreDelta: number;

  baselineAssuranceDebt: number;
  currentAssuranceDebt: number;
  assuranceDebtDelta: number;

  certifiedIncidentDelta: number;
  validatedImprovementDelta: number;
  auditVerifiedIncidentDelta: number;

  executiveSummary: string;

  generatedAt: string;
};

export type PortfolioObjectiveValidation = {
  id: string;

  objectiveId: string;
  validationNumber: number;

  baselineSnapshotId: string;
  baselineSnapshotNumber: number;

  validationSnapshotId: string;
  validationSnapshotNumber: number;

  outcome: PortfolioObjectiveOutcome;

  baselineScore: number;
  validationScore: number;
  scoreDelta: number;

  baselineAssuranceDebt: number;
  validationAssuranceDebt: number;
  assuranceDebtDelta: number;

  certifiedIncidentDelta: number;
  validatedImprovementDelta: number;
  auditVerifiedIncidentDelta: number;

  executiveSummary: string;

  createdAt: string;
};

export type PortfolioObjectiveValidationHistoryResponse = {
  objectiveId: string;
  validations: PortfolioObjectiveValidation[];
};
