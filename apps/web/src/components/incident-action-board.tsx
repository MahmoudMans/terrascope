"use client";

import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  CircleDot,
  ListChecks,
  Plus,
  RefreshCw,
  Trash2,
  UserRound,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createIncidentAction,
  deleteIncidentAction,
  fetchIncidentActionBoard,
  updateIncidentActionOwner,
  updateIncidentActionPriority,
  updateIncidentActionStatus,
} from "@/lib/api";

import type {
  CreateIncidentActionRequest,
  IncidentActionBoardResponse,
  IncidentActionPriority,
  IncidentActionStatus,
} from "@/types/incident-actions";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentActionBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialAction:
  CreateIncidentActionRequest = {
    title: "",
    description: "",
    priority: "HIGH",
    owner: "Unassigned",
    dueAt: null,
  };

function priorityClass(
  priority:
    IncidentActionPriority,
): string {
  return (
    `response-action-priority response-action-priority--${priority.toLowerCase()}`
  );
}

function statusLabel(
  status: IncidentActionStatus,
): string {
  switch (status) {
    case "TODO":
      return "To do";

    case "IN_PROGRESS":
      return "In progress";

    case "BLOCKED":
      return "Blocked";

    case "DONE":
      return "Done";
  }
}

function isOverdue(
  dueAt: string | null,
  status: IncidentActionStatus,
): boolean {
  if (
    dueAt === null
    || status === "DONE"
  ) {
    return false;
  }

  return (
    new Date(dueAt).getTime()
    < Date.now()
  );
}

function formatDueAt(
  dueAt: string | null,
): string {
  if (dueAt === null) {
    return "No due date";
  }

  return new Intl.DateTimeFormat(
    "en",
    {
      dateStyle: "medium",
      timeStyle: "short",
    },
  ).format(
    new Date(dueAt),
  );
}

