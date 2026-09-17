import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

export type EvacuationZoneStatus =
  | "MONITORING"
  | "EVACUATING"
  | "CLEARED"
  | "CANCELLED";

export type ShelterStatus =
  | "OPEN"
  | "FULL"
  | "CLOSED";

export type EvacuationZone = {
  id: string;
  incidentId: string;
  name: string;
  description: string;
  priority: IncidentActionPriority;
  status: EvacuationZoneStatus;
  populationAtRisk: number;
  evacuatedPopulation: number;
  owner: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
};

export type IncidentShelter = {
  id: string;
  incidentId: string;
  name: string;
  address: string;
  capacity: number;
  occupancy: number;
  status: ShelterStatus;
  owner: string;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
};

export type EvacuationOperationsBoardResponse = {
  incidentId: string;

  zoneCount: number;
  activeZoneCount: number;
  clearedZoneCount: number;

  populationAtRisk: number;
  evacuatedPopulation: number;
  remainingToEvacuate: number;

  shelterCount: number;
  openShelterCount: number;
  fullShelterCount: number;
  closedShelterCount: number;

  shelterCapacity: number;
  shelterOccupancy: number;
  remainingShelterCapacity: number;

  zones: EvacuationZone[];
  shelters: IncidentShelter[];
};

export type CreateEvacuationZoneRequest = {
  name: string;
  description: string;
  priority: IncidentActionPriority;
  populationAtRisk: number;
  evacuatedPopulation: number;
  owner: string;
};

export type CreateShelterRequest = {
  name: string;
  address: string;
  capacity: number;
  occupancy: number;
  owner: string;
};
