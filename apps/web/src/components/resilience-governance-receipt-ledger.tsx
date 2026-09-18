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
  fetchGovernanceAssuranceReceiptLedger,
  verifyGovernanceAssuranceReceiptLedger,
} from "@/lib/api";

import type {
  GovernanceAssuranceReceiptLedgerHistoryResponse,
  GovernanceAssuranceReceiptLedgerVerificationResponse,
} from "@/types/resilience-governance-receipt-ledger";

function shortValue(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "GENESIS";
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

export function ResilienceGovernanceReceiptLedger() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceAssuranceReceiptLedgerHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceAssuranceReceiptLedgerVerificationResponse | null
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

    void fetchGovernanceAssuranceReceiptLedger(
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
              : "Unable to load receipt transparency ledger.",
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

    return fetchGovernanceAssuranceReceiptLedger()
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

  const verify = () => {
    setBusy(true);
    setError(null);

    void verifyGovernanceAssuranceReceiptLedger()
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
              : "Unable to verify receipt transparency ledger.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latestEntry =
    history
      ?.entries
      .at(0)
    ?? null;

  return (
    <section className="receipt-ledger">
      <div className="receipt-ledger-heading">
        <div>
          <span className="panel-kicker">
            RECEIPT TRANSPARENCY
          </span>

          <strong>
            Verification receipt ledger
          </strong>
        </div>

        <div className="receipt-ledger-actions">
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
                        : "Unable to refresh receipt ledger.",
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

            Verify ledger
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="receipt-ledger-error">
          {error}
        </div>
      )}

      <div className="receipt-ledger-hero">
        <div className="receipt-ledger-icon">
          <Link2
            size={24}
          />
        </div>

        <div>
          <span>
            TAMPER-EVIDENT DECISION HISTORY
          </span>

          <strong>
            Chained assurance receipts
          </strong>

          <p>
            Every stateless verification decision
            is fingerprinted and chained to the
            previous decision so later receipt or
            ledger mutation can be detected.
          </p>
        </div>

        <div className="receipt-ledger-count">
          <span>
            Anchored
          </span>

          <strong>
            {history
              ?.entries
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {latestEntry !== null && (
        <div className="receipt-ledger-latest">
          <article>
            <span>
              Latest ledger
            </span>

            <strong>
              #
              {latestEntry.ledgerNumber}
            </strong>
          </article>

          <article>
            <span>
              Receipt
            </span>

            <strong>
              #
              {latestEntry.receiptNumber}
            </strong>
          </article>

          <article>
            <span>
              Decision
            </span>

            <strong>
              {latestEntry.verificationResult}
            </strong>
          </article>

          <article>
            <span>
              Ledger hash
            </span>

            <code>
              {shortValue(
                latestEntry.ledgerHash,
              )}
            </code>
          </article>
        </div>
      )}

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "receipt-ledger-verification receipt-ledger-verification--valid"
              : "receipt-ledger-verification receipt-ledger-verification--invalid"
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
                  .verifiedEntryCount
              }
              {" / "}
              {
                verification
                  .entryCount
              }
              {" entries verified"}
            </span>
          </div>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="receipt-ledger-empty">
            Loading receipt transparency ledger…
          </div>
        )
        : history === null
          || history.entries.length
            === 0
          ? (
            <div className="receipt-ledger-empty">
              No verification receipts have been
              anchored yet.
            </div>
          )
          : (
            <div className="receipt-ledger-history">
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
                        Receipt
                      </span>

                      <strong>
                        #
                        {entry.receiptNumber}
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
                        Receipt payload
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

                    <div>
                      <span>
                        Ledger hash
                      </span>

                      <code>
                        {shortValue(
                          entry.ledgerHash,
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