export function IncidentActionBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentActionBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      IncidentActionBoardResponse | null
    >(null);

  const [
    draft,
    setDraft,
  ] =
    useState<
      CreateIncidentActionRequest
    >(initialAction);

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

    void fetchIncidentActionBoard(
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
              : "Unable to load response actions.",
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

    return fetchIncidentActionBoard(
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
      Promise<IncidentActionBoardResponse>,
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
              : "Unable to update response action.",
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
      draft.title.trim() === ""
    ) {
      return;
    }

    let dueAt:
      string | null = null;

    if (
      dueDraft.trim()
      !== ""
    ) {
      dueAt =
        new Date(
          dueDraft,
        ).toISOString();
    }

    setBusy(true);
    setError(null);

    void createIncidentAction(
      incidentId,
      {
        ...draft,
        title:
          draft.title.trim(),
        description:
          draft.description.trim(),
        owner:
          draft.owner.trim()
          || "Unassigned",
        dueAt,
      },
    )
      .then((response) => {
        setBoard(response);

        setDraft(
          initialAction,
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
              : "Unable to create response action.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const readOnly =
    incidentStatus === "CLOSED";

  return (
    <section className="response-action-board">
      <div className="response-action-heading">
        <div>
          <span className="panel-kicker">
            RESPONSE COORDINATION
          </span>

          <strong>
            Action board
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
                (caughtError: unknown) => {
                  setError(
                    caughtError
                      instanceof Error
                      ? caughtError.message
                      : "Unable to refresh actions.",
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

      <div className="response-action-summary">
        <div>
          <span>
            Total
          </span>

          <strong>
            {board?.totalCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            To do
          </span>

          <strong>
            {board?.todoCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Active
          </span>

          <strong>
            {board?.inProgressCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Blocked
          </span>

          <strong>
            {board?.blockedCount
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Done
          </span>

          <strong>
            {board?.doneCount
              ?? 0}
          </strong>
        </div>

        <div
          className={
            (board?.overdueCount
              ?? 0) > 0
              ? "response-action-summary__overdue"
              : ""
          }
        >
          <span>
            Overdue
          </span>

          <strong>
            {board?.overdueCount
              ?? 0}
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="response-action-error">
          {error}
        </div>
      )}

      {!readOnly && (
        <form
          className="response-action-form"
          onSubmit={handleCreate}
        >
          <label className="response-action-field response-action-field--wide">
            <span>
              Action
            </span>

            <input
              required
              maxLength={300}
              value={draft.title}
              placeholder="e.g. Establish perimeter and validate access routes"
              onChange={(event) =>
                setDraft({
                  ...draft,
                  title:
                    event.target.value,
                })
              }
            />
          </label>

          <label className="response-action-field">
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

          <label className="response-action-field">
            <span>
              Owner
            </span>

            <input
              maxLength={120}
              value={draft.owner}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  owner:
                    event.target.value,
                })
              }
            />
          </label>

          <label className="response-action-field response-action-field--wide">
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

          <label className="response-action-field response-action-field--wide">
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
            className="response-action-create"
            disabled={busy}
          >
            <Plus size={13} />

            Add response action
          </button>
        </form>
      )}

      {readOnly && (
        <div className="response-action-closed-note">
          This incident is closed.
          Response actions are read-only.
        </div>
      )}

      <div className="response-action-list">
        {loading
          && board === null
          ? (
            <div className="response-action-empty">
              Loading actions…
            </div>
          )
          : board === null
            || board.actions.length === 0
            ? (
              <div className="response-action-empty response-action-empty--large">
                <ListChecks
                  size={27}
                />

                <strong>
                  No response actions
                </strong>

                <span>
                  Add operational tasks for
                  this incident.
                </span>
              </div>
            )
            : board.actions.map(
              (action) => {
                const overdue =
                  isOverdue(
                    action.dueAt,
                    action.status,
                  );

                return (
                  <article
                    className={
                      overdue
                        ? "response-action-card response-action-card--overdue"
                        : "response-action-card"
                    }
                    key={action.id}
                  >
                    <div className="response-action-card__heading">
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

                        {overdue && (
                          <span className="response-action-overdue">
                            <AlertTriangle
                              size={10}
                            />

                            OVERDUE
                          </span>
                        )}
                      </div>

                      {!readOnly && (
                        <button
                          type="button"
                          className="response-action-delete"
                          disabled={busy}
                          aria-label={
                            `Delete ${action.title}`
                          }
                          onClick={() =>
                            applyMutation(
                              deleteIncidentAction(
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

                    <strong className="response-action-card__title">
                      {action.title}
                    </strong>

                    {action.description
                      !== "" && (
                      <p>
                        {action.description}
                      </p>
                    )}

                    <div className="response-action-meta">
                      <span>
                        <UserRound
                          size={11}
                        />

                        {action.owner}
                      </span>

                      <span>
                        <CalendarClock
                          size={11}
                        />

                        {formatDueAt(
                          action.dueAt,
                        )}
                      </span>
                    </div>

                    <div className="response-action-controls">
                      <label>
                        <span>
                          Status
                        </span>

                        <select
                          disabled={
                            busy
                            || readOnly
                          }
                          value={
                            action.status
                          }
                          onChange={(event) =>
                            applyMutation(
                              updateIncidentActionStatus(
                                incidentId,
                                action.id,
                                event.target.value as IncidentActionStatus,
                              ),
                            )
                          }
                        >
                          <option value="TODO">
                            To do
                          </option>

                          <option value="IN_PROGRESS">
                            In progress
                          </option>

                          <option value="BLOCKED">
                            Blocked
                          </option>

                          <option value="DONE">
                            Done
                          </option>
                        </select>
                      </label>

                      <label>
                        <span>
                          Priority
                        </span>

                        <select
                          disabled={
                            busy
                            || readOnly
                          }
                          value={
                            action.priority
                          }
                          onChange={(event) =>
                            applyMutation(
                              updateIncidentActionPriority(
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
                      </label>
                    </div>

                    {!readOnly && (
                      <form
                        className="response-action-owner-inline"
                        onSubmit={(event) => {
                          event.preventDefault();

                          const form =
                            event.currentTarget;

                          const data =
                            new FormData(
                              form,
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
                            updateIncidentActionOwner(
                              incidentId,
                              action.id,
                              owner,
                            ),
                          );
                        }}
                      >
                        <input
                          name="owner"
                          defaultValue={
                            action.owner
                          }
                          maxLength={120}
                        />

                        <button
                          type="submit"
                          disabled={busy}
                        >
                          Assign
                        </button>
                      </form>
                    )}

                    <div className="response-action-state">
                      {action.status
                        === "DONE"
                        ? (
                          <>
                            <CheckCircle2
                              size={13}
                            />

                            Completed
                          </>
                        )
                        : action.status
                          === "BLOCKED"
                          ? (
                            <>
                              <AlertTriangle
                                size={13}
                              />

                              Blocked
                            </>
                          )
                          : (
                            <>
                              <CircleDot
                                size={13}
                              />

                              {statusLabel(
                                action.status,
                              )}
                            </>
                          )
                      }
                    </div>
                  </article>
                );
              },
            )
        }
      </div>
    </section>
  );
}
