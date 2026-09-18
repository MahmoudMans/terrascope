"use client";

import {
  CalendarClock,
  CheckCircle2,
  CircleAlert,
  RefreshCw,
  Target,
  UserRound,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  completePortfolioObjective,
  createPortfolioObjective,
  fetchPortfolioObjectiveBoard,
  fetchResiliencePortfolioReviewHistory,
  updatePortfolioObjectiveOwner,
  updatePortfolioObjectiveProgress,
  updatePortfolioObjectiveStatus,
  updatePortfolioObjectiveTarget,
} from "@/lib/api";

import type {
  ResiliencePortfolioReviewHistoryResponse,
} from "@/types/resilience-portfolio-review";

import type {
  CreatePortfolioObjectiveRequest,
  PortfolioObjectiveBoardResponse,
  PortfolioObjectivePriority,
  PortfolioObjectiveStatus,
} from "@/types/resilience-portfolio-objectives";

function formatDate(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "No target date";
  }

  return new Intl.DateTimeFormat(
    "en",
    {
      dateStyle: "medium",
      timeStyle: "short",
    },
  ).format(
    new Date(value),
  );
}

function objectiveStatusClass(
  status: PortfolioObjectiveStatus,
): string {
  return (
    `strategic-objective-status strategic-objective-status--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function isOverdue(
  status: PortfolioObjectiveStatus,
  targetAt: string | null,
): boolean {
  if (
    status !== "PLANNED"
    && status !== "IN_PROGRESS"
    && status !== "BLOCKED"
  ) {
    return false;
  }

  if (
    targetAt === null
  ) {
    return false;
  }

  return (
    new Date(
      targetAt,
    ).getTime()
    < Date.now()
  );
}

export function ResilienceStrategicObjectives() {
  const [
    board,
    setBoard,
  ] =
    useState<
      PortfolioObjectiveBoardResponse | null
    >(null);

  const [
    reviews,
    setReviews,
  ] =
    useState<
      ResiliencePortfolioReviewHistoryResponse | null
    >(null);

  const [
    selectedReviewId,
    setSelectedReviewId,
  ] =
    useState("");

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    busy,
    setBusy,
  ] =
    useState(false);

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  useEffect(() => {
    const controller =
      new AbortController();

    void Promise.all([
      fetchPortfolioObjectiveBoard(
        controller.signal,
      ),

      fetchResiliencePortfolioReviewHistory(
        controller.signal,
      ),
    ])
      .then(
        ([
          boardResponse,
          reviewResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setBoard(
            boardResponse,
          );

          setReviews(
            reviewResponse,
          );

          if (
            reviewResponse.reviews.length
            > 0
          ) {
            setSelectedReviewId(
              reviewResponse.reviews[0].id,
            );
          }

          setError(null);
        },
      )
      .catch(
        (caughtError: unknown) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load strategic objectives.",
          );
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setLoading(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, []);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchPortfolioObjectiveBoard(),
      fetchResiliencePortfolioReviewHistory(),
    ])
      .then(
        ([
          boardResponse,
          reviewResponse,
        ]) => {
          setBoard(
            boardResponse,
          );

          setReviews(
            reviewResponse,
          );

          if (
            selectedReviewId === ""
            && reviewResponse.reviews.length
              > 0
          ) {
            setSelectedReviewId(
              reviewResponse.reviews[0].id,
            );
          }

          setError(null);
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const applyMutation = (
    operation:
      Promise<PortfolioObjectiveBoardResponse>,
  ) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        setBoard(response);
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update strategic objective.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const reviewOptions =
    reviews?.reviews ?? [];

  return (
    <section className="strategic-objectives">
      <div className="strategic-objectives-heading">
        <div>
          <span className="panel-kicker">
            GOVERNANCE ACTION PLAN
          </span>

          <strong>
            Strategic resilience objectives
          </strong>
        </div>

        <button
          type="button"
          disabled={
            loading
            || busy
          }
          onClick={() => {
            void refresh()
              .catch(
                (
                  caughtError:
                    unknown,
                ) => {
                  setError(
                    caughtError
                      instanceof Error
                      ? caughtError.message
                      : "Unable to refresh objectives.",
                  );
                },
              );
          }}
        >
          <RefreshCw
            size={13}
          />

          Refresh
        </button>
      </div>

      <div className="strategic-objectives-summary">
        <div>
          <span>
            Objectives
          </span>

          <strong>
            {board?.totalCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Planned
          </span>

          <strong>
            {board?.plannedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            In progress
          </span>

          <strong>
            {board?.inProgressCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Blocked
          </span>

          <strong>
            {board?.blockedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Completed
          </span>

          <strong>
            {board?.completedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Overdue
          </span>

          <strong>
            {board?.overdueCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Avg progress
          </span>

          <strong>
            {board
              ?.averageProgressPct
              .toFixed(1)
              ?? "0.0"}
            %
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="strategic-objectives-error">
          {error}
        </div>
      )}

      {reviewOptions.length === 0
        ? (
          <div className="strategic-objectives-empty">
            Capture an executive resilience
            review before creating governance
            objectives.
          </div>
        )
        : (
          <form
            className="strategic-objectives-form"
            onSubmit={(event) => {
              event.preventDefault();

              const data =
                new FormData(
                  event.currentTarget,
                );

              const title =
                String(
                  data.get(
                    "title",
                  )
                  ?? "",
                ).trim();

              const successMetric =
                String(
                  data.get(
                    "successMetric",
                  )
                  ?? "",
                ).trim();

              if (
                title === ""
                || successMetric === ""
                || selectedReviewId === ""
              ) {
                return;
              }

              const rawTarget =
                String(
                  data.get(
                    "targetAt",
                  )
                  ?? "",
                ).trim();

              const request:
                CreatePortfolioObjectiveRequest = {
                  portfolioReviewId:
                    selectedReviewId,

                  title,

                  description:
                    String(
                      data.get(
                        "description",
                      )
                      ?? "",
                    ).trim(),

                  priority:
                    String(
                      data.get(
                        "priority",
                      )
                      ?? "HIGH",
                    ) as PortfolioObjectivePriority,

                  owner:
                    String(
                      data.get(
                        "owner",
                      )
                      ?? "",
                    ).trim()
                    || "Executive Resilience Office",

                  targetAt:
                    rawTarget === ""
                      ? null
                      : new Date(
                        rawTarget,
                      ).toISOString(),

                  successMetric,
                };

              setBusy(true);
              setError(null);

              void createPortfolioObjective(
                request,
              )
                .then((response) => {
                  setBoard(response);

                  event.currentTarget.reset();
                })
                .catch(
                  (
                    caughtError:
                      unknown,
                  ) => {
                    setError(
                      caughtError
                        instanceof Error
                        ? caughtError.message
                        : "Unable to create strategic objective.",
                    );
                  },
                )
                .finally(() => {
                  setBusy(false);
                });
            }}
          >
            <label>
              <span>
                Executive review
              </span>

              <select
                value={
                  selectedReviewId
                }
                onChange={(event) =>
                  setSelectedReviewId(
                    event.target.value,
                  )
                }
              >
                {reviewOptions.map(
                  (review) => (
                    <option
                      key={review.id}
                      value={review.id}
                    >
                      Review #
                      {review.reviewNumber}
                      {" · "}
                      {review.trend}
                    </option>
                  ),
                )}
              </select>
            </label>

            <label>
              <span>
                Priority
              </span>

              <select
                name="priority"
                defaultValue="HIGH"
              >
                <option value="LOW">
                  Low
                </option>

                <option value="MEDIUM">
                  Medium
                </option>

                <option value="HIGH">
                  High
                </option>

                <option value="CRITICAL">
                  Critical
                </option>
              </select>
            </label>

            <label className="strategic-objectives-form-wide">
              <span>
                Objective
              </span>

              <input
                required
                name="title"
                maxLength={300}
                placeholder="e.g. Raise verified audit-ledger coverage"
              />
            </label>

            <label>
              <span>
                Owner
              </span>

              <input
                name="owner"
                maxLength={120}
                defaultValue="Executive Resilience Office"
              />
            </label>

            <label>
              <span>
                Target date
              </span>

              <input
                name="targetAt"
                type="datetime-local"
              />
            </label>

            <label className="strategic-objectives-form-wide">
              <span>
                Success metric
              </span>

              <textarea
                required
                name="successMetric"
                maxLength={1200}
                placeholder="Define the measurable success condition."
              />
            </label>

            <label className="strategic-objectives-form-wide">
              <span>
                Description
              </span>

              <textarea
                name="description"
                maxLength={2500}
                placeholder="Governance rationale, scope, and expected outcome."
              />
            </label>

            <button
              type="submit"
              disabled={busy}
            >
              <Target
                size={13}
              />

              Create strategic objective
            </button>
          </form>
        )
      }

      <div className="strategic-objectives-list">
        {loading
          && board === null
          ? (
            <div className="strategic-objectives-empty">
              Loading governance objectives…
            </div>
          )
          : board === null
            || board.objectives.length
              === 0
            ? (
              <div className="strategic-objectives-empty">
                No strategic resilience
                objectives yet.
              </div>
            )
            : board.objectives.map(
              (objective) => {
                const overdue =
                  isOverdue(
                    objective.status,
                    objective.targetAt,
                  );

                const mutable =
                  objective.status
                    === "PLANNED"
                  || objective.status
                    === "IN_PROGRESS"
                  || objective.status
                    === "BLOCKED";

                return (
                  <article
                    key={objective.id}
                    className={
                      overdue
                        ? "strategic-objective-card strategic-objective-card--overdue"
                        : "strategic-objective-card"
                    }
                  >
                    <div className="strategic-objective-top">
                      <div>
                        <span
                          className={
                            objectiveStatusClass(
                              objective.status,
                            )
                          }
                        >
                          {objective.status}
                        </span>

                        <span className="strategic-objective-priority">
                          {objective.priority}
                        </span>
                      </div>

                      <span>
                        Review #
                        {
                          objective
                            .portfolioReviewNumber
                        }
                      </span>
                    </div>

                    <strong className="strategic-objective-title">
                      {objective.title}
                    </strong>

                    {objective.description !== "" && (
                      <p>
                        {objective.description}
                      </p>
                    )}

                    <div className="strategic-objective-meta">
                      <div>
                        <UserRound
                          size={11}
                        />

                        <span>
                          {objective.owner}
                        </span>
                      </div>

                      <div>
                        {overdue
                          ? (
                            <CircleAlert
                              size={11}
                            />
                          )
                          : (
                            <CalendarClock
                              size={11}
                            />
                          )
                        }

                        <span>
                          {formatDate(
                            objective.targetAt,
                          )}
                        </span>
                      </div>

                      <div>
                        <Target
                          size={11}
                        />

                        <span>
                          {objective.progressPct}%
                        </span>
                      </div>
                    </div>

                    <div className="strategic-objective-progress">
                      <div
                        style={{
                          width:
                            `${objective.progressPct}%`,
                        }}
                      />
                    </div>

                    <div className="strategic-objective-metric">
                      <span>
                        Success metric
                      </span>

                      <p>
                        {objective.successMetric}
                      </p>
                    </div>

                    {objective.completionEvidence !== "" && (
                      <div className="strategic-objective-evidence">
                        <CheckCircle2
                          size={13}
                        />

                        <div>
                          <span>
                            Completion evidence
                          </span>

                          <p>
                            {
                              objective
                                .completionEvidence
                            }
                          </p>
                        </div>
                      </div>
                    )}

                    {overdue && (
                      <div className="strategic-objective-overdue">
                        <CircleAlert
                          size={12}
                        />

                        Strategic objective is overdue
                      </div>
                    )}

                    {mutable && (
                      <>
                        <select
                          value={
                            objective.status
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updatePortfolioObjectiveStatus(
                                objective.id,
                                event.target.value as PortfolioObjectiveStatus,
                              ),
                            )
                          }
                        >
                          <option value="PLANNED">
                            Planned
                          </option>

                          <option value="IN_PROGRESS">
                            In progress
                          </option>

                          <option value="BLOCKED">
                            Blocked
                          </option>

                          <option value="CANCELLED">
                            Cancelled
                          </option>
                        </select>

                        <form
                          className="strategic-objective-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const owner =
                              String(
                                data.get(
                                  "owner",
                                )
                                ?? "",
                              ).trim();

                            if (
                              owner === ""
                            ) {
                              return;
                            }

                            applyMutation(
                              updatePortfolioObjectiveOwner(
                                objective.id,
                                owner,
                              ),
                            );
                          }}
                        >
                          <input
                            name="owner"
                            maxLength={120}
                            defaultValue={
                              objective.owner
                            }
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Assign
                          </button>
                        </form>

                        <form
                          className="strategic-objective-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const raw =
                              String(
                                data.get(
                                  "targetAt",
                                )
                                ?? "",
                              ).trim();

                            applyMutation(
                              updatePortfolioObjectiveTarget(
                                objective.id,
                                raw === ""
                                  ? null
                                  : new Date(
                                    raw,
                                  ).toISOString(),
                              ),
                            );
                          }}
                        >
                          <input
                            name="targetAt"
                            type="datetime-local"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Target
                          </button>
                        </form>

                        <form
                          className="strategic-objective-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const progress =
                              Number(
                                data.get(
                                  "progress",
                                ),
                              );

                            if (
                              !Number.isInteger(
                                progress,
                              )
                              || progress < 0
                              || progress > 99
                            ) {
                              setError(
                                "Progress must be an integer from 0 to 99. Use completion for 100%.",
                              );
                              return;
                            }

                            applyMutation(
                              updatePortfolioObjectiveProgress(
                                objective.id,
                                progress,
                              ),
                            );
                          }}
                        >
                          <input
                            required
                            name="progress"
                            type="number"
                            min="0"
                            max="99"
                            defaultValue={
                              objective.progressPct
                            }
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Progress
                          </button>
                        </form>

                        <form
                          className="strategic-objective-complete"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const evidence =
                              String(
                                data.get(
                                  "completionEvidence",
                                )
                                ?? "",
                              ).trim();

                            if (
                              evidence === ""
                            ) {
                              return;
                            }

                            applyMutation(
                              completePortfolioObjective(
                                objective.id,
                                evidence,
                              ),
                            );
                          }}
                        >
                          <textarea
                            required
                            name="completionEvidence"
                            maxLength={3000}
                            placeholder="Governance evidence proving the success metric was achieved."
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            <CheckCircle2
                              size={13}
                            />

                            Complete objective
                          </button>
                        </form>
                      </>
                    )}
                  </article>
                );
              },
            )
        }
      </div>
    </section>
  );
}
