"use client";

import {
  Activity,
  Ambulance,
  Bus,
  CircleCheck,
  Droplets,
  Fuel,
  HeartPulse,
  PlugZap,
  RadioTower,
  RefreshCw,
  Siren,
  Trash2,
  UserRound,
  UtilityPole,
  Waves,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createLifelineAsset,
  deleteLifelineAsset,
  fetchLifelineOperationsBoard,
  updateLifelineImpact,
  updateLifelineOwner,
  updateLifelineRestorationEta,
  updateLifelineStatus,
} from "@/lib/api";

import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  CreateLifelineAssetRequest,
  LifelineAssetCategory,
  LifelineAssetStatus,
  LifelineOperationsBoardResponse,
} from "@/types/lifeline-operations";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentLifelineBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialAsset:
  CreateLifelineAssetRequest = {
    category: "POWER",
    name: "",
    description: "",
    priority: "HIGH",
    status: "OPERATIONAL",
    affectedPopulation: 0,
    owner: "Unassigned",
    restorationEta: null,
  };

function categoryLabel(
  category:
    LifelineAssetCategory,
): string {
  switch (category) {
    case "POWER":
      return "Power";

    case "WATER":
      return "Water";

    case "HEALTHCARE":
      return "Healthcare";

    case "TRANSPORT":
      return "Transport";

    case "COMMUNICATIONS":
      return "Communications";

    case "FUEL":
      return "Fuel";

    case "OTHER":
      return "Other";
  }
}

function CategoryIcon({
  category,
}: {
  category:
    LifelineAssetCategory;
}) {
  switch (category) {
    case "POWER":
      return <PlugZap size={14} />;

    case "WATER":
      return <Droplets size={14} />;

    case "HEALTHCARE":
      return <HeartPulse size={14} />;

    case "TRANSPORT":
      return <Bus size={14} />;

    case "COMMUNICATIONS":
      return <RadioTower size={14} />;

    case "FUEL":
      return <Fuel size={14} />;

    case "OTHER":
      return <UtilityPole size={14} />;
  }
}

function statusClass(
  status:
    LifelineAssetStatus,
): string {
  return (
    `lifeline-status lifeline-status--${status.toLowerCase()}`
  );
}

function priorityClass(
  priority:
    IncidentActionPriority,
): string {
  return (
    `lifeline-priority lifeline-priority--${priority.toLowerCase()}`
  );
}

function isDisrupted(
  status:
    LifelineAssetStatus,
): boolean {
  return (
    status === "DEGRADED"
    || status === "OUTAGE"
  );
}

