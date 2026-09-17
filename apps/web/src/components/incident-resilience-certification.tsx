"use client";

import {
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
  createResilienceCertification,
  fetchResilienceCertificationHistory,
  fetchResilienceCertificationReadiness,
} from "@/lib/api";

import type {
  ResilienceCertificationHistoryResponse,
  ResilienceCertificationReadinessResponse,
} from "@/types/resilience-certification";

type IncidentResilienceCertificationProps = {
  incidentId: string;
  onTimelineChanged: () => void;
};

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

export function IncidentResilienceCertification({
  incidentId,
  onTimelineChanged,
}: IncidentResilienceCertificationProps) {
  const [
    readiness,
    setReadiness,
  ] =
    useState<
      ResilienceCertificationReadinessResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      ResilienceCertificationHistoryResponse | null
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
      fetchResilienceCertificationReadiness(
        incidentId,
        controller.signal,
      ),

      fetchResilienceCertificationHistory(
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
              : "Unable to load resilience certification.",
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
      fetchResilienceCertificationReadiness(
        incidentId,
      ),

      fetchResilienceCertificationHistory(
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

  const certify = () => {
    setBusy(true);
    setError(null);

    void createResilienceCertification(
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
              : "Unable to issue resilience certification.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="resilience-certification">
      <div className="resilience-certification-heading">
        <div>
          <span className="panel-kicker">
            FINAL ASSURANCE
          </span>

          <strong>
            Resilience certification
          </strong>
        </div>

        <div className="resilience-certification-actions">
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
                        : "Unable to refresh certification.",
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
              || readiness === null
              || !readiness.ready
            }
            onClick={certify}
          >
            <Award
              size={13}
            />

            Certify
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="resilience-certification-error">
          {error}
        </div>
      )}

      {loading
        && readiness === null
        ? (
          <div className="resilience-certification-empty">
            Evaluating assurance evidence…
          </div>
        )
        : readiness !== null && (
          <>
            <div
              className={
                readiness.ready
                  ? "resilience-certification-hero resilience-certification-hero--ready"
                  : "resilience-certification-hero resilience-certification-hero--blocked"
              }
            >
              <div>
                {readiness.ready
                  ? (
                    <ShieldCheck
                      size={27}
                    />
                  )
                  : (
                    <ShieldX
                      size={27}
                    />
                  )
                }
              </div>

              <div>
                <span>
                  CERTIFICATION READINESS
                </span>

                <strong>
                  {readiness.status}
                </strong>

                <p>
                  {readiness.ready
                    ? "The post-incident improvement and validation lifecycle satisfies the deterministic TerraScope assurance gate."
                    : `${readiness.blockers.length} assurance blockers remain before certification.`
                  }
                </p>
              </div>

              <div className="resilience-certification-score">
                <span>
                  Latest readiness
                </span>

                <strong>
                  {readiness
                    .averageLatestReadinessScore
                    .toFixed(1)}
                </strong>

                <small>
                  /100
                </small>
              </div>
            </div>

            <div className="resilience-certification-summary">
              <div>
                <span>
                  Improvements
                </span>

                <strong>
                  {
                    readiness
                      .completedImprovementCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Validated
                </span>

                <strong>
                  {readiness.validatedCount}
                </strong>
              </div>

              <div>
                <span>
                  Untested
                </span>

                <strong>
                  {readiness.untestedCount}
                </strong>
              </div>

              <div>
                <span>
                  Revalidate
                </span>

                <strong>
                  {
                    readiness
                      .needsRevalidationCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Failed
                </span>

                <strong>
                  {readiness.failedCount}
                </strong>
              </div>

              <div>
                <span>
                  Open corrective
                </span>

                <strong>
                  {
                    readiness
                      .openCorrectiveActionCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Exercises
                </span>

                <strong>
                  {
                    readiness
                      .totalExerciseCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Passed
                </span>

                <strong>
                  {
                    readiness
                      .passedExerciseCount
                  }
                </strong>
              </div>
            </div>

            <div className="resilience-certification-grid">
              <section>
                <div className="resilience-certification-section-heading">
                  {readiness.ready
                    ? (
                      <CheckCircle2
                        size={14}
                      />
                    )
                    : (
                      <ShieldX
                        size={14}
                      />
                    )
                  }

                  <strong>
                    Assurance blockers
                  </strong>

                  <span>
                    {readiness.blockers.length}
                  </span>
                </div>

                {readiness.blockers.length
                  === 0
                  ? (
                    <div className="resilience-certification-clear">
                      <ShieldCheck
                        size={23}
                      />

                      <strong>
                        Assurance gate satisfied
                      </strong>

                      <span>
                        All completed improvements
                        are validated and no active
                        corrective actions remain.
                      </span>
                    </div>
                  )
                  : (
                    <div className="resilience-certification-blockers">
                      {readiness.blockers.map(
                        (blocker) => (
                          <article
                            key={blocker.code}
                          >
                            <ShieldX
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

              <section>
                <div className="resilience-certification-section-heading">
                  <Award
                    size={14}
                  />

                  <strong>
                    Certification history
                  </strong>

                  <span>
                    {history
                      ?.certifications
                      .length
                      ?? 0}
                  </span>
                </div>

                {history === null
                  || history
                    .certifications
                    .length === 0
                  ? (
                    <div className="resilience-certification-clear">
                      <Award
                        size={23}
                      />

                      <span>
                        No certification snapshots
                        have been issued.
                      </span>
                    </div>
                  )
                  : (
                    <div className="resilience-certification-history">
                      {history.certifications.map(
                        (certification) => (
                          <article
                            key={
                              certification.id
                            }
                          >
                            <div>
                              <span>
                                CERT
                              </span>

                              <strong>
                                #
                                {
                                  certification
                                    .certificationNumber
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Status
                              </span>

                              <strong>
                                {
                                  certification
                                    .status
                                }
                              </strong>
                            </div>

                            <div>
                              <span>
                                Readiness
                              </span>

                              <strong>
                                {
                                  certification
                                    .averageLatestReadinessScore
                                    .toFixed(1)
                                }
                              </strong>
                            </div>

                            <time>
                              {formatDate(
                                certification.createdAt,
                              )}
                            </time>
                          </article>
                        ),
                      )}
                    </div>
                  )
                }
              </section>
            </div>

            {history !== null
              && history.certifications.length
                > 0 && (
              <section className="resilience-certification-latest">
                <span>
                  LATEST CERTIFICATION EVIDENCE
                </span>

                <p>
                  {
                    history
                      .certifications[0]
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
