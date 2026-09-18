import type {
  BenefitRealizationBand,
} from "@/types/resilience-benefit-realization";

export type GovernanceClosureReadinessResponse = {
  ready: boolean;

  band: BenefitRealizationBand;

  totalObjectiveCount: number;
  completedObjectiveCount: number;
  validatedObjectiveCount: number;
  achievedCount: number;

  validationCoveragePct: number;
  realizedBenefitRatePct: number;

  blockers: string[];

  generatedAt: string;
};

export type GovernanceClosureCertificate = {
  id: string;

  certificateNumber: number;

  benefitSnapshotId: string;
  benefitSnapshotNumber: number;

  band: BenefitRealizationBand;
  closureReady: boolean;

  totalObjectiveCount: number;
  completedObjectiveCount: number;
  validatedObjectiveCount: number;
  achievedCount: number;

  validationCoveragePct: number;
  realizedBenefitRatePct: number;

  averageScoreDelta: number;
  averageAssuranceDebtDelta: number;

  previousCertificateHash: string | null;

  dossierVersion: string;
  dossierHash: string;

  certificateVersion: string;
  certificateHash: string;

  executiveSummary: string;

  createdAt: string;
};

export type GovernanceClosureCertificateHistoryResponse = {
  certificates: GovernanceClosureCertificate[];
};

export type GovernanceClosureVerificationResponse = {
  valid: boolean;

  certificateCount: number;
  verifiedCertificateCount: number;

  brokenCertificateId: string | null;
  brokenCertificateNumber: number | null;

  message: string;

  verifiedAt: string;
};
