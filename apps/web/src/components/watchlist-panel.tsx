"use client";

import {
  Activity,
  AlertTriangle,
  BookmarkCheck,
  CloudLightning,
  Flame,
  Mountain,
  Trash2,
  X,
} from "lucide-react";

import {
  useEffect,
  useRef,
  useState,
} from "react";

import { ExportButtons } from "@/components/export-buttons";

import { exportWatchlist } from "@/lib/export-sets";

import {
  useDialogBehavior,
} from "@/lib/use-dialog-behavior";

import {
  deleteWatchlistEntry,
  fetchWatchlist,
  updateWatchlistPriority,
} from "@/lib/api";

import {
  formatDateTime,
} from "@/lib/format";

import type {
  WatchlistEntry,
  WatchlistFeedResponse,
  WatchPriority,
} from "@/types/watchlist";

type WatchlistPanelProps = {
  open: boolean;
  refreshToken: number;
  onClose: () => void;
};

const priorities: WatchPriority[] = [
  "LOW",
  "MEDIUM",
  "HIGH",
  "CRITICAL",
];

function hazardLabel(
  hazardType: WatchlistEntry["hazardType"],
): string {
  switch (hazardType) {
    case "earthquakes":
      return "Earthquake";

    case "volcanoes":
      return "Volcano";

    case "wildfires":
      return "Wildfire";

    case "severe-storms":
      return "Severe storm";
  }
}

function HazardIcon({
  hazardType,
}: {
  hazardType: WatchlistEntry["hazardType"];
}) {
  if (hazardType === "earthquakes") {
    return (
      <Activity size={18} />
    );
  }

  if (hazardType === "volcanoes") {
    return (
      <Mountain size={18} />
    );
  }

  if (hazardType === "wildfires") {
    return (
      <Flame size={18} />
    );
  }

  return (
    <CloudLightning size={18} />
  );
}

