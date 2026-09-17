export type PortfolioTrendDirection =
  | "BASELINE"
  | "IMPROVING"
  | "STABLE"
  | "DECLINING";

export type ResiliencePortfolioReviewPreview = {
  previousSnapshotId: string | null;
  previousSnapshotNumber: number | null;

  trend: PortfolioTrendDirection;

  currentScore: number;
  previousScore: number | null;
  scoreDelta: number;

  currentAssuranceDebt: number;
  previousAssuranceDebt: number | null;
  assuranceDebtDelta: number;

  currentCertifiedIncidentCount: number;
  certifiedIncidentDelta: number;

  currentValidatedImprovementCount: number;
  validatedImprovementDelta: number;

  currentAuditVerifiedIncidentCount: number;
  auditVerifiedIncidentDelta: number;

  executiveSummary: string;

  generatedAt: string;
};

export type ResiliencePortfolioReview = {
  id: string;
  reviewNumber: number;

  currentSnapshotId: string;
  currentSnapshotNumber: number;

  previousSnapshotId: string | null;
  previousSnapshotNumber: number | null;

  trend: PortfolioTrendDirection;

  currentScore: number;
  previousScore: number | null;
  scoreDelta: number;

  currentAssuranceDebt: number;
  previousAssuranceDebt: number | null;
  assuranceDebtDelta: number;

  certifiedIncidentDelta: number;
  validatedImprovementDelta: number;
  auditVerifiedIncidentDelta: number;

  executiveSummary: string;

  createdAt: string;
};

export type ResiliencePortfolioReviewHistoryResponse = {
  reviews: ResiliencePortfolioReview[];
};
