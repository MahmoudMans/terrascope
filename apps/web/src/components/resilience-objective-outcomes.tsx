"use client";

import {
  Award,
  CheckCircle2,
  Minus,
  RefreshCw,
  ShieldX,
  TrendingDown,
  TrendingUp,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  fetchPortfolioObjectiveBoard,
  fetchPortfolioObjectiveOutcomePreview,
  fetchPortfolioObjectiveValidationHistory,
  validatePortfolioObjectiveOutcome,
} from "@/lib/api";

import type {
  PortfolioObjective,
  PortfolioObjectiveBoardResponse,
} from "@/types/resilience-portfolio-objectives";

import type {
  PortfolioObjectiveOutcome,
  PortfolioObjectiveOutcomePreview,
  PortfolioObjectiveValidationHistoryResponse,
} from "@/types/resilience-objective-outcomes";

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

function outcomeClass(
  outcome: PortfolioObjectiveOutcome,
): string {
  return (
    `objective-outcome objective-outcome--${outcome.toLowerCase().replaceAll("_", "-")}`
  );
}

function OutcomeIcon({
  outcome,
}: {
  outcome: PortfolioObjectiveOutcome;
}) {
  if (
    outcome === "ACHIEVED"
  ) {
    return (
      <TrendingUp
        size={23}
      />
    );
  }

  if (
    outcome === "NOT_ACHIEVED"
  ) {
    return (
      <TrendingDown
        size={23}
      />
    );
  }

  return (
    <Minus
      size={23}
    />
  );
}

