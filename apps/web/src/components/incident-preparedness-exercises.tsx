"use client";

import {
  Beaker,
  CalendarClock,
  CheckCircle2,
  CircleAlert,
  FlaskConical,
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

import type {
  FormEvent,
} from "react";

import {
  assessPreparednessExercise,
  createPreparednessExercise,
  deletePreparednessExercise,
  fetchIncidentImprovementBoard,
  fetchPreparednessExerciseBoard,
  updatePreparednessExerciseOwner,
  updatePreparednessExerciseSchedule,
  updatePreparednessExerciseStatus,
} from "@/lib/api";

import type {
  ImprovementActionBoardResponse,
} from "@/types/improvement-actions";

import type {
  IncidentStatus,
} from "@/types/incidents";

import type {
  AssessPreparednessExerciseRequest,
  CreatePreparednessExerciseRequest,
  PreparednessExerciseBoardResponse,
  PreparednessExerciseStatus,
  PreparednessExerciseType,
  PreparednessValidationResult,
} from "@/types/preparedness-exercises";

type IncidentPreparednessExercisesProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialDraft = {
  exerciseType:
    "TABLETOP" as PreparednessExerciseType,

  title: "",
  description: "",
  owner: "Preparedness Office",
};

function exerciseTypeLabel(
  value: PreparednessExerciseType,
): string {
  return value
    .replaceAll(
      "_",
      " ",
    )
    .toLowerCase()
    .replace(
      /\b\w/g,
      (character) =>
        character.toUpperCase(),
    );
}

function statusClass(
  status: PreparednessExerciseStatus,
): string {
  return (
    `preparedness-status preparedness-status--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function resultClass(
  result: PreparednessValidationResult,
): string {
  return (
    `preparedness-result preparedness-result--${result.toLowerCase().replaceAll("_", "-")}`
  );
}

function isOverdue(
  status: PreparednessExerciseStatus,
  scheduledAt: string | null,
): boolean {
  if (
    status !== "PLANNED"
    && status !== "IN_PROGRESS"
  ) {
    return false;
  }

  if (
    scheduledAt === null
  ) {
    return false;
  }

  return (
    new Date(
      scheduledAt,
    ).getTime()
    < Date.now()
  );
}

function formatTimestamp(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "Not scheduled";
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

export function IncidentPreparednessExercises({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentPreparednessExercisesProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      PreparednessExerciseBoardResponse | null
    >(null);

  const [
    improvements,
    setImprovements,
  ] =
    useState<
      ImprovementActionBoardResponse | null
    >(null);

  const [
    selectedImprovementId,
    setSelectedImprovementId,
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
    scheduleDraft,
    setScheduleDraft,
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
      fetchPreparednessExerciseBoard(
        incidentId,
        controller.signal,
      ),

      fetchIncidentImprovementBoard(
        incidentId,
        controller.signal,
      ),
    ])
      .then(
        ([
          boardResponse,
          improvementResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setBoard(
            boardResponse,
          );

          setImprovements(
            improvementResponse,
          );

          const completedImprovement =
            improvementResponse
              .actions
              .find(
                (action) =>
                  action.status
                  === "COMPLETED",
              );

          if (
            completedImprovement
            !== undefined
          ) {
            setSelectedImprovementId(
              completedImprovement.id,
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
              : "Unable to load preparedness validation.",
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
      fetchPreparednessExerciseBoard(
        incidentId,
      ),

      fetchIncidentImprovementBoard(
        incidentId,
      ),
    ])
      .then(
        ([
          boardResponse,
          improvementResponse,
        ]) => {
          setBoard(
            boardResponse,
          );

          setImprovements(
            improvementResponse,
          );

          if (
            selectedImprovementId
            === ""
          ) {
            const completedImprovement =
              improvementResponse
                .actions
                .find(
                  (action) =>
                    action.status
                    === "COMPLETED",
                );

            if (
              completedImprovement
              !== undefined
            ) {
              setSelectedImprovementId(
                completedImprovement.id,
              );
            }
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
      Promise<PreparednessExerciseBoardResponse>,
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
              : "Unable to update preparedness exercise.",
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
      || selectedImprovementId
        === ""
      || draft.title.trim()
        === ""
    ) {
      return;
    }

    const request:
      CreatePreparednessExerciseRequest = {
        improvementActionId:
          selectedImprovementId,

        exerciseType:
          draft.exerciseType,

        title:
          draft.title.trim(),

        description:
          draft.description.trim(),

        owner:
          draft.owner.trim()
          || "Unassigned",

        scheduledAt:
          scheduleDraft === ""
            ? null
            : new Date(
              scheduleDraft,
            ).toISOString(),
      };

    setBusy(true);
    setError(null);

    void createPreparednessExercise(
      incidentId,
      request,
    )
      .then((response) => {
        setBoard(response);

        setDraft(
          initialDraft,
        );

        setScheduleDraft("");

        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create preparedness exercise.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const closed =
    incidentStatus === "CLOSED";

  const completedImprovements =
    improvements
      ?.actions
      .filter(
        (action) =>
          action.status
          === "COMPLETED",
      )
    ?? [];

  return (
    <section className="preparedness-board">
      <div className="preparedness-heading">
        <div>
          <span className="panel-kicker">
            CONTINUOUS IMPROVEMENT
          </span>

          <strong>
            Preparedness exercise & validation center
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
                      : "Unable to refresh preparedness validation.",
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

      <div className="preparedness-summary">
        <div>
          <span>
            Exercises
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
            Passed
          </span>

          <strong>
            {board?.passedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Partial / failed
          </span>

          <strong>
            {board?.partialCount ?? 0}
            {" / "}
            {board?.failedCount ?? 0}
          </strong>
        </div>

        <div
          className={
            (board?.overdueCount ?? 0)
            > 0
              ? "preparedness-summary-alert"
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

        <div className="preparedness-score-summary">
          <span>
            Avg readiness
          </span>

          <strong>
            {
              board
                ?.averageReadinessScore
                .toFixed(1)
              ?? "0.0"
            }
            /100
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="preparedness-error">
          {error}
        </div>
      )}

      {!closed && (
        <div className="preparedness-state-message">
          <ShieldCheck
            size={15}
          />

          Preparedness validation becomes available
          after incident closure.
        </div>
      )}

      {closed
        && completedImprovements.length
          === 0 && (
        <div className="preparedness-state-message">
          <Target
            size={15}
          />

          Complete at least one lessons-learned
          improvement action before scheduling
          a validation exercise.
        </div>
      )}

      {closed
        && completedImprovements.length
          > 0 && (
        <form
          className="preparedness-form"
          onSubmit={handleCreate}
        >
          <label>
            <span>
              Improvement to validate
            </span>

            <select
              value={
                selectedImprovementId
              }
              onChange={(event) =>
                setSelectedImprovementId(
                  event.target.value,
                )
              }
            >
              {completedImprovements.map(
                (improvement) => (
                  <option
                    key={
                      improvement.id
                    }
                    value={
                      improvement.id
                    }
                  >
                    {improvement.title}
                  </option>
                ),
              )}
            </select>
          </label>

          <label>
            <span>
              Exercise type
            </span>

            <select
              value={
                draft.exerciseType
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  exerciseType:
                    event.target.value as PreparednessExerciseType,
                })
              }
            >
              <option value="TABLETOP">
                Tabletop
              </option>

              <option value="FUNCTIONAL">
                Functional
              </option>

              <option value="FULL_SCALE">
                Full scale
              </option>

              <option value="TECHNICAL">
                Technical
              </option>

              <option value="COMMUNICATIONS">
                Communications
              </option>

              <option value="OTHER">
                Other
              </option>
            </select>
          </label>

          <label className="preparedness-form-wide">
            <span>
              Exercise title
            </span>

            <input
              required
              maxLength={300}
              value={
                draft.title
              }
              placeholder="e.g. Validate incident command handoff"
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
              Scheduled
            </span>

            <input
              type="datetime-local"
              value={
                scheduleDraft
              }
              onChange={(event) =>
                setScheduleDraft(
                  event.target.value,
                )
              }
            />
          </label>

          <label className="preparedness-form-wide">
            <span>
              Scenario / objective
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
            className="preparedness-create"
            disabled={
              busy
              || selectedImprovementId
                === ""
            }
          >
            <FlaskConical
              size={13}
            />

            Schedule validation exercise
          </button>
        </form>
      )}

      <div className="preparedness-list">
        {loading
          && board === null
          ? (
            <div className="preparedness-empty">
              Loading preparedness exercises…
            </div>
          )
          : board === null
            || board.exercises.length
              === 0
            ? (
              <div className="preparedness-empty preparedness-empty--large">
                <Beaker
                  size={28}
                />

                <strong>
                  No preparedness exercises
                </strong>

                <span>
                  Complete an improvement action,
                  then validate it through a
                  documented exercise.
                </span>
              </div>
            )
            : board.exercises.map(
              (exercise) => {
                const overdue =
                  isOverdue(
                    exercise.status,
                    exercise.scheduledAt,
                  );

                const mutable =
                  exercise.status
                    === "PLANNED"
                  || exercise.status
                    === "IN_PROGRESS";

                return (
                  <article
                    key={
                      exercise.id
                    }
                    className={
                      overdue
                        ? "preparedness-card preparedness-card--overdue"
                        : exercise.validationResult
                          === "PASSED"
                          ? "preparedness-card preparedness-card--passed"
                          : "preparedness-card"
                    }
                  >
                    <div className="preparedness-card-top">
                      <div>
                        <span
                          className={
                            statusClass(
                              exercise.status,
                            )
                          }
                        >
                          {exercise.status}
                        </span>

                        <span
                          className={
                            resultClass(
                              exercise.validationResult,
                            )
                          }
                        >
                          {
                            exercise
                              .validationResult
                          }
                        </span>
                      </div>

                      {closed && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            applyMutation(
                              deletePreparednessExercise(
                                incidentId,
                                exercise.id,
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

                    <div className="preparedness-card-title">
                      <span>
                        {exerciseTypeLabel(
                          exercise.exerciseType,
                        )}
                      </span>

                      <strong>
                        {exercise.title}
                      </strong>
                    </div>

                    {exercise.description
                      !== "" && (
                      <p>
                        {exercise.description}
                      </p>
                    )}

                    <div className="preparedness-meta">
                      <div>
                        <UserRound
                          size={11}
                        />

                        <span>
                          {exercise.owner}
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
                          {formatTimestamp(
                            exercise.scheduledAt,
                          )}
                        </span>
                      </div>

                      <div>
                        <ShieldCheck
                          size={11}
                        />

                        <span>
                          Score:
                          {" "}
                          {
                            exercise
                              .readinessScore
                            ?? "—"
                          }
                        </span>
                      </div>
                    </div>

                    {overdue && (
                      <div className="preparedness-overdue">
                        <CircleAlert
                          size={12}
                        />

                        Preparedness exercise is overdue
                      </div>
                    )}

                    {exercise.validationResult
                      !== "NOT_ASSESSED" && (
                      <div className="preparedness-assessment-result">
                        <div>
                          <span>
                            Evidence
                          </span>

                          <p>
                            {exercise.evidenceSummary
                              || "No evidence summary."
                            }
                          </p>
                        </div>

                        <div>
                          <span>
                            Findings
                          </span>

                          <p>
                            {exercise.findings
                              || "No findings recorded."
                            }
                          </p>
                        </div>

                        <div>
                          <span>
                            Corrective follow-up
                          </span>

                          <p>
                            {exercise.correctiveAction
                              || "No corrective follow-up."
                            }
                          </p>
                        </div>
                      </div>
                    )}

                    {closed && mutable && (
                      <>
                        <select
                          className="preparedness-select"
                          value={
                            exercise.status
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updatePreparednessExerciseStatus(
                                incidentId,
                                exercise.id,
                                event.target.value as PreparednessExerciseStatus,
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

                          <option value="CANCELLED">
                            Cancelled
                          </option>
                        </select>

                        <form
                          className="preparedness-inline"
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
                              updatePreparednessExerciseOwner(
                                incidentId,
                                exercise.id,
                                owner,
                              ),
                            );
                          }}
                        >
                          <input
                            name="owner"
                            maxLength={120}
                            defaultValue={
                              exercise.owner
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
                          className="preparedness-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const raw =
                              String(
                                data.get(
                                  "scheduledAt",
                                )
                                ?? "",
                              ).trim();

                            const scheduledAt =
                              raw === ""
                                ? null
                                : new Date(
                                  raw,
                                ).toISOString();

                            applyMutation(
                              updatePreparednessExerciseSchedule(
                                incidentId,
                                exercise.id,
                                scheduledAt,
                              ),
                            );
                          }}
                        >
                          <input
                            name="scheduledAt"
                            type="datetime-local"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Schedule
                          </button>
                        </form>

                        <form
                          className="preparedness-assessment-form"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const result =
                              String(
                                data.get(
                                  "result",
                                )
                                ?? "PASSED",
                              ) as AssessPreparednessExerciseRequest["result"];

                            const rawScore =
                              Number(
                                data.get(
                                  "score",
                                ),
                              );

                            if (
                              !Number.isFinite(
                                rawScore,
                              )
                              || rawScore < 0
                              || rawScore > 100
                            ) {
                              setError(
                                "Readiness score must be between 0 and 100.",
                              );
                              return;
                            }

                            const performedRaw =
                              String(
                                data.get(
                                  "performedAt",
                                )
                                ?? "",
                              ).trim();

                            const request:
                              AssessPreparednessExerciseRequest = {
                                result,

                                readinessScore:
                                  rawScore,

                                performedAt:
                                  performedRaw === ""
                                    ? null
                                    : new Date(
                                      performedRaw,
                                    ).toISOString(),

                                evidenceSummary:
                                  String(
                                    data.get(
                                      "evidence",
                                    )
                                    ?? "",
                                  ).trim(),

                                findings:
                                  String(
                                    data.get(
                                      "findings",
                                    )
                                    ?? "",
                                  ).trim(),

                                correctiveAction:
                                  String(
                                    data.get(
                                      "correctiveAction",
                                    )
                                    ?? "",
                                  ).trim(),
                              };

                            applyMutation(
                              assessPreparednessExercise(
                                incidentId,
                                exercise.id,
                                request,
                              ),
                            );
                          }}
                        >
                          <div className="preparedness-assessment-heading">
                            <CheckCircle2
                              size={13}
                            />

                            <strong>
                              Exercise assessment
                            </strong>
                          </div>

                          <select
                            name="result"
                            defaultValue="PASSED"
                          >
                            <option value="PASSED">
                              Passed
                            </option>

                            <option value="PARTIAL">
                              Partial
                            </option>

                            <option value="FAILED">
                              Failed
                            </option>
                          </select>

                          <input
                            required
                            name="score"
                            type="number"
                            min="0"
                            max="100"
                            defaultValue="80"
                            placeholder="Score 0-100"
                          />

                          <input
                            name="performedAt"
                            type="datetime-local"
                          />

                          <textarea
                            name="evidence"
                            maxLength={2000}
                            placeholder="Evidence summary"
                          />

                          <textarea
                            name="findings"
                            maxLength={3000}
                            placeholder="Exercise findings"
                          />

                          <textarea
                            name="correctiveAction"
                            maxLength={2000}
                            placeholder="Corrective follow-up"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            <ShieldCheck
                              size={13}
                            />

                            Record assessment
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
