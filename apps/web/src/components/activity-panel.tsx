"use client";

import { RefreshCw, X } from "lucide-react";

import { useEffect, useRef, useState } from "react";

import {
  ExportButtons,
  type ExportResult,
} from "@/components/export-buttons";

import type { ExportFormat } from "@/lib/export";

import { exportActivity } from "@/lib/export-sets";

import {
  ActivityRequestError,
  dayKey,
  dayLabel,
  fetchActivity,
  shortEntityId,
  timeLabel,
  type ActivityEntry,
} from "@/lib/activity";

import { roleLabel } from "@/lib/auth";
import { formatRelativeTime } from "@/lib/format";
import { useDialogBehavior } from "@/lib/use-dialog-behavior";
import { useSession } from "@/lib/use-session";

type ActivityPanelProps = {
  onClose: () => void;
};

type Loaded = {
  key: string;
  entries: ActivityEntry[];
  nextBefore: string | null;
  error: string | null;
};

function isAbort(caught: unknown): boolean {
  return caught instanceof DOMException && caught.name === "AbortError";
}

function messageOf(caught: unknown, fallback: string): string {
  return caught instanceof ActivityRequestError ? caught.message : fallback;
}

function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter((word) => word.length > 0);

  if (words.length === 0) {
    return "?";
  }

  const letters =
    words.length === 1
      ? words[0].slice(0, 2)
      : `${words[0][0]}${words[words.length - 1][0]}`;

  return letters.toUpperCase();
}

function groupByDay(
  entries: ActivityEntry[],
): Array<{ key: string; label: string; entries: ActivityEntry[] }> {
  const groups: Array<{
    key: string;
    label: string;
    entries: ActivityEntry[];
  }> = [];

  for (const entry of entries) {
    const key = dayKey(entry.occurredAt);
    const last = groups[groups.length - 1];

    if (last !== undefined && last.key === key) {
      last.entries.push(entry);
    } else {
      groups.push({
        key,
        label: dayLabel(entry.occurredAt),
        entries: [entry],
      });
    }
  }

  return groups;
}

