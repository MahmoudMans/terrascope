"use client";

import {
  Archive,
  Fingerprint,
  PackageCheck,
  RefreshCw,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createGovernanceOfflineTrustCheckpointBundle,
  fetchGovernanceOfflineTrustCheckpointBundles,
} from "@/lib/api";

import type {
  GovernanceOfflineTrustCheckpointBundleHistoryResponse,
} from "@/types/resilience-governance-offline-trust-bundles";

function shortValue(
  value: string,
): string {
  if (
    value.length <= 28
  ) {
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

export function ResilienceGovernanceOfflineTrustBundles() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceOfflineTrustCheckpointBundleHistoryResponse | null
    >(null);

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
      await fetchGovernanceOfflineTrustCheckpointBundles();

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

    void fetchGovernanceOfflineTrustCheckpointBundles(
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
                : "Unable to load portable checkpoint bundles.",
            );
          }
        },
      );

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

    void createGovernanceOfflineTrustCheckpointBundle()
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
              : "Unable to create a portable checkpoint evidence bundle.",
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
      ?.bundles
      .at(0)
    ?? null;

  return (
    <section className="offline-bundles">
      <div className="offline-bundles-heading">
        <div>
          <span className="panel-kicker">
            PORTABLE CHECKPOINT EVIDENCE
          </span>

          <strong>
            Source-independent checkpoint bundles
          </strong>
        </div>

        <div className="offline-bundles-actions">
          <button
            type="button"
            disabled={busy}
            onClick={create}
          >
            <Archive
              size={13}
            />

            Build bundle
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
                        : "Unable to refresh checkpoint bundles.",
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
        <div className="offline-bundles-error">
          {error}
        </div>
      )}

      <div className="offline-bundles-hero">
        <div className="offline-bundles-icon">
          <PackageCheck
            size={24}
          />
        </div>

        <div>
          <span>
            PORTABLE + PINNED
          </span>

          <strong>
            Take signed checkpoint evidence outside the platform
          </strong>

          <p>
            The bundle contains the signed checkpoint,
            its complete covered receipt-ledger prefix,
            and the signing public key. Verification
            is bound to an independently supplied
            signing-key SHA-256 fingerprint.
          </p>
        </div>

        <div className="offline-bundles-count">
          <span>
            Bundles
          </span>

          <strong>
            {history
              ?.bundles
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      {latest !== null && (
        <div className="offline-bundles-latest">
          <article>
            <span>
              Bundle
            </span>

            <strong>
              #
              {latest.bundleNumber}
            </strong>
          </article>

          <article>
            <span>
              Checkpoint
            </span>

            <strong>
              #
              {latest.sourceCheckpointNumber}
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
              Key pin
            </span>

            <code>
              {shortValue(
                latest.signingPublicKeyFingerprint,
              )}
            </code>
          </article>

          <article>
            <span>
              Bundle SHA
            </span>

            <code>
              {shortValue(
                latest.bundleHash,
              )}
            </code>
          </article>
        </div>
      )}

      {history === null
        || history.bundles.length === 0
        ? (
          <div className="offline-bundles-empty">
            No portable checkpoint bundle exists yet.
          </div>
        )
        : (
          <div className="offline-bundles-history">
            {history.bundles.map(
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
                      Checkpoint
                    </span>

                    <strong>
                      #
                      {bundle.sourceCheckpointNumber}
                    </strong>
                  </div>

                  <div>
                    <span>
                      Key
                    </span>

                    <code>
                      {shortValue(
                        bundle.signingKeyId,
                      )}
                    </code>
                  </div>

                  <Fingerprint
                    size={12}
                  />

                  <code>
                    {shortValue(
                      bundle.bundleHash,
                    )}
                  </code>

                  <time>
                    {formatDate(
                      bundle.createdAt,
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