function formatEta(
  value: string | null,
): string {
  if (value === null) {
    return "No ETA";
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

export function IncidentLifelineBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentLifelineBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      LifelineOperationsBoardResponse | null
    >(null);

  const [
    draft,
    setDraft,
  ] =
    useState<
      CreateLifelineAssetRequest
    >(initialAsset);

  const [
    etaDraft,
    setEtaDraft,
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

    void fetchLifelineOperationsBoard(
      incidentId,
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setBoard(response);
        setError(null);
      })
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
              : "Unable to load lifeline operations.",
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

  const applyMutation = (
    operation:
      Promise<LifelineOperationsBoardResponse>,
  ) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        setBoard(response);
        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update lifeline operation.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleCreate = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      draft.name.trim()
      === ""
    ) {
      return;
    }

    let restorationEta:
      string | null = null;

    if (
      etaDraft.trim()
      !== ""
    ) {
      restorationEta =
        new Date(
          etaDraft,
        ).toISOString();
    }

    applyMutation(
      createLifelineAsset(
        incidentId,
        {
          ...draft,
          name:
            draft.name.trim(),
          description:
            draft.description.trim(),
          owner:
            draft.owner.trim()
            || "Unassigned",
          restorationEta:
            isDisrupted(
              draft.status,
            )
              ? restorationEta
              : null,
          affectedPopulation:
            isDisrupted(
              draft.status,
            )
              ? draft.affectedPopulation
              : 0,
        },
      ),
    );

    setDraft(
      initialAsset,
    );

    setEtaDraft("");
  };

  const readOnly =
    incidentStatus === "CLOSED";

  return (
    <section className="lifeline-board">
      <div className="lifeline-heading">
        <div>
          <span className="panel-kicker">
            CRITICAL INFRASTRUCTURE
          </span>

          <strong>
            Lifeline operations
          </strong>
        </div>

        <button
          type="button"
          disabled={
            loading
            || busy
          }
          onClick={() => {
            setLoading(true);

            void fetchLifelineOperationsBoard(
              incidentId,
            )
              .then((response) => {
                setBoard(response);
                setError(null);
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
                      : "Unable to refresh lifelines.",
                  );
                },
              )
              .finally(() => {
                setLoading(false);
              });
          }}
        >
          <RefreshCw size={13} />
          Refresh
        </button>
      </div>

      <div className="lifeline-summary">
        <div>
          <span>Total</span>
          <strong>{board?.totalCount ?? 0}</strong>
        </div>

        <div>
          <span>Disrupted</span>
          <strong>{board?.disruptedCount ?? 0}</strong>
        </div>

        <div>
          <span>Outages</span>
          <strong>{board?.outageCount ?? 0}</strong>
        </div>

        <div>
          <span>Critical</span>
          <strong>{board?.criticalDisruptionCount ?? 0}</strong>
        </div>

        <div>
          <span>Affected population</span>
          <strong>{board?.affectedPopulation ?? 0}</strong>
        </div>

        <div>
          <span>ETA tracked</span>
          <strong>{board?.etaTrackedCount ?? 0}</strong>
        </div>
      </div>

      {error !== null && (
        <div className="lifeline-error">
          {error}
        </div>
      )}

      {!readOnly && (
        <form
          className="lifeline-form"
          onSubmit={handleCreate}
        >
          <label>
            <span>Category</span>

            <select
              value={draft.category}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  category:
                    event.target.value as LifelineAssetCategory,
                })
              }
            >
              <option value="POWER">Power</option>
              <option value="WATER">Water</option>
              <option value="HEALTHCARE">Healthcare</option>
              <option value="TRANSPORT">Transport</option>
              <option value="COMMUNICATIONS">Communications</option>
              <option value="FUEL">Fuel</option>
              <option value="OTHER">Other</option>
            </select>
          </label>

          <label>
            <span>Priority</span>

            <select
              value={draft.priority}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  priority:
                    event.target.value as IncidentActionPriority,
                })
              }
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>

          <label className="lifeline-form-wide">
            <span>Infrastructure asset</span>

            <input
              required
              maxLength={300}
              value={draft.name}
              placeholder="e.g. North Grid Substation"
              onChange={(event) =>
                setDraft({
                  ...draft,
                  name:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>Status</span>

            <select
              value={draft.status}
              onChange={(event) => {
                const status =
                  event.target.value as LifelineAssetStatus;

                setDraft({
                  ...draft,
                  status,
                  affectedPopulation:
                    isDisrupted(
                      status,
                    )
                      ? draft.affectedPopulation
                      : 0,
                });

                if (
                  !isDisrupted(
                    status,
                  )
                ) {
                  setEtaDraft("");
                }
              }}
            >
              <option value="OPERATIONAL">Operational</option>
              <option value="DEGRADED">Degraded</option>
              <option value="OUTAGE">Outage</option>
              <option value="RESTORED">Restored</option>
            </select>
          </label>

          <label>
            <span>Affected population</span>

            <input
              type="number"
              min={0}
              max={100000000}
              disabled={
                !isDisrupted(
                  draft.status,
                )
              }
              value={
                draft.affectedPopulation
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  affectedPopulation:
                    Number(
                      event.target.value,
                    ),
                })
              }
            />
          </label>

          <label>
            <span>Owner</span>

            <input
              maxLength={120}
              value={draft.owner}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  owner:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>Restoration ETA</span>

            <input
              type="datetime-local"
              disabled={
                !isDisrupted(
                  draft.status,
                )
              }
              value={etaDraft}
              onChange={(event) =>
                setEtaDraft(
                  event.target.value,
                )
              }
            />
          </label>

          <label className="lifeline-form-wide">
            <span>Description</span>

            <textarea
              maxLength={2000}
              value={draft.description}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  description:
                    event.target.value,
                })
              }
            />
          </label>

          <button
            type="submit"
            className="lifeline-create"
            disabled={busy}
          >
            <Activity size={13} />
            Add infrastructure asset
          </button>
        </form>
      )}

      {readOnly && (
        <div className="lifeline-readonly">
          This incident is closed.
          Lifeline operations are read-only.
        </div>
      )}

      <div className="lifeline-list">
        {loading
          && board === null
          ? (
            <div className="lifeline-empty">
              Loading critical infrastructure…
            </div>
          )
          : board === null
            || board.assets.length
              === 0
            ? (
              <div className="lifeline-empty lifeline-empty--large">
                <Waves size={28} />

                <strong>
                  No lifeline assets
                </strong>

                <span>
                  Add power, water, healthcare,
                  transport or communications
                  infrastructure.
                </span>
              </div>
            )
            : board.assets.map(
              (asset) => (
                <article
                  key={asset.id}
                  className={
                    isDisrupted(
                      asset.status,
                    )
                      ? "lifeline-card lifeline-card--disrupted"
                      : "lifeline-card"
                  }
                >
                  <div className="lifeline-card-top">
                    <div>
                      <span className="lifeline-category-icon">
                        <CategoryIcon
                          category={
                            asset.category
                          }
                        />
                      </span>

                      <span
                        className={
                          priorityClass(
                            asset.priority,
                          )
                        }
                      >
                        {asset.priority}
                      </span>

                      <span
                        className={
                          statusClass(
                            asset.status,
                          )
                        }
                      >
                        {asset.status}
                      </span>
                    </div>

                    {!readOnly && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          applyMutation(
                            deleteLifelineAsset(
                              incidentId,
                              asset.id,
                            ),
                          )
                        }
                      >
                        <Trash2 size={13} />
                      </button>
                    )}
                  </div>

                  <div className="lifeline-card-title">
                    <span>
                      {categoryLabel(
                        asset.category,
                      )}
                    </span>

                    <strong>
                      {asset.name}
                    </strong>
                  </div>

                  {asset.description
                    !== "" && (
                    <p>
                      {asset.description}
                    </p>
                  )}

                  <div className="lifeline-metrics">
                    <div>
                      <span>Affected</span>
                      <strong>
                        {asset.affectedPopulation}
                      </strong>
                    </div>

                    <div>
                      <span>Restoration</span>
                      <strong>
                        {formatEta(
                          asset.restorationEta,
                        )}
                      </strong>
                    </div>
                  </div>

                  <div className="lifeline-owner">
                    <UserRound size={11} />
                    <span>{asset.owner}</span>

                    {asset.status === "OUTAGE" && (
                      <Siren size={11} />
                    )}

                    {asset.status === "RESTORED" && (
                      <CircleCheck size={11} />
                    )}
                  </div>

                  {!readOnly && (
                    <>
                      <select
                        className="lifeline-status-select"
                        value={asset.status}
                        disabled={busy}
                        onChange={(event) =>
                          applyMutation(
                            updateLifelineStatus(
                              incidentId,
                              asset.id,
                              event.target.value as LifelineAssetStatus,
                            ),
                          )
                        }
                      >
                        <option value="OPERATIONAL">
                          Operational
                        </option>

                        <option value="DEGRADED">
                          Degraded
                        </option>

                        <option value="OUTAGE">
                          Outage
                        </option>

                        <option value="RESTORED">
                          Restored
                        </option>
                      </select>

                      {isDisrupted(
                        asset.status,
                      ) && (
                        <>
                          <form
                            className="lifeline-inline"
                            onSubmit={(event) => {
                              event.preventDefault();

                              const data =
                                new FormData(
                                  event.currentTarget,
                                );

                              const value =
                                Number(
                                  data.get(
                                    "impact",
                                  ),
                                );

                              if (
                                !Number.isFinite(
                                  value,
                                )
                                || value < 0
                              ) {
                                setError(
                                  "Affected population must be zero or greater.",
                                );

                                return;
                              }

                              applyMutation(
                                updateLifelineImpact(
                                  incidentId,
                                  asset.id,
                                  value,
                                ),
                              );
                            }}
                          >
                            <input
                              name="impact"
                              type="number"
                              min={0}
                              max={100000000}
                              defaultValue={
                                asset.affectedPopulation
                              }
                            />

                            <button
                              type="submit"
                              disabled={busy}
                            >
                              Impact
                            </button>
                          </form>

                          <form
                            className="lifeline-inline"
                            onSubmit={(event) => {
                              event.preventDefault();

                              const data =
                                new FormData(
                                  event.currentTarget,
                                );

                              const raw =
                                String(
                                  data.get(
                                    "eta",
                                  )
                                  ?? "",
                                ).trim();

                              const eta =
                                raw === ""
                                  ? null
                                  : new Date(
                                    raw,
                                  ).toISOString();

                              applyMutation(
                                updateLifelineRestorationEta(
                                  incidentId,
                                  asset.id,
                                  eta,
                                ),
                              );
                            }}
                          >
                            <input
                              name="eta"
                              type="datetime-local"
                            />

                            <button
                              type="submit"
                              disabled={busy}
                            >
                              ETA
                            </button>
                          </form>
                        </>
                      )}

                      <form
                        className="lifeline-inline"
                        onSubmit={(event) => {
                          event.preventDefault();

                          const data =
                            new FormData(
                              event.currentTarget,
                            );

                          const owner =
                            String(
                              data.get(
                                "owner",
                              )
                              ?? "",
                            ).trim();

                          if (
                            owner === ""
                          ) {
                            return;
                          }

                          applyMutation(
                            updateLifelineOwner(
                              incidentId,
                              asset.id,
                              owner,
                            ),
                          );
                        }}
                      >
                        <input
                          name="owner"
                          maxLength={120}
                          defaultValue={
                            asset.owner
                          }
                        />

                        <button
                          type="submit"
                          disabled={busy}
                        >
                          Assign
                        </button>
                      </form>
                    </>
                  )}

                  <div className="lifeline-card-footer">
                    {asset.category === "POWER" && (
                      <UtilityPole size={12} />
                    )}

                    {asset.category === "WATER" && (
                      <Droplets size={12} />
                    )}

                    {asset.category === "HEALTHCARE" && (
                      <Ambulance size={12} />
                    )}

                    <span>
                      {asset.status === "OUTAGE"
                        ? "Service unavailable"
                        : asset.status === "DEGRADED"
                          ? "Service degraded"
                          : asset.status === "RESTORED"
                            ? "Service restored"
                            : "Service operational"}
                    </span>
                  </div>
                </article>
              ),
            )
        }
      </div>
    </section>
  );
}
