export type ResilienceEvidencePackagePreview = {
  incidentId: string;

  certificationId: string;
  certificationNumber: number;
  certificationReadinessScore: number;
  certificationCount: number;

  afterActionReviewCount: number;
  sitrepCount: number;
  improvementCount: number;
  exerciseCount: number;
  correctiveActionCount: number;
  timelineEventCount: number;

  manifestVersion: string;
  manifestHash: string;

  executiveSummary: string;

  generatedAt: string;
};

export type ResilienceEvidencePackage = {
  id: string;
  incidentId: string;
  packageNumber: number;

  certificationId: string;
  certificationNumber: number;
  certificationReadinessScore: number;
  certificationCount: number;

  afterActionReviewCount: number;
  sitrepCount: number;
  improvementCount: number;
  exerciseCount: number;
  correctiveActionCount: number;
  timelineEventCount: number;

  manifestVersion: string;
  manifestHash: string;

  executiveSummary: string;

  createdAt: string;
};

export type ResilienceEvidencePackageHistoryResponse = {
  incidentId: string;
  packages: ResilienceEvidencePackage[];
};

export type EvidencePackageVerificationResponse = {
  incidentId: string;
  packageId: string;

  valid: boolean;

  manifestVersion: string;
  storedHash: string;
  recomputedHash: string;

  verifiedAt: string;
};
