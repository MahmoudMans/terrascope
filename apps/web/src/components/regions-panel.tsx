"use client";

import { ChevronRight, Globe2, Radar, X } from "lucide-react";

import { useMemo, useRef, useState } from "react";

import { HazardIcon } from "@/components/hazard-icon";

import {
  HAZARD_KINDS,
  HAZARD_LABELS,
  summarizeRegions,
  timestampOf,
  type HazardRegion,
  type RegionSummary,
  type UnifiedHazardEvent,
} from "@/lib/hazard-events";

import { formatRelativeTime } from "@/lib/format";

import { useDialogBehavior } from "@/lib/use-dialog-behavior";

type RegionsPanelProps = {
  open: boolean;
  events: UnifiedHazardEvent[];
  onClose: () => void;
  onSelect: (event: UnifiedHazardEvent) => void;
  onAnalyze: (region: HazardRegion) => void;
};

type RegionSort = "events" | "strongest" | "recent";

const SORT_CHOICES: Array<{ value: RegionSort; label: string }> = [
  { value: "events", label: "Most events" },
  { value: "strongest", label: "Strongest quake" },
  { value: "recent", label: "Most recent" },
];

const PREVIEW_COUNT = 6;

const clockFormat = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

function clockOf(isoValue: string): string {
  const date = new Date(isoValue);

  return Number.isNaN(date.getTime()) ? "Unknown" : clockFormat.format(date);
}

function compare(
  sort: RegionSort,
  left: RegionSummary,
  right: RegionSummary,
): number {
  if (sort === "strongest") {
    const difference =
      (right.strongestMagnitude ?? -1) - (left.strongestMagnitude ?? -1);

    if (difference !== 0) {
      return difference;
    }
  }

  if (sort === "recent") {
    const difference =
      timestampOf(right.latestAt ?? "") - timestampOf(left.latestAt ?? "");

    if (difference !== 0) {
      return difference;
    }
  }

  const byCount = right.events.length - left.events.length;

  return byCount !== 0
    ? byCount
    : left.region.name.localeCompare(right.region.name);
}

export function RegionsPanel({
  open,
  events,
  onClose,
  onSelect,
  onAnalyze,
}: RegionsPanelProps) {
  const [sort, setSort] = useState<RegionSort>("events");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  const summaries = useMemo(() => summarizeRegions(events), [events]);

  const sorted = useMemo(
    () => [...summaries].sort((left, right) => compare(sort, left, right)),
    [summaries, sort],
  );

  if (!open) {
    return null;
  }

  const largest = Math.max(
    1,
    ...summaries.map((summary) => summary.events.length),
  );

  return (
    <div className="drawer-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close regions"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="drawer drawer--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="regions-title"
        tabIndex={-1}
      >
        <header className="drawer__header">
          <div>
            <span className="panel-kicker">REGIONAL OVERVIEW</span>

            <h2 id="regions-title">Hazard activity by region</h2>

            <p>
              {summaries.length}
              {summaries.length === 1 ? " region" : " regions"} with activity
              {" · "}
              {events.length}
              {events.length === 1 ? " event" : " events"}. Regions are
              approximate geographic areas.
            </p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close regions"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div
          className="drawer__toolbar"
          role="group"
          aria-label="Sort regions"
        >
          <div className="segmented-control">
            {SORT_CHOICES.map((choice) => (
              <button
                key={choice.value}
                type="button"
                aria-pressed={sort === choice.value}
                className={
                  sort === choice.value
                    ? "segmented-control__item segmented-control__item--active"
                    : "segmented-control__item"
                }
                onClick={() => setSort(choice.value)}
              >
                {choice.label}
              </button>
            ))}
          </div>
        </div>

        <div className="drawer__body">
          {sorted.length === 0 ? (
            <div className="drawer__empty">
              <Globe2 size={28} aria-hidden="true" />

              <strong>No regional activity</strong>

              <span>
                The feeds returned no events for the current filters.
              </span>
            </div>
          ) : (
            <div className="region-list">
              {sorted.map((summary) => {
                const expanded = expandedId === summary.region.id;
                const share = (summary.events.length / largest) * 100;
                const bodyId = `region-body-${summary.region.id}`;

                return (
                  <article
                    key={summary.region.id}
                    className={
                      expanded
                        ? "region-card region-card--open"
                        : "region-card"
                    }
                  >
                    <button
                      type="button"
                      className="region-card__head"
                      aria-expanded={expanded}
                      aria-controls={bodyId}
                      onClick={() =>
                        setExpandedId(expanded ? null : summary.region.id)
                      }
                    >
                      <span className="region-card__title">
                        <strong>{summary.region.name}</strong>

                        <small>
                          {summary.events.length}
                          {summary.events.length === 1 ? " event" : " events"}
                          {summary.strongestMagnitude !== null
                            ? ` · strongest M${summary.strongestMagnitude.toFixed(1)}`
                            : ""}
                          {summary.latestAt !== null
                            ? ` · latest ${formatRelativeTime(summary.latestAt)}`
                            : ""}
                        </small>
                      </span>

                      <span className="region-card__counts">
                        {HAZARD_KINDS.filter(
                          (kind) => summary.counts[kind] > 0,
                        ).map((kind) => (
                          <span
                            key={kind}
                            className="region-chip"
                            data-hazard={kind}
                            title={HAZARD_LABELS[kind]}
                          >
                            <HazardIcon kind={kind} size={13} />
                            {summary.counts[kind]}
                          </span>
                        ))}
                      </span>

                      <ChevronRight
                        size={16}
                        className="region-card__chevron"
                        aria-hidden="true"
                      />
                    </button>

                    <div className="region-card__bar" aria-hidden="true">
                      <span style={{ width: `${share}%` }} />
                    </div>

                    {expanded && (
                      <div id={bodyId} className="region-card__body">
                        <ul className="region-events">
                          {summary.events
                            .slice(0, PREVIEW_COUNT)
                            .map((event) => (
                              <li key={event.key}>
                                <button
                                  type="button"
                                  data-hazard={event.kind}
                                  onClick={() => onSelect(event)}
                                >
                                  <span className="region-events__icon">
                                    <HazardIcon kind={event.kind} size={14} />
                                  </span>

                                  <span className="region-events__text">
                                    <strong>{event.title}</strong>

                                    <small>
                                      {clockOf(event.occurredAt)}
                                      {" · "}
                                      {event.detail}
                                    </small>
                                  </span>
                                </button>
                              </li>
                            ))}
                        </ul>

                        <div className="region-card__actions">
                          <small>
                            {summary.events.length > PREVIEW_COUNT
                              ? `+${summary.events.length - PREVIEW_COUNT} more in the timeline`
                              : "All events shown"}
                          </small>

                          <button
                            type="button"
                            className="drawer-action"
                            onClick={() => onAnalyze(summary.region)}
                          >
                            <Radar size={14} aria-hidden="true" />
                            Analyze region
                          </button>
                        </div>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
