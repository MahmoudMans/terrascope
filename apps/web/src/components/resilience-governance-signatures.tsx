"use client";

import {
  CheckCircle2,
  Fingerprint,
  KeyRound,
  PenLine,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  fetchGovernanceClosureCertificates,
  fetchGovernanceClosureSignatures,
  fetchGovernanceSigningReadiness,
  signGovernanceClosureCertificate,
  verifyGovernanceClosureSignatures,
} from "@/lib/api";

import type {
  GovernanceClosureCertificateHistoryResponse,
} from "@/types/resilience-governance-closure";

import type {
  GovernanceClosureSignatureHistoryResponse,
  GovernanceSignatureVerificationResponse,
  GovernanceSigningReadinessResponse,
} from "@/types/resilience-governance-signatures";

function shortValue(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "—";
  }

  if (
    value.length <= 26
  ) {
    return value;
  }

  return (
    `${value.slice(0, 13)}…${value.slice(-9)}`
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

export function ResilienceGovernanceSignatures() {
  const [
    readiness,
    setReadiness,
  ] =
    useState<
      GovernanceSigningReadinessResponse | null
    >(null);

  const [
    certificates,
    setCertificates,
  ] =
    useState<
      GovernanceClosureCertificateHistoryResponse | null
    >(null);

  const [
    signatures,
    setSignatures,
  ] =
    useState<
      GovernanceClosureSignatureHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceSignatureVerificationResponse | null
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
      fetchGovernanceSigningReadiness(
        controller.signal,
      ),

      fetchGovernanceClosureCertificates(
        controller.signal,
      ),

      fetchGovernanceClosureSignatures(
        controller.signal,
      ),
    ])
      .then(
        ([
          readinessResponse,
          certificateResponse,
          signatureResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setReadiness(
            readinessResponse,
          );

          setCertificates(
            certificateResponse,
          );

          setSignatures(
            signatureResponse,
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
              : "Unable to load governance signatures.",
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

  const unsignedCertificate =
    useMemo(() => {
      const signedIds =
        new Set(
          signatures
            ?.signatures
            .map(
              (signature) =>
                signature.certificateId,
            )
          ?? [],
        );

      return (
        certificates
          ?.certificates
          .find(
            (certificate) =>
              !signedIds.has(
                certificate.id,
              ),
          )
        ?? null
      );
    }, [
      certificates,
      signatures,
    ]);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchGovernanceSigningReadiness(),
      fetchGovernanceClosureCertificates(),
      fetchGovernanceClosureSignatures(),
    ])
      .then(
        ([
          readinessResponse,
          certificateResponse,
          signatureResponse,
        ]) => {
          setReadiness(
            readinessResponse,
          );

          setCertificates(
            certificateResponse,
          );

          setSignatures(
            signatureResponse,
          );

          setError(null);
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const sign = () => {
    if (
      unsignedCertificate === null
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void signGovernanceClosureCertificate(
      unsignedCertificate.id,
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
              : "Unable to sign governance closure certificate.",
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

    void verifyGovernanceClosureSignatures()
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
              : "Unable to verify governance signatures.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="governance-signatures">
      <div className="governance-signatures-heading">
        <div>
          <span className="panel-kicker">
            CRYPTOGRAPHIC TRUST
          </span>

          <strong>
            Ed25519 governance signatures
          </strong>
        </div>

        <div className="governance-signatures-actions">
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
                        : "Unable to refresh signing state.",
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
              || unsignedCertificate === null
            }
            onClick={sign}
          >
            <PenLine
              size={13}
            />

            Sign next
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={verify}
          >
            <ShieldCheck
              size={13}
            />

            Verify signatures
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="governance-signatures-error">
          {error}
        </div>
      )}

      {loading
        && readiness === null
        ? (
          <div className="governance-signatures-empty">
            Loading local governance trust anchor…
          </div>
        )
        : readiness !== null && (
          <>
            <div className="governance-signatures-hero">
              <div
                className={
                  readiness.ready
                    ? "governance-signatures-key governance-signatures-key--ready"
                    : "governance-signatures-key governance-signatures-key--blocked"
                }
              >
                <KeyRound
                  size={24}
                />
              </div>

              <div>
                <span>
                  LOCAL SIGNING KEY
                </span>

                <strong>
                  {readiness.ready
                    ? "ED25519 READY"
                    : "NOT CONFIGURED"
                  }
                </strong>

                <p>
                  {readiness.message}
                </p>
              </div>

              <div className="governance-signatures-anchor">
                <span>
                  Trust anchor
                </span>

                <strong>
                  {readiness.trustAnchorRegistered
                    ? "REGISTERED"
                    : "PENDING"
                  }
                </strong>
              </div>
            </div>

            <div className="governance-signatures-identity">
              <article>
                <span>
                  Key ID
                </span>

                <code>
                  {readiness.keyId ?? "—"}
                </code>
              </article>

              <article>
                <span>
                  Algorithm
                </span>

                <strong>
                  {readiness.algorithm}
                </strong>
              </article>

              <article>
                <span>
                  Public-key fingerprint
                </span>

                <code>
                  {shortValue(
                    readiness.publicKeyFingerprint,
                  )}
                </code>
              </article>

              <article>
                <span>
                  Signed certificates
                </span>

                <strong>
                  {signatures
                    ?.signatures
                    .length
                    ?? 0}
                </strong>
              </article>
            </div>

            {unsignedCertificate !== null && (
              <div className="governance-signatures-next">
                <Fingerprint
                  size={14}
                />

                <div>
                  <span>
                    NEXT UNSIGNED CERTIFICATE
                  </span>

                  <strong>
                    Certificate #
                    {
                      unsignedCertificate
                        .certificateNumber
                    }
                  </strong>

                  <code>
                    {shortValue(
                      unsignedCertificate
                        .certificateHash,
                    )}
                  </code>
                </div>
              </div>
            )}

            {verification !== null && (
              <div
                className={
                  verification.valid
                    ? "governance-signatures-verification governance-signatures-verification--valid"
                    : "governance-signatures-verification governance-signatures-verification--invalid"
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
                        .verifiedSignatureCount
                    }
                    {" / "}
                    {
                      verification
                        .signatureCount
                    }
                    {" verified"}
                  </span>
                </div>
              </div>
            )}

            <section className="governance-signatures-history-section">
              <div className="governance-signatures-section-heading">
                <Fingerprint
                  size={14}
                />

                <strong>
                  Detached signature evidence
                </strong>
              </div>

              {signatures === null
                || signatures.signatures.length
                  === 0
                ? (
                  <div className="governance-signatures-history-empty">
                    No Ed25519 governance signatures issued.
                  </div>
                )
                : (
                  <div className="governance-signatures-history">
                    {signatures.signatures.map(
                      (signature) => (
                        <article
                          key={
                            signature.id
                          }
                        >
                          <div>
                            <span>
                              CERTIFICATE
                            </span>

                            <strong>
                              #
                              {
                                signature
                                  .certificateNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Key ID
                            </span>

                            <code>
                              {signature.keyId}
                            </code>
                          </div>

                          <div>
                            <span>
                              Fingerprint
                            </span>

                            <code>
                              {shortValue(
                                signature
                                  .publicKeyFingerprint,
                              )}
                            </code>
                          </div>

                          <div>
                            <span>
                              Certificate hash
                            </span>

                            <code>
                              {shortValue(
                                signature
                                  .certificateHash,
                              )}
                            </code>
                          </div>

                          <div>
                            <span>
                              Signature
                            </span>

                            <code>
                              {shortValue(
                                signature
                                  .signatureBase64,
                              )}
                            </code>
                          </div>

                          <time>
                            {formatDate(
                              signature.createdAt,
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
