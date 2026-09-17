export type PreparednessExerciseType =
  | "TABLETOP"
  | "FUNCTIONAL"
  | "FULL_SCALE"
  | "TECHNICAL"
  | "COMMUNICATIONS"
  | "OTHER";

export type PreparednessExerciseStatus =
  | "PLANNED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type PreparednessValidationResult =
  | "NOT_ASSESSED"
  | "PASSED"
  | "PARTIAL"
  | "FAILED";

export type IncidentPreparednessExercise = {
  id: string;
  incidentId: string;
  improvementActionId: string;

  exerciseType: PreparednessExerciseType;

  title: string;
  description: string;

  status: PreparednessExerciseStatus;

  owner: string;

  scheduledAt: string | null;
  performedAt: string | null;

  validationResult:
    PreparednessValidationResult;

  readinessScore: number | null;

  evidenceSummary: string;
  findings: string;
  correctiveAction: string;

  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type PreparednessExerciseBoardResponse = {
  incidentId: string;

  totalCount: number;
  plannedCount: number;
  inProgressCount: number;
  completedCount: number;
  cancelledCount: number;

  passedCount: number;
  partialCount: number;
  failedCount: number;

  overdueCount: number;

  averageReadinessScore: number;

  exercises: IncidentPreparednessExercise[];
};

export type CreatePreparednessExerciseRequest = {
  improvementActionId: string;

  exerciseType: PreparednessExerciseType;

  title: string;
  description: string;

  owner: string;

  scheduledAt: string | null;
};

export type AssessPreparednessExerciseRequest = {
  result: Exclude<
    PreparednessValidationResult,
    "NOT_ASSESSED"
  >;

  readinessScore: number;

  performedAt: string | null;

  evidenceSummary: string;
  findings: string;
  correctiveAction: string;
};
