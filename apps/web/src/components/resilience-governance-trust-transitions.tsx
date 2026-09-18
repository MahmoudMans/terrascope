"use client";

import {
  ArrowRight,
  CheckCircle2,
  Fingerprint,
  KeyRound,
  RefreshCw,
  RotateCcwKey,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
  type FormEvent,
} from "react";

import {
  fetchGovernanceTrustAnchorTransitions,
  rotateGovernanceTrustAnchor,
  verifyGovernanceTrustAnchorTransitions,
} from "@/lib/api";

import type {
  GovernanceTrustAnchorTransitionHistoryResponse,
  GovernanceTrustAnchorTransitionVerificationResponse,
} from "@/types/resilience-governance-trust-transitions";

function shortValue(
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "GENESIS";
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

export function ResilienceGovernanceTrustTransitions() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceTrustAnchorTransitionHistoryResponse | null
    >(null);

  const [
    verification,
    setVerification,
  ] =
    useState<
      GovernanceTrustAnchorTransitionVerificationResponse | null
    >(null);

  const [
    replacementKeyId,
    setReplacementKeyId,
  ] =
    useState("");

  const [
    replacementPublicKey,
    setReplacementPublicKey,
  ] =
    useState("");

  const [
    reason,
    setReason,
  ] =
    useState("");

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

    void fetchGovernanceTrustAnchorTransitions(
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
              : "Unable to load signed key transitions.",
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

    return fetchGovernanceTrustAnchorTransitions()
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

  const rotate = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      replacementKeyId.trim() === ""
      || replacementPublicKey.trim() === ""
      || reason.trim() === ""
    ) {
      setError(
        "Replacement key ID, public key, and transition reason are required.",
      );

      return;
    }

    setBusy(true);
    setError(null);

    void rotateGovernanceTrustAnchor(
      {
        replacementKeyId:
          replacementKeyId.trim(),

        replacementPublicKeyX509Base64:
          replacementPublicKey.trim(),

        reason:
          reason.trim(),
      },
    )
      .then(() => {
        setReplacementKeyId("");
        setReplacementPublicKey("");
        setReason("");

        return refresh();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to create signed trust-anchor transition.",
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

    void verifyGovernanceTrustAnchorTransitions()
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
              : "Unable to verify signed trust-anchor transitions.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const latest =
    history
      ?.transitions
      .at(0)
    ?? null;

  return (
    <section className="trust-transitions">
      <div className="trust-transitions-heading">
        <div>
          <span className="panel-kicker">
            CRYPTOGRAPHIC KEY LINEAGE
          </span>

          <strong>
            Signed trust-anchor transitions
          </strong>
        </div>

        <div className="trust-transitions-actions">
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
                        : "Unable to refresh signed transitions.",
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

            Verify lineage
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="trust-transitions-error">
          {error}
        </div>
      )}

      <div className="trust-transitions-hero">
        <div className="trust-transitions-icon">
          <RotateCcwKey
            size={24}
          />
        </div>

        <div>
          <span>
            OUTGOING-KEY SIGNED ROTATION
          </span>

          <strong>
            Cryptographic trust succession
          </strong>

          <p>
            The currently active Ed25519 key
            cryptographically attests its
            replacement before retirement,
            creating a verifiable key-lineage
            chain across governance generations.
          </p>
        </div>

        <div className="trust-transitions-count">
          <span>
            Transitions
          </span>

          <strong>
            {history
              ?.transitions
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      <form
        className="trust-transitions-form"
        onSubmit={rotate}
      >
        <label>
          <span>
            Replacement key ID
          </span>

          <input
            value={replacementKeyId}
            onChange={(event) => {
              setReplacementKeyId(
                event.target.value,
              );
            }}
            placeholder="governance-ed25519-v2"
          />
        </label>

        <label>
          <span>
            Replacement X509 public key
          </span>

          <textarea
            value={replacementPublicKey}
            onChange={(event) => {
              setReplacementPublicKey(
                event.target.value,
              );
            }}
            placeholder="Base64 X509 Ed25519 public key"
            rows={3}
          />
        </label>

        <label>
          <span>
            Rotation reason
          </span>

          <input
            value={reason}
            onChange={(event) => {
              setReason(
                event.target.value,
              );
            }}
            placeholder="Scheduled cryptographic rotation"
          />
        </label>

        <button
          type="submit"
          disabled={busy}
        >
          <KeyRound
            size={13}
          />

          Sign & retire current key
        </button>
      </form>

      {latest !== null && (
        <div className="trust-transitions-latest">
          <article>
            <span>
              Transition
            </span>

            <strong>
              #
              {latest.transitionNumber}
            </strong>
          </article>

          <article>
            <span>
              Outgoing
            </span>

            <code>
              {shortValue(
                latest.outgoingKeyId,
              )}
            </code>
          </article>

          <article>
            <span>
              Replacement
            </span>

            <code>
              {shortValue(
                latest.replacementKeyId,
              )}
            </code>
          </article>

          <article>
            <span>
              Transition hash
            </span>

            <code>
              {shortValue(
                latest.transitionHash,
              )}
            </code>
          </article>
        </div>
      )}

      {verification !== null && (
        <div
          className={
            verification.valid
              ? "trust-transitions-verification trust-transitions-verification--valid"
              : "trust-transitions-verification trust-transitions-verification--invalid"
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
              {" transitions verified"}
            </span>
          </div>
        </div>
      )}

      {loading
        && history === null
        ? (
          <div className="trust-transitions-empty">
            Loading cryptographic key lineage…
          </div>
        )
        : history === null
          || history.transitions.length
            === 0
          ? (
            <div className="trust-transitions-empty">
              No signed trust-anchor transition
              has been issued yet.
            </div>
          )
          : (
            <div className="trust-transitions-history">
              {history.transitions.map(
                (transition) => (
                  <article
                    key={transition.id}
                  >
                    <div>
                      <span>
                        TRANSITION
                      </span>

                      <strong>
                        #
                        {transition.transitionNumber}
                      </strong>
                    </div>

                    <div className="trust-transitions-pair">
                      <code>
                        {transition.outgoingKeyId}
                      </code>

                      <ArrowRight
                        size={12}
                      />

                      <code>
                        {transition.replacementKeyId}
                      </code>
                    </div>

                    <div>
                      <span>
                        Outgoing fingerprint
                      </span>

                      <code>
                        {shortValue(
                          transition.outgoingPublicKeyFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Replacement fingerprint
                      </span>

                      <code>
                        {shortValue(
                          transition.replacementPublicKeyFingerprint,
                        )}
                      </code>
                    </div>

                    <div>
                      <span>
                        Previous
                      </span>

                      <code>
                        {shortValue(
                          transition.previousTransitionHash,
                        )}
                      </code>
                    </div>

                    <Fingerprint
                      size={12}
                    />

                    <time>
                      {formatDate(
                        transition.createdAt,
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
