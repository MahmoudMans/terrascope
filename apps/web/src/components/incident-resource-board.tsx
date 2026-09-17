"use client";

import {
  Ambulance,
  Boxes,
  CheckCircle2,
  CircleAlert,
  PackageCheck,
  Plus,
  RefreshCw,
  Trash2,
  UserRound,
  XCircle,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  cancelIncidentResource,
  createIncidentResource,
  deleteIncidentResource,
  fetchIncidentResourceBoard,
  updateIncidentResourceAllocation,
  updateIncidentResourceOwner,
  updateIncidentResourcePriority,
} from "@/lib/api";

import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  CreateIncidentResourceRequest,
  IncidentResourceBoardResponse,
  IncidentResourceCategory,
  IncidentResourceStatus,
} from "@/types/incident-resources";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentResourceBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialResource:
  CreateIncidentResourceRequest = {
    category: "MEDICAL",
    name: "",
    description: "",
    priority: "HIGH",
    requestedQuantity: 1,
    unit: "units",
    owner: "Unassigned",
  };

function categoryLabel(
  category:
    IncidentResourceCategory,
): string {
  switch (category) {
    case "PERSONNEL":
      return "Personnel";

    case "MEDICAL":
      return "Medical";

    case "TRANSPORT":
      return "Transport";

    case "SHELTER":
      return "Shelter";

    case "EQUIPMENT":
      return "Equipment";

    case "COMMUNICATIONS":
      return "Communications";

    case "OTHER":
      return "Other";
  }
}

function statusLabel(
  status:
    IncidentResourceStatus,
): string {
  switch (status) {
    case "REQUESTED":
      return "Requested";

    case "PARTIALLY_ALLOCATED":
      return "Partial";

    case "FULFILLED":
      return "Fulfilled";

    case "CANCELLED":
      return "Cancelled";
  }
}

function statusClass(
  status:
    IncidentResourceStatus,
): string {
  return (
    `resource-status resource-status--${status.toLowerCase().replaceAll("_", "-")}`
  );
}

function priorityClass(
  priority:
    IncidentActionPriority,
): string {
  return (
    `resource-priority resource-priority--${priority.toLowerCase()}`
  );
}

