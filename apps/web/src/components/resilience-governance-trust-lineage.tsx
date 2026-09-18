"use client";

import {
  CheckCircle2,
  Clipboard,
  FileKey2,
  Fingerprint,
  PackageCheck,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createGovernanceTrustLineagePackage,
  exportGovernanceTrustLineagePackage,
  fetchGovernanceTrustLineagePackages,
  verifyGovernanceTrustLineageEnvelope,
} from "@/lib/api";

import type {
  GovernanceTrustLineagePackageHistoryResponse,
  GovernanceTrustLineageVerificationResponse,
} from "@/types/resilience-governance-trust-lineage";

function shortValue(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "—";
  }

  if (
    value.length <= 28
  ) {
    return value;
  }

  return (
    `${value.slice(0, 14)}…${value.slice(-10)}`
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

export function ResilienceGovernanceTrustLineage() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceTrustLineagePackageHistoryResponse | null
    >(null);

  const [
    envelopeJson,
    setEnvelopeJson,
  ] =
    useState("");

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceTrustLineageVerificationResponse | null
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

    void fetchGovernanceTrustLineagePackages(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setHistory(
          response,
        );

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
              : "Unable to load portable trust-lineage packages.",
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

    return fetchGovernanceTrustLineagePackages()
      .then((response) => {
        setHistory(
          response,
        );

        setError(null);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const createPackage = () => {
    setBusy(true);
    setError(null);

    void createGovernanceTrustLineagePackage()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create portable trust-lineage package.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const copyExport = (
    packageId: string,
  ) => {
    setBusy(true);
    setError(null);

    void exportGovernanceTrustLineagePackage(
      packageId,
    )
      .then(
        async (
          exported,
        ) => {
          setEnvelopeJson(
            exported,
          );

          await navigator.clipboard.writeText(
            exported,
          );
        },
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to export portable trust-lineage package.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verifyEnvelope = () => {
    if (
      envelopeJson.trim() === ""
    ) {
      setError(
        "Paste or export a portable trust-lineage envelope first.",
      );

      return;
    }

    setBusy(true);
    setError(null);

    void verifyGovernanceTrustLineageEnvelope(
      envelopeJson,
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
              : "Unable to verify portable trust lineage.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latest =
    history
      ?.packages
      .at(0)
    ?? null;

  return (
    <section className="trust-lineage-package">
      <div className="trust-lineage-package-heading">
        <div>
          <span className="panel-kicker">
            PORTABLE TRUST EVIDENCE
          </span>

          <strong>
            Trust-lineage evidence packages
          </strong>
        </div>

        <div className="trust-lineage-package-actions">
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
                        : "Unable to refresh trust-lineage packages.",
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
            disabled={busy}
            onClick={createPackage}
          >
            <PackageCheck
              size={13}
            />

            Package lineage
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="trust-lineage-package-error">
          {error}
        </div>
      )}

      <div className="trust-lineage-package-hero">
        <div className="trust-lineage-package-icon">
          <FileKey2
            size={24}
          />
        </div>

        <div>
          <span>
            SOURCE-FREE CRYPTOGRAPHIC CONTINUITY
          </span>

          <strong>
            Portable signed key lineage
          </strong>

          <p>
            Canonical evidence packages contain
            the signed transition chain and only
            the public trust anchors required to
            validate it. Verification requires no
            signing private key and no source
            transition database.
          </p>
        </div>

        <div className="trust-lineage-package-count">
          <span>
            Packages
          </span>

          <strong>
            {history
              ?.packages
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {latest !== null && (
        <div className="trust-lineage-package-latest">
          <article>
            <span>
              Package
            </span>

            <strong>
              #
              {latest.packageNumber}
            </strong>
          </article>

          <article>
            <span>
              Transitions
            </span>

            <strong>
              {latest.transitionCount}
            </strong>
          </article>

          <article>
            <span>
              Public anchors
            </span>

            <strong>
              {latest.trustAnchorCount}
            </strong>
          </article>

          <article>
            <span>
              Lineage head
            </span>

            <code>
              {shortValue(
                latest.lineageHeadHash,
              )}
            </code>
          </article>

          <article>
            <span>
              Package hash
            </span>

            <code>
              {shortValue(
                latest.packageHash,
              )}
            </code>
          </article>
        </div>
      )}

      <div className="trust-lineage-package-import">
        <div className="trust-lineage-package-import-heading">
          <div>
            <span>
              STATELESS VERIFIER
            </span>

            <strong>
              Verify portable envelope
            </strong>
          </div>

          <button
            type="button"
            disabled={
              busy
              || envelopeJson.trim() === ""
            }
            onClick={verifyEnvelope}
          >
            <ShieldCheck
              size={13}
            />

            Verify envelope
          </button>
        </div>

        <textarea
          rows={6}
          value={envelopeJson}
          onChange={(event) => {
            setEnvelopeJson(
              event.target.value,
            );
          }}
          placeholder="Paste a TS-GOVERNANCE-TRUST-LINEAGE-ENVELOPE-V1 JSON envelope here."
        />
      </div>

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "trust-lineage-package-verification trust-lineage-package-verification--valid"
              : "trust-lineage-package-verification trust-lineage-package-verification--invalid"
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
              {verification.verifiedTransitionCount}
              {" / "}
              {verification.transitionCount}
              {" signed transitions · "}
              {verification.trustAnchorCount}
              {" public anchors"}
            </span>

            <span>
              {verification.rootKeyId ?? "—"}
              {" → "}
              {verification.terminalKeyId ?? "—"}
            </span>
          </div>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="trust-lineage-package-empty">
            Loading portable trust lineage…
          </div>
        )
        : history === null
          || history.packages.length
            === 0
          ? (
            <div className="trust-lineage-package-empty">
              No portable trust-lineage package
              has been generated yet.
            </div>
          )
          : (
            <div className="trust-lineage-package-history">
              {history.packages.map(
                (item) => (
                  <article
                    key={item.id}
                  >
                    <div>
                      <span>
                        PACKAGE
                      </span>

                      <strong>
                        #
                        {item.packageNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Range
                      </span>

                      <strong>
                        #
                        {item.firstTransitionNumber}
                        {" → #"}
                        {item.lastTransitionNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Transitions
                      </span>

                      <strong>
                        {item.transitionCount}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Anchors
                      </span>

                      <strong>
                        {item.trustAnchorCount}
                      </strong>
                    </div>

                    <div>
                      <span>
                        SHA-256
                      </span>

                      <code>
                        {shortValue(
                          item.packageHash,
                        )}
                      </code>
                    </div>

                    <Fingerprint
                      size={12}
                    />

                    <time>
                      {formatDate(
                        item.createdAt,
                      )}
                    </time>

                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => {
                        copyExport(
                          item.id,
                        );
                      }}
                    >
                      <Clipboard
                        size={12}
                      />

                      Copy export
                    </button>
                  </article>
                ),
              )}
            </div>
          )
      }
    </section>
  );
}
