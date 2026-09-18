export type PortfolioObjectivePriority =
  | "LOW"
  | "MEDIUM"
  | "HIGH"
  | "CRITICAL";

export type PortfolioObjectiveStatus =
  | "PLANNED"
  | "IN_PROGRESS"
  | "BLOCKED"
  | "COMPLETED"
  | "CANCELLED";

export type PortfolioObjective = {
  id: string;

  portfolioReviewId: string;
  portfolioReviewNumber: number;

  title: string;
  description: string;

  priority: PortfolioObjectivePriority;
  status: PortfolioObjectiveStatus;

  owner: string;

  targetAt: string | null;

  progressPct: number;

  successMetric: string;
  completionEvidence: string;

  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type PortfolioObjectiveEvent = {
  id: string;
  objectiveId: string;

  eventType: string;
  detail: string;

  createdAt: string;
};

export type PortfolioObjectiveHistoryResponse = {
  objectiveId: string;
  events: PortfolioObjectiveEvent[];
};

export type PortfolioObjectiveBoardResponse = {
  totalCount: number;

  plannedCount: number;
  inProgressCount: number;
  blockedCount: number;
  completedCount: number;
  cancelledCount: number;

  overdueCount: number;

  averageProgressPct: number;

  objectives: PortfolioObjective[];
};

export type CreatePortfolioObjectiveRequest = {
  portfolioReviewId: string;

  title: string;
  description: string;

  priority: PortfolioObjectivePriority;

  owner: string;

  targetAt: string | null;

  successMetric: string;
};
