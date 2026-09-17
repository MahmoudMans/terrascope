import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

export type LifelineAssetCategory =
  | "POWER"
  | "WATER"
  | "HEALTHCARE"
  | "TRANSPORT"
  | "COMMUNICATIONS"
  | "FUEL"
  | "OTHER";

export type LifelineAssetStatus =
  | "OPERATIONAL"
  | "DEGRADED"
  | "OUTAGE"
  | "RESTORED";

export type LifelineAsset = {
  id: string;
  incidentId: string;
  category: LifelineAssetCategory;
  name: string;
  description: string;
  priority: IncidentActionPriority;
  status: LifelineAssetStatus;
  affectedPopulation: number;
  owner: string;
  restorationEta: string | null;
  createdAt: string;
  updatedAt: string;
  restoredAt: string | null;
};

export type LifelineOperationsBoardResponse = {
  incidentId: string;

  totalCount: number;
  operationalCount: number;
  degradedCount: number;
  outageCount: number;
  restoredCount: number;

  disruptedCount: number;
  criticalDisruptionCount: number;

  affectedPopulation: number;
  etaTrackedCount: number;

  assets: LifelineAsset[];
};

export type CreateLifelineAssetRequest = {
  category: LifelineAssetCategory;
  name: string;
  description: string;
  priority: IncidentActionPriority;
  status: LifelineAssetStatus;
  affectedPopulation: number;
  owner: string;
  restorationEta: string | null;
};
