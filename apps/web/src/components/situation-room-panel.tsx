"use client";

import {
  Building2,
  ChevronRight,
  RefreshCw,
  Search,
  Siren,
  UserRound,
  X,
} from "lucide-react";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";

import { ExportButtons } from "@/components/export-buttons";

import {
  IncidentWorkspace,
  type IncidentWorkspaceTabId,
} from "@/components/incident-workspace";

import {
  PortfolioGovernanceWorkspace,
} from "@/components/portfolio-governance-workspace";

import {
  addIncidentNote,
  fetchIncidentDashboard,
  fetchIncidentDetail,
  updateIncidentOwner,
  updateIncidentSeverity,
  updateIncidentStatus,
} from "@/lib/api";

import { exportIncidents } from "@/lib/export-sets";

import { formatRelativeTime } from "@/lib/format";

import {
  useDialogBehavior,
} from "@/lib/use-dialog-behavior";

import type {
  IncidentCase,
  IncidentDashboardResponse,
  IncidentDetail,
  IncidentSeverity,
  IncidentStatus,
} from "@/types/incidents";

type SituationRoomPanelProps = {
  open: boolean;
  onClose: () => void;
  onOpenAlertCenter?: () => void;
};

type WorkspaceView = "incidents" | "portfolio";

type QueueFilter =
  | "all"
  | "active"
  | "responding"
  | "critical"
  | "closed";

const HAZARD_LABELS: Record<IncidentCase["hazardType"], string> = {
  earthquakes: "Earthquake",
  volcanoes: "Volcano",
  wildfires: "Wildfire",
  "severe-storms": "Severe storm",
};

const dateTimeFormat = new Intl.DateTimeFormat("en", {
  dateStyle: "medium",
  timeStyle: "short",
});

function severityClass(severity: IncidentSeverity): string {
  return `incident-severity incident-severity--${severity.toLowerCase()}`;
}

function statusClass(status: IncidentStatus): string {
  return `incident-status incident-status--${status.toLowerCase()}`;
}

function formatTime(value: string): string {
  return dateTimeFormat.format(new Date(value));
}

function matchesFilter(
  incident: IncidentCase,
  filter: QueueFilter,
): boolean {
  switch (filter) {
    case "active":
      return incident.status !== "CLOSED";
    case "responding":
      return incident.status === "RESPONDING";
    case "critical":
      return (
        incident.severity === "CRITICAL" && incident.status !== "CLOSED"
      );
    case "closed":
      return incident.status === "CLOSED";
    default:
      return true;
  }
}

function errorMessage(caught: unknown, fallback: string): string {
  return caught instanceof Error ? caught.message : fallback;
}

function isAbort(caught: unknown): boolean {
  return caught instanceof DOMException && caught.name === "AbortError";
}