export function ActivityPanel({ onClose }: ActivityPanelProps) {
  const [actor, setActor] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [people, setPeople] = useState<Record<string, string>>({});
  const [moreBusy, setMoreBusy] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);

  const signInOff = useSession() === null;

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open: true, onClose, containerRef: dialogRef });

  const requestKey = `${actor}#${reloadKey}`;

  useEffect(() => {
    const controller = new AbortController();

    fetchActivity({
      actor: actor === "" ? null : actor,
      signal: controller.signal,
    })
      .then((page) => {
        if (controller.signal.aborted) {
          return;
        }

        setLoaded({
          key: requestKey,
          entries: page.entries,
          nextBefore: page.nextBefore,
          error: null,
        });

        setPeople((known) => {
          const next = { ...known };

          for (const entry of page.entries) {
            next[entry.actorUsername] = entry.actorDisplayName;
          }

          return next;
        });
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted || isAbort(caught)) {
          return;
        }

        setLoaded({
          key: requestKey,
          entries: [],
          nextBefore: null,
          error: messageOf(caught, "Unable to load activity."),
        });
      });

    return () => {
      controller.abort();
    };
  }, [actor, requestKey]);

  const ready = loaded !== null && loaded.key === requestKey;
  const entries = ready ? loaded.entries : [];
  const nextBefore = ready ? loaded.nextBefore : null;
  const error = ready ? loaded.error : null;

  const loadMore = () => {
    if (!ready || nextBefore === null || moreBusy) {
      return;
    }

    const key = requestKey;

    setMoreBusy(true);
    setMoreError(null);

    fetchActivity({
      actor: actor === "" ? null : actor,
      before: nextBefore,
    })
      .then((page) => {
        setLoaded((current) =>
          current !== null && current.key === key
            ? {
                ...current,
                entries: [...current.entries, ...page.entries],
                nextBefore: page.nextBefore,
              }
            : current,
        );
      })
      .catch((caught: unknown) => {
        setMoreError(messageOf(caught, "Unable to load older activity."));
      })
      .finally(() => {
        setMoreBusy(false);
      });
  };

  const buildActivityExport = async (
    format: ExportFormat,
  ): Promise<ExportResult | null> => {
    const { file, truncated } = await exportActivity(
      actor === "" ? null : actor,
      format,
    );

    if (!truncated) {
      return { file };
    }

    return {
      file,
      note: `Exported the newest ${file.count} activity records as ${format.toUpperCase()}. Older records were left out.`,
    };
  };

  const groups = groupByDay(entries);

  const peopleOptions = Object.entries(people).sort((left, right) =>
    left[1].localeCompare(right[1]),
  );

  return (
    <div className="drawer-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close activity"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="drawer drawer--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="activity-title"
        tabIndex={-1}
      >
        <header className="drawer__header">
          <div>
            <span className="panel-kicker">AUDIT TRAIL</span>

            <h2 id="activity-title">Activity</h2>

            <p>Who changed what, newest first.</p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close activity"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="drawer__toolbar">
          <label className="activity-filter">
            <span>Person</span>

            <select
              className="setting-select users-select"
              value={actor}
              onChange={(event) => setActor(event.target.value)}
            >
              <option value="">Everyone</option>

              {peopleOptions.map(([username, name]) => (
                <option key={username} value={username}>
                  {name}
                </option>
              ))}
            </select>
          </label>

          <button
            type="button"
            className="drawer-action drawer-action--quiet"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            <RefreshCw size={14} aria-hidden="true" />
            Refresh
          </button>

          <ExportButtons
            noun="activity record"
            subject="activity"
            count={entries.length}
            onBuild={buildActivityExport}
          />
        </div>

        <div className="drawer__body">
          {signInOff && (
            <p className="activity-note">
              Sign-in is off on this server, so every change is recorded as
              &ldquo;Local session&rdquo;. Turn sign-in on to see who made
              each change.
            </p>
          )}

          {!ready && (
            <div className="drawer__empty" role="status">
              <strong>Loading activity…</strong>
            </div>
          )}

          {error !== null && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}

          {ready && error === null && entries.length === 0 && (
            <div className="drawer__empty">
              <strong>No activity yet</strong>

              <span>
                Changes to incidents, alerts, the watchlist and users will
                show up here as they happen.
              </span>
            </div>
          )}

          {groups.map((group) => (
            <section key={group.key} className="activity-day">
              <h3>{group.label}</h3>

              <ul className="activity-list">
                {group.entries.map((entry) => (
                  <li key={entry.id} className="activity-row">
                    <span className="user-avatar" aria-hidden="true">
                      {initialsOf(entry.actorDisplayName)}
                    </span>

                    <div className="activity-row__body">
                      <p>
                        <strong>{entry.actorDisplayName}</strong>{" "}
                        {entry.action}
                      </p>

                      <small>
                        {entry.entityType !== null && (
                          <>
                            {entry.entityType}
                            {entry.entityId !== null
                              ? ` ${shortEntityId(entry.entityId)}`
                              : ""}
                            {" · "}
                          </>
                        )}

                        <time
                          dateTime={entry.occurredAt}
                          title={new Date(entry.occurredAt).toLocaleString()}
                        >
                          {timeLabel(entry.occurredAt)} ·{" "}
                          {formatRelativeTime(entry.occurredAt)}
                        </time>
                      </small>
                    </div>

                    {entry.actorRole !== null && (
                      <span
                        className={`role-chip role-chip--${entry.actorRole.toLowerCase()}`}
                      >
                        {roleLabel(entry.actorRole)}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          ))}

          {moreError !== null && (
            <p className="login-error" role="alert">
              {moreError}
            </p>
          )}

          {nextBefore !== null && (
            <button
              type="button"
              className="drawer__more"
              disabled={moreBusy}
              onClick={loadMore}
            >
              {moreBusy ? "Loading…" : "Load older activity"}
            </button>
          )}
        </div>
      </section>
    </div>
  );
}
