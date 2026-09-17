import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

export type ImprovementCategory =
  | "PEOPLE"
  | "PROCESS"
  | "TECHNOLOGY"
  | "COMMUNICATIONS"
  | "LOGISTICS"
  | "INFRASTRUCTURE"
  | "OTHER";

export type ImprovementActionStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "DISMISSED";

export type IncidentImprovementAction = {
  id: string;
  incidentId: string;
  afterActionReviewId: string;

  category: ImprovementCategory;

  title: string;
  description: string;

  priority: IncidentActionPriority;
  status: ImprovementActionStatus;

  owner: string;

  dueAt: string | null;

  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type ImprovementActionBoardResponse = {
  incidentId: string;

  totalCount: number;
  openCount: number;
  inProgressCount: number;
  completedCount: number;
  dismissedCount: number;
  overdueCount: number;

  actions: IncidentImprovementAction[];
};

export type CreateImprovementActionRequest = {
  afterActionReviewId: string;

  category: ImprovementCategory;

  title: string;
  description: string;

  priority: IncidentActionPriority;

  owner: string;

  dueAt: string | null;
};
