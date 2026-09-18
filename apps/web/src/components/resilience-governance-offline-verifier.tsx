"use client";

import {
  CheckCircle2,
  FileCheck2,
  Fingerprint,
  LaptopMinimalCheck,
  RefreshCw,
  ShieldCheck,
  ShieldX,
  TerminalSquare,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  fetchGovernanceOfflineTrustVerificationReceipts,
  importGovernanceOfflineTrustVerificationReceipt,
} from "@/lib/api";

import type {
  GovernanceOfflineTrustVerificationReceiptHistoryResponse,
} from "@/types/resilience-governance-offline-trust-verification";

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

export function ResilienceGovernanceOfflineVerifier() {
  const [
    receiptJson,
    setReceiptJson,
  ] =
    useState("");

  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceOfflineTrustVerificationReceiptHistoryResponse | null
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

  const [
    success,
    setSuccess,
  ] =
    useState<string | null>(
      null,
    );

  useEffect(() => {
    const controller =
      new AbortController();

    void fetchGovernanceOfflineTrustVerificationReceipts(
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
              : "Unable to load offline verification receipts.",
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

    return fetchGovernanceOfflineTrustVerificationReceipts()
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

  const importReceipt = () => {
    if (
      receiptJson.trim() === ""
    ) {
      setError(
        "Paste a standalone verifier receipt first.",
      );

      return;
    }

    setBusy(true);
    setError(null);
    setSuccess(null);

    void importGovernanceOfflineTrustVerificationReceipt(
      receiptJson.trim(),
    )
      .then((receipt) => {
        setSuccess(
          `Imported offline receipt #${receipt.importNumber} after fresh cryptographic re-verification.`,
        );

        return refresh();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to import offline verification receipt.",
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
    <section className="offline-trust-verifier">
      <div className="offline-trust-verifier-heading">
        <div>
          <span className="panel-kicker">
            AIR-GAPPED VERIFICATION
          </span>

          <strong>
            Standalone trust verifier
          </strong>
        </div>

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
                      : "Unable to refresh offline verification history.",
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

      {error !== null && (
        <div className="offline-trust-verifier-error">
          {error}
        </div>
      )}

      {success !== null && (
        <div className="offline-trust-verifier-success">
          <CheckCircle2
            size={14}
          />

          {success}
        </div>
      )}

      <div className="offline-trust-verifier-hero">
        <div className="offline-trust-verifier-icon">
          <LaptopMinimalCheck
            size={24}
          />
        </div>

        <div>
          <span>
            NO SERVER · NO DATABASE · NO PRIVATE KEY
          </span>

          <strong>
            Pure-JDK offline verification
          </strong>

          <p>
            The standalone verifier consumes a
            portable lineage envelope and a root
            fingerprint file locally. TerraScope
            can later import the resulting receipt
            only after independently repeating the
            cryptographic verification.
          </p>
        </div>

        <div className="offline-trust-verifier-count">
          <span>
            Imported
          </span>

          <strong>
            {history
              ?.receipts
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      <div className="offline-trust-verifier-command">
        <TerminalSquare
          size={14}
        />

        <code>
          java -jar terrascope-trust-verifier.jar
          {" --envelope lineage.json"}
          {" --root-pin root.sha256"}
          {" --receipt verification.json"}
        </code>
      </div>

      <div className="offline-trust-verifier-import">
        <label>
          <span>
            Standalone verification receipt
          </span>

          <textarea
            rows={7}
            value={receiptJson}
            onChange={(event) => {
              setReceiptJson(
                event.target.value,
              );
            }}
            placeholder="Paste TS-STANDALONE-TRUST-VERIFIER-V1 receipt JSON."
          />
        </label>

        <button
          type="button"
          disabled={
            busy
            || receiptJson.trim() === ""
          }
          onClick={importReceipt}
        >
          <FileCheck2
            size={13}
          />

          Re-verify & import
        </button>
      </div>

      {latest !== null && (
        <div className="offline-trust-verifier-latest">
          <article>
            <span>
              Import
            </span>

            <strong>
              #
              {latest.importNumber}
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
              Lineage
            </span>

            <strong>
              {latest.lineageValid
                ? "VALID"
                : "INVALID"}
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
              Receipt SHA
            </span>

            <code>
              {shortValue(
                latest.receiptHash,
              )}
            </code>
          </article>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="offline-trust-verifier-empty">
            Loading imported offline receipts…
          </div>
        )
        : history === null
          || history.receipts.length
            === 0
          ? (
            <div className="offline-trust-verifier-empty">
              No standalone verification receipt
              has been imported yet.
            </div>
          )
          : (
            <div className="offline-trust-verifier-history">
              {history.receipts.map(
                (receipt) => (
                  <article
                    key={receipt.id}
                  >
                    <div>
                      <span>
                        IMPORT
                      </span>

                      <strong>
                        #
                        {receipt.importNumber}
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
                        Trusted root
                      </span>

                      <code>
                        {shortValue(
                          receipt.trustedRootFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Presented root
                      </span>

                      <code>
                        {shortValue(
                          receipt.presentedRootFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Transitions
                      </span>

                      <strong>
                        {receipt.verifiedTransitionCount}
                        {" / "}
                        {receipt.transitionCount}
                      </strong>
                    </div>

                    {receipt.verificationResult === "VALID"
                      ? (
                        <ShieldCheck
                          size={12}
                        />
                      )
                      : (
                        <ShieldX
                          size={12}
                        />
                      )
                    }

                    <Fingerprint
                      size={12}
                    />

                    <time>
                      {formatDate(
                        receipt.offlineVerifiedAt,
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
