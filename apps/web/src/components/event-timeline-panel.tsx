"use client";

import { ChevronRight, Clock3, X } from "lucide-react";

import { useMemo, useRef, useState } from "react";

import { HazardIcon } from "@/components/hazard-icon";

import {
  HAZARD_KINDS,
  HAZARD_LABELS,
  HAZARD_PLURAL_LABELS,
  groupByDay,
  type HazardKind,
  type UnifiedHazardEvent,
} from "@/lib/hazard-events";

import { useDialogBehavior } from "@/lib/use-dialog-behavior";

type EventTimelinePanelProps = {
  open: boolean;
  events: UnifiedHazardEvent[];
  periodLabel: string;
  onClose: () => void;
  onSelect: (event: UnifiedHazardEvent) => void;
};

type TimelineFilter = "all" | HazardKind;

const PAGE_SIZE = 60;

const clockFormat = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
});

function clockOf(isoValue: string): string {
  const date = new Date(isoValue);

  return Number.isNaN(date.getTime()) ? "--:--" : clockFormat.format(date);
}

export function EventTimelinePanel({
  open,
  events,
  periodLabel,
  onClose,
  onSelect,
}: EventTimelinePanelProps) {
  const [filter, setFilter] = useState<TimelineFilter>("all");
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  const counts = useMemo(() => {
    const next: Record<HazardKind, number> = {
      earthquakes: 0,
      volcanoes: 0,
      wildfires: 0,
      "severe-storms": 0,
    };

    for (const event of events) {
      next[event.kind] += 1;
    }

    return next;
  }, [events]);

  const filtered = useMemo(
    () =>
      filter === "all"
        ? events
        : events.filter((event) => event.kind === filter),
    [events, filter],
  );

  const visible = useMemo(
    () => filtered.slice(0, visibleCount),
    [filtered, visibleCount],
  );

  const groups = useMemo(() => groupByDay(visible), [visible]);

  if (!open) {
    return null;
  }

  const remaining = filtered.length - visible.length;

  const chooseFilter = (next: TimelineFilter) => {
    setFilter(next);
    setVisibleCount(PAGE_SIZE);
  };

  return (
    <div className="drawer-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close timeline"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="drawer drawer--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-timeline-title"
        tabIndex={-1}
      >
        <header className="drawer__header">
          <div>
            <span className="panel-kicker">EVENT TIMELINE</span>

            <h2 id="event-timeline-title">All-hazard timeline</h2>

            <p>
              Every event on the monitor in one chronological feed, newest
              first. Earthquakes follow the observation filter ({periodLabel}).
            </p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close timeline"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div
          className="drawer__toolbar"
          role="group"
          aria-label="Filter by hazard"
        >
          <button
            type="button"
            className="filter-chip"
            aria-pressed={filter === "all"}
            onClick={() => chooseFilter("all")}
          >
            All
            <small>{events.length}</small>
          </button>

          {HAZARD_KINDS.map((kind) => (
            <button
              key={kind}
              type="button"
              className="filter-chip"
              data-hazard={kind}
              aria-pressed={filter === kind}
              onClick={() => chooseFilter(kind)}
            >
              <HazardIcon kind={kind} size={14} />
              {HAZARD_PLURAL_LABELS[kind]}
              <small>{counts[kind]}</small>
            </button>
          ))}
        </div>

        <div className="drawer__body">
          {filtered.length === 0 ? (
            <div className="drawer__empty">
              <Clock3 size={28} aria-hidden="true" />

              <strong>No events to show</strong>

              <span>
                {events.length === 0
                  ? "The feeds returned no events for the current filters."
                  : "No events match this hazard filter."}
              </span>
            </div>
          ) : (
            groups.map((group) => (
              <section
                key={group.dayKey}
                className="etl-day"
                aria-label={group.label}
              >
                <h3 className="etl-day__label">
                  {group.label}
                  <span>
                    {group.items.length}
                    {group.items.length === 1 ? " event" : " events"}
                  </span>
                </h3>

                <ol className="etl-list">
                  {group.items.map((event) => (
                    <li key={event.key}>
                      <button
                        type="button"
                        className="etl-item"
                        data-hazard={event.kind}
                        onClick={() => onSelect(event)}
                      >
                        <span className="etl-item__time">
                          {clockOf(event.occurredAt)}
                        </span>

                        <span className="etl-item__marker">
                          <HazardIcon kind={event.kind} size={15} />
                        </span>

                        <span className="etl-item__content">
                          <strong>{event.title}</strong>

                          <span>
                            {HAZARD_LABELS[event.kind]}
                            {" · "}
                            {event.detail}
                          </span>
                        </span>

                        <ChevronRight size={15} aria-hidden="true" />
                      </button>
                    </li>
                  ))}
                </ol>
              </section>
            ))
          )}

          {remaining > 0 && (
            <button
              type="button"
              className="drawer__more"
              onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
            >
              Show {Math.min(PAGE_SIZE, remaining)} more
              {" · "}
              {remaining} remaining
            </button>
          )}
        </div>
      </section>
    </div>
  );
}
