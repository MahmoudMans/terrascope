"use client";

import {
  Archive,
  CheckCircle2,
  CircleAlert,
  ClipboardList,
  Lightbulb,
  RefreshCw,
  Target,
  Trash2,
  UserRound,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createIncidentImprovementAction,
  deleteIncidentImprovementAction,
  fetchAfterActionReviewHistory,
  fetchIncidentImprovementBoard,
  updateImprovementActionDueAt,
  updateImprovementActionOwner,
  updateImprovementActionPriority,
  updateImprovementActionStatus,
} from "@/lib/api";

import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  AfterActionReviewHistoryResponse,
} from "@/types/closure-review";

import type {
  CreateImprovementActionRequest,
  ImprovementActionBoardResponse,
  ImprovementActionStatus,
  ImprovementCategory,
} from "@/types/improvement-actions";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentImprovementBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialDraft = {
  category: "PROCESS" as ImprovementCategory,
  title: "",
  description: "",
  priority: "HIGH" as IncidentActionPriority,
  owner: "Unassigned",
};

function categoryLabel(
  category:
    ImprovementCategory,
): string {
  switch (category) {
    case "PEOPLE":
      return "People";

    case "PROCESS":
      return "Process";

    case "TECHNOLOGY":
      return "Technology";

    case "COMMUNICATIONS":
      return "Communications";

    case "LOGISTICS":
      return "Logistics";

    case "INFRASTRUCTURE":
      return "Infrastructure";

    case "OTHER":
      return "Other";
  }
}

