export type ResilienceCertificationStatus =
  | "BLOCKED"
  | "READY"
  | "CERTIFIED";

export type CertificationBlocker = {
  code: string;
  label: string;
  value: string;
};

export type ResilienceCertificationReadinessResponse = {
  incidentId: string;

  status: ResilienceCertificationStatus;
  ready: boolean;

  completedImprovementCount: number;

  validatedCount: number;
  untestedCount: number;
  needsRevalidationCount: number;
  failedCount: number;

  openCorrectiveActionCount: number;

  totalExerciseCount: number;
  passedExerciseCount: number;

  averageLatestReadinessScore: number;

  afterActionReviewCount: number;
  sitrepCount: number;

  blockers: CertificationBlocker[];

  generatedAt: string;
};

export type IncidentResilienceCertification = {
  id: string;
  incidentId: string;
  certificationNumber: number;

  status: "CERTIFIED";

  completedImprovementCount: number;

  validatedCount: number;
  untestedCount: number;
  needsRevalidationCount: number;
  failedCount: number;

  openCorrectiveActionCount: number;

  totalExerciseCount: number;
  passedExerciseCount: number;

  averageLatestReadinessScore: number;

  afterActionReviewCount: number;
  sitrepCount: number;

  executiveSummary: string;

  createdAt: string;
};

export type ResilienceCertificationHistoryResponse = {
  incidentId: string;
  certifications: IncidentResilienceCertification[];
};
