"use client";

import {
  Archive,
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
  createResilienceEvidencePackage,
  fetchResilienceEvidenceHistory,
  fetchResilienceEvidencePreview,
  verifyResilienceEvidencePackage,
} from "@/lib/api";

import type {
  EvidencePackageVerificationResponse,
  ResilienceEvidencePackageHistoryResponse,
  ResilienceEvidencePackagePreview,
} from "@/types/resilience-evidence";

type IncidentResilienceEvidenceProps = {
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

function shortHash(
  value: string,
): string {
  if (
    value.length <= 20
  ) {
    return value;
  }

  return (
    `${value.slice(0, 12)}…${value.slice(-8)}`
  );
}

export function IncidentResilienceEvidence({
  incidentId,
  onTimelineChanged,
}: IncidentResilienceEvidenceProps) {
  const [
    preview,
    setPreview,
  ] =
    useState<
      ResilienceEvidencePackagePreview | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      ResilienceEvidencePackageHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      EvidencePackageVerificationResponse | null
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
      fetchResilienceEvidencePreview(
        incidentId,
        controller.signal,
      ),

      fetchResilienceEvidenceHistory(
        incidentId,
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
              : "Unable to load resilience evidence.",
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
      fetchResilienceEvidencePreview(
        incidentId,
      ),

      fetchResilienceEvidenceHistory(
        incidentId,
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

  const createPackage = () => {
    setBusy(true);
    setError(null);

    void createResilienceEvidencePackage(
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
              : "Unable to create evidence package.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verifyPackage = (
    packageId: string,
  ) => {
    setBusy(true);
    setError(null);

    void verifyResilienceEvidencePackage(
      incidentId,
      packageId,
    )
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
              : "Unable to verify evidence package.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="resilience-evidence">
      <div className="resilience-evidence-heading">
        <div>
          <span className="panel-kicker">
            AUDIT EVIDENCE
          </span>

          <strong>
            Resilience evidence package
          </strong>
        </div>

        <div className="resilience-evidence-actions">
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
                        : "Unable to refresh evidence.",
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
            onClick={createPackage}
          >
            <Archive
              size={13}
            />

            Snapshot
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="resilience-evidence-error">
          {error}
        </div>
      )}

      {loading
        && preview === null
        ? (
          <div className="resilience-evidence-empty">
            Building evidence manifest…
          </div>
        )
        : preview !== null && (
          <>
            <div className="resilience-evidence-hero">
              <div>
                <Fingerprint
                  size={27}
                />
              </div>

              <div>
                <span>
                  SHA-256 AUDIT MANIFEST
                </span>

                <strong>
                  Certification #
                  {preview.certificationNumber}
                </strong>

                <p>
                  Local deterministic evidence
                  fingerprint anchored to the
                  latest resilience certification.
                </p>
              </div>

              <div className="resilience-evidence-score">
                <span>
                  Certified readiness
                </span>

                <strong>
                  {preview
                    .certificationReadinessScore
                    .toFixed(1)}
                </strong>

                <small>
                  /100
                </small>
              </div>
            </div>

            <div className="resilience-evidence-hash">
              <Fingerprint
                size={13}
              />

              <div>
                <span>
                  {preview.manifestVersion}
                </span>

                <code>
                  {preview.manifestHash}
                </code>
              </div>
            </div>

            <div className="resilience-evidence-summary">
              <div>
                <span>
                  Certifications
                </span>

                <strong>
                  {preview.certificationCount}
                </strong>
              </div>

              <div>
                <span>
                  AAR
                </span>

                <strong>
                  {
                    preview
                      .afterActionReviewCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  SITREPs
                </span>

                <strong>
                  {preview.sitrepCount}
                </strong>
              </div>

              <div>
                <span>
                  Improvements
                </span>

                <strong>
                  {preview.improvementCount}
                </strong>
              </div>

              <div>
                <span>
                  Exercises
                </span>

                <strong>
                  {preview.exerciseCount}
                </strong>
              </div>

              <div>
                <span>
                  Corrective
                </span>

                <strong>
                  {
                    preview
                      .correctiveActionCount
                  }
                </strong>
              </div>

              <div>
                <span>
                  Timeline
                </span>

                <strong>
                  {
                    preview
                      .timelineEventCount
                  }
                </strong>
              </div>
            </div>

            {verification !== null && (
              <div
                className={
                  verification.valid
                    ? "resilience-evidence-verification resilience-evidence-verification--valid"
                    : "resilience-evidence-verification resilience-evidence-verification--invalid"
                }
              >
                {verification.valid
                  ? (
                    <ShieldCheck
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
                    {verification.valid
                      ? "Manifest integrity verified"
                      : "Manifest integrity mismatch"
                    }
                  </strong>

                  <span>
                    {shortHash(
                      verification.recomputedHash,
                    )}
                  </span>
                </div>
              </div>
            )}

            <section className="resilience-evidence-history-section">
              <div className="resilience-evidence-section-heading">
                <Archive
                  size={14}
                />

                <strong>
                  Immutable package history
                </strong>

                <span>
                  {history
                    ?.packages
                    .length
                    ?? 0}
                </span>
              </div>

              {history === null
                || history.packages.length
                  === 0
                ? (
                  <div className="resilience-evidence-history-empty">
                    No evidence packages captured.
                  </div>
                )
                : (
                  <div className="resilience-evidence-history">
                    {history.packages.map(
                      (
                        evidencePackage,
                      ) => (
                        <article
                          key={
                            evidencePackage.id
                          }
                        >
                          <div>
                            <span>
                              PACKAGE
                            </span>

                            <strong>
                              #
                              {
                                evidencePackage
                                  .packageNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Certification
                            </span>

                            <strong>
                              #
                              {
                                evidencePackage
                                  .certificationNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Fingerprint
                            </span>

                            <code>
                              {shortHash(
                                evidencePackage
                                  .manifestHash,
                              )}
                            </code>
                          </div>

                          <time>
                            {formatDate(
                              evidencePackage
                                .createdAt,
                            )}
                          </time>

                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              verifyPackage(
                                evidencePackage.id,
                              )
                            }
                          >
                            <CheckCircle2
                              size={12}
                            />

                            Verify
                          </button>
                        </article>
                      ),
                    )}
                  </div>
                )
              }
            </section>

            <section className="resilience-evidence-latest">
              <span>
                CURRENT MANIFEST SUMMARY
              </span>

              <p>
                {preview.executiveSummary}
              </p>
            </section>
          </>
        )
      }
    </section>
  );
}