function statusClass(
  status:
    ImprovementActionStatus,
): string {
  return (
    `improvement-status improvement-status--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function priorityClass(
  priority:
    IncidentActionPriority,
): string {
  return (
    `improvement-priority improvement-priority--${priority.toLowerCase()}`
  );
}

function isOverdue(
  status:
    ImprovementActionStatus,
  dueAt:
    string | null,
): boolean {
  if (
    status !== "OPEN"
    && status !== "IN_PROGRESS"
  ) {
    return false;
  }

  if (
    dueAt === null
  ) {
    return false;
  }

  return (
    new Date(
      dueAt,
    ).getTime()
    < Date.now()
  );
}

function formatTimestamp(
  value:
    string | null,
): string {
  if (
    value === null
  ) {
    return "No due date";
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

export function IncidentImprovementBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentImprovementBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      ImprovementActionBoardResponse | null
    >(null);

  const [
    reviews,
    setReviews,
  ] =
    useState<
      AfterActionReviewHistoryResponse | null
    >(null);

  const [
    selectedReviewId,
    setSelectedReviewId,
  ] =
    useState("");

  const [
    draft,
    setDraft,
  ] =
    useState(
      initialDraft,
    );

  const [
    dueDraft,
    setDueDraft,
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
      fetchIncidentImprovementBoard(
        incidentId,
        controller.signal,
      ),

      fetchAfterActionReviewHistory(
        incidentId,
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
            reviewResponse
              .reviews
              .length > 0
          ) {
            setSelectedReviewId(
              reviewResponse
                .reviews[0]
                .id,
            );
          }

          setError(null);
        },
      )
      .catch(
        (caughtError: unknown) => {
          if (
            controller.signal.aborted
            || (
              caughtError
                instanceof DOMException
              && caughtError.name
                === "AbortError"
            )
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load improvement register.",
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
  }, [
    incidentId,
  ]);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchIncidentImprovementBoard(
        incidentId,
      ),

      fetchAfterActionReviewHistory(
        incidentId,
      ),
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
            && reviewResponse
              .reviews
              .length > 0
          ) {
            setSelectedReviewId(
              reviewResponse
                .reviews[0]
                .id,
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
      Promise<ImprovementActionBoardResponse>,
  ) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        setBoard(response);
        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update improvement register.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleCreate = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      incidentStatus !== "CLOSED"
      || selectedReviewId === ""
      || draft.title.trim() === ""
    ) {
      return;
    }

    let dueAt:
      string | null = null;

    if (
      dueDraft.trim() !== ""
    ) {
      dueAt =
        new Date(
          dueDraft,
        ).toISOString();
    }

    const request:
      CreateImprovementActionRequest = {
        afterActionReviewId:
          selectedReviewId,

        category:
          draft.category,

        title:
          draft.title.trim(),

        description:
          draft.description.trim(),

        priority:
          draft.priority,

        owner:
          draft.owner.trim()
          || "Unassigned",

        dueAt,
      };

    setBusy(true);
    setError(null);

    void createIncidentImprovementAction(
      incidentId,
      request,
    )
      .then((response) => {
        setBoard(response);

        setDraft(
          initialDraft,
        );

        setDueDraft("");

        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create improvement action.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const closed =
    incidentStatus === "CLOSED";

  const hasReviews =
    (
      reviews
      ?.reviews
      .length
      ?? 0
    ) > 0;

  return (
    <section className="improvement-board">
      <div className="improvement-heading">
        <div>
          <span className="panel-kicker">
            ORGANIZATIONAL LEARNING
          </span>

          <strong>
            Lessons learned & improvement register
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
                      : "Unable to refresh improvements.",
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

      <div className="improvement-summary">
        <div>
          <span>
            Total
          </span>

          <strong>
            {board?.totalCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Open
          </span>

          <strong>
            {board?.openCount ?? 0}
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
            Completed
          </span>

          <strong>
            {board?.completedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Dismissed
          </span>

          <strong>
            {board?.dismissedCount ?? 0}
          </strong>
        </div>

        <div
          className={
            (board?.overdueCount ?? 0)
            > 0
              ? "improvement-summary-overdue"
              : ""
          }
        >
          <span>
            Overdue
          </span>

          <strong>
            {board?.overdueCount ?? 0}
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="improvement-error">
          {error}
        </div>
      )}

      {!closed && (
        <div className="improvement-state-message">
          <Archive
            size={15}
          />

          Improvement actions become available
          after incident closure and completion
          of an after-action review.
        </div>
      )}

      {closed && !hasReviews && (
        <div className="improvement-state-message">
          <ClipboardList
            size={15}
          />

          Capture an after-action review before
          creating organizational improvement
          actions.
        </div>
      )}

      {closed && hasReviews && (
        <form
          className="improvement-form"
          onSubmit={handleCreate}
        >
          <label>
            <span>
              Source review
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
              {reviews
                ?.reviews
                .map(
                  (review) => (
                    <option
                      key={
                        review.id
                      }
                      value={
                        review.id
                      }
                    >
                      AAR #
                      {
                        review
                          .reviewNumber
                      }
                      {" · peak "}
                      {
                        review
                          .peakCommandScore
                      }
                      /100
                    </option>
                  ),
                )
              }
            </select>
          </label>

          <label>
            <span>
              Category
            </span>

            <select
              value={
                draft.category
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  category:
                    event.target.value as ImprovementCategory,
                })
              }
            >
              <option value="PEOPLE">
                People
              </option>

              <option value="PROCESS">
                Process
              </option>

              <option value="TECHNOLOGY">
                Technology
              </option>

              <option value="COMMUNICATIONS">
                Communications
              </option>

              <option value="LOGISTICS">
                Logistics
              </option>

              <option value="INFRASTRUCTURE">
                Infrastructure
              </option>

              <option value="OTHER">
                Other
              </option>
            </select>
          </label>

          <label className="improvement-form-wide">
            <span>
              Improvement action
            </span>

            <input
              required
              maxLength={300}
              value={
                draft.title
              }
              placeholder="e.g. Formalize incident command handoff checklist"
              onChange={(event) =>
                setDraft({
                  ...draft,
                  title:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>
              Priority
            </span>

            <select
              value={
                draft.priority
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  priority:
                    event.target.value as IncidentActionPriority,
                })
              }
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

          <label>
            <span>
              Owner
            </span>

            <input
              maxLength={120}
              value={
                draft.owner
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  owner:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>
              Due date
            </span>

            <input
              type="datetime-local"
              value={dueDraft}
              onChange={(event) =>
                setDueDraft(
                  event.target.value,
                )
              }
            />
          </label>

          <label className="improvement-form-wide">
            <span>
              Description
            </span>

            <textarea
              maxLength={2000}
              value={
                draft.description
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  description:
                    event.target.value,
                })
              }
            />
          </label>

          <button
            type="submit"
            className="improvement-create"
            disabled={
              busy
              || selectedReviewId
                === ""
            }
          >
            <Lightbulb
              size={13}
            />

            Add improvement action
          </button>
        </form>
      )}

      <div className="improvement-list">
        {loading
          && board === null
          ? (
            <div className="improvement-empty">
              Loading improvement register…
            </div>
          )
          : board === null
            || board.actions.length
              === 0
            ? (
              <div className="improvement-empty improvement-empty--large">
                <Target
                  size={28}
                />

                <strong>
                  No improvement actions
                </strong>

                <span>
                  Convert after-action findings
                  into owned and measurable
                  organizational improvements.
                </span>
              </div>
            )
            : board.actions.map(
              (action) => {
                const overdue =
                  isOverdue(
                    action.status,
                    action.dueAt,
                  );

                return (
                  <article
                    key={action.id}
                    className={
                      overdue
                        ? "improvement-card improvement-card--overdue"
                        : action.status === "COMPLETED"
                          ? "improvement-card improvement-card--completed"
                          : "improvement-card"
                    }
                  >
                    <div className="improvement-card-top">
                      <div>
                        <span
                          className={
                            priorityClass(
                              action.priority,
                            )
                          }
                        >
                          {action.priority}
                        </span>

                        <span
                          className={
                            statusClass(
                              action.status,
                            )
                          }
                        >
                          {action.status}
                        </span>
                      </div>

                      {closed && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            applyMutation(
                              deleteIncidentImprovementAction(
                                incidentId,
                                action.id,
                              ),
                            )
                          }
                        >
                          <Trash2
                            size={13}
                          />
                        </button>
                      )}
                    </div>

                    <div className="improvement-card-title">
                      <span>
                        {categoryLabel(
                          action.category,
                        )}
                      </span>

                      <strong>
                        {action.title}
                      </strong>
                    </div>

                    {action.description
                      !== "" && (
                      <p>
                        {action.description}
                      </p>
                    )}

                    <div className="improvement-meta">
                      <div>
                        <UserRound
                          size={11}
                        />

                        <span>
                          {action.owner}
                        </span>
                      </div>

                      <div>
                        {overdue
                          ? (
                            <CircleAlert
                              size={11}
                            />
                          )
                          : action.status
                            === "COMPLETED"
                            ? (
                              <CheckCircle2
                                size={11}
                              />
                            )
                            : (
                              <Target
                                size={11}
                              />
                            )
                        }

                        <span>
                          {formatTimestamp(
                            action.dueAt,
                          )}
                        </span>
                      </div>
                    </div>

                    {overdue && (
                      <div className="improvement-overdue">
                        <CircleAlert
                          size={12}
                        />

                        Improvement action is overdue
                      </div>
                    )}

                    {closed && (
                      <>
                        <select
                          className="improvement-select"
                          value={
                            action.status
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updateImprovementActionStatus(
                                incidentId,
                                action.id,
                                event.target.value as ImprovementActionStatus,
                              ),
                            )
                          }
                        >
                          <option value="OPEN">
                            Open
                          </option>

                          <option value="IN_PROGRESS">
                            In progress
                          </option>

                          <option value="COMPLETED">
                            Completed
                          </option>

                          <option value="DISMISSED">
                            Dismissed
                          </option>
                        </select>

                        <select
                          className="improvement-select"
                          value={
                            action.priority
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updateImprovementActionPriority(
                                incidentId,
                                action.id,
                                event.target.value as IncidentActionPriority,
                              ),
                            )
                          }
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

                        <form
                          className="improvement-inline"
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
                              updateImprovementActionOwner(
                                incidentId,
                                action.id,
                                owner,
                              ),
                            );
                          }}
                        >
                          <input
                            name="owner"
                            maxLength={120}
                            defaultValue={
                              action.owner
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
                          className="improvement-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const raw =
                              String(
                                data.get(
                                  "dueAt",
                                )
                                ?? "",
                              ).trim();

                            const dueAt =
                              raw === ""
                                ? null
                                : new Date(
                                  raw,
                                ).toISOString();

                            applyMutation(
                              updateImprovementActionDueAt(
                                incidentId,
                                action.id,
                                dueAt,
                              ),
                            );
                          }}
                        >
                          <input
                            name="dueAt"
                            type="datetime-local"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Due
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
