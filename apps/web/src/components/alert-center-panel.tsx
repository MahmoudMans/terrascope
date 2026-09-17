"use client";

import {
  Activity,
  BellRing,
  Check,
  CloudLightning,
  Flame,
  MapPin,
  Mountain,
  RefreshCw,
  ShieldAlert,
  Trash2,
  X,
  XCircle,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createAlertRule,
  deleteAlertRule,
  evaluateAlertRules,
  fetchAlertDashboard,
  fetchEarthquakes,
  fetchNaturalHazards,
  fetchVolcanoes,
  setAlertRuleEnabled,
  updateAlertEventStatus,
} from "@/lib/api";

import type {
  AlertDashboardResponse,
  AlertEvent,
  AlertRuleHazardType,
  CreateAlertRuleRequest,
} from "@/types/alerts";

import type {
  RegionalHazardPoint,
} from "@/types/regional-intelligence";

type AlertCenterPanelProps = {
  open: boolean;
  onClose: () => void;
};

const initialRule:
  CreateAlertRuleRequest = {
    name:
      "Regional hazard watch",
    hazardType: "all",
    latitude: 36.8065,
    longitude: 10.1815,
    radiusKm: 500,
    minimumIntensity: 0,
  };

function hazardLabel(
  hazardType:
    AlertRuleHazardType,
): string {
  switch (hazardType) {
    case "all":
      return "All hazards";

    case "earthquakes":
      return "Earthquake";

    case "volcanoes":
      return "Volcano";

    case "wildfires":
      return "Wildfire";

    case "severe-storms":
      return "Severe storm";
  }
}

function HazardIcon({
  hazardType,
}: {
  hazardType:
    AlertEvent["hazardType"];
}) {
  if (
    hazardType
    === "earthquakes"
  ) {
    return <Activity size={16} />;
  }

  if (
    hazardType
    === "volcanoes"
  ) {
    return <Mountain size={16} />;
  }

  if (
    hazardType
    === "wildfires"
  ) {
    return <Flame size={16} />;
  }

  return (
    <CloudLightning size={16} />
  );
}

