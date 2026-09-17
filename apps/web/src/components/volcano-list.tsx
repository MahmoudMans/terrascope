"use client";

import {
  ChevronRight,
  Mountain,
} from "lucide-react";

import {
  formatRelativeTime,
} from "@/lib/format";

import type {
  VolcanoEvent,
} from "@/types/volcano";

type VolcanoListProps = {
  events: VolcanoEvent[];
  selectedEvent: VolcanoEvent | null;
  onSelect: (event: VolcanoEvent) => void;
};

export function VolcanoList({
  events,
  selectedEvent,
  onSelect,
}: VolcanoListProps) {
  if (events.length === 0) {
    return (
      <div className="event-list-empty">
        <div className="event-list-empty__pulse" />

        <strong>
          No matching volcanic events
        </strong>

        <span>
          NASA EONET currently has no events
          matching these filters.
        </span>
      </div>
    );
  }

  return (
    <div className="event-list">
      {events.map((event) => (
        <button
          className={
            selectedEvent?.id === event.id
              ? "event-row event-row--selected"
              : "event-row"
          }
          key={event.id}
          type="button"
          onClick={() => onSelect(event)}
        >
          <div className="volcano-badge">
            <Mountain
              size={18}
              strokeWidth={1.7}
            />
          </div>

          <div className="event-row__content">
            <div className="event-row__heading">
              <strong>
                {event.title}
              </strong>
            </div>

            <div className="event-row__meta">
              <span>
                {event.primarySource}
              </span>

              <span className="meta-separator">
                •
              </span>

              <span>
                {event.observationCount} obs.
              </span>

              <span className="meta-separator">
                •
              </span>

              <span>
                {formatRelativeTime(
                  event.observedAt,
                )}
              </span>
            </div>
          </div>

          <ChevronRight
            className="event-row__arrow"
            size={16}
          />
        </button>
      ))}
    </div>
  );
}