export function SituationRoomPanel({
  open,
  onClose,
  onOpenAlertCenter,
}: SituationRoomPanelProps) {
  const [dashboard, setDashboard] =
    useState<IncidentDashboardResponse | null>(null);

  const [selectedIncidentId, setSelectedIncidentId] =
    useState<string | null>(null);

  const [detail, setDetail] = useState<IncidentDetail | null>(null);

  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ownerDraft, setOwnerDraft] = useState("");
  const [error, setError] = useState<string | null>(null);

  const [view, setView] = useState<WorkspaceView>("incidents");

  const [incidentTab, setIncidentTab] =
    useState<IncidentWorkspaceTabId>("overview");
  const [filter, setFilter] = useState<QueueFilter>("all");
  const [search, setSearch] = useState("");
  const [queueOpen, setQueueOpen] = useState(true);

  const dialogRef = useRef<HTMLElement | null>(null);

  const detailRequestRef = useRef(0);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  const ownerBaselineRef =
    useRef<{ id: string; owner: string } | null>(null);

  const applyDetail = (response: IncidentDetail) => {
    const baseline = ownerBaselineRef.current;
    const keepDraft =
      baseline !== null && baseline.id === response.incident.id;

    ownerBaselineRef.current = {
      id: response.incident.id,
      owner: response.incident.owner,
    };

    setDetail(response);
    setOwnerDraft((draft) =>
      keepDraft && draft !== baseline.owner
        ? draft
        : response.incident.owner,
    );
  };

  useEffect(() => {
    if (!open) {
      return;
    }

    const controller = new AbortController();
    const requestId = ++detailRequestRef.current;

    void fetchIncidentDashboard(controller.signal)
      .then((response) => {
        if (controller.signal.aborted) {
          return null;
        }

        setDashboard(response);
        setError(null);

        const first = response.incidents[0];

        if (first === undefined) {
          setSelectedIncidentId(null);
          setDetail(null);
          return null;
        }

        setSelectedIncidentId(first.id);

        return fetchIncidentDetail(first.id, controller.signal);
      })
      .then((response) => {
        if (
          response === null
          || response === undefined
          || controller.signal.aborted
          || requestId !== detailRequestRef.current
        ) {
          return;
        }

        ownerBaselineRef.current = {
          id: response.incident.id,
          owner: response.incident.owner,
        };
        setDetail(response);
        setOwnerDraft(response.incident.owner);
        setQueueOpen(false);
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted || isAbort(caught)) {
          return;
        }

        setError(errorMessage(caught, "Unable to load Situation Room."));
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [open]);

  const refreshDashboard = (preferredId?: string) =>
    fetchIncidentDashboard()
      .then((response) => {
        setDashboard(response);

        const targetId =
          preferredId
          ?? selectedIncidentId
          ?? response.incidents[0]?.id
          ?? null;

        if (targetId === null) {
          setSelectedIncidentId(null);
          setDetail(null);
          return null;
        }

        setSelectedIncidentId(targetId);

        return fetchIncidentDetail(targetId);
      })
      .then((response) => {
        if (response === null || response === undefined) {
          return;
        }

        applyDetail(response);
      });

  const selectIncident = (incident: IncidentCase) => {
    const requestId = ++detailRequestRef.current;

    setSelectedIncidentId(incident.id);
    setDetailLoading(true);
    setError(null);
    setQueueOpen(false);

    void fetchIncidentDetail(incident.id)
      .then((response) => {
        if (requestId === detailRequestRef.current) {
          applyDetail(response);
        }
      })
      .catch((caught: unknown) => {
        if (requestId === detailRequestRef.current) {
          setError(errorMessage(caught, "Unable to load incident."));
        }
      })
      .finally(() => {
        if (requestId === detailRequestRef.current) {
          setDetailLoading(false);
        }
      });
  };

  const mutate = (operation: Promise<IncidentDetail>) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        applyDetail(response);

        return refreshDashboard(response.incident.id);
      })
      .catch((caught: unknown) => {
        setError(errorMessage(caught, "Unable to update incident."));
      })
      .finally(() => {
        setBusy(false);
      });
  };

  const handleTimelineChanged = (label: string) => {
    if (detail === null) {
      return;
    }

    const incidentId = detail.incident.id;

    void fetchIncidentDetail(incidentId)
      .then((response) => {
        if (response.incident.id === selectedIncidentId) {
          applyDetail(response);
        }
      })
      .catch((caught: unknown) => {
        setError(errorMessage(caught, `Unable to refresh ${label} timeline.`));
      });

    void fetchIncidentDashboard()
      .then(setDashboard)
      .catch(() => undefined);
  };

  const handleOwnerSave = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (detail === null || ownerDraft.trim() === "") {
      return;
    }

    mutate(updateIncidentOwner(detail.incident.id, ownerDraft.trim()));
  };

  const handleRefresh = () => {
    setLoading(true);

    void refreshDashboard()
      .catch((caught: unknown) => {
        setError(errorMessage(caught, "Unable to refresh incidents."));
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const visibleIncidents = useMemo(() => {
    const incidents = dashboard?.incidents ?? [];
    const needle = search.trim().toLowerCase();

    return incidents.filter(
      (incident) =>
        matchesFilter(incident, filter)
        && (needle === ""
          || incident.title.toLowerCase().includes(needle)
          || incident.owner.toLowerCase().includes(needle)
          || HAZARD_LABELS[incident.hazardType]
            .toLowerCase()
            .includes(needle)),
    );
  }, [dashboard, filter, search]);

  if (!open) {
    return null;
  }

  const summaryTiles: Array<{
    id: QueueFilter;
    label: string;
    value: number;
  }> = [
    { id: "all", label: "Incidents", value: dashboard?.totalCount ?? 0 },
    { id: "active", label: "Active", value: dashboard?.activeCount ?? 0 },
    {
      id: "responding",
      label: "Responding",
      value: dashboard?.respondingCount ?? 0,
    },
    {
      id: "critical",
      label: "Critical",
      value: dashboard?.criticalCount ?? 0,
    },
    { id: "closed", label: "Closed", value: dashboard?.closedCount ?? 0 },
  ];

  const selectedSummary = dashboard?.incidents.find(
    (incident) => incident.id === selectedIncidentId,
  );

  return (
    <div className="situation-overlay">
      <button
        type="button"
        className="situation-backdrop"
        aria-label="Close Situation Room"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="situation-workspace"
        role="dialog"
        aria-modal="true"
        aria-labelledby="situation-room-title"
        tabIndex={-1}
      >
        <header className="situation-header">
          <div className="situation-header__title">
            <span className="situation-header__icon" aria-hidden="true">
              <Siren size={20} />
            </span>

            <div>
              <span className="panel-kicker">
                {view === "incidents"
                  ? "INCIDENT OPERATIONS"
                  : "RESILIENCE GOVERNANCE"}
              </span>

              <h2 id="situation-room-title">Situation Room</h2>

              <p>
                {view === "incidents"
                  ? "Promote hazard notifications into operational incidents and keep an auditable response timeline."
                  : "Portfolio-wide governance, trust and assurance - shared by every incident."}
              </p>
            </div>
          </div>

          <div className="situation-header__actions">
            <div
              className="segmented-control"
              role="group"
              aria-label="Workspace view"
            >
              <button
                type="button"
                aria-pressed={view === "incidents"}
                className={
                  view === "incidents"
                    ? "segmented-control__item segmented-control__item--active"
                    : "segmented-control__item"
                }
                onClick={() => setView("incidents")}
              >
                <Siren size={14} aria-hidden="true" />
                Incidents
              </button>

              <button
                type="button"
                aria-pressed={view === "portfolio"}
                className={
                  view === "portfolio"
                    ? "segmented-control__item segmented-control__item--active"
                    : "segmented-control__item"
                }
                onClick={() => setView("portfolio")}
              >
                <Building2 size={14} aria-hidden="true" />
                Portfolio governance
              </button>
            </div>

            <button
              type="button"
              className="icon-button"
              aria-label="Close Situation Room"
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
        </header>

        {view === "portfolio" ? (
          <div className="situation-portfolio">
            <PortfolioGovernanceWorkspace />
          </div>
        ) : (
          <>
            <div
              className="situation-summary"
              role="group"
              aria-label="Filter incidents"
            >
              {summaryTiles.map((tile) => (
                <button
                  key={tile.id}
                  type="button"
                  aria-pressed={filter === tile.id}
                  className={
                    filter === tile.id
                      ? `situation-summary__tile situation-summary__tile--${tile.id} situation-summary__tile--selected`
                      : `situation-summary__tile situation-summary__tile--${tile.id}`
                  }
                  onClick={() => setFilter(tile.id)}
                >
                  <span>{tile.label}</span>
                  <strong>{tile.value}</strong>
                </button>
              ))}
            </div>

            <div className="situation-body">
              <aside
                className={
                  queueOpen
                    ? "situation-list-column situation-list-column--open"
                    : "situation-list-column"
                }
              >
                <div className="situation-list-heading">
                  <div className="situation-list-heading__label">
                    <span className="panel-kicker">INCIDENT QUEUE</span>

                    <strong>
                      {visibleIncidents.length}
                      {" of "}
                      {dashboard?.totalCount ?? 0}
                      {" cases"}
                    </strong>
                  </div>

                  <button
                    type="button"
                    className="situation-queue-toggle"
                    aria-expanded={queueOpen}
                    onClick={() => setQueueOpen((current) => !current)}
                  >
                    <ChevronRight size={15} aria-hidden="true" />

                    <span>
                      <span className="panel-kicker">INCIDENT QUEUE</span>

                      <strong>
                        {selectedSummary !== undefined
                          ? selectedSummary.title
                          : "Operational cases"}
                      </strong>
                    </span>
                  </button>

                  <button
                    type="button"
                    className="situation-refresh"
                    disabled={loading || busy}
                    aria-label="Refresh incidents"
                    onClick={handleRefresh}
                  >
                    <RefreshCw
                      size={14}
                      className={loading ? "spin" : ""}
                      aria-hidden="true"
                    />
                    <span>Refresh</span>
                  </button>
                </div>

                <div className="situation-queue-body">
                  <label className="situation-search">
                    <Search size={14} aria-hidden="true" />

                    <input
                      type="search"
                      value={search}
                      placeholder="Search title, owner or hazard"
                      aria-label="Search incidents"
                      onChange={(event) => setSearch(event.target.value)}
                    />
                  </label>

                  <div className="export-row">
                    <span>Download this list</span>

                    <ExportButtons
                      noun="incident"
                      subject="incidents"
                      count={visibleIncidents.length}
                      onBuild={(format) => ({
                        file: exportIncidents(visibleIncidents, format, {
                          filter,
                          search: search.trim(),
                          totalCount: dashboard?.totalCount ?? 0,
                        }),
                      })}
                    />
                  </div>

                  <div
                    className="situation-incident-list"
                    aria-busy={loading}
                  >
                    {loading && dashboard === null ? (
                      <div className="situation-empty">
                        Loading incidents…
                      </div>
                    ) : dashboard === null
                      || dashboard.incidents.length === 0 ? (
                      <div className="situation-empty situation-empty--large">
                        <Siren size={28} aria-hidden="true" />

                        <strong>No incidents yet</strong>

                        <span>
                          Open the Alert Center and promote an alert into
                          the Situation Room.
                        </span>

                        {onOpenAlertCenter !== undefined && (
                          <button
                            type="button"
                            className="situation-empty__action"
                            onClick={onOpenAlertCenter}
                          >
                            Open Alert Center
                          </button>
                        )}
                      </div>
                    ) : visibleIncidents.length === 0 ? (
                      <div className="situation-empty">
                        No incidents match this filter.
                      </div>
                    ) : (
                      visibleIncidents.map((incident) => (
                        <button
                          type="button"
                          key={incident.id}
                          data-severity={incident.severity.toLowerCase()}
                          aria-current={
                            selectedIncidentId === incident.id
                              ? "true"
                              : undefined
                          }
                          className={
                            selectedIncidentId === incident.id
                              ? "situation-incident-card situation-incident-card--selected"
                              : "situation-incident-card"
                          }
                          onClick={() => selectIncident(incident)}
                        >
                          <div className="situation-incident-card__top">
                            <span className={severityClass(incident.severity)}>
                              {incident.severity}
                            </span>

                            <span className={statusClass(incident.status)}>
                              {incident.status}
                            </span>
                          </div>

                          <strong>{incident.title}</strong>

                          <span>
                            {HAZARD_LABELS[incident.hazardType]}
                            {" · "}
                            {incident.owner}
                          </span>

                          <small>
                            {incident.timelineEventCount} events
                            {" · updated "}
                            {formatRelativeTime(incident.updatedAt)}
                          </small>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              </aside>

              <div className="situation-detail-column">
                {error !== null && (
                  <div className="situation-error" role="alert">
                    {error}
                  </div>
                )}

                {detailLoading ? (
                  <div className="situation-empty situation-empty--large">
                    Loading incident…
                  </div>
                ) : detail === null ? (
                  <div className="situation-empty situation-empty--large">
                    <Siren size={30} aria-hidden="true" />

                    <strong>Situation Room ready</strong>

                    <span>
                      Operational incidents opened from alerts will appear
                      here.
                    </span>
                  </div>
                ) : (
                  <>
                    <div className="incident-command-bar">
                      <div className="incident-command-bar__title">
                        <span className="panel-kicker">
                          INCIDENT
                          {" · "}
                          {HAZARD_LABELS[detail.incident.hazardType]
                            .toUpperCase()}
                        </span>

                        <h3>{detail.incident.title}</h3>

                        <p>{detail.incident.description}</p>
                      </div>

                      <div className="situation-detail-badges">
                        <span
                          className={severityClass(detail.incident.severity)}
                        >
                          {detail.incident.severity}
                        </span>

                        <span className={statusClass(detail.incident.status)}>
                          {detail.incident.status}
                        </span>
                      </div>
                    </div>

                    <dl className="incident-meta">
                      <div>
                        <dt>Opened</dt>
                        <dd>{formatTime(detail.incident.openedAt)}</dd>
                      </div>

                      <div>
                        <dt>Updated</dt>
                        <dd>{formatRelativeTime(detail.incident.updatedAt)}</dd>
                      </div>

                      <div>
                        <dt>Location</dt>
                        <dd>
                          {detail.incident.latitude.toFixed(2)}°,{" "}
                          {detail.incident.longitude.toFixed(2)}°
                        </dd>
                      </div>

                      <div>
                        <dt>Events</dt>
                        <dd>{detail.incident.timelineEventCount}</dd>
                      </div>
                    </dl>

                    <div className="incident-controls">
                      <label>
                        <span>Status</span>

                        <select
                          value={detail.incident.status}
                          disabled={busy}
                          onChange={(event) =>
                            mutate(
                              updateIncidentStatus(
                                detail.incident.id,
                                event.target.value as IncidentStatus,
                              ),
                            )
                          }
                        >
                          <option value="OPEN">Open</option>
                          <option value="MONITORING">Monitoring</option>
                          <option value="RESPONDING">Responding</option>
                          <option value="STABILIZED">Stabilized</option>
                          <option value="CLOSED">Closed</option>
                        </select>
                      </label>

                      <label>
                        <span>Severity</span>

                        <select
                          value={detail.incident.severity}
                          disabled={busy}
                          onChange={(event) =>
                            mutate(
                              updateIncidentSeverity(
                                detail.incident.id,
                                event.target.value as IncidentSeverity,
                              ),
                            )
                          }
                        >
                          <option value="LOW">Low</option>
                          <option value="MODERATE">Moderate</option>
                          <option value="HIGH">High</option>
                          <option value="SEVERE">Severe</option>
                          <option value="CRITICAL">Critical</option>
                        </select>
                      </label>

                      <form
                        className="incident-controls__owner"
                        onSubmit={handleOwnerSave}
                      >
                        <label>
                          <span>Response owner</span>

                          <input
                            required
                            maxLength={120}
                            value={ownerDraft}
                            onChange={(event) =>
                              setOwnerDraft(event.target.value)
                            }
                          />
                        </label>

                        <button
                          type="submit"
                          disabled={
                            busy
                            || ownerDraft.trim() === ""
                            || ownerDraft.trim() === detail.incident.owner
                          }
                        >
                          <UserRound size={14} aria-hidden="true" />
                          Assign
                        </button>
                      </form>
                    </div>

                    <IncidentWorkspace
                      detail={detail}
                      busy={busy}
                      activeTab={incidentTab}
                      onTabChange={setIncidentTab}
                      onAddNote={(message) =>
                        mutate(addIncidentNote(detail.incident.id, message))
                      }
                      onTimelineChanged={handleTimelineChanged}
                    />
                  </>
                )}
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
