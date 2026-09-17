export type IncidentActionPriority =
  | "LOW"
  | "MEDIUM"
  | "HIGH"
  | "CRITICAL";

export type IncidentActionStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "BLOCKED"
  | "DONE";

export type IncidentActionItem = {
  id: string;
  incidentId: string;
  title: string;
  description: string;
  priority: IncidentActionPriority;
  status: IncidentActionStatus;
  owner: string;
  dueAt: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type IncidentActionBoardResponse = {
  incidentId: string;
  totalCount: number;
  todoCount: number;
  inProgressCount: number;
  blockedCount: number;
  doneCount: number;
  overdueCount: number;
  actions: IncidentActionItem[];
};

export type CreateIncidentActionRequest = {
  title: string;
  description: string;
  priority: IncidentActionPriority;
  owner: string;
  dueAt: string | null;
};
