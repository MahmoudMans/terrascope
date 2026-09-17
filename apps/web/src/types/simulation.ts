export type SimulationHazardType =
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

export type SimulationRiskBand =
  | "LOW"
  | "MODERATE"
  | "HIGH"
  | "SEVERE"
  | "EXTREME";

export type ImpactSimulationRequest = {
  name: string;
  hazardType: SimulationHazardType;
  latitude: number;
  longitude: number;
  radiusKm: number;
  intensity: number;
  exposedPopulation: number;
  infrastructureResiliencePct: number;
  emergencyReadinessPct: number;
};

export type SimulationScenario = {
  id: string;
  name: string;
  hazardType: SimulationHazardType;
  latitude: number;
  longitude: number;
  radiusKm: number;
  intensity: number;
  exposedPopulation: number;
  infrastructureResiliencePct: number;
  emergencyReadinessPct: number;
  createdAt: string;
};

export type ImpactSimulationResult = {
  id: string;
  scenarioId: string;
  affectedPopulation: number;
  displacedPopulation: number;
  estimatedEconomicLossMusd: number;
  infrastructureDisruptionPct: number;
  responsePressurePct: number;
  compositeRiskScore: number;
  riskBand: SimulationRiskBand;
  methodologyVersion: string;
  createdAt: string;
};

export type ImpactSimulationRunResponse = {
  scenario: SimulationScenario;
  result: ImpactSimulationResult;
};

export type SimulationHistoryItem = {
  scenario: SimulationScenario;
  result: ImpactSimulationResult;
};

export type SimulationHistoryResponse = {
  totalCount: number;
  extremeCount: number;
  severeCount: number;
  averageRiskScore: number;
  runs: SimulationHistoryItem[];
};
