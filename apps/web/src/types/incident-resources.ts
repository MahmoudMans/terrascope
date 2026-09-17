import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

export type IncidentResourceCategory =
  | "PERSONNEL"
  | "MEDICAL"
  | "TRANSPORT"
  | "SHELTER"
  | "EQUIPMENT"
  | "COMMUNICATIONS"
  | "OTHER";

export type IncidentResourceStatus =
  | "REQUESTED"
  | "PARTIALLY_ALLOCATED"
  | "FULFILLED"
  | "CANCELLED";

export type IncidentResourceRequest = {
  id: string;
  incidentId: string;
  category: IncidentResourceCategory;
  name: string;
  description: string;
  priority: IncidentActionPriority;
  status: IncidentResourceStatus;
  requestedQuantity: number;
  allocatedQuantity: number;
  unit: string;
  owner: string;
  createdAt: string;
  updatedAt: string;
  fulfilledAt: string | null;
  cancelledAt: string | null;
};

export type IncidentResourceBoardResponse = {
  incidentId: string;
  totalCount: number;
  requestedCount: number;
  partiallyAllocatedCount: number;
  fulfilledCount: number;
  cancelledCount: number;
  shortageCount: number;
  resources: IncidentResourceRequest[];
};

export type CreateIncidentResourceRequest = {
  category: IncidentResourceCategory;
  name: string;
  description: string;
  priority: IncidentActionPriority;
  requestedQuantity: number;
  unit: string;
  owner: string;
};
