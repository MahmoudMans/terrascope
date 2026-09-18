"use client";

import {
  CheckCircle2,
  Fingerprint,
  KeyRound,
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
  createGovernanceAssuranceReceiptCheckpoint,
  fetchGovernanceAssuranceReceiptCheckpoints,
  verifyGovernanceAssuranceReceiptCheckpoints,
} from "@/lib/api";

import type {
  GovernanceAssuranceReceiptCheckpointHistoryResponse,
  GovernanceAssuranceReceiptCheckpointVerificationResponse,
} from "@/types/resilience-governance-receipt-checkpoints";

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

export function ResilienceGovernanceReceiptCheckpoints() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceAssuranceReceiptCheckpointHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceAssuranceReceiptCheckpointVerificationResponse | null
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

    void fetchGovernanceAssuranceReceiptCheckpoints(
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
              : "Unable to load receipt-ledger checkpoints.",
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

    return fetchGovernanceAssuranceReceiptCheckpoints()
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

  const create = () => {
    setBusy(true);
    setError(null);

    void createGovernanceAssuranceReceiptCheckpoint()
      .then(() =>
        refresh()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create receipt-ledger checkpoint.",
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

    void verifyGovernanceAssuranceReceiptCheckpoints()
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
              : "Unable to verify receipt-ledger checkpoints.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latest =
    history
      ?.checkpoints
      .at(0)
    ?? null;

  return (
    <section className="receipt-checkpoints">
      <div className="receipt-checkpoints-heading">
        <div>
          <span className="panel-kicker">
            SIGNED CHECKPOINTS
          </span>

          <strong>
            Receipt-ledger trust checkpoints
          </strong>
        </div>

        <div className="receipt-checkpoints-actions">
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
                        : "Unable to refresh checkpoints.",
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
            onClick={create}
          >
            <KeyRound
              size={13}
            />

            Sign head
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={verify}
          >
            <ShieldCheck
              size={13}
            />

            Verify
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="receipt-checkpoints-error">
          {error}
        </div>
      )}

      <div className="receipt-checkpoints-hero">
        <div className="receipt-checkpoints-icon">
          <Fingerprint
            size={24}
          />
        </div>

        <div>
          <span>
            ED25519 LEDGER HEAD ATTESTATION
          </span>

          <strong>
            Signed transparency checkpoints
          </strong>

          <p>
            Each checkpoint binds the current
            receipt-ledger head, receipt counts,
            signing-key identity, and the previous
            checkpoint into an independently
            verifiable Ed25519 attestation.
          </p>
        </div>

        <div className="receipt-checkpoints-count">
          <span>
            Checkpoints
          </span>

          <strong>
            {history
              ?.checkpoints
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {latest !== null && (
        <div className="receipt-checkpoints-latest">
          <article>
            <span>
              Latest
            </span>

            <strong>
              #
              {latest.checkpointNumber}
            </strong>
          </article>

          <article>
            <span>
              Ledger head
            </span>

            <strong>
              #
              {latest.coveredLedgerNumber}
            </strong>
          </article>

          <article>
            <span>
              Valid
            </span>

            <strong>
              {latest.validReceiptCount}
            </strong>
          </article>

          <article>
            <span>
              Invalid
            </span>

            <strong>
              {latest.invalidReceiptCount}
            </strong>
          </article>

          <article>
            <span>
              Signing key
            </span>

            <code>
              {shortValue(
                latest.keyId,
              )}
            </code>
          </article>

          <article>
            <span>
              Checkpoint hash
            </span>

            <code>
              {shortValue(
                latest.checkpointHash,
              )}
            </code>
          </article>
        </div>
      )}

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "receipt-checkpoints-verification receipt-checkpoints-verification--valid"
              : "receipt-checkpoints-verification receipt-checkpoints-verification--invalid"
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
              {verification.verifiedCheckpointCount}
              {" / "}
              {verification.checkpointCount}
              {" checkpoints verified"}
            </span>
          </div>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="receipt-checkpoints-empty">
            Loading signed checkpoints…
          </div>
        )
        : history === null
          || history.checkpoints.length
            === 0
          ? (
            <div className="receipt-checkpoints-empty">
              No signed receipt-ledger checkpoint
              has been issued yet.
            </div>
          )
          : (
            <div className="receipt-checkpoints-history">
              {history.checkpoints.map(
                (checkpoint) => (
                  <article
                    key={checkpoint.id}
                  >
                    <div>
                      <span>
                        CHECKPOINT
                      </span>

                      <strong>
                        #
                        {checkpoint.checkpointNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Ledger
                      </span>

                      <strong>
                        #
                        {checkpoint.coveredLedgerNumber}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Key
                      </span>

                      <code>
                        {checkpoint.keyId}
                      </code>
                    </div>

                    <div>
                      <span>
                        Previous
                      </span>

                      <code>
                        {shortValue(
                          checkpoint.previousCheckpointHash,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Hash
                      </span>

                      <code>
                        {shortValue(
                          checkpoint.checkpointHash,
                        )}
                      </code>
                    </div>

                    <Link2
                      size={12}
                    />

                    <time>
                      {formatDate(
                        checkpoint.createdAt,
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
