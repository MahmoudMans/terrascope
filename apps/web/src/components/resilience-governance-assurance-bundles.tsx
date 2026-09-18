"use client";

import {
  Archive,
  CheckCircle2,
  FileKey2,
  Fingerprint,
  PackageCheck,
  RefreshCw,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  createGovernanceAssuranceBundle,
  fetchGovernanceAssuranceBundles,
  fetchGovernanceClosureCertificates,
  fetchGovernanceClosureSignatures,
  verifyGovernanceAssuranceBundle,
} from "@/lib/api";

import type {
  GovernanceClosureCertificateHistoryResponse,
} from "@/types/resilience-governance-closure";

import type {
  GovernanceClosureSignatureHistoryResponse,
} from "@/types/resilience-governance-signatures";

import type {
  GovernanceAssuranceBundleHistoryResponse,
  GovernanceAssuranceBundleVerificationResponse,
} from "@/types/resilience-governance-assurance-bundles";

function shortValue(
  value: string,
): string {
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

export function ResilienceGovernanceAssuranceBundles() {
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
    bundles,
    setBundles,
  ] =
    useState<
      GovernanceAssuranceBundleHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceAssuranceBundleVerificationResponse | null
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
      fetchGovernanceClosureCertificates(
        controller.signal,
      ),

      fetchGovernanceClosureSignatures(
        controller.signal,
      ),

      fetchGovernanceAssuranceBundles(
        controller.signal,
      ),
    ])
      .then(
        ([
          certificateResponse,
          signatureResponse,
          bundleResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setCertificates(
            certificateResponse,
          );

          setSignatures(
            signatureResponse,
          );

          setBundles(
            bundleResponse,
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
              : "Unable to load portable assurance bundles.",
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

  const latestSignedCertificate =
    useMemo(() => {
      const signedCertificateIds =
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
              signedCertificateIds.has(
                certificate.id,
              ),
          )
        ?? null
      );
    }, [
      certificates,
      signatures,
    ]);

  const latestBundle =
    bundles
      ?.bundles
      .at(0)
    ?? null;

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchGovernanceClosureCertificates(),
      fetchGovernanceClosureSignatures(),
      fetchGovernanceAssuranceBundles(),
    ])
      .then(
        ([
          certificateResponse,
          signatureResponse,
          bundleResponse,
        ]) => {
          setCertificates(
            certificateResponse,
          );

          setSignatures(
            signatureResponse,
          );

          setBundles(
            bundleResponse,
          );

          setError(null);
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const createBundle = () => {
    if (
      latestSignedCertificate === null
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void createGovernanceAssuranceBundle(
      latestSignedCertificate.id,
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
              : "Unable to create portable assurance bundle.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verifyLatest = () => {
    if (
      latestBundle === null
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void verifyGovernanceAssuranceBundle(
      latestBundle.id,
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
              : "Unable to verify portable assurance bundle.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="assurance-bundles">
      <div className="assurance-bundles-heading">
        <div>
          <span className="panel-kicker">
            PORTABLE ASSURANCE
          </span>

          <strong>
            Governance assurance bundles
          </strong>
        </div>

        <div className="assurance-bundles-actions">
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
                        : "Unable to refresh assurance bundles.",
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
              || latestSignedCertificate === null
            }
            onClick={createBundle}
          >
            <FileKey2
              size={13}
            />

            Create bundle
          </button>

          <button
            type="button"
            disabled={
              busy
              || latestBundle === null
            }
            onClick={verifyLatest}
          >
            <PackageCheck
              size={13}
            />

            Verify latest
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="assurance-bundles-error">
          {error}
        </div>
      )}

      <div className="assurance-bundles-hero">
        <div className="assurance-bundles-icon">
          <FileKey2
            size={24}
          />
        </div>

        <div>
          <span>
            INDEPENDENT VERIFICATION
          </span>

          <strong>
            Portable closure evidence
          </strong>

          <p>
            Bundle the closure certificate,
            detached Ed25519 signatures,
            public trust anchors, rotation state,
            and lifecycle evidence into one
            deterministic SHA-256 fingerprinted
            payload.
          </p>
        </div>

        <div className="assurance-bundles-count">
          <span>
            Bundles
          </span>

          <strong>
            {bundles
              ?.bundles
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {latestBundle !== null && (
        <div className="assurance-bundles-latest">
          <article>
            <span>
              Latest bundle
            </span>

            <strong>
              #
              {latestBundle.bundleNumber}
            </strong>
          </article>

          <article>
            <span>
              Certificate
            </span>

            <strong>
              #
              {latestBundle.certificateNumber}
            </strong>
          </article>

          <article>
            <span>
              Signatures
            </span>

            <strong>
              {latestBundle.signatureCount}
            </strong>
          </article>

          <article>
            <span>
              Trust anchors
            </span>

            <strong>
              {latestBundle.trustAnchorCount}
            </strong>
          </article>

          <article>
            <span>
              Retired anchors
            </span>

            <strong>
              {latestBundle.retiredAnchorCount}
            </strong>
          </article>

          <article>
            <span>
              Lifecycle events
            </span>

            <strong>
              {latestBundle.lifecycleEventCount}
            </strong>
          </article>
        </div>
      )}

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "assurance-bundles-verification assurance-bundles-verification--valid"
              : "assurance-bundles-verification assurance-bundles-verification--invalid"
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
              Hash:
              {" "}
              {verification.hashValid
                ? "valid"
                : "invalid"
              }
              {" · "}
              Canonical:
              {" "}
              {verification.canonicalPayloadValid
                ? "yes"
                : "no"
              }
              {" · "}
              Signatures:
              {" "}
              {verification.verifiedSignatureCount}
              /
              {verification.signatureCount}
            </span>
          </div>
        </div>
      )}

      {loading
        && bundles === null
        ? (
          <div className="assurance-bundles-empty">
            Loading portable assurance evidence…
          </div>
        )
        : bundles === null
          || bundles.bundles.length
            === 0
          ? (
            <div className="assurance-bundles-empty">
              No portable governance assurance
              bundle captured yet.
            </div>
          )
          : (
            <section className="assurance-bundles-history-section">
              <div className="assurance-bundles-section-heading">
                <Archive
                  size={14}
                />

                <strong>
                  Portable bundle history
                </strong>
              </div>

              <div className="assurance-bundles-history">
                {bundles.bundles.map(
                  (bundle) => (
                    <article
                      key={bundle.id}
                    >
                      <div>
                        <span>
                          BUNDLE
                        </span>

                        <strong>
                          #
                          {bundle.bundleNumber}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Certificate
                        </span>

                        <strong>
                          #
                          {bundle.certificateNumber}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Bundle hash
                        </span>

                        <code>
                          {shortValue(
                            bundle.bundleHash,
                          )}
                        </code>
                      </div>

                      <div>
                        <span>
                          Signatures
                        </span>

                        <strong>
                          {bundle.signatureCount}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Trust anchors
                        </span>

                        <strong>
                          {bundle.trustAnchorCount}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Status
                        </span>

                        <strong>
                          {bundle.portableVerificationValid
                            ? "VERIFIED"
                            : "INVALID"
                          }
                        </strong>
                      </div>

                      <Fingerprint
                        size={12}
                      />

                      <time>
                        {formatDate(
                          bundle.createdAt,
                        )}
                      </time>
                    </article>
                  ),
                )}
              </div>
            </section>
          )
      }
    </section>
  );
}
