"use client";

import {
  Activity,
  Archive,
  Blocks,
  CircleCheck,
  Gauge,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
  Users,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createCommandPulseSnapshot,
  fetchCommandPulseHistory,
  fetchIncidentCommandPulse,
} from "@/lib/api";

import type {
  CommandPressureBand,
  CommandPulseHistoryResponse,
  IncidentCommandPulseResponse,
} from "@/types/command-pulse";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentCommandPulseProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  refreshToken?: number;
  onTimelineChanged: () => void;
};

function bandClass(
  band:
    CommandPressureBand,
): string {
  return (
    `command-band command-band--${band.toLowerCase()}`
  );
}

function formatTimestamp(
  timestamp: string,
): string {
  return new Intl.DateTimeFormat(
    "en",
    {
      dateStyle: "medium",
      timeStyle: "short",
    },
  ).format(
    new Date(timestamp),
  );
}

export function IncidentCommandPulse({
  incidentId,
  incidentStatus,
  refreshToken = 0,
  onTimelineChanged,
}: IncidentCommandPulseProps) {
  const [
    pulse,
    setPulse,
  ] =
    useState<
      IncidentCommandPulseResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      CommandPulseHistoryResponse | null
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

    void Promise.all([
      fetchIncidentCommandPulse(
        incidentId,
        controller.signal,
      ),
      fetchCommandPulseHistory(
        incidentId,
        controller.signal,
      ),
    ])
      .then(
        ([
          pulseResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setPulse(
            pulseResponse,
          );

          setHistory(
            historyResponse,
          );

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
              : "Unable to load command pulse.",
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
    refreshToken,
  ]);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchIncidentCommandPulse(
        incidentId,
      ),
      fetchCommandPulseHistory(
        incidentId,
      ),
    ])
      .then(
        ([
          pulseResponse,
          historyResponse,
        ]) => {
          setPulse(
            pulseResponse,
          );

          setHistory(
            historyResponse,
          );

          setError(null);
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const saveSnapshot = () => {
    setBusy(true);
    setError(null);

    void createCommandPulseSnapshot(
      incidentId,
    )
      .then(() => {
        onTimelineChanged();

        return refresh();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create command snapshot.",
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
    <section className="command-pulse">
      <div className="command-pulse-heading">
        <div>
          <span className="panel-kicker">
            INCIDENT COMMAND
          </span>

          <strong>
            Unified command pulse
          </strong>
        </div>

        <div className="command-pulse-actions">
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
                        : "Unable to refresh command pulse.",
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

          {!readOnly && (
            <button
              type="button"
              disabled={
                loading
                || busy
                || pulse === null
              }
              onClick={
                saveSnapshot
              }
            >
              <Archive
                size={13}
              />

              Snapshot
            </button>
          )}
        </div>
      </div>

      {error !== null && (
        <div className="command-pulse-error">
          {error}
        </div>
      )}

      {loading
        && pulse === null
        ? (
          <div className="command-pulse-empty">
            Loading command pulse…
          </div>
        )
        : pulse !== null && (
          <>
            <div className="command-pulse-hero">
              <div className="command-score">
                <Gauge
                  size={21}
                />

                <div>
                  <span>
                    Operational pressure
                  </span>

                  <strong>
                    {pulse.score}
                    <small>
                      /100
                    </small>
                  </strong>
                </div>
              </div>

              <span
                className={
                  bandClass(
                    pulse.band,
                  )
                }
              >
                {pulse.band}
              </span>

              <div className="command-generated">
                <Activity
                  size={12}
                />

                {formatTimestamp(
                  pulse.generatedAt,
                )}
              </div>
            </div>

            <div className="command-pulse-summary">
              <div>
                <span>
                  Open actions
                </span>

                <strong>
                  {pulse.openActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Overdue
                </span>

                <strong>
                  {pulse.overdueActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Blocked
                </span>

                <strong>
                  {pulse.blockedActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Shortages
                </span>

                <strong>
                  {pulse.resourceShortageCount}
                </strong>
              </div>

              <div>
                <span>
                  Evacuation backlog
                </span>

                <strong>
                  {pulse.evacuationRemaining}
                </strong>
              </div>

              <div>
                <span>
                  Shelter capacity
                </span>

                <strong>
                  {pulse.shelterRemainingCapacity}
                </strong>
              </div>

              <div>
                <span>
                  Lifelines down
                </span>

                <strong>
                  {pulse.lifelineDisruptedCount}
                </strong>
              </div>

              <div>
                <span>
                  Population affected
                </span>

                <strong>
                  {pulse.lifelineAffectedPopulation}
                </strong>
              </div>

              <div>
                <span>
                  Critical damage
                </span>

                <strong>
                  {pulse.criticalDamageCount}
                </strong>
              </div>

              <div>
                <span>
                  Recovery backlog
                </span>

                <strong>
                  $
                  {pulse
                    .unresolvedDamageLossMusd
                    .toFixed(1)}
                  M
                </strong>
              </div>
            </div>

            <div className="command-pulse-grid">
              <section className="command-blockers">
                <div className="command-section-heading">
                  <ShieldAlert
                    size={14}
                  />

                  <strong>
                    Priority blockers
                  </strong>

                  <span>
                    {pulse.blockers.length}
                  </span>
                </div>

                {pulse.blockers.length
                  === 0
                  ? (
                    <div className="command-clear-state">
                      <CircleCheck
                        size={20}
                      />

                      <strong>
                        No active blockers
                      </strong>

                      <span>
                        The incident has no
                        scored operational
                        pressure factors.
                      </span>
                    </div>
                  )
                  : (
                    <div className="command-blocker-list">
                      {pulse.blockers.map(
                        (
                          blocker,
                          index,
                        ) => (
                          <article
                            key={
                              blocker.code
                            }
                          >
                            <span className="command-blocker-rank">
                              {index + 1}
                            </span>

                            <div>
                              <strong>
                                {blocker.label}
                              </strong>

                              <span>
                                {blocker.value}
                              </span>
                            </div>

                            <span className="command-blocker-weight">
                              +
                              {blocker.weight}
                            </span>
                          </article>
                        ),
                      )}
                    </div>
                  )
                }
              </section>

              <section className="command-history">
                <div className="command-section-heading">
                  <TrendingUp
                    size={14}
                  />

                  <strong>
                    Command snapshots
                  </strong>

                  <span>
                    {history
                      ?.snapshots
                      .length
                      ?? 0}
                  </span>
                </div>

                {history === null
                  || history
                    .snapshots
                    .length === 0
                  ? (
                    <div className="command-history-empty">
                      <Archive
                        size={20}
                      />

                      <span>
                        No snapshots yet.
                      </span>
                    </div>
                  )
                  : (
                    <div className="command-history-list">
                      {history
                        .snapshots
                        .slice(
                          0,
                          6,
                        )
                        .map(
                          (snapshot) => (
                            <article
                              key={
                                snapshot.id
                              }
                            >
                              <span
                                className={
                                  bandClass(
                                    snapshot.band,
                                  )
                                }
                              >
                                {snapshot.band}
                              </span>

                              <strong>
                                {snapshot.score}
                                /100
                              </strong>

                              <span>
                                {
                                  snapshot
                                    .blockerCount
                                }
                                {" blockers"}
                              </span>

                              <time>
                                {formatTimestamp(
                                  snapshot.createdAt,
                                )}
                              </time>
                            </article>
                          ),
                        )
                      }
                    </div>
                  )
                }
              </section>
            </div>

            <div className="command-pulse-footer">
              <Blocks
                size={12}
              />

              <span>
                Deterministic command score
                combines overdue and blocked
                actions, resource shortages,
                evacuation pressure, shelter
                capacity, lifeline disruption,
                and unresolved damage.
              </span>

              <Users
                size={12}
              />

              <span>
                Average repair progress:
                {" "}
                {pulse
                  .averageRepairProgressPct
                  .toFixed(1)}
                %
              </span>
            </div>
          </>
        )
      }
    </section>
  );
}
