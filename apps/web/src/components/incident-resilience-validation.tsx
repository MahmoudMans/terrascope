"use client";

import {
  CheckCircle2,
  CircleAlert,
  RefreshCw,
  ShieldCheck,
  Target,
  Trash2,
  UserRound,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createPreparednessCorrectiveAction,
  deletePreparednessCorrectiveAction,
  fetchResilienceValidationBoard,
  updatePreparednessCorrectiveDueAt,
  updatePreparednessCorrectiveOwner,
  updatePreparednessCorrectiveStatus,
} from "@/lib/api";

import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  IncidentStatus,
} from "@/types/incidents";

import type {
  CorrectiveActionStatus,
  ImprovementValidationStatus,
  ResilienceValidationBoardResponse,
} from "@/types/resilience-validation";

type IncidentResilienceValidationProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

function validationClass(
  status:
    ImprovementValidationStatus,
): string {
  return (
    `resilience-validation-state resilience-validation-state--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function correctiveStatusClass(
  status:
    CorrectiveActionStatus,
): string {
  return (
    `resilience-corrective-status resilience-corrective-status--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function isOverdue(
  status:
    CorrectiveActionStatus,
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

function formatDate(
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

export function IncidentResilienceValidation({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentResilienceValidationProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      ResilienceValidationBoardResponse | null
    >(null);

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

    void fetchResilienceValidationBoard(
      incidentId,
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setBoard(response);
        setError(null);
      })
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
              : "Unable to load resilience validation.",
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

    return fetchResilienceValidationBoard(
      incidentId,
    )
      .then((response) => {
        setBoard(response);
        setError(null);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const applyMutation = (
    operation:
      Promise<ResilienceValidationBoardResponse>,
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
              : "Unable to update resilience validation.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const closed =
    incidentStatus === "CLOSED";

  return (
    <section className="resilience-validation">
      <div className="resilience-validation-heading">
        <div>
          <span className="panel-kicker">
            RESILIENCE ASSURANCE
          </span>

          <strong>
            Validation & corrective-action closure
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
                      : "Unable to refresh resilience validation.",
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

      <div className="resilience-validation-summary">
        <div>
          <span>
            Improvements
          </span>

          <strong>
            {board
              ?.completedImprovementCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Validated
          </span>

          <strong>
            {board?.validatedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Revalidate
          </span>

          <strong>
            {board
              ?.needsRevalidationCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Failed
          </span>

          <strong>
            {board?.failedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Untested
          </span>

          <strong>
            {board?.untestedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Open corrective
          </span>

          <strong>
            {(
              board
                ?.openCorrectiveActionCount
              ?? 0
            )
            + (
              board
                ?.inProgressCorrectiveActionCount
              ?? 0
            )}
          </strong>
        </div>

        <div>
          <span>
            Overdue
          </span>

          <strong>
            {board
              ?.overdueCorrectiveActionCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Avg readiness
          </span>

          <strong>
            {board
              ?.averageLatestReadinessScore
              .toFixed(1)
              ?? "0.0"}
            /100
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="resilience-validation-error">
          {error}
        </div>
      )}

      {!closed && (
        <div className="resilience-validation-message">
          <ShieldCheck
            size={15}
          />

          Resilience validation is available
          after incident closure.
        </div>
      )}

      <div className="resilience-validation-grid">
        <section className="resilience-validation-improvements">
          <div className="resilience-section-heading">
            <ShieldCheck
              size={14}
            />

            <strong>
              Improvement validation state
            </strong>

            <span>
              {board?.validations.length ?? 0}
            </span>
          </div>

          {loading
            && board === null
            ? (
              <div className="resilience-validation-empty">
                Loading validation state…
              </div>
            )
            : board === null
              || board.validations.length
                === 0
              ? (
                <div className="resilience-validation-empty">
                  No completed improvements are
                  available for validation.
                </div>
              )
              : board.validations.map(
                (validation) => (
                  <article
                    key={
                      validation
                        .improvementActionId
                    }
                    className="resilience-validation-card"
                  >
                    <div className="resilience-validation-card-top">
                      <strong>
                        {validation.title}
                      </strong>

                      <span
                        className={
                          validationClass(
                            validation.validationStatus,
                          )
                        }
                      >
                        {
                          validation
                            .validationStatus
                        }
                      </span>
                    </div>

                    <div className="resilience-validation-metrics">
                      <div>
                        <span>
                          Exercises
                        </span>

                        <strong>
                          {
                            validation
                              .totalExerciseCount
                          }
                        </strong>
                      </div>

                      <div>
                        <span>
                          Latest result
                        </span>

                        <strong>
                          {
                            validation
                              .latestValidationResult
                            ?? "—"
                          }
                        </strong>
                      </div>

                      <div>
                        <span>
                          Readiness
                        </span>

                        <strong>
                          {
                            validation
                              .latestReadinessScore
                            ?? "—"
                          }
                        </strong>
                      </div>

                      <div>
                        <span>
                          Open corrective
                        </span>

                        <strong>
                          {
                            validation
                              .openCorrectiveActionCount
                          }
                        </strong>
                      </div>
                    </div>

                    {closed
                      && validation
                        .latestExerciseId
                        !== null
                      && (
                        validation
                          .validationStatus
                          === "NEEDS_REVALIDATION"
                        || validation
                          .validationStatus
                          === "FAILED"
                      ) && (
                        <form
                          className="resilience-corrective-create"
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

                            if (
                              title === ""
                              || validation
                                .latestExerciseId
                                === null
                            ) {
                              return;
                            }

                            const rawDue =
                              String(
                                data.get(
                                  "dueAt",
                                )
                                ?? "",
                              ).trim();

                            const priority =
                              String(
                                data.get(
                                  "priority",
                                )
                                ?? "HIGH",
                              ) as IncidentActionPriority;

                            applyMutation(
                              createPreparednessCorrectiveAction(
                                incidentId,
                                validation
                                  .latestExerciseId,
                                {
                                  title,

                                  description:
                                    String(
                                      data.get(
                                        "description",
                                      )
                                      ?? "",
                                    ).trim(),

                                  priority,

                                  owner:
                                    String(
                                      data.get(
                                        "owner",
                                      )
                                      ?? "",
                                    ).trim()
                                    || "Preparedness Office",

                                  dueAt:
                                    rawDue === ""
                                      ? null
                                      : new Date(
                                        rawDue,
                                      ).toISOString(),
                                },
                              ),
                            );
                          }}
                        >
                          <input
                            required
                            name="title"
                            maxLength={300}
                            placeholder="Corrective action"
                          />

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

                          <input
                            name="owner"
                            maxLength={120}
                            defaultValue="Preparedness Office"
                          />

                          <input
                            name="dueAt"
                            type="datetime-local"
                          />

                          <textarea
                            name="description"
                            maxLength={2000}
                            placeholder="Corrective-action description"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            <Target
                              size={12}
                            />

                            Add corrective action
                          </button>
                        </form>
                      )}
                  </article>
                ),
              )
          }
        </section>

        <section className="resilience-corrective-register">
          <div className="resilience-section-heading">
            <Target
              size={14}
            />

            <strong>
              Corrective-action register
            </strong>

            <span>
              {
                board
                  ?.totalCorrectiveActionCount
                ?? 0
              }
            </span>
          </div>

          {board === null
            || board.correctiveActions.length
              === 0
            ? (
              <div className="resilience-validation-empty">
                No exercise corrective actions.
              </div>
            )
            : board.correctiveActions.map(
              (action) => {
                const overdue =
                  isOverdue(
                    action.status,
                    action.dueAt,
                  );

                const mutable =
                  action.status
                    === "OPEN"
                  || action.status
                    === "IN_PROGRESS";

                return (
                  <article
                    key={action.id}
                    className={
                      overdue
                        ? "resilience-corrective-card resilience-corrective-card--overdue"
                        : "resilience-corrective-card"
                    }
                  >
                    <div className="resilience-corrective-top">
                      <div>
                        <span
                          className={
                            correctiveStatusClass(
                              action.status,
                            )
                          }
                        >
                          {action.status}
                        </span>

                        <span className="resilience-corrective-priority">
                          {action.priority}
                        </span>
                      </div>

                      {closed && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            applyMutation(
                              deletePreparednessCorrectiveAction(
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

                    <strong className="resilience-corrective-title">
                      {action.title}
                    </strong>

                    {action.description !== "" && (
                      <p>
                        {action.description}
                      </p>
                    )}

                    <div className="resilience-corrective-meta">
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
                          : (
                            <CheckCircle2
                              size={11}
                            />
                          )
                        }

                        <span>
                          {formatDate(
                            action.dueAt,
                          )}
                        </span>
                      </div>
                    </div>

                    {overdue && (
                      <div className="resilience-corrective-overdue">
                        <CircleAlert
                          size={12}
                        />

                        Corrective action is overdue
                      </div>
                    )}

                    {closed && mutable && (
                      <>
                        <select
                          className="resilience-corrective-select"
                          value={
                            action.status
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updatePreparednessCorrectiveStatus(
                                incidentId,
                                action.id,
                                event.target.value as CorrectiveActionStatus,
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

                          <option value="WAIVED">
                            Waived
                          </option>
                        </select>

                        <form
                          className="resilience-corrective-inline"
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
                              updatePreparednessCorrectiveOwner(
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
                          className="resilience-corrective-inline"
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

                            applyMutation(
                              updatePreparednessCorrectiveDueAt(
                                incidentId,
                                action.id,
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
        </section>
      </div>
    </section>
  );
}
