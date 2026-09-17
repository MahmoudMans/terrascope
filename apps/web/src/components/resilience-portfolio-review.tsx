"use client";

import {
  Archive,
  Minus,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createResiliencePortfolioReview,
  fetchResiliencePortfolioReviewHistory,
  fetchResiliencePortfolioReviewPreview,
} from "@/lib/api";

import type {
  PortfolioTrendDirection,
  ResiliencePortfolioReviewHistoryResponse,
  ResiliencePortfolioReviewPreview,
} from "@/types/resilience-portfolio-review";

function formatDelta(
  value: number,
): string {
  if (
    value > 0
  ) {
    return `+${value}`;
  }

  return String(value);
}

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

function trendClass(
  trend: PortfolioTrendDirection,
): string {
  return (
    `portfolio-review-trend portfolio-review-trend--${trend.toLowerCase()}`
  );
}

function TrendIcon({
  trend,
}: {
  trend: PortfolioTrendDirection;
}) {
  if (
    trend === "IMPROVING"
  ) {
    return (
      <TrendingUp
        size={20}
      />
    );
  }

  if (
    trend === "DECLINING"
  ) {
    return (
      <TrendingDown
        size={20}
      />
    );
  }

  return (
    <Minus
      size={20}
    />
  );
}

export function ResiliencePortfolioReview() {
  const [
    preview,
    setPreview,
  ] =
    useState<
      ResiliencePortfolioReviewPreview | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      ResiliencePortfolioReviewHistoryResponse | null
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
      fetchResiliencePortfolioReviewPreview(
        controller.signal,
      ),

      fetchResiliencePortfolioReviewHistory(
        controller.signal,
      ),
    ])
      .then(
        ([
          previewResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setPreview(
            previewResponse,
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
              : "Unable to load executive resilience review.",
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
      fetchResiliencePortfolioReviewPreview(),
      fetchResiliencePortfolioReviewHistory(),
    ])
      .then(
        ([
          previewResponse,
          historyResponse,
        ]) => {
          setPreview(
            previewResponse,
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

    void createResiliencePortfolioReview()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to capture executive portfolio review.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="portfolio-review">
      <div className="portfolio-review-heading">
        <div>
          <span className="panel-kicker">
            EXECUTIVE GOVERNANCE
          </span>

          <strong>
            Resilience trend review
          </strong>
        </div>

        <div className="portfolio-review-actions">
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
                        : "Unable to refresh review.",
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
              || preview === null
            }
            onClick={
              captureReview
            }
          >
            <Archive
              size={13}
            />

            Capture review
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="portfolio-review-error">
          {error}
        </div>
      )}

      {loading
        && preview === null
        ? (
          <div className="portfolio-review-empty">
            Evaluating portfolio trend…
          </div>
        )
        : preview !== null && (
          <>
            <div className="portfolio-review-hero">
              <div
                className={
                  trendClass(
                    preview.trend,
                  )
                }
              >
                <TrendIcon
                  trend={
                    preview.trend
                  }
                />
              </div>

              <div>
                <span>
                  CURRENT TREND
                </span>

                <strong>
                  {preview.trend}
                </strong>

                <p>
                  {preview.executiveSummary}
                </p>
              </div>

              <div className="portfolio-review-score">
                <span>
                  Portfolio score
                </span>

                <strong>
                  {preview.currentScore}
                </strong>

                <small>
                  {formatDelta(
                    preview.scoreDelta,
                  )}
                </small>
              </div>
            </div>

            <div className="portfolio-review-metrics">
              <article>
                <span>
                  Assurance debt
                </span>

                <strong>
                  {
                    preview
                      .currentAssuranceDebt
                  }
                </strong>

                <small>
                  {formatDelta(
                    preview
                      .assuranceDebtDelta,
                  )}
                </small>
              </article>

              <article>
                <span>
                  Certified incidents
                </span>

                <strong>
                  {
                    preview
                      .currentCertifiedIncidentCount
                  }
                </strong>

                <small>
                  {formatDelta(
                    preview
                      .certifiedIncidentDelta,
                  )}
                </small>
              </article>

              <article>
                <span>
                  Validated improvements
                </span>

                <strong>
                  {
                    preview
                      .currentValidatedImprovementCount
                  }
                </strong>

                <small>
                  {formatDelta(
                    preview
                      .validatedImprovementDelta,
                  )}
                </small>
              </article>

              <article>
                <span>
                  Verified audit ledgers
                </span>

                <strong>
                  {
                    preview
                      .currentAuditVerifiedIncidentCount
                  }
                </strong>

                <small>
                  {formatDelta(
                    preview
                      .auditVerifiedIncidentDelta,
                  )}
                </small>
              </article>
            </div>

            <section className="portfolio-review-history-section">
              <div className="portfolio-review-section-heading">
                <Archive
                  size={14}
                />

                <strong>
                  Executive review history
                </strong>

                <span>
                  {history
                    ?.reviews
                    .length
                    ?? 0}
                </span>
              </div>

              {history === null
                || history.reviews.length
                  === 0
                ? (
                  <div className="portfolio-review-history-empty">
                    No executive resilience
                    reviews captured.
                  </div>
                )
                : (
                  <div className="portfolio-review-history">
                    {history.reviews
                      .slice(
                        0,
                        8,
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
                                REVIEW
                              </span>

                              <strong>
                                #
                                {
                                  review
                                    .reviewNumber
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Trend
                              </span>

                              <strong>
                                {
                                  review
                                    .trend
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Score
                              </span>

                              <strong>
                                {
                                  review
                                    .currentScore
                                }
                                {" "}
                                (
                                {formatDelta(
                                  review
                                    .scoreDelta,
                                )}
                                )
                              </strong>
                            </div>

                            <div>
                              <span>
                                Debt
                              </span>

                              <strong>
                                {
                                  review
                                    .currentAssuranceDebt
                                }
                                {" "}
                                (
                                {formatDelta(
                                  review
                                    .assuranceDebtDelta,
                                )}
                                )
                              </strong>
                            </div>

                            <time>
                              {formatDate(
                                review.createdAt,
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