export function AlertCenterPanel({
  open,
  onClose,
}: AlertCenterPanelProps) {
  const [
    dashboard,
    setDashboard,
  ] =
    useState<
      AlertDashboardResponse | null
    >(null);

  const [
    rule,
    setRule,
  ] =
    useState<
      CreateAlertRuleRequest
    >(initialRule);

  const [
    dashboardLoading,
    setDashboardLoading,
  ] =
    useState(true);

  const [
    busy,
    setBusy,
  ] =
    useState(false);

  const [
    message,
    setMessage,
  ] =
    useState<string | null>(
      null,
    );

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

    void fetchAlertDashboard(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setDashboard(response);
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
              : "Unable to load alert center.",
          );
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setDashboardLoading(
            false,
          );
        }
      });

    return () => {
      controller.abort();
    };
  }, [
    open,
  ]);

  const refreshDashboard = () => {
    setDashboardLoading(true);

    return fetchAlertDashboard()
      .then((response) => {
        setDashboard(response);
        setError(null);
      })
      .finally(() => {
        setDashboardLoading(false);
      });
  };

  const loadLivePoints =
    async (): Promise<
      RegionalHazardPoint[]
    > => {
      const [
        earthquakes,
        volcanoes,
        wildfires,
        storms,
      ] =
        await Promise.all([
          fetchEarthquakes({
            period: "week",
            minMagnitude: 2.5,
            maxDepth: 1000,
            search: "",
            sort: "time",
            limit: 500,
          }),

          fetchVolcanoes({
            status: "open",
            days: 3650,
            limit: 500,
            search: "",
          }),

          fetchNaturalHazards({
            type: "wildfires",
            status: "open",
            days: 365,
            limit: 500,
            search: "",
          }),

          fetchNaturalHazards({
            type: "severe-storms",
            status: "open",
            days: 365,
            limit: 500,
            search: "",
          }),
        ]);

      const points:
        RegionalHazardPoint[] = [
          ...earthquakes.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "earthquakes" as const,
              title: event.place,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitude,
              observedAt:
                event.occurredAt,
            }),
          ),

          ...volcanoes.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "volcanoes" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity: null,
              observedAt:
                event.observedAt,
            }),
          ),

          ...wildfires.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "wildfires" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitudeValue,
              observedAt:
                event.observedAt,
            }),
          ),

          ...storms.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "severe-storms" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitudeValue,
              observedAt:
                event.observedAt,
            }),
          ),
        ];

      return points.slice(
        0,
        2000,
      );
    };

  const handleCreateRule = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setBusy(true);
    setError(null);
    setMessage(null);

    void createAlertRule(
      rule,
    )
      .then(() =>
        refreshDashboard()
      )
      .then(() => {
        setMessage(
          "Alert rule created.",
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create rule.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleEvaluate = () => {
    setBusy(true);
    setError(null);
    setMessage(null);

    void loadLivePoints()
      .then((points) =>
        evaluateAlertRules({
          points,
        }),
      )
      .then((response) => {
        setDashboard(
          response.dashboard,
        );

        setMessage(
          `${response.newlyTriggeredCount} new alert`
          + (
            response.newlyTriggeredCount
            === 1
              ? ""
              : "s"
          )
          + " triggered.",
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to evaluate alert rules.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleToggleRule = (
    id: string,
    enabled: boolean,
  ) => {
    setBusy(true);
    setError(null);

    void setAlertRuleEnabled(
      id,
      enabled,
    )
      .then(() =>
        refreshDashboard()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update rule.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleDeleteRule = (
    id: string,
  ) => {
    setBusy(true);
    setError(null);

    void deleteAlertRule(id)
      .then(() =>
        refreshDashboard()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to delete rule.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleAlertStatus = (
    id: string,
    status:
      "ACKNOWLEDGED"
      | "DISMISSED",
  ) => {
    setBusy(true);
    setError(null);

    void updateAlertEventStatus(
      id,
      status,
    )
      .then(() =>
        refreshDashboard()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update alert.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  if (!open) {
    return null;
  }

  return (
    <div className="alert-center-overlay">
      <button
        className="alert-center-backdrop"
        type="button"
        aria-label="Close alert center"
        onClick={onClose}
      />

      <section className="alert-center-workspace">
        <header className="alert-center-header">
          <div>
            <span className="panel-kicker">
              LOCAL NOTIFICATION CENTER
            </span>

            <h2>
              Hazard alert rules
            </h2>

            <p>
              Evaluate deterministic rules
              against TerraScope&apos;s
              monitored hazard feeds.
            </p>
          </div>

          <button
            className="icon-button"
            type="button"
            aria-label="Close alert center"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="alert-center-summary">
          <div>
            <span>
              Rules
            </span>

            <strong>
              {dashboard?.ruleCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Enabled
            </span>

            <strong>
              {dashboard?.enabledRuleCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              New alerts
            </span>

            <strong>
              {dashboard?.newAlertCount
                ?? 0}
            </strong>
          </div>

          <div>
            <span>
              Acknowledged
            </span>

            <strong>
              {dashboard
                ?.acknowledgedAlertCount
                ?? 0}
            </strong>
          </div>
        </div>

        <div className="alert-center-body">
          <div className="alert-rule-column">
            <form
              className="alert-rule-form"
              onSubmit={handleCreateRule}
            >
              <div className="alert-section-heading">
                <ShieldAlert
                  size={15}
                />

                <span>
                  New alert rule
                </span>
              </div>

              <label className="alert-field alert-field--wide">
                <span>
                  Rule name
                </span>

                <input
                  required
                  maxLength={160}
                  value={rule.name}
                  onChange={(event) =>
                    setRule({
                      ...rule,
                      name:
                        event.target.value,
                    })
                  }
                />
              </label>

              <label className="alert-field alert-field--wide">
                <span>
                  Hazard type
                </span>

                <select
                  value={
                    rule.hazardType
                  }
                  onChange={(event) =>
                    setRule({
                      ...rule,
                      hazardType:
                        event.target.value as AlertRuleHazardType,
                    })
                  }
                >
                  <option value="all">
                    All hazards
                  </option>

                  <option value="earthquakes">
                    Earthquakes
                  </option>

                  <option value="volcanoes">
                    Volcanoes
                  </option>

                  <option value="wildfires">
                    Wildfires
                  </option>

                  <option value="severe-storms">
                    Severe storms
                  </option>
                </select>
              </label>

              <label className="alert-field">
                <span>
                  Latitude
                </span>

                <input
                  type="number"
                  min={-90}
                  max={90}
                  step={0.0001}
                  value={
                    rule.latitude
                  }
                  onChange={(event) =>
                    setRule({
                      ...rule,
                      latitude:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />
              </label>

              <label className="alert-field">
                <span>
                  Longitude
                </span>

                <input
                  type="number"
                  min={-180}
                  max={180}
                  step={0.0001}
                  value={
                    rule.longitude
                  }
                  onChange={(event) =>
                    setRule({
                      ...rule,
                      longitude:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />
              </label>

              <label className="alert-field">
                <span>
                  Radius
                </span>

                <div className="alert-input-unit">
                  <input
                    type="number"
                    min={1}
                    max={2000}
                    step={10}
                    value={
                      rule.radiusKm
                    }
                    onChange={(event) =>
                      setRule({
                        ...rule,
                        radiusKm:
                          Number(
                            event.target.value,
                          ),
                      })
                    }
                  />

                  <small>
                    km
                  </small>
                </div>
              </label>

              <label className="alert-field">
                <span>
                  Minimum intensity
                </span>

                <input
                  type="number"
                  min={0}
                  max={1000}
                  step={0.1}
                  value={
                    rule.minimumIntensity
                  }
                  onChange={(event) =>
                    setRule({
                      ...rule,
                      minimumIntensity:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />
              </label>

              <button
                className="alert-primary-action"
                type="submit"
                disabled={busy}
              >
                <BellRing size={15} />

                Create rule
              </button>
            </form>

            <div className="alert-rules-heading">
              <div>
                <span className="panel-kicker">
                  RULES
                </span>

                <strong>
                  Monitoring policies
                </strong>
              </div>

              <button
                type="button"
                disabled={
                  dashboardLoading
                  || busy
                }
                onClick={() => {
                  void refreshDashboard()
                    .catch(
                      (caughtError: unknown) => {
                        setError(
                          caughtError
                            instanceof Error
                            ? caughtError.message
                            : "Unable to refresh.",
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

            <div className="alert-rule-list">
              {dashboardLoading
                && dashboard === null
                ? (
                  <div className="alert-empty">
                    Loading rules…
                  </div>
                )
                : dashboard === null
                  || dashboard.rules.length === 0
                  ? (
                    <div className="alert-empty">
                      No alert rules yet.
                    </div>
                  )
                  : dashboard.rules.map(
                    (item) => (
                      <article
                        className="alert-rule-card"
                        key={item.id}
                      >
                        <div>
                          <strong>
                            {item.name}
                          </strong>

                          <span>
                            {hazardLabel(
                              item.hazardType,
                            )}
                            {" · "}
                            {item.radiusKm
                              .toFixed(0)}
                            {" "}km
                            {" · "}
                            min{" "}
                            {item.minimumIntensity}
                          </span>
                        </div>

                        <div className="alert-rule-card__actions">
                          <button
                            type="button"
                            disabled={busy}
                            className={
                              item.enabled
                                ? "alert-toggle alert-toggle--enabled"
                                : "alert-toggle"
                            }
                            onClick={() =>
                              handleToggleRule(
                                item.id,
                                !item.enabled,
                              )
                            }
                          >
                            {item.enabled
                              ? "Enabled"
                              : "Disabled"}
                          </button>

                          <button
                            type="button"
                            disabled={busy}
                            className="alert-delete-rule"
                            aria-label={
                              `Delete ${item.name}`
                            }
                            onClick={() =>
                              handleDeleteRule(
                                item.id,
                              )
                            }
                          >
                            <Trash2
                              size={14}
                            />
                          </button>
                        </div>
                      </article>
                    ),
                  )
              }
            </div>
          </div>

          <div className="alert-event-column">
            <div className="alert-section-heading">
              <BellRing size={15} />

              <span>
                Notification center
              </span>
            </div>

            <button
              type="button"
              className="alert-evaluate-button"
              disabled={
                busy
                || (
                  dashboard
                    ?.enabledRuleCount
                  ?? 0
                ) === 0
              }
              onClick={handleEvaluate}
            >
              <Activity size={15} />

              {busy
                ? "Evaluating..."
                : "Evaluate live hazard feeds"}
            </button>

            <div className="alert-explanation">
              <MapPin size={14} />

              <span>
                Rules are evaluated locally
                using hazard type, distance
                and optional intensity
                thresholds. Re-evaluating the
                same source event does not
                create duplicate alerts.
              </span>
            </div>

            {message !== null && (
              <div className="alert-message">
                {message}
              </div>
            )}

            {error !== null && (
              <div className="alert-error">
                {error}
              </div>
            )}

            <div className="alert-event-list">
              {dashboard === null
                || dashboard.alerts.length === 0
                ? (
                  <div className="alert-event-empty">
                    <BellRing
                      size={29}
                    />

                    <strong>
                      No active notifications
                    </strong>

                    <span>
                      Create a rule and
                      evaluate the current
                      monitored feeds.
                    </span>
                  </div>
                )
                : dashboard.alerts.map(
                    (alert) => (
                      <article
                        className={
                          alert.status
                            === "NEW"
                            ? "alert-event-card alert-event-card--new"
                            : "alert-event-card"
                        }
                        key={alert.id}
                      >
                        <span className="alert-event-icon">
                          <HazardIcon
                            hazardType={
                              alert.hazardType
                            }
                          />
                        </span>

                        <div className="alert-event-content">
                          <div className="alert-event-top">
                            <div>
                              <span>
                                {hazardLabel(
                                  alert.hazardType,
                                )}
                              </span>

                              <strong>
                                {alert.title}
                              </strong>
                            </div>

                            <span
                              className={
                                alert.status
                                  === "NEW"
                                  ? "alert-status alert-status--new"
                                  : "alert-status"
                              }
                            >
                              {alert.status}
                            </span>
                          </div>

                          <div className="alert-event-meta">
                            <span>
                              Rule:
                              {" "}
                              {alert.ruleName}
                            </span>

                            <span>
                              {alert.distanceKm
                                .toFixed(1)}
                              {" "}km
                            </span>

                            {alert.intensity
                              !== null && (
                              <span>
                                intensity{" "}
                                {alert.intensity}
                              </span>
                            )}
                          </div>

                          <div className="alert-event-actions">
                            {alert.status
                              === "NEW" && (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() =>
                                  handleAlertStatus(
                                    alert.id,
                                    "ACKNOWLEDGED",
                                  )
                                }
                              >
                                <Check
                                  size={13}
                                />

                                Acknowledge
                              </button>
                            )}

                            <button
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                handleAlertStatus(
                                  alert.id,
                                  "DISMISSED",
                                )
                              }
                            >
                              <XCircle
                                size={13}
                              />

                              Dismiss
                            </button>
                          </div>
                        </div>
                      </article>
                    ),
                  )
              }
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