export function WatchlistPanel({
  open,
  refreshToken,
  onClose,
}: WatchlistPanelProps) {
  const [
    data,
    setData,
  ] =
    useState<
      WatchlistFeedResponse | null
    >(null);

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  const [
    mutationId,
    setMutationId,
  ] =
    useState<string | null>(
      null,
    );

  useEffect(() => {
    if (!open) {
      return;
    }

    const controller =
      new AbortController();

    void fetchWatchlist(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setData(response);
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
              : "Unable to load saved events.",
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
    open,
    refreshToken,
  ]);

  const reload = () =>
    fetchWatchlist()
      .then((response) => {
        setData(response);
        setError(null);
      });

  const handlePriorityChange = (
    entry: WatchlistEntry,
    priority: WatchPriority,
  ) => {
    setMutationId(
      entry.id,
    );

    setError(null);

    void updateWatchlistPriority(
      entry.id,
      priority,
    )
      .then(() =>
        reload()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update priority.",
          );
        },
      )
      .finally(() => {
        setMutationId(null);
      });
  };

  const handleDelete = (
    entry: WatchlistEntry,
  ) => {
    setMutationId(
      entry.id,
    );

    setError(null);

    void deleteWatchlistEntry(
      entry.id,
    )
      .then(() =>
        reload()
      )
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to delete saved event.",
          );
        },
      )
      .finally(() => {
        setMutationId(null);
      });
  };

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  if (!open) {
    return null;
  }

  return (
    <div className="watchlist-overlay">
      <button
        className="watchlist-backdrop"
        type="button"
        aria-label="Close saved events"
        tabIndex={-1}
        onClick={onClose}
      />

      <aside
        ref={dialogRef}
        className="watchlist-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="watchlist-title"
        tabIndex={-1}
      >
        <header className="watchlist-panel__header">
          <div>
            <span className="panel-kicker">
              EVENT WATCHLIST
            </span>

            <h2 id="watchlist-title">
              Saved events
            </h2>
          </div>

          <div className="export-actions">
            <ExportButtons
              noun="saved event"
              subject="saved events"
              count={data?.entries.length ?? 0}
              onBuild={(format) => ({
                file: exportWatchlist(data?.entries ?? [], format),
              })}
            />

            <button
              className="icon-button"
              type="button"
              aria-label="Close saved events"
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
        </header>

        {data !== null && (
          <div className="watchlist-summary">
            <div>
              <span>
                Total
              </span>

              <strong>
                {data.summary.totalCount}
              </strong>
            </div>

            <div>
              <span>
                Critical
              </span>

              <strong>
                {data.summary.criticalCount}
              </strong>
            </div>

            <div>
              <span>
                High
              </span>

              <strong>
                {data.summary.highCount}
              </strong>
            </div>

            <div>
              <span>
                Hazards
              </span>

              <strong>
                {
                  [
                    data.summary.earthquakeCount,
                    data.summary.volcanoCount,
                    data.summary.wildfireCount,
                    data.summary.severeStormCount,
                  ].filter(
                    (count) => count > 0,
                  ).length
                }
              </strong>
            </div>
          </div>
        )}

        {error !== null && (
          <div className="watchlist-error">
            <AlertTriangle size={15} />

            <span>
              {error}
            </span>
          </div>
        )}

        <div className="watchlist-panel__body">
          {loading
            && data === null
            ? (
              <div className="watchlist-loading">
                Loading saved events…
              </div>
            )
            : data === null
              || data.entries.length === 0
              ? (
                <div className="watchlist-empty">
                  <BookmarkCheck size={28} />

                  <strong>
                    No saved events yet
                  </strong>

                  <span>
                    Select any monitored event
                    and use “Save event” to
                    add it to your watchlist.
                  </span>
                </div>
              )
              : (
                data.entries.map(
                  (entry) => (
                    <article
                      className="watchlist-card"
                      key={entry.id}
                    >
                      <div className="watchlist-card__icon">
                        <HazardIcon
                          hazardType={
                            entry.hazardType
                          }
                        />
                      </div>

                      <div className="watchlist-card__content">
                        <div className="watchlist-card__top">
                          <div>
                            <span className="watchlist-card__hazard">
                              {hazardLabel(
                                entry.hazardType,
                              )}
                            </span>

                            <h3>
                              {entry.title}
                            </h3>
                          </div>

                          <button
                            className="watchlist-delete"
                            type="button"
                            disabled={
                              mutationId
                              === entry.id
                            }
                            aria-label={
                              `Remove ${entry.title}`
                            }
                            onClick={() =>
                              handleDelete(
                                entry,
                              )
                            }
                          >
                            <Trash2
                              size={15}
                            />
                          </button>
                        </div>

                        <div className="watchlist-card__meta">
                          <span>
                            {entry.sourceProvider}
                          </span>

                          <span>
                            {formatDateTime(
                              entry.observedAt,
                            )}
                          </span>

                          {entry.magnitudeValue
                            !== null && (
                            <span>
                              {entry.magnitudeValue}
                              {entry.magnitudeUnit
                                ? ` ${entry.magnitudeUnit}`
                                : ""}
                            </span>
                          )}
                        </div>

                        <div className="watchlist-card__footer">
                          <label>
                            <span>
                              Priority
                            </span>

                            <select
                              value={
                                entry.priority
                              }
                              disabled={
                                mutationId
                                === entry.id
                              }
                              onChange={(event) =>
                                handlePriorityChange(
                                  entry,
                                  event.target.value as WatchPriority,
                                )
                              }
                            >
                              {priorities.map(
                                (priority) => (
                                  <option
                                    value={
                                      priority
                                    }
                                    key={
                                      priority
                                    }
                                  >
                                    {priority}
                                  </option>
                                ),
                              )}
                            </select>
                          </label>

                          <span
                            className={
                              `priority-badge priority-badge--${entry.priority.toLowerCase()}`
                            }
                          >
                            {entry.priority}
                          </span>
                        </div>
                      </div>
                    </article>
                  ),
                )
              )
          }
        </div>
      </aside>
    </div>
  );
}
