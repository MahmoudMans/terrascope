"use client";

import {
  CheckCircle2,
  Fingerprint,
  Link2,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createResilienceAuditReceipt,
  fetchResilienceAuditLedgerHistory,
  fetchResilienceAuditLedgerPreview,
  verifyResilienceAuditLedger,
} from "@/lib/api";

import type {
  ResilienceAuditLedgerHistoryResponse,
  ResilienceAuditLedgerPreview,
  ResilienceAuditLedgerVerificationResponse,
} from "@/types/resilience-audit-ledger";

type IncidentResilienceAuditLedgerProps = {
  incidentId: string;
  onTimelineChanged: () => void;
};

function shortHash(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "GENESIS";
  }

  if (
    value.length <= 22
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

export function IncidentResilienceAuditLedger({
  incidentId,
  onTimelineChanged,
}: IncidentResilienceAuditLedgerProps) {
  const [
    preview,
    setPreview,
  ] =
    useState<
      ResilienceAuditLedgerPreview | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      ResilienceAuditLedgerHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      ResilienceAuditLedgerVerificationResponse | null
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
      fetchResilienceAuditLedgerPreview(
        incidentId,
        controller.signal,
      ),

      fetchResilienceAuditLedgerHistory(
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
              : "Unable to load audit ledger.",
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
      fetchResilienceAuditLedgerPreview(
        incidentId,
      ),

      fetchResilienceAuditLedgerHistory(
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

  const createReceipt = () => {
    setBusy(true);
    setError(null);

    void createResilienceAuditReceipt(
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
              : "Unable to create audit receipt.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const verifyLedger = () => {
    setBusy(true);
    setError(null);

    void verifyResilienceAuditLedger(
      incidentId,
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
              : "Unable to verify audit ledger.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="audit-ledger">
      <div className="audit-ledger-heading">
        <div>
          <span className="panel-kicker">
            TAMPER-EVIDENT ASSURANCE
          </span>

          <strong>
            Evidence audit ledger
          </strong>
        </div>

        <div className="audit-ledger-actions">
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
                        : "Unable to refresh audit ledger.",
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
              || preview.latestPackageReceipted
            }
            onClick={createReceipt}
          >
            <Link2
              size={13}
            />

            Chain package
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={verifyLedger}
          >
            <ShieldCheck
              size={13}
            />

            Verify ledger
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="audit-ledger-error">
          {error}
        </div>
      )}

      {loading
        && preview === null
        ? (
          <div className="audit-ledger-empty">
            Building audit-ledger preview…
          </div>
        )
        : preview !== null && (
          <>
            <div className="audit-ledger-hero">
              <div>
                <Link2
                  size={27}
                />
              </div>

              <div>
                <span>
                  NEXT LEDGER RECEIPT
                </span>

                <strong>
                  Receipt #
                  {preview.nextReceiptNumber}
                </strong>

                <p>
                  Evidence package #
                  {preview.evidencePackageNumber}
                  {" · "}
                  {
                    preview
                      .latestPackageReceipted
                      ? "already chained"
                      : "ready to chain"
                  }
                </p>
              </div>

              <div className="audit-ledger-version">
                <span>
                  Ledger
                </span>

                <strong>
                  V1
                </strong>
              </div>
            </div>

            <div className="audit-ledger-hash-grid">
              <article>
                <span>
                  Evidence fingerprint
                </span>

                <code>
                  {shortHash(
                    preview.packageManifestHash,
                  )}
                </code>
              </article>

              <article>
                <span>
                  Previous receipt
                </span>

                <code>
                  {shortHash(
                    preview.previousReceiptHash,
                  )}
                </code>
              </article>

              <article>
                <span>
                  Next ledger hash
                </span>

                <code>
                  {shortHash(
                    preview.ledgerHash,
                  )}
                </code>
              </article>
            </div>

            {verification !== null && (
              <div
                className={
                  verification.valid
                    ? "audit-ledger-verification audit-ledger-verification--valid"
                    : "audit-ledger-verification audit-ledger-verification--invalid"
                }
              >
                {verification.valid
                  ? (
                    <ShieldCheck
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
                    {verification.message}
                  </strong>

                  <span>
                    {verification.verifiedReceiptCount}
                    {" / "}
                    {verification.receiptCount}
                    {" receipts verified"}
                  </span>
                </div>
              </div>
            )}

            <section className="audit-ledger-history-section">
              <div className="audit-ledger-section-heading">
                <Fingerprint
                  size={14}
                />

                <strong>
                  Immutable receipt chain
                </strong>

                <span>
                  {history
                    ?.receipts
                    .length
                    ?? 0}
                </span>
              </div>

              {history === null
                || history.receipts.length
                  === 0
                ? (
                  <div className="audit-ledger-history-empty">
                    No evidence packages have been
                    chained into the audit ledger.
                  </div>
                )
                : (
                  <div className="audit-ledger-history">
                    {history.receipts.map(
                      (receipt) => (
                        <article
                          key={receipt.id}
                        >
                          <div className="audit-ledger-receipt-number">
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
                              Evidence package
                            </span>

                            <strong>
                              #
                              {
                                receipt
                                  .evidencePackageNumber
                              }
                            </strong>
                          </div>

                          <div>
                            <span>
                              Previous
                            </span>

                            <code>
                              {shortHash(
                                receipt
                                  .previousReceiptHash,
                              )}
                            </code>
                          </div>

                          <div>
                            <span>
                              Ledger hash
                            </span>

                            <code>
                              {shortHash(
                                receipt.ledgerHash,
                              )}
                            </code>
                          </div>

                          <time>
                            {formatDate(
                              receipt.createdAt,
                            )}
                          </time>

                          <CheckCircle2
                            size={13}
                          />
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
