export type DamageAssetCategory =
  | "RESIDENTIAL"
  | "COMMERCIAL"
  | "PUBLIC_BUILDING"
  | "ROAD"
  | "BRIDGE"
  | "UTILITY"
  | "HEALTHCARE"
  | "OTHER";

export type DamageSeverity =
  | "MINOR"
  | "MODERATE"
  | "MAJOR"
  | "DESTROYED";

export type DamageRecoveryStatus =
  | "REPORTED"
  | "VERIFIED"
  | "REPAIRING"
  | "RESTORED";

export type DamageAssessment = {
  id: string;
  incidentId: string;
  category: DamageAssetCategory;
  assetName: string;
  description: string;
  severity: DamageSeverity;
  status: DamageRecoveryStatus;
  affectedUnits: number;
  estimatedLossMusd: number;
  owner: string;
  recoveryEta: string | null;
  repairProgressPct: number;
  createdAt: string;
  updatedAt: string;
  verifiedAt: string | null;
  restoredAt: string | null;
};

export type DamageRecoveryBoardResponse = {
  incidentId: string;

  totalCount: number;
  reportedCount: number;
  verifiedCount: number;
  repairingCount: number;
  restoredCount: number;

  criticalDamageCount: number;

  affectedUnits: number;

  totalEstimatedLossMusd: number;
  unresolvedEstimatedLossMusd: number;

  averageRepairProgressPct: number;

  assessments: DamageAssessment[];
};

export type CreateDamageAssessmentRequest = {
  category: DamageAssetCategory;
  assetName: string;
  description: string;
  severity: DamageSeverity;
  affectedUnits: number;
  estimatedLossMusd: number;
  owner: string;
  recoveryEta: string | null;
};