export function IncidentResourceBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentResourceBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      IncidentResourceBoardResponse | null
    >(null);

  const [
    draft,
    setDraft,
  ] =
    useState<
      CreateIncidentResourceRequest
    >(initialResource);

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

    void fetchIncidentResourceBoard(
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
              : "Unable to load resources.",
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

    return fetchIncidentResourceBoard(
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
      Promise<IncidentResourceBoardResponse>,
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
              : "Unable to update resource.",
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
      draft.name.trim() === ""
      || draft.unit.trim() === ""
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void createIncidentResource(
      incidentId,
      {
        ...draft,
        name:
          draft.name.trim(),
        description:
          draft.description.trim(),
        unit:
          draft.unit.trim(),
        owner:
          draft.owner.trim()
          || "Unassigned",
      },
    )
      .then((response) => {
        setBoard(response);

        setDraft(
          initialResource,
        );

        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to request resource.",
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
    <section className="resource-board">
      <div className="resource-board-heading">
        <div>
          <span className="panel-kicker">
            LOGISTICS
          </span>

          <strong>
            Resource mobilization
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
                      : "Unable to refresh resources.",
                  );
                },
              );
          }}
        >
          <RefreshCw size={13} />
          Refresh
        </button>
      </div>

      <div className="resource-summary">
        <div>
          <span>Total</span>
          <strong>{board?.totalCount ?? 0}</strong>
        </div>

        <div>
          <span>Requested</span>
          <strong>{board?.requestedCount ?? 0}</strong>
        </div>

        <div>
          <span>Partial</span>
          <strong>{board?.partiallyAllocatedCount ?? 0}</strong>
        </div>

        <div>
          <span>Fulfilled</span>
          <strong>{board?.fulfilledCount ?? 0}</strong>
        </div>

        <div>
          <span>Cancelled</span>
          <strong>{board?.cancelledCount ?? 0}</strong>
        </div>

        <div
          className={
            (board?.shortageCount ?? 0) > 0
              ? "resource-summary__shortage"
              : ""
          }
        >
          <span>Shortages</span>
          <strong>{board?.shortageCount ?? 0}</strong>
        </div>
      </div>

      {error !== null && (
        <div className="resource-board-error">
          {error}
        </div>
      )}

      {!readOnly && (
        <form
          className="resource-form"
          onSubmit={handleCreate}
        >
          <label className="resource-field">
            <span>Category</span>

            <select
              value={draft.category}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  category:
                    event.target.value as IncidentResourceCategory,
                })
              }
            >
              <option value="PERSONNEL">
                Personnel
              </option>
              <option value="MEDICAL">
                Medical
              </option>
              <option value="TRANSPORT">
                Transport
              </option>
              <option value="SHELTER">
                Shelter
              </option>
              <option value="EQUIPMENT">
                Equipment
              </option>
              <option value="COMMUNICATIONS">
                Communications
              </option>
              <option value="OTHER">
                Other
              </option>
            </select>
          </label>

          <label className="resource-field">
            <span>Priority</span>

            <select
              value={draft.priority}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  priority:
                    event.target.value as IncidentActionPriority,
                })
              }
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>

          <label className="resource-field resource-field--wide">
            <span>Resource</span>

            <input
              required
              maxLength={300}
              value={draft.name}
              placeholder="e.g. Trauma kits, ambulances, radio units"
              onChange={(event) =>
                setDraft({
                  ...draft,
                  name:
                    event.target.value,
                })
              }
            />
          </label>

          <label className="resource-field">
            <span>Quantity</span>

            <input
              type="number"
              min={1}
              max={1000000}
              value={draft.requestedQuantity}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  requestedQuantity:
                    Number(
                      event.target.value,
                    ),
                })
              }
            />
          </label>

          <label className="resource-field">
            <span>Unit</span>

            <input
              required
              maxLength={40}
              value={draft.unit}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  unit:
                    event.target.value,
                })
              }
            />
          </label>

          <label className="resource-field resource-field--wide">
            <span>Logistics owner</span>

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

          <label className="resource-field resource-field--wide">
            <span>Description</span>

            <textarea
              maxLength={2000}
              value={draft.description}
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
            className="resource-create"
            disabled={busy}
          >
            <Plus size={13} />
            Request resource
          </button>
        </form>
      )}

      {readOnly && (
        <div className="resource-board-closed">
          This incident is closed.
          Resource logistics are read-only.
        </div>
      )}

      <div className="resource-list">
        {loading
          && board === null
          ? (
            <div className="resource-empty">
              Loading resources…
            </div>
          )
          : board === null
            || board.resources.length === 0
            ? (
              <div className="resource-empty resource-empty--large">
                <Boxes size={28} />

                <strong>
                  No resource requests
                </strong>

                <span>
                  Add personnel, medical,
                  transport or equipment
                  requirements.
                </span>
              </div>
            )
            : board.resources.map(
              (resource) => {
                const shortage =
                  resource.status !== "FULFILLED"
                  && resource.status !== "CANCELLED"
                  && resource.allocatedQuantity
                    < resource.requestedQuantity;

                const active =
                  resource.status !== "CANCELLED";

                return (
                  <article
                    key={resource.id}
                    className={
                      shortage
                        ? "resource-card resource-card--shortage"
                        : "resource-card"
                    }
                  >
                    <div className="resource-card-heading">
                      <div>
                        <span
                          className={
                            priorityClass(
                              resource.priority,
                            )
                          }
                        >
                          {resource.priority}
                        </span>

                        <span
                          className={
                            statusClass(
                              resource.status,
                            )
                          }
                        >
                          {statusLabel(
                            resource.status,
                          )}
                        </span>
                      </div>

                      {!readOnly && (
                        <div className="resource-card-actions">
                          {active && (
                            <button
                              type="button"
                              className="resource-cancel"
                              disabled={busy}
                              aria-label={
                                `Cancel ${resource.name}`
                              }
                              onClick={() =>
                                applyMutation(
                                  cancelIncidentResource(
                                    incidentId,
                                    resource.id,
                                  ),
                                )
                              }
                            >
                              <XCircle size={13} />
                            </button>
                          )}

                          <button
                            type="button"
                            className="resource-delete"
                            disabled={busy}
                            aria-label={
                              `Delete ${resource.name}`
                            }
                            onClick={() =>
                              applyMutation(
                                deleteIncidentResource(
                                  incidentId,
                                  resource.id,
                                ),
                              )
                            }
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      )}
                    </div>

                    <div className="resource-card-title">
                      <span>
                        {categoryLabel(
                          resource.category,
                        )}
                      </span>

                      <strong>
                        {resource.name}
                      </strong>
                    </div>

                    {resource.description !== "" && (
                      <p>
                        {resource.description}
                      </p>
                    )}

                    <div className="resource-allocation">
                      <div>
                        <span>Requested</span>
                        <strong>
                          {resource.requestedQuantity}{" "}
                          {resource.unit}
                        </strong>
                      </div>

                      <div>
                        <span>Allocated</span>
                        <strong>
                          {resource.allocatedQuantity}{" "}
                          {resource.unit}
                        </strong>
                      </div>

                      <div>
                        <span>Remaining</span>
                        <strong>
                          {Math.max(
                            0,
                            resource.requestedQuantity
                            - resource.allocatedQuantity,
                          )}{" "}
                          {resource.unit}
                        </strong>
                      </div>
                    </div>

                    {shortage && (
                      <div className="resource-shortage">
                        <CircleAlert size={12} />
                        Allocation shortage
                      </div>
                    )}

                    {resource.status === "FULFILLED" && (
                      <div className="resource-fulfilled">
                        <PackageCheck size={12} />
                        Requirement fulfilled
                      </div>
                    )}

                    {!readOnly && active && (
                      <>
                        <form
                          className="resource-allocation-form"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const quantity =
                              Number(
                                data.get(
                                  "allocation",
                                ),
                              );

                            if (
                              !Number.isFinite(
                                quantity,
                              )
                              || quantity < 0
                              || quantity
                                > resource.requestedQuantity
                            ) {
                              setError(
                                "Allocation must be between 0 and requested quantity.",
                              );

                              return;
                            }

                            applyMutation(
                              updateIncidentResourceAllocation(
                                incidentId,
                                resource.id,
                                quantity,
                              ),
                            );
                          }}
                        >
                          <label>
                            <span>Allocation</span>

                            <input
                              name="allocation"
                              type="number"
                              min={0}
                              max={resource.requestedQuantity}
                              defaultValue={resource.allocatedQuantity}
                            />
                          </label>

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Update
                          </button>
                        </form>

                        <div className="resource-controls">
                          <label>
                            <span>Priority</span>

                            <select
                              value={resource.priority}
                              disabled={busy}
                              onChange={(event) =>
                                applyMutation(
                                  updateIncidentResourcePriority(
                                    incidentId,
                                    resource.id,
                                    event.target.value as IncidentActionPriority,
                                  ),
                                )
                              }
                            >
                              <option value="LOW">Low</option>
                              <option value="MEDIUM">Medium</option>
                              <option value="HIGH">High</option>
                              <option value="CRITICAL">Critical</option>
                            </select>
                          </label>
                        </div>

                        <form
                          className="resource-owner-form"
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

                            if (owner === "") {
                              return;
                            }

                            applyMutation(
                              updateIncidentResourceOwner(
                                incidentId,
                                resource.id,
                                owner,
                              ),
                            );
                          }}
                        >
                          <label>
                            <span>Owner</span>

                            <input
                              name="owner"
                              maxLength={120}
                              defaultValue={resource.owner}
                            />
                          </label>

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            <UserRound size={12} />
                            Assign
                          </button>
                        </form>
                      </>
                    )}

                    <div className="resource-card-footer">
                      <Ambulance size={12} />

                      <span>
                        {resource.owner}
                      </span>

                      {resource.status === "FULFILLED" && (
                        <CheckCircle2 size={12} />
                      )}
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
