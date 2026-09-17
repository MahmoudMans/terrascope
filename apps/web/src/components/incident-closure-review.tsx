"use client";

import {
  Archive,
  CheckCircle2,
  CircleX,
  Clock3,
  RefreshCw,
  ShieldCheck,
  ShieldX,
  TrendingUp,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createAfterActionReview,
  fetchAfterActionReviewHistory,
  fetchIncidentClosureReadiness,
} from "@/lib/api";

import type {
  AfterActionReviewHistoryResponse,
  IncidentClosureReadinessResponse,
} from "@/types/closure-review";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentClosureReviewProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

function formatTimestamp(
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

function formatDuration(
  minutes: number,
): string {
  const hours =
    Math.floor(
      minutes / 60,
    );

  const remainingMinutes =
    minutes % 60;

  if (hours <= 0) {
    return `${remainingMinutes}m`;
  }

  return `${hours}h ${remainingMinutes}m`;
}

export function IncidentClosureReview({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentClosureReviewProps) {
  const [
    readiness,
    setReadiness,
  ] =
    useState<
      IncidentClosureReadinessResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      AfterActionReviewHistoryResponse | null
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
      fetchIncidentClosureReadiness(
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
          readinessResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setReadiness(
            readinessResponse,
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
              : "Unable to load closure review.",
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
      fetchIncidentClosureReadiness(
        incidentId,
      ),
      fetchAfterActionReviewHistory(
        incidentId,
      ),
    ])
      .then(
        ([
          readinessResponse,
          historyResponse,
        ]) => {
          setReadiness(
            readinessResponse,
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

  const captureReview = () => {
    setBusy(true);
    setError(null);

    void createAfterActionReview(
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
              : "Unable to create after-action review.",
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
    <section className="closure-review">
      <div className="closure-heading">
        <div>
          <span className="panel-kicker">
            INCIDENT LIFECYCLE
          </span>

          <strong>
            Closure readiness & after-action
          </strong>
        </div>

        <div className="closure-actions">
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
                        : "Unable to refresh closure readiness.",
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

          {closed && (
            <button
              type="button"
              disabled={
                loading
                || busy
                || readiness === null
              }
              onClick={
                captureReview
              }
            >
              <Archive
                size={13}
              />

              After-action
            </button>
          )}
        </div>
      </div>

      {error !== null && (
        <div className="closure-error">
          {error}
        </div>
      )}

      {loading
        && readiness === null
        ? (
          <div className="closure-empty">
            Evaluating closure readiness…
          </div>
        )
        : readiness !== null && (
          <>
            <div
              className={
                readiness.ready
                  ? "closure-hero closure-hero--ready"
                  : "closure-hero closure-hero--blocked"
              }
            >
              <div className="closure-status-icon">
                {readiness.ready
                  ? (
                    <ShieldCheck
                      size={24}
                    />
                  )
                  : (
                    <ShieldX
                      size={24}
                    />
                  )
                }
              </div>

              <div>
                <span>
                  CLOSURE READINESS
                </span>

                <strong>
                  {readiness.status}
                </strong>

                <p>
                  {readiness.ready
                    ? "Tracked operational obligations are resolved and command pressure is stable."
                    : `${readiness.unresolvedItemCount} unresolved operational items remain before clean closure.`
                  }
                </p>
              </div>

              <div className="closure-command">
                <span>
                  Command pressure
                </span>

                <strong>
                  {readiness.commandScore}
                  /100
                </strong>

                <small>
                  {readiness.commandBand}
                </small>
              </div>
            </div>

            <div className="closure-summary">
              <div>
                <span>
                  Open actions
                </span>

                <strong>
                  {readiness.openActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Shortages
                </span>

                <strong>
                  {readiness.resourceShortageCount}
                </strong>
              </div>

              <div>
                <span>
                  Evacuations
                </span>

                <strong>
                  {readiness.activeEvacuationZoneCount}
                </strong>
              </div>

              <div>
                <span>
                  Active shelters
                </span>

                <strong>
                  {readiness.activeShelterCount}
                </strong>
              </div>

              <div>
                <span>
                  Lifelines
                </span>

                <strong>
                  {readiness.disruptedLifelineCount}
                </strong>
              </div>

              <div>
                <span>
                  Damage
                </span>

                <strong>
                  {readiness.unresolvedDamageCount}
                </strong>
              </div>
            </div>

            <div className="closure-grid">
              <section className="closure-blockers">
                <div className="closure-section-heading">
                  {readiness.ready
                    ? (
                      <CheckCircle2
                        size={14}
                      />
                    )
                    : (
                      <CircleX
                        size={14}
                      />
                    )
                  }

                  <strong>
                    Closure blockers
                  </strong>

                  <span>
                    {readiness.blockers.length}
                  </span>
                </div>

                {readiness.blockers.length
                  === 0
                  ? (
                    <div className="closure-clear">
                      <ShieldCheck
                        size={22}
                      />

                      <strong>
                        No closure blockers
                      </strong>

                      <span>
                        The tracked operational
                        state is ready for closure.
                      </span>
                    </div>
                  )
                  : (
                    <div className="closure-blocker-list">
                      {readiness.blockers.map(
                        (blocker) => (
                          <article
                            key={
                              blocker.code
                            }
                          >
                            <CircleX
                              size={12}
                            />

                            <div>
                              <strong>
                                {blocker.label}
                              </strong>

                              <span>
                                {blocker.value}
                              </span>
                            </div>
                          </article>
                        ),
                      )}
                    </div>
                  )
                }
              </section>

              <section className="after-action-history">
                <div className="closure-section-heading">
                  <TrendingUp
                    size={14}
                  />

                  <strong>
                    After-action history
                  </strong>

                  <span>
                    {history
                      ?.reviews
                      .length
                      ?? 0}
                  </span>
                </div>

                {history === null
                  || history
                    .reviews
                    .length === 0
                  ? (
                    <div className="closure-history-empty">
                      <Archive
                        size={20}
                      />

                      <span>
                        After-action reviews become
                        available once the incident
                        is closed.
                      </span>
                    </div>
                  )
                  : (
                    <div className="closure-history-list">
                      {history
                        .reviews
                        .slice(
                          0,
                          6,
                        )
                        .map(
                          (review) => (
                            <article
                              key={
                                review.id
                              }
                            >
                              <div>
                                <span>
                                  AAR
                                </span>

                                <strong>
                                  #
                                  {review.reviewNumber}
                                </strong>
                              </div>

                              <div>
                                <span>
                                  Readiness
                                </span>

                                <strong>
                                  {
                                    review
                                      .closureReadinessStatus
                                  }
                                </strong>
                              </div>

                              <div>
                                <span>
                                  Final / peak
                                </span>

                                <strong>
                                  {
                                    review
                                      .finalCommandScore
                                  }
                                  {" / "}
                                  {
                                    review
                                      .peakCommandScore
                                  }
                                </strong>
                              </div>

                              <div>
                                <Clock3
                                  size={11}
                                />

                                <span>
                                  {formatDuration(
                                    review.durationMinutes,
                                  )}
                                </span>
                              </div>

                              <time>
                                {formatTimestamp(
                                  review.createdAt,
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

            {history !== null
              && history.reviews.length > 0 && (
              <section className="closure-latest-review">
                <span>
                  LATEST AFTER-ACTION SUMMARY
                </span>

                <p>
                  {
                    history
                      .reviews[0]
                      .executiveSummary
                  }
                </p>
              </section>
            )}
          </>
        )
      }
    </section>
  );
}