export function ResilienceObjectiveOutcomes() {
  const [
    board,
    setBoard,
  ] =
    useState<
      PortfolioObjectiveBoardResponse | null
    >(null);

  const [
    selectedObjectiveId,
    setSelectedObjectiveId,
  ] =
    useState("");

  const [
    preview,
    setPreview,
  ] =
    useState<
      PortfolioObjectiveOutcomePreview | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      PortfolioObjectiveValidationHistoryResponse | null
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

  const completedObjectives:
    PortfolioObjective[] =
      board?.objectives.filter(
        (objective) =>
          objective.status
          === "COMPLETED",
      )
      ?? [];

  useEffect(() => {
    const controller =
      new AbortController();

    void fetchPortfolioObjectiveBoard(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setBoard(response);

        const firstCompleted =
          response.objectives.find(
            (objective) =>
              objective.status
              === "COMPLETED",
          );

        if (
          firstCompleted !== undefined
        ) {
          setSelectedObjectiveId(
            firstCompleted.id,
          );
        }

        setError(null);
      })
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
              : "Unable to load objective outcomes.",
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

  useEffect(() => {
    if (
      selectedObjectiveId === ""
    ) {
      return;
    }

    const controller =
      new AbortController();

    void Promise.all([
      fetchPortfolioObjectiveOutcomePreview(
        selectedObjectiveId,
        controller.signal,
      ),

      fetchPortfolioObjectiveValidationHistory(
        selectedObjectiveId,
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
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to evaluate objective outcome.",
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
    selectedObjectiveId,
  ]);

  const refresh = () => {
    if (
      selectedObjectiveId === ""
    ) {
      return Promise.resolve();
    }

    setLoading(true);

    return Promise.all([
      fetchPortfolioObjectiveOutcomePreview(
        selectedObjectiveId,
      ),

      fetchPortfolioObjectiveValidationHistory(
        selectedObjectiveId,
      ),
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

  const validateOutcome = () => {
    if (
      selectedObjectiveId === ""
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void validatePortfolioObjectiveOutcome(
      selectedObjectiveId,
    )
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to validate objective outcome.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="objective-outcomes">
      <div className="objective-outcomes-heading">
        <div>
          <span className="panel-kicker">
            BENEFIT REALIZATION
          </span>

          <strong>
            Strategic objective outcome validation
          </strong>
        </div>

        <div className="objective-outcomes-actions">
          <button
            type="button"
            disabled={
              loading
              || busy
              || selectedObjectiveId === ""
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
              || selectedObjectiveId === ""
            }
            onClick={
              validateOutcome
            }
          >
            <Award
              size={13}
            />

            Validate outcome
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="objective-outcomes-error">
          {error}
        </div>
      )}

      {completedObjectives.length
        === 0
        ? (
          <div className="objective-outcomes-empty">
            Complete a strategic resilience
            objective before validating realized
            portfolio benefits.
          </div>
        )
        : (
          <label className="objective-outcomes-select">
            <span>
              Completed objective
            </span>

            <select
              value={
                selectedObjectiveId
              }
              onChange={(event) =>
                setSelectedObjectiveId(
                  event.target.value,
                )
              }
            >
              {completedObjectives.map(
                (objective) => (
                  <option
                    key={objective.id}
                    value={objective.id}
                  >
                    {objective.title}
                  </option>
                ),
              )}
            </select>
          </label>
        )
      }

      {loading
        && preview === null
        ? (
          <div className="objective-outcomes-empty">
            Calculating benefit realization…
          </div>
        )
        : preview !== null && (
          <>
            <div className="objective-outcomes-hero">
              <div
                className={
                  outcomeClass(
                    preview.outcome,
                  )
                }
              >
                <OutcomeIcon
                  outcome={
                    preview.outcome
                  }
                />
              </div>

              <div>
                <span>
                  CURRENT OUTCOME
                </span>

                <strong>
                  {preview.outcome}
                </strong>

                <p>
                  {preview.executiveSummary}
                </p>
              </div>

              <div className="objective-outcomes-score">
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

            <div className="objective-outcomes-metrics">
              <article>
                <span>
                  Baseline snapshot
                </span>

                <strong>
                  #
                  {
                    preview
                      .baselineSnapshotNumber
                  }
                </strong>
              </article>

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
                  {formatDelta(
                    preview
                      .certifiedIncidentDelta,
                  )}
                </strong>
              </article>

              <article>
                <span>
                  Validated improvements
                </span>

                <strong>
                  {formatDelta(
                    preview
                      .validatedImprovementDelta,
                  )}
                </strong>
              </article>

              <article>
                <span>
                  Verified audit ledgers
                </span>

                <strong>
                  {formatDelta(
                    preview
                      .auditVerifiedIncidentDelta,
                  )}
                </strong>
              </article>
            </div>

            <section className="objective-outcomes-history-section">
              <div className="objective-outcomes-section-heading">
                <Award
                  size={14}
                />

                <strong>
                  Validation history
                </strong>

                <span>
                  {history
                    ?.validations
                    .length
                    ?? 0}
                </span>
              </div>

              {history === null
                || history.validations.length
                  === 0
                ? (
                  <div className="objective-outcomes-history-empty">
                    No realized-benefit validations
                    captured yet.
                  </div>
                )
                : (
                  <div className="objective-outcomes-history">
                    {history.validations.map(
                      (validation) => (
                        <article
                          key={
                            validation.id
                          }
                        >
                          <div>
                            <span>
                              VALIDATION
                            </span>

                            <strong>
                              #
                              {
                                validation
                                  .validationNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Outcome
                            </span>

                            <strong>
                              {
                                validation
                                  .outcome
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Score
                            </span>

                            <strong>
                              {
                                validation
                                  .validationScore
                              }
                              {" "}
                              (
                              {formatDelta(
                                validation
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
                                validation
                                  .validationAssuranceDebt
                              }
                              {" "}
                              (
                              {formatDelta(
                                validation
                                  .assuranceDebtDelta,
                              )}
                              )
                            </strong>
                          </div>

                          <time>
                            {formatDate(
                              validation.createdAt,
                            )}
                          </time>

                          {validation.outcome
                            === "ACHIEVED"
                            ? (
                              <CheckCircle2
                                size={13}
                              />
                            )
                            : validation.outcome
                              === "NOT_ACHIEVED"
                              ? (
                                <ShieldX
                                  size={13}
                                />
                              )
                              : (
                                <Minus
                                  size={13}
                                />
                              )
                          }
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
