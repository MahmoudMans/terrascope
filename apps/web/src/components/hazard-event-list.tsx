"use client";

import {
  ChevronRight,
  CloudLightning,
  Flame,
} from "lucide-react";

import {
  formatRelativeTime,
} from "@/lib/format";

import type {
  NaturalHazardEvent,
} from "@/types/natural-hazard";

type HazardEventListProps = {
  events: NaturalHazardEvent[];
  selectedEvent:
    NaturalHazardEvent | null;
  onSelect:
    (event: NaturalHazardEvent) => void;
};

export function HazardEventList({
  events,
  selectedEvent,
  onSelect,
}: HazardEventListProps) {
  if (events.length === 0) {
    return (
      <div className="event-list-empty">
        <div className="event-list-empty__pulse" />

        <strong>
          No matching events
        </strong>

        <span>
          NASA EONET currently has no
          events matching these filters.
        </span>
      </div>
    );
  }

  return (
    <div className="event-list">
      {events.map((event) => {
        const wildfire =
          event.hazardType
          === "wildfires";

        return (
          <button
            className={
              selectedEvent?.id
                === event.id
                ? "event-row event-row--selected"
                : "event-row"
            }
            key={`${event.hazardType}-${event.id}`}
            type="button"
            onClick={() =>
              onSelect(event)
            }
          >
            <div
              className={
                wildfire
                  ? "hazard-event-badge hazard-event-badge--fire"
                  : "hazard-event-badge hazard-event-badge--storm"
              }
            >
              {wildfire
                ? (
                  <Flame
                    size={18}
                    strokeWidth={1.7}
                  />
                )
                : (
                  <CloudLightning
                    size={18}
                    strokeWidth={1.7}
                  />
                )
              }
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

                {event.magnitudeValue
                  !== null && (
                  <>
                    <span className="meta-separator">
                      •
                    </span>

                    <span>
                      {event.magnitudeValue}
                      {event.magnitudeUnit
                        ? ` ${event.magnitudeUnit}`
                        : ""}
                    </span>
                  </>
                )}

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
        );
      })}
    </div>
  );
}
