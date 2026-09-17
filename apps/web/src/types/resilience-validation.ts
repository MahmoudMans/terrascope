import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  PreparednessValidationResult,
} from "@/types/preparedness-exercises";

export type CorrectiveActionStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "WAIVED";

export type ImprovementValidationStatus =
  | "UNTESTED"
  | "VALIDATED"
  | "NEEDS_REVALIDATION"
  | "FAILED";

export type PreparednessCorrectiveAction = {
  id: string;
  incidentId: string;
  exerciseId: string;
  improvementActionId: string;

  title: string;
  description: string;

  priority: IncidentActionPriority;
  status: CorrectiveActionStatus;

  owner: string;

  dueAt: string | null;

  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type ImprovementValidationRecord = {
  improvementActionId: string;
  title: string;
  priority: IncidentActionPriority;

  totalExerciseCount: number;

  latestExerciseId: string | null;

  latestValidationResult:
    PreparednessValidationResult | null;

  latestReadinessScore: number | null;

  openCorrectiveActionCount: number;

  validationStatus:
    ImprovementValidationStatus;
};

export type ResilienceValidationBoardResponse = {
  incidentId: string;

  completedImprovementCount: number;

  untestedCount: number;
  validatedCount: number;
  needsRevalidationCount: number;
  failedCount: number;

  totalCorrectiveActionCount: number;
  openCorrectiveActionCount: number;
  inProgressCorrectiveActionCount: number;
  completedCorrectiveActionCount: number;
  waivedCorrectiveActionCount: number;
  overdueCorrectiveActionCount: number;

  averageLatestReadinessScore: number;

  validations: ImprovementValidationRecord[];

  correctiveActions: PreparednessCorrectiveAction[];
};

export type CreatePreparednessCorrectiveActionRequest = {
  title: string;
  description: string;
  priority: IncidentActionPriority;
  owner: string;
  dueAt: string | null;
};
