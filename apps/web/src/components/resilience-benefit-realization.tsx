"use client";

import {
  Archive,
  CheckCircle2,
  CircleAlert,
  RefreshCw,
  ShieldCheck,
  Target,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createBenefitRealizationSnapshot,
  fetchBenefitRealization,
  fetchBenefitRealizationHistory,
} from "@/lib/api";

import type {
  BenefitRealizationBand,
  BenefitRealizationHistoryResponse,
  BenefitRealizationResponse,
} from "@/types/resilience-benefit-realization";

function formatDate(
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

function formatDelta(
  value: number,
): string {
  if (
    value > 0
  ) {
    return `+${value.toFixed(1)}`;
  }

  return value.toFixed(1);
}

function bandClass(
  band: BenefitRealizationBand,
): string {
  return (
    `benefit-realization-band benefit-realization-band--${band.toLowerCase()}`
  );
}

export function ResilienceBenefitRealization() {
  const [
    realization,
    setRealization,
  ] =
    useState<
      BenefitRealizationResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      BenefitRealizationHistoryResponse | null
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
      fetchBenefitRealization(
        controller.signal,
      ),

      fetchBenefitRealizationHistory(
        controller.signal,
      ),
    ])
      .then(
        ([
          realizationResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setRealization(
            realizationResponse,
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
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load benefit realization.",
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
  }, []);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchBenefitRealization(),
      fetchBenefitRealizationHistory(),
    ])
      .then(
        ([
          realizationResponse,
          historyResponse,
        ]) => {
          setRealization(
            realizationResponse,
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

  const snapshot = () => {
    setBusy(true);
    setError(null);

    void createBenefitRealizationSnapshot()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to capture benefit realization snapshot.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="benefit-realization">
      <div className="benefit-realization-heading">
        <div>
          <span className="panel-kicker">
            ENTERPRISE BENEFITS
          </span>

          <strong>
            Benefit realization & governance closure
          </strong>
        </div>

        <div className="benefit-realization-actions">
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
                        : "Unable to refresh benefit realization.",
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

          <button
            type="button"
            disabled={
              busy
              || realization === null
            }
            onClick={snapshot}
          >
            <Archive
              size={13}
            />

            Snapshot
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="benefit-realization-error">
          {error}
        </div>
      )}

      {loading
        && realization === null
        ? (
          <div className="benefit-realization-empty">
            Calculating enterprise benefit realization…
          </div>
        )
        : realization !== null && (
          <>
            <div className="benefit-realization-hero">
              <div
                className={
                  bandClass(
                    realization.band,
                  )
                }
              >
                <Target
                  size={24}
                />
              </div>

              <div>
                <span>
                  REALIZATION BAND
                </span>

                <strong>
                  {realization.band}
                </strong>

                <p>
                  Latest outcome validation across
                  all completed strategic resilience
                  objectives.
                </p>
              </div>

              <div
                className={
                  realization.closureReady
                    ? "benefit-realization-closure benefit-realization-closure--ready"
                    : "benefit-realization-closure benefit-realization-closure--blocked"
                }
              >
                {realization.closureReady
                  ? (
                    <ShieldCheck
                      size={18}
                    />
                  )
                  : (
                    <CircleAlert
                      size={18}
                    />
                  )
                }

                <span>
                  Governance closure
                </span>

                <strong>
                  {realization.closureReady
                    ? "READY"
                    : "OPEN"
                  }
                </strong>
              </div>
            </div>

            <div className="benefit-realization-summary">
              <article>
                <span>
                  Objectives
                </span>

                <strong>
                  {realization.totalObjectiveCount}
                </strong>
              </article>

              <article>
                <span>
                  Completed
                </span>

                <strong>
                  {
                    realization
                      .completedObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  Validated
                </span>

                <strong>
                  {
                    realization
                      .validatedObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  Unvalidated
                </span>

                <strong>
                  {
                    realization
                      .unvalidatedCompletedObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  Achieved
                </span>

                <strong>
                  {realization.achievedCount}
                </strong>
              </article>

              <article>
                <span>
                  Partial
                </span>

                <strong>
                  {realization.partialCount}
                </strong>
              </article>

              <article>
                <span>
                  Not achieved
                </span>

                <strong>
                  {realization.notAchievedCount}
                </strong>
              </article>
            </div>

            <div className="benefit-realization-rates">
              <article>
                <span>
                  Validation coverage
                </span>

                <strong>
                  {realization
                    .validationCoveragePct
                    .toFixed(1)}
                  %
                </strong>
              </article>

              <article>
                <span>
                  Realized benefit rate
                </span>

                <strong>
                  {realization
                    .realizedBenefitRatePct
                    .toFixed(1)}
                  %
                </strong>
              </article>

              <article>
                <span>
                  Avg score delta
                </span>

                <strong>
                  {formatDelta(
                    realization
                      .averageScoreDelta,
                  )}
                </strong>
              </article>

              <article>
                <span>
                  Avg debt delta
                </span>

                <strong>
                  {formatDelta(
                    realization
                      .averageAssuranceDebtDelta,
                  )}
                </strong>
              </article>
            </div>

            <div className="benefit-realization-workflow">
              <article>
                <span>
                  Planned
                </span>

                <strong>
                  {
                    realization
                      .plannedObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  In progress
                </span>

                <strong>
                  {
                    realization
                      .inProgressObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  Blocked
                </span>

                <strong>
                  {
                    realization
                      .blockedObjectiveCount
                  }
                </strong>
              </article>

              <article>
                <span>
                  Cancelled
                </span>

                <strong>
                  {
                    realization
                      .cancelledObjectiveCount
                  }
                </strong>
              </article>
            </div>

            <section className="benefit-realization-history-section">
              <div className="benefit-realization-section-heading">
                <Archive
                  size={14}
                />

                <strong>
                  Enterprise realization history
                </strong>

                <span>
                  {history
                    ?.snapshots
                    .length
                    ?? 0}
                </span>
              </div>

              {history === null
                || history.snapshots.length
                  === 0
                ? (
                  <div className="benefit-realization-history-empty">
                    No enterprise benefit
                    realization snapshots captured.
                  </div>
                )
                : (
                  <div className="benefit-realization-history">
                    {history.snapshots
                      .slice(
                        0,
                        10,
                      )
                      .map(
                        (snapshotItem) => (
                          <article
                            key={
                              snapshotItem.id
                            }
                          >
                            <div>
                              <span>
                                SNAPSHOT
                              </span>

                              <strong>
                                #
                                {
                                  snapshotItem
                                    .snapshotNumber
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Band
                              </span>

                              <strong>
                                {snapshotItem.band}
                              </strong>
                            </div>

                            <div>
                              <span>
                                Coverage
                              </span>

                              <strong>
                                {
                                  snapshotItem
                                    .validationCoveragePct
                                    .toFixed(1)
                                }
                                %
                              </strong>
                            </div>

                            <div>
                              <span>
                                Realized
                              </span>

                              <strong>
                                {
                                  snapshotItem
                                    .realizedBenefitRatePct
                                    .toFixed(1)
                                }
                                %
                              </strong>
                            </div>

                            <div>
                              {snapshotItem.closureReady
                                ? (
                                  <CheckCircle2
                                    size={13}
                                  />
                                )
                                : (
                                  <CircleAlert
                                    size={13}
                                  />
                                )
                              }

                              <strong>
                                {snapshotItem.closureReady
                                  ? "READY"
                                  : "OPEN"
                                }
                              </strong>
                            </div>

                            <time>
                              {formatDate(
                                snapshotItem.createdAt,
                              )}
                            </time>
                          </article>
                        ),
                      )}
                  </div>
                )
              }
            </section>
          </>
        )
      }
    </section>
  );
}
