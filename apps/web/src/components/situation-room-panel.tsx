"use client";

import {
  Activity,
  BadgeAlert,
  CheckCircle2,
  Clock3,
  MessageSquarePlus,
  RefreshCw,
  Shield,
  Siren,
  UserRound,
  X,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  addIncidentNote,
  fetchIncidentDashboard,
  fetchIncidentDetail,
  updateIncidentOwner,
  updateIncidentSeverity,
  updateIncidentStatus,
} from "@/lib/api";

import type {
  IncidentCase,
  IncidentDashboardResponse,
  IncidentDetail,
  IncidentSeverity,
  IncidentStatus,
} from "@/types/incidents";

import {
  IncidentActionBoard,
} from "@/components/incident-action-board";

type SituationRoomPanelProps = {
  open: boolean;
  onClose: () => void;
};

function severityClass(
  severity: IncidentSeverity,
): string {
  return (
    `incident-severity incident-severity--${severity.toLowerCase()}`
  );
}

function statusClass(
  status: IncidentStatus,
): string {
  return (
    `incident-status incident-status--${status.toLowerCase()}`
  );
}

function formatTime(
  value: string,
): string {
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

export function SituationRoomPanel({
  open,
  onClose,
}: SituationRoomPanelProps) {
  const [
    dashboard,
    setDashboard,
  ] =
    useState<
      IncidentDashboardResponse | null
    >(null);

  const [
    selectedIncidentId,
    setSelectedIncidentId,
  ] =
    useState<string | null>(
      null,
    );

  const [
    detail,
    setDetail,
  ] =
    useState<
      IncidentDetail | null
    >(null);

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    detailLoading,
    setDetailLoading,
  ] =
    useState(false);

  const [
    busy,
    setBusy,
  ] =
    useState(false);

  const [
    ownerDraft,
    setOwnerDraft,
  ] =
    useState("");

  const [
    noteDraft,
    setNoteDraft,
  ] =
    useState("");

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  useEffect(() => {
    if (!open) {
      return;
    }

    const controller =
      new AbortController();

    void fetchIncidentDashboard(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return null;
        }

        setDashboard(response);
        setError(null);

        const first =
          response.incidents[0];

        if (first === undefined) {
          setSelectedIncidentId(
            null,
          );

          setDetail(null);

          return null;
        }

        setSelectedIncidentId(
          first.id,
        );

        return fetchIncidentDetail(
          first.id,
          controller.signal,
        );
      })
      .then((response) => {
        if (
          response === null
          || response === undefined
          || controller.signal.aborted
        ) {
          return;
        }

        setDetail(response);

        setOwnerDraft(
          response.incident.owner,
        );
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
              : "Unable to load Situation Room.",
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
    open,
  ]);

  const refreshDashboard = (
    preferredId?: string,
  ) => {
    return fetchIncidentDashboard()
      .then((response) => {
        setDashboard(response);

        const targetId =
          preferredId
          ?? selectedIncidentId
          ?? response.incidents[0]
            ?.id
          ?? null;

        if (targetId === null) {
          setSelectedIncidentId(
            null,
          );

          setDetail(null);

          return null;
        }

        setSelectedIncidentId(
          targetId,
        );

        return fetchIncidentDetail(
          targetId,
        );
      })
      .then((response) => {
        if (
          response === null
          || response === undefined
        ) {
          return;
        }

        setDetail(response);

        setOwnerDraft(
          response.incident.owner,
        );
      });
  };

  const selectIncident = (
    incident: IncidentCase,
  ) => {
    setSelectedIncidentId(
      incident.id,
    );

    setDetailLoading(true);
    setError(null);

    void fetchIncidentDetail(
      incident.id,
    )
      .then((response) => {
        setDetail(response);

        setOwnerDraft(
          response.incident.owner,
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load incident.",
          );
        },
      )
      .finally(() => {
        setDetailLoading(false);
      });
  };

  const mutate = (
    operation:
      Promise<IncidentDetail>,
  ) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        setDetail(response);

        setOwnerDraft(
          response.incident.owner,
        );

        return refreshDashboard(
          response.incident.id,
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update incident.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleOwnerSave = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      detail === null
      || ownerDraft.trim() === ""
    ) {
      return;
    }

    mutate(
      updateIncidentOwner(
        detail.incident.id,
        ownerDraft.trim(),
      ),
    );
  };

  const handleNote = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      detail === null
      || noteDraft.trim() === ""
    ) {
      return;
    }

    const message =
      noteDraft.trim();

    setNoteDraft("");

    mutate(
      addIncidentNote(
        detail.incident.id,
        message,
      ),
    );
  };

  if (!open) {
    return null;
  }

  return (
    <div className="situation-overlay">
      <button
        type="button"
        className="situation-backdrop"
        aria-label="Close Situation Room"
        onClick={onClose}
      />

      <section className="situation-workspace">
        <header className="situation-header">
          <div>
            <span className="panel-kicker">
              INCIDENT OPERATIONS
            </span>

            <h2>
              Situation Room
            </h2>

            <p>
              Promote hazard notifications
              into operational incidents and
              maintain an auditable response
              timeline.
            </p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close Situation Room"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="situation-summary">
          <div>
            <span>
              Incidents
            </span>

            <strong>
              {dashboard?.totalCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Active
            </span>

            <strong>
              {dashboard?.activeCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Responding
            </span>

            <strong>
              {dashboard?.respondingCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Critical
            </span>

            <strong>
              {dashboard?.criticalCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Closed
            </span>

            <strong>
              {dashboard?.closedCount
                ?? 0}
            </strong>
          </div>
        </div>

        <div className="situation-body">
          <aside className="situation-list-column">
            <div className="situation-list-heading">
              <div>
                <span className="panel-kicker">
                  INCIDENT QUEUE
                </span>

                <strong>
                  Operational cases
                </strong>
              </div>

              <button
                type="button"
                disabled={loading || busy}
                onClick={() => {
                  setLoading(true);

                  void refreshDashboard()
                    .catch(
                      (caughtError: unknown) => {
                        setError(
                          caughtError
                            instanceof Error
                            ? caughtError.message
                            : "Unable to refresh incidents.",
                        );
                      },
                    )
                    .finally(() => {
                      setLoading(false);
                    });
                }}
              >
                <RefreshCw
                  size={13}
                />

                Refresh
              </button>
            </div>

            <div className="situation-incident-list">
              {loading
                && dashboard === null
                ? (
                  <div className="situation-empty">
                    Loading incidents…
                  </div>
                )
                : dashboard === null
                  || dashboard.incidents.length === 0
                  ? (
                    <div className="situation-empty situation-empty--large">
                      <Siren size={28} />

                      <strong>
                        No incidents
                      </strong>

                      <span>
                        Open Alert Center and
                        promote an alert into
                        Situation Room.
                      </span>
                    </div>
                  )
                  : dashboard.incidents.map(
                    (incident) => (
                      <button
                        type="button"
                        key={incident.id}
                        className={
                          selectedIncidentId
                            === incident.id
                            ? "situation-incident-card situation-incident-card--selected"
                            : "situation-incident-card"
                        }
                        onClick={() =>
                          selectIncident(
                            incident,
                          )
                        }
                      >
                        <div className="situation-incident-card__top">
                          <span
                            className={
                              severityClass(
                                incident.severity,
                              )
                            }
                          >
                            {incident.severity}
                          </span>

                          <span
                            className={
                              statusClass(
                                incident.status,
                              )
                            }
                          >
                            {incident.status}
                          </span>
                        </div>

                        <strong>
                          {incident.title}
                        </strong>

                        <span>
                          {incident.owner}
                          {" · "}
                          {incident.timelineEventCount}
                          {" events"}
                        </span>
                      </button>
                    ),
                  )
              }
            </div>
          </aside>

          <div className="situation-detail-column">
            {error !== null && (
              <div className="situation-error">
                {error}
              </div>
            )}

            {detailLoading
              ? (
                <div className="situation-empty situation-empty--large">
                  Loading incident…
                </div>
              )
              : detail === null
                ? (
                  <div className="situation-empty situation-empty--large">
                    <Siren size={30} />

                    <strong>
                      Situation Room ready
                    </strong>

                    <span>
                      Operational incidents
                      opened from alerts will
                      appear here.
                    </span>
                  </div>
                )
                : (
                  <>
                    <div className="situation-detail-heading">
                      <div>
                        <span className="panel-kicker">
                          INCIDENT
                        </span>

                        <h3>
                          {detail.incident
                            .title}
                        </h3>

                        <p>
                          {detail.incident
                            .description}
                        </p>
                      </div>

                      <div className="situation-detail-badges">
                        <span
                          className={
                            severityClass(
                              detail.incident
                                .severity,
                            )
                          }
                        >
                          {detail.incident
                            .severity}
                        </span>

                        <span
                          className={
                            statusClass(
                              detail.incident
                                .status,
                            )
                          }
                        >
                          {detail.incident
                            .status}
                        </span>
                      </div>
                    </div>

                    <div className="situation-controls">
                      <label>
                        <span>
                          Status
                        </span>

                        <select
                          value={
                            detail.incident
                              .status
                          }
                          disabled={busy}
                          onChange={(event) =>
                            mutate(
                              updateIncidentStatus(
                                detail.incident
                                  .id,
                                event.target.value as IncidentStatus,
                              ),
                            )
                          }
                        >
                          <option value="OPEN">
                            Open
                          </option>

                          <option value="MONITORING">
                            Monitoring
                          </option>

                          <option value="RESPONDING">
                            Responding
                          </option>

                          <option value="STABILIZED">
                            Stabilized
                          </option>

                          <option value="CLOSED">
                            Closed
                          </option>
                        </select>
                      </label>

                      <label>
                        <span>
                          Severity
                        </span>

                        <select
                          value={
                            detail.incident
                              .severity
                          }
                          disabled={busy}
                          onChange={(event) =>
                            mutate(
                              updateIncidentSeverity(
                                detail.incident
                                  .id,
                                event.target.value as IncidentSeverity,
                              ),
                            )
                          }
                        >
                          <option value="LOW">
                            Low
                          </option>

                          <option value="MODERATE">
                            Moderate
                          </option>

                          <option value="HIGH">
                            High
                          </option>

                          <option value="SEVERE">
                            Severe
                          </option>

                          <option value="CRITICAL">
                            Critical
                          </option>
                        </select>
                      </label>
                    </div>

                    <div className="situation-metrics">
                      <div>
                        <Shield size={15} />

                        <span>
                          Hazard
                        </span>

                        <strong>
                          {detail.incident
                            .hazardType}
                        </strong>
                      </div>

                      <div>
                        <UserRound
                          size={15}
                        />

                        <span>
                          Owner
                        </span>

                        <strong>
                          {detail.incident
                            .owner}
                        </strong>
                      </div>

                      <div>
                        <Clock3 size={15} />

                        <span>
                          Opened
                        </span>

                        <strong>
                          {formatTime(
                            detail.incident
                              .openedAt,
                          )}
                        </strong>
                      </div>

                      <div>
                        <Activity size={15} />

                        <span>
                          Timeline
                        </span>

                        <strong>
                          {detail.incident
                            .timelineEventCount}
                        </strong>
                      </div>
                    </div>

                    <form
                      className="situation-owner-form"
                      onSubmit={handleOwnerSave}
                    >
                      <label>
                        <span>
                          Response owner
                        </span>

                        <input
                          required
                          maxLength={120}
                          value={ownerDraft}
                          onChange={(event) =>
                            setOwnerDraft(
                              event.target.value,
                            )
                          }
                        />
                      </label>

                      <button
                        type="submit"
                        disabled={busy}
                      >
                        <UserRound
                          size={13}
                        />

                        Assign
                      </button>
                    </form>

                    <form
                      className="situation-note-form"
                      onSubmit={handleNote}
                    >
                      <label>
                        <span>
                          Operational note
                        </span>

                        <textarea
                          maxLength={2000}
                          value={noteDraft}
                          placeholder="Add an operational update, decision or observation…"
                          onChange={(event) =>
                            setNoteDraft(
                              event.target.value,
                            )
                          }
                        />
                      </label>

                      <button
                        type="submit"
                        disabled={
                          busy
                          || noteDraft.trim()
                            === ""
                        }
                      >
                        <MessageSquarePlus
                          size={13}
                        />

                        Add note
                      </button>
                    </form>

                    <IncidentActionBoard
                      incidentId={
                        detail.incident.id
                      }
                      incidentStatus={
                        detail.incident.status
                      }
                      onTimelineChanged={() => {
                        void fetchIncidentDetail(
                          detail.incident.id,
                        )
                          .then(
                            (response) => {
                              setDetail(
                                response,
                              );
                            },
                          )
                          .catch(
                            (
                              caughtError:
                                unknown,
                            ) => {
                              setError(
                                caughtError
                                  instanceof Error
                                  ? caughtError.message
                                  : "Unable to refresh incident timeline.",
                              );
                            },
                          );
                      }}
                    />

                    <div className="situation-timeline-heading">
                      <BadgeAlert
                        size={15}
                      />

                      <span>
                        Response timeline
                      </span>
                    </div>

                    <div className="situation-timeline">
                      {detail.timeline.length === 0
                        ? (
                          <div className="situation-empty">
                            No timeline events.
                          </div>
                        )
                        : detail.timeline.map(
                          (event) => (
                            <article
                              key={event.id}
                            >
                              <span className="situation-timeline__marker">
                                {event.eventType
                                  === "NOTE_ADDED"
                                  ? (
                                    <MessageSquarePlus
                                      size={12}
                                    />
                                  )
                                  : event.eventType
                                    === "STATUS_CHANGED"
                                    ? (
                                      <CheckCircle2
                                        size={12}
                                      />
                                    )
                                    : (
                                      <Activity
                                        size={12}
                                      />
                                    )
                                }
                              </span>

                              <div>
                                <span>
                                  {event.eventType
                                    .replaceAll(
                                      "_",
                                      " ",
                                    )}
                                </span>

                                <strong>
                                  {event.message}
                                </strong>

                                <small>
                                  {formatTime(
                                    event.createdAt,
                                  )}
                                </small>
                              </div>
                            </article>
                          ),
                        )
                      }
                    </div>
                  </>
                )
            }
          </div>
        </div>
      </section>
    </div>
  );
}
