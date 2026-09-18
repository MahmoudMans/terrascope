"use client";

import {
  Archive,
  CheckCircle2,
  Download,
  FileCheck2,
  Fingerprint,
  ShieldX,
  Upload,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createGovernanceAssuranceVerificationReceipt,
  exportGovernanceAssuranceEnvelope,
  fetchGovernanceAssuranceBundles,
  fetchGovernanceAssuranceVerificationReceipts,
  verifyGovernanceAssuranceEnvelope,
} from "@/lib/api";

import type {
  GovernanceAssuranceBundleHistoryResponse,
} from "@/types/resilience-governance-assurance-bundles";

import type {
  GovernanceAssuranceEnvelopeVerificationResponse,
  GovernanceAssuranceVerificationReceiptHistoryResponse,
} from "@/types/resilience-governance-airgap";

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

export function ResilienceGovernanceAirgap() {
  const [
    bundles,
    setBundles,
  ] =
    useState<
      GovernanceAssuranceBundleHistoryResponse | null
    >(null);

  const [
    receipts,
    setReceipts,
  ] =
    useState<
      GovernanceAssuranceVerificationReceiptHistoryResponse | null
    >(null);

  const [
    importedEnvelope,
    setImportedEnvelope,
  ] =
    useState("");

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceAssuranceEnvelopeVerificationResponse | null
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
      fetchGovernanceAssuranceBundles(
        controller.signal,
      ),

      fetchGovernanceAssuranceVerificationReceipts(
        controller.signal,
      ),
    ])
      .then(
        ([
          bundleResponse,
          receiptResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setBundles(
            bundleResponse,
          );

          setReceipts(
            receiptResponse,
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
              : "Unable to load air-gapped assurance workspace.",
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

  const latestBundle =
    bundles
      ?.bundles
      .at(0)
    ?? null;

  const refreshReceipts = () =>
    fetchGovernanceAssuranceVerificationReceipts()
      .then((response) => {
        setReceipts(
          response,
        );
      });

  const exportLatest = () => {
    if (
      latestBundle === null
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void exportGovernanceAssuranceEnvelope(
      latestBundle.id,
    )
      .then((envelopeJson) => {
        const blob =
          new Blob(
            [
              envelopeJson,
            ],
            {
              type:
                "application/json",
            },
          );

        const url =
          URL.createObjectURL(
            blob,
          );

        const anchor =
          document.createElement(
            "a",
          );

        anchor.href =
          url;

        anchor.download =
          `terrascope-assurance-bundle-${latestBundle.bundleNumber}.json`;

        document.body.appendChild(
          anchor,
        );

        anchor.click();
        anchor.remove();

        URL.revokeObjectURL(
          url,
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to export portable assurance envelope.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verifyImported = () => {
    if (
      importedEnvelope === ""
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void verifyGovernanceAssuranceEnvelope(
      importedEnvelope,
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
              : "Unable to verify imported assurance envelope.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const recordReceipt = () => {
    if (
      importedEnvelope === ""
    ) {
      return;
    }

    setBusy(true);
    setError(null);

    void createGovernanceAssuranceVerificationReceipt(
      importedEnvelope,
    )
      .then(() =>
        refreshReceipts()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to record assurance verification receipt.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="governance-airgap">
      <div className="governance-airgap-heading">
        <div>
          <span className="panel-kicker">
            AIR-GAPPED ASSURANCE
          </span>

          <strong>
            Stateless external verification
          </strong>
        </div>

        <div className="governance-airgap-actions">
          <button
            type="button"
            disabled={
              busy
              || latestBundle === null
            }
            onClick={exportLatest}
          >
            <Download
              size={13}
            />

            Export latest
          </button>

          <button
            type="button"
            disabled={
              busy
              || importedEnvelope === ""
            }
            onClick={verifyImported}
          >
            <FileCheck2
              size={13}
            />

            Verify import
          </button>

          <button
            type="button"
            disabled={
              busy
              || importedEnvelope === ""
            }
            onClick={recordReceipt}
          >
            <Archive
              size={13}
            />

            Record receipt
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="governance-airgap-error">
          {error}
        </div>
      )}

      <div className="governance-airgap-hero">
        <div className="governance-airgap-icon">
          <Upload
            size={24}
          />
        </div>

        <div>
          <span>
            STATELESS VERIFICATION
          </span>

          <strong>
            No database source or private key required
          </strong>

          <p>
            Export a self-contained envelope
            carrying the canonical bundle,
            Ed25519 signatures, public trust
            anchors, and lifecycle evidence.
          </p>
        </div>

        <div className="governance-airgap-count">
          <span>
            Receipts
          </span>

          <strong>
            {receipts
              ?.receipts
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      <label className="governance-airgap-import">
        <span>
          Import TerraScope assurance JSON
        </span>

        <input
          type="file"
          accept="application/json,.json"
          onChange={(event) => {
            const file =
              event.target.files?.[0];

            if (
              file === undefined
            ) {
              return;
            }

            void file
              .text()
              .then((text) => {
                setImportedEnvelope(
                  text,
                );

                setVerification(
                  null,
                );

                setError(null);
              })
              .catch(() => {
                setError(
                  "Unable to read the selected assurance file.",
                );
              });
          }}
        />
      </label>

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "governance-airgap-verification governance-airgap-verification--valid"
              : "governance-airgap-verification governance-airgap-verification--invalid"
          }
        >
          {verification.valid
            ? (
              <CheckCircle2
                size={17}
              />
            )
            : (
              <ShieldX
                size={17}
              />
            )
          }

          <div>
            <strong>
              {verification.valid
                ? "PORTABLE EVIDENCE VERIFIED"
                : "VERIFICATION FAILED"
              }
            </strong>

            <p>
              {verification.message}
            </p>

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
        && receipts === null
        ? (
          <div className="governance-airgap-empty">
            Loading external verification evidence…
          </div>
        )
        : receipts === null
          || receipts.receipts.length
            === 0
          ? (
            <div className="governance-airgap-empty">
              No stateless verification receipts
              recorded.
            </div>
          )
          : (
            <section className="governance-airgap-history-section">
              <div className="governance-airgap-section-heading">
                <Fingerprint
                  size={14}
                />

                <strong>
                  Verification receipts
                </strong>
              </div>

              <div className="governance-airgap-history">
                {receipts.receipts.map(
                  (receipt) => (
                    <article
                      key={receipt.id}
                    >
                      <div>
                        <span>
                          RECEIPT
                        </span>

                        <strong>
                          #
                          {receipt.receiptNumber}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Result
                        </span>

                        <strong>
                          {receipt.verificationResult}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Envelope
                        </span>

                        <code>
                          {shortValue(
                            receipt.envelopeHash,
                          )}
                        </code>
                      </div>

                      <div>
                        <span>
                          Bundle
                        </span>

                        <code>
                          {shortValue(
                            receipt.bundleHash,
                          )}
                        </code>
                      </div>

                      <div>
                        <span>
                          Signatures
                        </span>

                        <strong>
                          {receipt.verifiedSignatureCount}
                          /
                          {receipt.signatureCount}
                        </strong>
                      </div>

                      <time>
                        {formatDate(
                          receipt.verifiedAt,
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
