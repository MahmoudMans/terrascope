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
  fetchGovernanceOfflineTrustReceiptLedger,
  verifyGovernanceOfflineTrustReceiptLedger,
} from "@/lib/api";

import type {
  GovernanceOfflineTrustReceiptLedgerHistoryResponse,
  GovernanceOfflineTrustReceiptLedgerVerificationResponse,
} from "@/types/resilience-governance-offline-trust-ledger";

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

export function ResilienceGovernanceOfflineTrustLedger() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceOfflineTrustReceiptLedgerHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceOfflineTrustReceiptLedgerVerificationResponse | null
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

  const refresh = () => {
    setLoading(true);

    return fetchGovernanceOfflineTrustReceiptLedger()
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

  useEffect(() => {
    const controller =
      new AbortController();

    void fetchGovernanceOfflineTrustReceiptLedger(
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
              : "Unable to load offline receipt transparency ledger.",
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

  const verify = () => {
    setBusy(true);
    setError(null);

    void verifyGovernanceOfflineTrustReceiptLedger()
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
              : "Unable to verify offline receipt transparency ledger.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latest =
    history
      ?.entries
      .at(0)
    ?? null;

  return (
    <section className="offline-receipt-ledger">
      <div className="offline-receipt-ledger-heading">
        <div>
          <span className="panel-kicker">
            TAMPER-EVIDENT IMPORT HISTORY
          </span>

          <strong>
            Offline receipt transparency ledger
          </strong>
        </div>

        <div className="offline-receipt-ledger-actions">
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
                        : "Unable to refresh offline receipt ledger.",
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
        <div className="offline-receipt-ledger-error">
          {error}
        </div>
      )}

      <div className="offline-receipt-ledger-hero">
        <div className="offline-receipt-ledger-icon">
          <Link2
            size={24}
          />
        </div>

        <div>
          <span>
            SHA-256 APPEND-ONLY CHAIN
          </span>

          <strong>
            Imported receipts become tamper-evident
          </strong>

          <p>
            Each standalone verifier receipt is
            hashed again from its persisted row,
            linked to the preceding ledger hash,
            and verified against current database
            state.
          </p>
        </div>

        <div className="offline-receipt-ledger-count">
          <span>
            Entries
          </span>

          <strong>
            {history
              ?.entries
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "offline-receipt-ledger-verification offline-receipt-ledger-verification--valid"
              : "offline-receipt-ledger-verification offline-receipt-ledger-verification--invalid"
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
              {verification.verifiedEntryCount}
              {" / "}
              {verification.ledgerEntryCount}
              {" entries verified"}
            </span>

            <code>
              {shortValue(
                verification.headLedgerHash,
              )}
            </code>
          </div>
        </div>
      )}

      {latest !== null && (
        <div className="offline-receipt-ledger-latest">
          <article>
            <span>
              Ledger
            </span>

            <strong>
              #
              {latest.ledgerNumber}
            </strong>
          </article>

          <article>
            <span>
              Import
            </span>

            <strong>
              #
              {latest.receiptImportNumber}
            </strong>
          </article>

          <article>
            <span>
              Result
            </span>

            <strong>
              {latest.verificationResult}
            </strong>
          </article>

          <article>
            <span>
              Receipt SHA
            </span>

            <code>
              {shortValue(
                latest.receiptHash,
              )}
            </code>
          </article>

          <article>
            <span>
              Ledger SHA
            </span>

            <code>
              {shortValue(
                latest.ledgerHash,
              )}
            </code>
          </article>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="offline-receipt-ledger-empty">
            Loading offline receipt ledger…
          </div>
        )
        : history === null
          || history.entries.length
            === 0
          ? (
            <div className="offline-receipt-ledger-empty">
              No offline verification receipts
              have been anchored yet.
            </div>
          )
          : (
            <div className="offline-receipt-ledger-history">
              {history.entries.map(
                (entry) => (
                  <article
                    key={entry.id}
                  >
                    <div>
                      <span>
                        LEDGER
                      </span>

                      <strong>
                        #
                        {entry.ledgerNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Import
                      </span>

                      <strong>
                        #
                        {entry.receiptImportNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Result
                      </span>

                      <strong>
                        {entry.verificationResult}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Payload SHA
                      </span>

                      <code>
                        {shortValue(
                          entry.receiptPayloadHash,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Previous
                      </span>

                      <code>
                        {shortValue(
                          entry.previousLedgerHash,
                        )}
                      </code>
                    </div>

                    <Fingerprint
                      size={12}
                    />

                    <time>
                      {formatDate(
                        entry.createdAt,
                      )}
                    </time>
                  </article>
                ),
              )}
            </div>
          )
      }
    </section>
  );
}
