"use client";

import {
  Activity,
  Archive,
  Award,
  CheckCircle2,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createResiliencePortfolioSnapshot,
  fetchResiliencePortfolio,
  fetchResiliencePortfolioHistory,
} from "@/lib/api";

import type {
  ResiliencePortfolioHistoryResponse,
  ResiliencePortfolioResponse,
} from "@/types/resilience-portfolio";

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

function bandClass(
  band: string,
): string {
  return (
    `portfolio-band portfolio-band--${band.toLowerCase().replaceAll("_", "-")}`
  );
}

export function ResiliencePortfolioScorecard() {
  const [
    portfolio,
    setPortfolio,
  ] =
    useState<
      ResiliencePortfolioResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      ResiliencePortfolioHistoryResponse | null
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
      fetchResiliencePortfolio(
        controller.signal,
      ),

      fetchResiliencePortfolioHistory(
        controller.signal,
      ),
    ])
      .then(
        ([
          portfolioResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setPortfolio(
            portfolioResponse,
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
              : "Unable to load resilience portfolio.",
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
      fetchResiliencePortfolio(),
      fetchResiliencePortfolioHistory(),
    ])
      .then(
        ([
          portfolioResponse,
          historyResponse,
        ]) => {
          setPortfolio(
            portfolioResponse,
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

    void createResiliencePortfolioSnapshot()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to capture portfolio snapshot.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="resilience-portfolio">
      <div className="resilience-portfolio-heading">
        <div>
          <span className="panel-kicker">
            ENTERPRISE RESILIENCE
          </span>

          <strong>
            Portfolio assurance scorecard
          </strong>
        </div>

        <div className="resilience-portfolio-actions">
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
                        : "Unable to refresh portfolio.",
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
              loading
              || busy
              || portfolio === null
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
        <div className="resilience-portfolio-error">
          {error}
        </div>
      )}

      {loading
        && portfolio === null
        ? (
          <div className="resilience-portfolio-empty">
            Calculating enterprise assurance…
          </div>
        )
        : portfolio !== null && (
          <>
            <div className="resilience-portfolio-hero">
              <div className="resilience-portfolio-score">
                <Activity
                  size={18}
                />

                <strong>
                  {portfolio.score}
                </strong>

                <span>
                  /100
                </span>
              </div>

              <div>
                <span>
                  PORTFOLIO ASSURANCE
                </span>

                <strong
                  className={
                    bandClass(
                      portfolio.band,
                    )
                  }
                >
                  {portfolio.band}
                </strong>

                <p>
                  Deterministic enterprise score
                  combining certification,
                  evidence packaging, verified
                  audit ledgers, validation
                  quality, exercises, and
                  unresolved assurance debt.
                </p>
              </div>

              <div className="resilience-portfolio-readiness">
                <span>
                  Avg latest readiness
                </span>

                <strong>
                  {portfolio
                    .averageLatestReadinessScore
                    .toFixed(1)}
                </strong>

                <small>
                  /100
                </small>
              </div>
            </div>

            <div className="resilience-portfolio-metrics">
              <div>
                <span>
                  Incidents
                </span>

                <strong>
                  {
                    portfolio
                      .totalIncidentCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Open / closed
                </span>

                <strong>
                  {
                    portfolio
                      .openIncidentCount
                  }
                  {" / "}
                  {
                    portfolio
                      .closedIncidentCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Certified
                </span>

                <strong>
                  {
                    portfolio
                      .certifiedIncidentCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Evidence
                </span>

                <strong>
                  {
                    portfolio
                      .evidencePackagedIncidentCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Audit ledgers
                </span>

                <strong>
                  {
                    portfolio
                      .auditLedgerIncidentCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Verified ledgers
                </span>

                <strong>
                  {
                    portfolio
                      .auditVerifiedIncidentCount
                  }
                </strong>
              </div>
            </div>

            <div className="resilience-portfolio-grid">
              <section>
                <div className="resilience-portfolio-section-heading">
                  <ShieldCheck
                    size={14}
                  />

                  <strong>
                    Improvement assurance
                  </strong>
                </div>

                <div className="resilience-portfolio-assurance-grid">
                  <article>
                    <span>
                      Completed
                    </span>

                    <strong>
                      {
                        portfolio
                          .completedImprovementCount
                      }
                    </strong>
                  </article>

                  <article>
                    <span>
                      Validated
                    </span>

                    <strong>
                      {
                        portfolio
                          .validatedImprovementCount
                      }
                    </strong>
                  </article>

                  <article>
                    <span>
                      Untested
                    </span>

                    <strong>
                      {
                        portfolio
                          .untestedImprovementCount
                      }
                    </strong>
                  </article>

                  <article>
                    <span>
                      Revalidation
                    </span>

                    <strong>
                      {
                        portfolio
                          .needsRevalidationCount
                      }
                    </strong>
                  </article>

                  <article>
                    <span>
                      Failed
                    </span>

                    <strong>
                      {
                        portfolio
                          .failedImprovementCount
                      }
                    </strong>
                  </article>

                  <article>
                    <span>
                      Open corrective
                    </span>

                    <strong>
                      {
                        portfolio
                          .openCorrectiveActionCount
                      }
                    </strong>
                  </article>
                </div>
              </section>

              <section>
                <div className="resilience-portfolio-section-heading">
                  <Award
                    size={14}
                  />

                  <strong>
                    Exercise validation
                  </strong>
                </div>

                <div className="resilience-portfolio-exercise">
                  <div>
                    <span>
                      Total
                    </span>

                    <strong>
                      {
                        portfolio
                          .totalExerciseCount
                      }
                    </strong>
                  </div>

                  <div>
                    <span>
                      Completed
                    </span>

                    <strong>
                      {
                        portfolio
                          .completedExerciseCount
                      }
                    </strong>
                  </div>

                  <div>
                    <span>
                      Passed
                    </span>

                    <strong>
                      {
                        portfolio
                          .passedExerciseCount
                      }
                    </strong>
                  </div>

                  <div>
                    {portfolio
                      .auditVerifiedIncidentCount
                      === portfolio
                        .auditLedgerIncidentCount
                      ? (
                        <CheckCircle2
                          size={20}
                        />
                      )
                      : (
                        <ShieldX
                          size={20}
                        />
                      )
                    }

                    <span>
                      Audit integrity
                    </span>

                    <strong>
                      {
                        portfolio
                          .auditVerifiedIncidentCount
                      }
                      /
                      {
                        portfolio
                          .auditLedgerIncidentCount
                      }
                    </strong>
                  </div>
                </div>
              </section>
            </div>

            <section className="resilience-portfolio-history-section">
              <div className="resilience-portfolio-section-heading">
                <Archive
                  size={14}
                />

                <strong>
                  Portfolio snapshot history
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
                  <div className="resilience-portfolio-history-empty">
                    No portfolio snapshots captured.
                  </div>
                )
                : (
                  <div className="resilience-portfolio-history">
                    {history.snapshots
                      .slice(
                        0,
                        8,
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
                                Score
                              </span>

                              <strong>
                                {
                                  snapshotItem
                                    .score
                                }
                                /100
                              </strong>
                            </div>

                            <div>
                              <span>
                                Band
                              </span>

                              <strong>
                                {
                                  snapshotItem
                                    .band
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Verified audit
                              </span>

                              <strong>
                                {
                                  snapshotItem
                                    .auditVerifiedIncidentCount
                                }
                              </strong>
                            </div>

                            <time>
                              {formatDate(
                                snapshotItem
                                  .createdAt,
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
