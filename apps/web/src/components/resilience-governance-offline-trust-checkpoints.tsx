"use client";

import {
  CheckCircle2,
  Fingerprint,
  KeyRound,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createGovernanceOfflineTrustReceiptCheckpoint,
  fetchGovernanceOfflineTrustReceiptCheckpoints,
  verifyGovernanceOfflineTrustReceiptCheckpoints,
} from "@/lib/api";

import type {
  GovernanceOfflineTrustReceiptCheckpointHistoryResponse,
  GovernanceOfflineTrustReceiptCheckpointVerificationResponse,
} from "@/types/resilience-governance-offline-trust-checkpoints";

function shortValue(
  value: string | null,
): string {
  if (value === null) {
    return "—";
  }

  if (value.length <= 28) {
    return value;
  }

  return `${value.slice(0, 14)}…${value.slice(-10)}`;
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

export function ResilienceGovernanceOfflineTrustCheckpoints() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceOfflineTrustReceiptCheckpointHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceOfflineTrustReceiptCheckpointVerificationResponse | null
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

  const refresh = async () => {
    const response =
      await fetchGovernanceOfflineTrustReceiptCheckpoints();

    setHistory(
      response,
    );

    setError(
      null,
    );
  };

  useEffect(() => {
    const controller =
      new AbortController();

    void fetchGovernanceOfflineTrustReceiptCheckpoints(
      controller.signal,
    )
      .then((response) => {
        if (
          !controller.signal.aborted
        ) {
          setHistory(
            response,
          );
        }
      })
      .catch(
        (
          caughtError:
            unknown,
        ) => {
          if (
            !controller.signal.aborted
          ) {
            setError(
              caughtError
                instanceof Error
                ? caughtError.message
                : "Unable to load signed offline receipt checkpoints.",
            );
          }
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setLoading(
            false,
          );
        }
      });

    return () => {
      controller.abort();
    };
  }, []);

  const create = () => {
    setBusy(
      true,
    );

    setError(
      null,
    );

    void createGovernanceOfflineTrustReceiptCheckpoint()
      .then(() =>
        refresh(),
      )
      .catch(
        (
          caughtError:
            unknown,
        ) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to sign the offline receipt ledger checkpoint.",
          );
        },
      )
      .finally(() => {
        setBusy(
          false,
        );
      });
  };

  const verify = () => {
    setBusy(
      true,
    );

    setError(
      null,
    );

    void verifyGovernanceOfflineTrustReceiptCheckpoints()
      .then((response) => {
        setVerification(
          response,
        );
      })
      .catch(
        (
          caughtError:
            unknown,
        ) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to verify signed offline receipt checkpoints.",
          );
        },
      )
      .finally(() => {
        setBusy(
          false,
        );
      });
  };

  const latest =
    history
      ?.checkpoints
      .at(0)
    ?? null;

  return (
    <section className="offline-checkpoints">
      <div className="offline-checkpoints-heading">
        <div>
          <span className="panel-kicker">
            CRYPTOGRAPHIC LEDGER ANCHOR
          </span>

          <strong>
            Signed offline receipt checkpoints
          </strong>
        </div>

        <div className="offline-checkpoints-actions">
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

          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setBusy(
                true,
              );

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
                        : "Unable to refresh signed checkpoints.",
                    );
                  },
                )
                .finally(() => {
                  setBusy(
                    false,
                  );
                });
            }}
          >
            <RefreshCw
              size={13}
            />

            Refresh
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="offline-checkpoints-error">
          {error}
        </div>
      )}

      <div className="offline-checkpoints-hero">
        <div className="offline-checkpoints-icon">
          <Fingerprint
            size={24}
          />
        </div>

        <div>
          <span>
            ED25519 + SHA-256
          </span>

          <strong>
            Ledger-head authenticity
          </strong>

          <p>
            Each checkpoint signs the exact
            receipt-ledger head, entry counts,
            signing-key fingerprint, and previous
            checkpoint hash.
          </p>
        </div>

        <div className="offline-checkpoints-count">
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

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "offline-checkpoints-verification offline-checkpoints-verification--valid"
              : "offline-checkpoints-verification offline-checkpoints-verification--invalid"
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

            <code>
              {shortValue(
                verification.checkpointHeadHash,
              )}
            </code>
          </div>
        </div>
      )}

      {latest !== null && (
        <div className="offline-checkpoints-latest">
          <article>
            <span>
              Checkpoint
            </span>

            <strong>
              #
              {latest.checkpointNumber}
            </strong>
          </article>

          <article>
            <span>
              Ledger
            </span>

            <strong>
              #
              {latest.coveredLedgerNumber}
            </strong>
          </article>

          <article>
            <span>
              Key
            </span>

            <code>
              {shortValue(
                latest.keyId,
              )}
            </code>
          </article>

          <article>
            <span>
              Fingerprint
            </span>

            <code>
              {shortValue(
                latest.publicKeyFingerprint,
              )}
            </code>
          </article>

          <article>
            <span>
              Checkpoint SHA
            </span>

            <code>
              {shortValue(
                latest.checkpointHash,
              )}
            </code>
          </article>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="offline-checkpoints-empty">
            Loading signed checkpoints…
          </div>
        )
        : history === null
          || history.checkpoints.length === 0
          ? (
            <div className="offline-checkpoints-empty">
              No signed offline receipt checkpoint
              exists yet.
            </div>
          )
          : (
            <div className="offline-checkpoints-history">
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
                        Receipts
                      </span>

                      <strong>
                        {checkpoint.entryCount}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Key
                      </span>

                      <code>
                        {shortValue(
                          checkpoint.keyId,
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
