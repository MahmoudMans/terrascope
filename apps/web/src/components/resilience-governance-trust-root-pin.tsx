"use client";

import {
  CheckCircle2,
  FileKey2,
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
  createGovernanceTrustLineagePinReceipt,
  fetchGovernanceTrustLineagePinReceipts,
  verifyGovernanceTrustLineagePinned,
} from "@/lib/api";

import type {
  GovernanceTrustLineagePinReceiptHistoryResponse,
  GovernanceTrustLineagePinnedVerificationResponse,
} from "@/types/resilience-governance-trust-root-pin";

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

export function ResilienceGovernanceTrustRootPin() {
  const [
    envelopeJson,
    setEnvelopeJson,
  ] =
    useState("");

  const [
    trustedRootFingerprint,
    setTrustedRootFingerprint,
  ] =
    useState("");

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceTrustLineagePinnedVerificationResponse | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceTrustLineagePinReceiptHistoryResponse | null
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

    void fetchGovernanceTrustLineagePinReceipts(
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
              : "Unable to load pinned trust-root verification history.",
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

    return fetchGovernanceTrustLineagePinReceipts()
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

  const request = () => ({
    envelopeJson:
      envelopeJson.trim(),

    trustedRootFingerprint:
      trustedRootFingerprint
        .trim()
        .toLowerCase(),
  });

  const verify = () => {
    if (
      envelopeJson.trim() === ""

      || trustedRootFingerprint.trim() === ""
    ) {
      setError(
        "Portable lineage envelope and externally trusted root fingerprint are required.",
      );

      return;
    }

    setBusy(true);
    setError(null);

    void verifyGovernanceTrustLineagePinned(
      request(),
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
              : "Unable to verify externally pinned trust lineage.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const record = () => {
    if (
      envelopeJson.trim() === ""

      || trustedRootFingerprint.trim() === ""
    ) {
      setError(
        "Portable lineage envelope and externally trusted root fingerprint are required.",
      );

      return;
    }

    setBusy(true);
    setError(null);

    void createGovernanceTrustLineagePinReceipt(
      request(),
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
              : "Unable to record pinned trust-root verification.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latest =
    history
      ?.receipts
      .at(0)
    ?? null;

  return (
    <section className="trust-root-pin">
      <div className="trust-root-pin-heading">
        <div>
          <span className="panel-kicker">
            EXTERNAL ROOT OF TRUST
          </span>

          <strong>
            Pinned trust-root verification
          </strong>
        </div>

        <div className="trust-root-pin-actions">
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
                        : "Unable to refresh pinned verification history.",
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
        </div>
      </div>

      {error !== null && (
        <div className="trust-root-pin-error">
          {error}
        </div>
      )}

      <div className="trust-root-pin-hero">
        <div className="trust-root-pin-icon">
          <KeyRound
            size={24}
          />
        </div>

        <div>
          <span>
            OUT-OF-BAND ROOT PINNING
          </span>

          <strong>
            Separate trusted root from portable evidence
          </strong>

          <p>
            A lineage package is accepted only
            when its cryptographically verified
            root fingerprint matches a SHA-256
            fingerprint supplied independently
            from the package itself.
          </p>
        </div>

        <div className="trust-root-pin-count">
          <span>
            Receipts
          </span>

          <strong>
            {history
              ?.receipts
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      <div className="trust-root-pin-inputs">
        <label>
          <span>
            Externally trusted root fingerprint
          </span>

          <input
            value={trustedRootFingerprint}
            onChange={(event) => {
              setTrustedRootFingerprint(
                event.target.value,
              );
            }}
            placeholder="64-character SHA-256 fingerprint"
          />
        </label>

        <label>
          <span>
            Portable trust-lineage envelope
          </span>

          <textarea
            rows={7}
            value={envelopeJson}
            onChange={(event) => {
              setEnvelopeJson(
                event.target.value,
              );
            }}
            placeholder="Paste a TS-GOVERNANCE-TRUST-LINEAGE-ENVELOPE-V1 JSON envelope."
          />
        </label>

        <div className="trust-root-pin-input-actions">
          <button
            type="button"
            disabled={busy}
            onClick={verify}
          >
            <ShieldCheck
              size={13}
            />

            Verify pinned root
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={record}
          >
            <FileKey2
              size={13}
            />

            Record receipt
          </button>
        </div>
      </div>

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "trust-root-pin-verification trust-root-pin-verification--valid"
              : "trust-root-pin-verification trust-root-pin-verification--invalid"
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
              {"Package hash "}
              {verification.packageHashValid
                ? "✓"
                : "✕"}

              {" · Canonical "}
              {verification.canonicalPayloadValid
                ? "✓"
                : "✕"}

              {" · Lineage "}
              {verification.lineageValid
                ? "✓"
                : "✕"}

              {" · Root pin "}
              {verification.rootPinValid
                ? "✓"
                : "✕"}
            </span>

            <span>
              {shortValue(
                verification.trustedRootFingerprint,
              )}

              {" ↔ "}

              {shortValue(
                verification.presentedRootFingerprint,
              )}
            </span>
          </div>
        </div>
      )}

      {latest !== null && (
        <div className="trust-root-pin-latest">
          <article>
            <span>
              Latest receipt
            </span>

            <strong>
              #
              {latest.receiptNumber}
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
              Root pin
            </span>

            <strong>
              {latest.rootPinValid
                ? "MATCH"
                : "MISMATCH"}
            </strong>
          </article>

          <article>
            <span>
              Trusted root
            </span>

            <code>
              {shortValue(
                latest.trustedRootFingerprint,
              )}
            </code>
          </article>

          <article>
            <span>
              Presented root
            </span>

            <code>
              {shortValue(
                latest.presentedRootFingerprint,
              )}
            </code>
          </article>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="trust-root-pin-empty">
            Loading pinned trust-root receipts…
          </div>
        )
        : history === null
          || history.receipts.length
            === 0
          ? (
            <div className="trust-root-pin-empty">
              No pinned trust-root verification
              receipt has been recorded yet.
            </div>
          )
          : (
            <div className="trust-root-pin-history">
              {history.receipts.map(
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
                        Trusted
                      </span>

                      <code>
                        {shortValue(
                          receipt.trustedRootFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Presented
                      </span>

                      <code>
                        {shortValue(
                          receipt.presentedRootFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Lineage
                      </span>

                      <strong>
                        {receipt.verifiedTransitionCount}
                        {" / "}
                        {receipt.transitionCount}
                      </strong>
                    </div>

                    <Fingerprint
                      size={12}
                    />

                    <time>
                      {formatDate(
                        receipt.verifiedAt,
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
