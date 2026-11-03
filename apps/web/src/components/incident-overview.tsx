"use client";

import {
  Activity,
  BadgeAlert,
  CheckCircle2,
  MessageSquarePlus,
} from "lucide-react";

import {
  useState,
  type FormEvent,
} from "react";

import { ExportButtons } from "@/components/export-buttons";

import {
  IncidentCommandPulse,
} from "@/components/incident-command-pulse";

import { exportTimeline } from "@/lib/export-sets";

import { formatRelativeTime } from "@/lib/format";

import type {
  IncidentDetail,
} from "@/types/incidents";

type IncidentOverviewProps = {
  detail: IncidentDetail;
  busy: boolean;
  onAddNote: (message: string) => void;
  onTimelineChanged: () => void;
};

const absoluteTime = new Intl.DateTimeFormat("en", {
  dateStyle: "medium",
  timeStyle: "short",
});

function eventIcon(eventType: string) {
  if (eventType === "NOTE_ADDED") {
    return <MessageSquarePlus size={13} aria-hidden="true" />;
  }

  if (eventType === "STATUS_CHANGED") {
    return <CheckCircle2 size={13} aria-hidden="true" />;
  }

  return <Activity size={13} aria-hidden="true" />;
}

export function IncidentOverview({
  detail,
  busy,
  onAddNote,
  onTimelineChanged,
}: IncidentOverviewProps) {
  const [noteDraft, setNoteDraft] = useState("");

  const handleNote = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const message = noteDraft.trim();

    if (message === "") {
      return;
    }

    setNoteDraft("");
    onAddNote(message);
  };

  return (
    <div className="incident-overview">
      <IncidentCommandPulse
        incidentId={detail.incident.id}
        incidentStatus={detail.incident.status}
        onTimelineChanged={onTimelineChanged}
      />

      <form className="situation-note-form" onSubmit={handleNote}>
        <label>
          <span>Operational note</span>

          <textarea
            maxLength={2000}
            rows={3}
            value={noteDraft}
            placeholder="Add an operational update, decision or observation…"
            onChange={(event) => setNoteDraft(event.target.value)}
          />
        </label>

        <button
          type="submit"
          disabled={busy || noteDraft.trim() === ""}
        >
          <MessageSquarePlus size={14} aria-hidden="true" />
          Add note
        </button>
      </form>

      <section aria-labelledby="situation-timeline-title">
        <div className="situation-timeline-heading">
          <BadgeAlert size={15} aria-hidden="true" />

          <h4 id="situation-timeline-title">Response timeline</h4>

          <span>{detail.timeline.length} events</span>

          <ExportButtons
            noun="timeline event"
            subject="timeline"
            count={detail.timeline.length}
            onBuild={(format) => ({
              file: exportTimeline(detail.incident, detail.timeline, format),
            })}
          />
        </div>

        <div className="situation-timeline">
          {detail.timeline.length === 0 ? (
            <div className="situation-empty">No timeline events.</div>
          ) : (
            detail.timeline.map((event) => (
              <article key={event.id}>
                <span className="situation-timeline__marker">
                  {eventIcon(event.eventType)}
                </span>

                <div>
                  <span>{event.eventType.replaceAll("_", " ")}</span>

                  <strong>{event.message}</strong>

                  <small title={absoluteTime.format(new Date(event.createdAt))}>
                    {absoluteTime.format(new Date(event.createdAt))}
                    {" · "}
                    {formatRelativeTime(event.createdAt)}
                  </small>
                </div>
              </article>
            ))
          )}
        </div>
      </section>
    </div>
  );
}
