"use client";

import {
  Archive,
  KeyRound,
  RefreshCw,
  RotateCcwKey,
  ShieldCheck,
  ShieldX,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  fetchGovernanceTrustAnchorHistory,
  registerGovernanceTrustAnchor,
  retireGovernanceTrustAnchor,
} from "@/lib/api";

import type {
  GovernanceTrustAnchorHistoryResponse,
} from "@/types/resilience-governance-trust-lifecycle";

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
  value: string | null,
): string {
  if (
    value === null
  ) {
    return "—";
  }

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

export function ResilienceGovernanceTrustLifecycle() {
  const [
    history,
    setHistory,
  ] =
    useState<
      GovernanceTrustAnchorHistoryResponse | null
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

    void fetchGovernanceTrustAnchorHistory(
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
              : "Unable to load governance trust-anchor lifecycle.",
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

    return fetchGovernanceTrustAnchorHistory()
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

  return (
    <section className="trust-lifecycle">
      <div className="trust-lifecycle-heading">
        <div>
          <span className="panel-kicker">
            KEY LIFECYCLE
          </span>

          <strong>
            Governance trust-anchor rotation
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
                      : "Unable to refresh trust anchors.",
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
        <div className="trust-lifecycle-error">
          {error}
        </div>
      )}

      <div className="trust-lifecycle-hero">
        <div className="trust-lifecycle-key">
          <RotateCcwKey
            size={23}
          />
        </div>

        <div>
          <span>
            ROTATION MODEL
          </span>

          <strong>
            Historical signature continuity
          </strong>

          <p>
            Retired anchors cannot issue new
            signatures, while signatures created
            before retirement remain independently
            verifiable.
          </p>
        </div>

        <div className="trust-lifecycle-count">
          <span>
            Trust anchors
          </span>

          <strong>
            {history
              ?.anchors
              .length
              ?? 0}
          </strong>
        </div>
      </div>

      <form
        className="trust-lifecycle-register"
        onSubmit={(event) => {
          event.preventDefault();

          const data =
            new FormData(
              event.currentTarget,
            );

          const keyId =
            String(
              data.get(
                "keyId",
              )
              ?? "",
            ).trim();

          const publicKey =
            String(
              data.get(
                "publicKey",
              )
              ?? "",
            ).trim();

          if (
            keyId === ""
            || publicKey === ""
          ) {
            return;
          }

          setBusy(true);
          setError(null);

          void registerGovernanceTrustAnchor({
            keyId,
            publicKeyX509Base64:
              publicKey,
          })
            .then(() =>
              refresh()
            )
            .then(() => {
              event.currentTarget.reset();
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
                    : "Unable to register replacement trust anchor.",
                );
              },
            )
            .finally(() => {
              setBusy(false);
            });
        }}
      >
        <label>
          <span>
            Replacement key ID
          </span>

          <input
            required
            name="keyId"
            maxLength={120}
            placeholder="governance-key-2027"
          />
        </label>

        <label className="trust-lifecycle-register-wide">
          <span>
            Ed25519 public key · X.509 Base64
          </span>

          <textarea
            required
            name="publicKey"
            placeholder="Public key only — never paste the private key here."
          />
        </label>

        <button
          type="submit"
          disabled={busy}
        >
          <KeyRound
            size={13}
          />

          Register replacement anchor
        </button>
      </form>

      {loading
        && history === null
        ? (
          <div className="trust-lifecycle-empty">
            Loading trust-anchor lifecycle…
          </div>
        )
        : history === null
          || history.anchors.length
            === 0
          ? (
            <div className="trust-lifecycle-empty">
              No governance trust anchors
              registered.
            </div>
          )
          : (
            <div className="trust-lifecycle-anchor-list">
              {history.anchors.map(
                (anchor) => (
                  <article
                    key={anchor.keyId}
                  >
                    <div className="trust-lifecycle-anchor-top">
                      <div>
                        {anchor.status
                          === "ACTIVE"
                          ? (
                            <ShieldCheck
                              size={15}
                            />
                          )
                          : (
                            <ShieldX
                              size={15}
                            />
                          )
                        }

                        <strong>
                          {anchor.keyId}
                        </strong>
                      </div>

                      <span
                        className={
                          anchor.status
                            === "ACTIVE"
                            ? "trust-lifecycle-status trust-lifecycle-status--active"
                            : "trust-lifecycle-status trust-lifecycle-status--retired"
                        }
                      >
                        {anchor.status}
                      </span>
                    </div>

                    <div className="trust-lifecycle-anchor-details">
                      <div>
                        <span>
                          Fingerprint
                        </span>

                        <code>
                          {shortValue(
                            anchor.publicKeyFingerprint,
                          )}
                        </code>
                      </div>

                      <div>
                        <span>
                          Created
                        </span>

                        <strong>
                          {formatDate(
                            anchor.createdAt,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Last used
                        </span>

                        <strong>
                          {formatDate(
                            anchor.lastUsedAt,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Replacement
                        </span>

                        <code>
                          {anchor.supersededByKeyId
                            ?? "—"
                          }
                        </code>
                      </div>
                    </div>

                    {anchor.status
                      === "RETIRED" && (
                      <div className="trust-lifecycle-retirement">
                        <span>
                          Retired
                          {" · "}
                          {formatDate(
                            anchor.retiredAt,
                          )}
                        </span>

                        <p>
                          {anchor.retirementReason}
                        </p>
                      </div>
                    )}

                    {anchor.status
                      === "ACTIVE"
                      && history.anchors.some(
                        (candidate) =>
                          candidate.status
                            === "ACTIVE"
                          && candidate.keyId
                            !== anchor.keyId,
                      )
                      && (
                        <form
                          className="trust-lifecycle-retire"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const replacementKeyId =
                              String(
                                data.get(
                                  "replacementKeyId",
                                )
                                ?? "",
                              ).trim();

                            const reason =
                              String(
                                data.get(
                                  "reason",
                                )
                                ?? "",
                              ).trim();

                            if (
                              replacementKeyId
                                === ""
                              || reason === ""
                            ) {
                              return;
                            }

                            setBusy(true);
                            setError(null);

                            void retireGovernanceTrustAnchor(
                              anchor.keyId,
                              {
                                replacementKeyId,
                                reason,
                              },
                            )
                              .then(() =>
                                refresh()
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
                                      : "Unable to retire governance key.",
                                  );
                                },
                              )
                              .finally(() => {
                                setBusy(false);
                              });
                          }}
                        >
                          <select
                            required
                            name="replacementKeyId"
                            defaultValue=""
                          >
                            <option
                              value=""
                              disabled
                            >
                              Replacement anchor
                            </option>

                            {history.anchors
                              .filter(
                                (candidate) =>
                                  candidate.status
                                    === "ACTIVE"
                                  && candidate.keyId
                                    !== anchor.keyId,
                              )
                              .map(
                                (candidate) => (
                                  <option
                                    key={
                                      candidate.keyId
                                    }
                                    value={
                                      candidate.keyId
                                    }
                                  >
                                    {candidate.keyId}
                                  </option>
                                ),
                              )}
                          </select>

                          <input
                            required
                            name="reason"
                            maxLength={1000}
                            placeholder="Rotation reason"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            <RotateCcwKey
                              size={12}
                            />

                            Retire
                          </button>
                        </form>
                      )
                    }
                  </article>
                ),
              )}
            </div>
          )
      }

      <section className="trust-lifecycle-events-section">
        <div className="trust-lifecycle-section-heading">
          <Archive
            size={14}
          />

          <strong>
            Trust lifecycle evidence
          </strong>

          <span>
            {history
              ?.events
              .length
              ?? 0}
          </span>
        </div>

        {history === null
          || history.events.length
            === 0
          ? (
            <div className="trust-lifecycle-empty">
              No lifecycle events recorded.
            </div>
          )
          : (
            <div className="trust-lifecycle-events">
              {history.events
                .slice(
                  0,
                  15,
                )
                .map(
                  (event) => (
                    <article
                      key={event.id}
                    >
                      <div>
                        <span>
                          {event.eventType}
                        </span>

                        <strong>
                          {event.keyId}
                        </strong>
                      </div>

                      <p>
                        {event.detail}
                      </p>

                      <code>
                        {event.replacementKeyId
                          ?? "—"
                        }
                      </code>

                      <time>
                        {formatDate(
                          event.createdAt,
                        )}
                      </time>
                    </article>
                  ),
                )}
            </div>
          )
        }
      </section>
    </section>
  );
}
