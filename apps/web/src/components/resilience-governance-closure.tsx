"use client";

import {
  Award,
  CheckCircle2,
  Fingerprint,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createGovernanceClosureCertificate,
  fetchGovernanceClosureCertificates,
  fetchGovernanceClosureReadiness,
  verifyGovernanceClosureCertificates,
} from "@/lib/api";

import type {
  GovernanceClosureCertificateHistoryResponse,
  GovernanceClosureReadinessResponse,
  GovernanceClosureVerificationResponse,
} from "@/types/resilience-governance-closure";

function shortHash(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "GENESIS";
  }

  if (
    value.length <= 24
  ) {
    return value;
  }

  return (
    `${value.slice(0, 12)}…${value.slice(-8)}`
  );
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

export function ResilienceGovernanceClosure() {
  const [
    readiness,
    setReadiness,
  ] =
    useState<
      GovernanceClosureReadinessResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceClosureCertificateHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceClosureVerificationResponse | null
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
      fetchGovernanceClosureReadiness(
        controller.signal,
      ),

      fetchGovernanceClosureCertificates(
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
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load governance closure assurance.",
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
      fetchGovernanceClosureReadiness(),
      fetchGovernanceClosureCertificates(),
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

  const issueCertificate = () => {
    setBusy(true);
    setError(null);

    void createGovernanceClosureCertificate()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to issue governance closure certificate.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verify = () => {
    setBusy(true);
    setError(null);

    void verifyGovernanceClosureCertificates()
      .then((response) => {
        setVerification(
          response,
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to verify closure certificate chain.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="governance-closure">
      <div className="governance-closure-heading">
        <div>
          <span className="panel-kicker">
            ENTERPRISE ASSURANCE
          </span>

          <strong>
            Governance closure certificate
          </strong>
        </div>

        <div className="governance-closure-actions">
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
                        : "Unable to refresh closure assurance.",
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
            onClick={
              issueCertificate
            }
          >
            <Award
              size={13}
            />

            Issue certificate
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={verify}
          >
            <ShieldCheck
              size={13}
            />

            Verify chain
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="governance-closure-error">
          {error}
        </div>
      )}

      {loading
        && readiness === null
        ? (
          <div className="governance-closure-empty">
            Evaluating enterprise governance closure…
          </div>
        )
        : readiness !== null && (
          <>
            <div className="governance-closure-hero">
              <div
                className={
                  readiness.ready
                    ? "governance-closure-seal governance-closure-seal--ready"
                    : "governance-closure-seal governance-closure-seal--blocked"
                }
              >
                {readiness.ready
                  ? (
                    <ShieldCheck
                      size={25}
                    />
                  )
                  : (
                    <ShieldX
                      size={25}
                    />
                  )
                }
              </div>

              <div>
                <span>
                  CLOSURE READINESS
                </span>

                <strong>
                  {readiness.ready
                    ? "READY TO CERTIFY"
                    : "BLOCKED"
                  }
                </strong>

                <p>
                  {readiness.band}
                  {" · "}
                  {readiness.validationCoveragePct.toFixed(1)}
                  % validation coverage
                  {" · "}
                  {readiness.realizedBenefitRatePct.toFixed(1)}
                  % realized benefit
                </p>
              </div>

              <div className="governance-closure-count">
                <span>
                  Certificates
                </span>

                <strong>
                  {history
                    ?.certificates
                    .length
                    ?? 0}
                </strong>
              </div>
            </div>

            <div className="governance-closure-metrics">
              <article>
                <span>
                  Objectives
                </span>

                <strong>
                  {readiness.totalObjectiveCount}
                </strong>
              </article>

              <article>
                <span>
                  Completed
                </span>

                <strong>
                  {readiness.completedObjectiveCount}
                </strong>
              </article>

              <article>
                <span>
                  Validated
                </span>

                <strong>
                  {readiness.validatedObjectiveCount}
                </strong>
              </article>

              <article>
                <span>
                  Achieved
                </span>

                <strong>
                  {readiness.achievedCount}
                </strong>
              </article>
            </div>

            {!readiness.ready
              && readiness.blockers.length > 0 && (
              <div className="governance-closure-blockers">
                {readiness.blockers.map(
                  (blocker) => (
                    <span key={blocker}>
                      {blocker}
                    </span>
                  ),
                )}
              </div>
            )}

            {verification !== null && (
              <div
                className={
                  verification.valid
                    ? "governance-closure-verification governance-closure-verification--valid"
                    : "governance-closure-verification governance-closure-verification--invalid"
                }
              >
                {verification.valid
                  ? (
                    <CheckCircle2
                      size={16}
                    />
                  )
                  : (
                    <ShieldX
                      size={16}
                    />
                  )
                }

                <div>
                  <strong>
                    {verification.message}
                  </strong>

                  <span>
                    {
                      verification
                        .verifiedCertificateCount
                    }
                    {" / "}
                    {
                      verification
                        .certificateCount
                    }
                    {" verified"}
                  </span>
                </div>
              </div>
            )}

            <section className="governance-closure-history-section">
              <div className="governance-closure-section-heading">
                <Fingerprint
                  size={14}
                />

                <strong>
                  Assurance certificate chain
                </strong>
              </div>

              {history === null
                || history.certificates.length
                  === 0
                ? (
                  <div className="governance-closure-history-empty">
                    No enterprise closure certificates issued.
                  </div>
                )
                : (
                  <div className="governance-closure-history">
                    {history.certificates.map(
                      (certificate) => (
                        <article
                          key={
                            certificate.id
                          }
                        >
                          <div>
                            <span>
                              CERTIFICATE
                            </span>

                            <strong>
                              #
                              {
                                certificate
                                  .certificateNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Benefit snapshot
                            </span>

                            <strong>
                              #
                              {
                                certificate
                                  .benefitSnapshotNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Dossier
                            </span>

                            <code>
                              {shortHash(
                                certificate.dossierHash,
                              )}
                            </code>
                          </div>

                          <div>
                            <span>
                              Previous
                            </span>

                            <code>
                              {shortHash(
                                certificate
                                  .previousCertificateHash,
                              )}
                            </code>
                          </div>

                          <div>
                            <span>
                              Certificate hash
                            </span>

                            <code>
                              {shortHash(
                                certificate
                                  .certificateHash,
                              )}
                            </code>
                          </div>

                          <time>
                            {formatDate(
                              certificate.createdAt,
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
