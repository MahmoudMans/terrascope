"use client";

import {
  ChevronRight,
  Waves,
} from "lucide-react";

import {
  formatDepth,
  formatRelativeTime,
  severityClass,
  severityLabel,
} from "@/lib/format";

import type {
  EarthquakeEvent,
} from "@/types/earthquake";

type EventListProps = {
  events: EarthquakeEvent[];
  selectedEvent: EarthquakeEvent | null;
  onSelect: (event: EarthquakeEvent) => void;
};

export function EventList({
  events,
  selectedEvent,
  onSelect,
}: EventListProps) {
  if (events.length === 0) {
    return (
      <div className="event-list-empty">
        <div className="event-list-empty__pulse" />
        <strong>No matching events</strong>
        <span>
          Try reducing the magnitude threshold or
          changing the selected time period.
        </span>
      </div>
    );
  }

  return (
    <div className="event-list">
      {events.map((event) => {
        const severity =
          severityClass(event.magnitude);

        return (
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
            <div
              className={`magnitude-badge magnitude-badge--${severity}`}
            >
              <span>M</span>
              <strong>
                {event.magnitude.toFixed(1)}
              </strong>
            </div>

            <div className="event-row__content">
              <div className="event-row__heading">
                <strong>{event.place}</strong>

                {event.tsunamiPotential && (
                  <span
                    className="tsunami-marker"
                    title="Tsunami potential"
                  >
                    <Waves size={13} />
                  </span>
                )}
              </div>

              <div className="event-row__meta">
                <span>
                  {severityLabel(
                    event.magnitude,
                  )}
                </span>

                <span className="meta-separator">
                  •
                </span>

                <span>
                  {formatDepth(
                    event.depthKm,
                  )}
                </span>

                <span className="meta-separator">
                  •
                </span>

                <span>
                  {formatRelativeTime(
                    event.occurredAt,
                  )}
                </span>
              </div>
            </div>

            <ChevronRight
              className="event-row__arrow"
              size={16}
            />
          </button>
        );
      })}
    </div>
  );
}
