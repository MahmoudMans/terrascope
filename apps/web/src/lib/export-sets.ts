import { fetchActivity, type ActivityEntry } from "@/lib/activity";

import {
  buildExport,
  type ExportDataset,
  type ExportFile,
  type ExportFormat,
} from "@/lib/export";

import type { EarthquakeEvent, EarthquakeFeedResponse } from "@/types/earthquake";

import type {
  IncidentCase,
  IncidentTimelineEvent,
} from "@/types/incidents";

import type {
  NaturalHazardEvent,
  NaturalHazardFeedResponse,
} from "@/types/natural-hazard";

import type { VolcanoEvent, VolcanoFeedResponse } from "@/types/volcano";

import type { WatchlistEntry } from "@/types/watchlist";

export type FeedKind =
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

export type FeedSources = {
  earthquakes: EarthquakeFeedResponse | null;
  volcanoes: VolcanoFeedResponse | null;
  wildfires: NaturalHazardFeedResponse | null;
  "severe-storms": NaturalHazardFeedResponse | null;
};

export const FEED_NOUNS: Record<FeedKind, string> = {
  earthquakes: "earthquake",
  volcanoes: "volcano event",
  wildfires: "wildfire",
  "severe-storms": "storm",
};

export const ACTIVITY_EXPORT_CAP = 5000;

const ACTIVITY_PAGE_SIZE = 200;

export const EARTHQUAKE_DATASET: ExportDataset<EarthquakeEvent> = {
  name: "earthquakes",
  columns: [
    { header: "id", value: (item) => item.id },
    { header: "magnitude", value: (item) => item.magnitude },
    { header: "place", value: (item) => item.place },
    { header: "occurred_at", value: (item) => item.occurredAt },
    { header: "updated_at", value: (item) => item.updatedAt },
    { header: "latitude", value: (item) => item.latitude },
    { header: "longitude", value: (item) => item.longitude },
    { header: "depth_km", value: (item) => item.depthKm },
    { header: "significance", value: (item) => item.significance },
    { header: "felt_reports", value: (item) => item.feltReports },
    { header: "tsunami_potential", value: (item) => item.tsunamiPotential },
    { header: "alert_level", value: (item) => item.alertLevel },
    { header: "review_status", value: (item) => item.reviewStatus },
    { header: "detail_url", value: (item) => item.detailUrl },
  ],
};

export const VOLCANO_DATASET: ExportDataset<VolcanoEvent> = {
  name: "volcanoes",
  columns: [
    { header: "id", value: (item) => item.id },
    { header: "title", value: (item) => item.title },
    { header: "description", value: (item) => item.description },
    { header: "observed_at", value: (item) => item.observedAt },
    { header: "latitude", value: (item) => item.latitude },
    { header: "longitude", value: (item) => item.longitude },
    { header: "observations", value: (item) => item.observationCount },
    { header: "sources", value: (item) => item.sourceCount },
    { header: "status", value: (item) => item.status },
    { header: "primary_source", value: (item) => item.primarySource },
    { header: "source_url", value: (item) => item.sourceUrl },
    { header: "detail_url", value: (item) => item.detailUrl },
  ],
};

function hazardColumns(): ExportDataset<NaturalHazardEvent>["columns"] {
  return [
    { header: "id", value: (item) => item.id },
    { header: "hazard_type", value: (item) => item.hazardType },
    { header: "title", value: (item) => item.title },
    { header: "description", value: (item) => item.description },
    { header: "observed_at", value: (item) => item.observedAt },
    { header: "latitude", value: (item) => item.latitude },
    { header: "longitude", value: (item) => item.longitude },
    { header: "magnitude_value", value: (item) => item.magnitudeValue },
    { header: "magnitude_unit", value: (item) => item.magnitudeUnit },
    {
      header: "magnitude_description",
      value: (item) => item.magnitudeDescription,
    },
    { header: "observations", value: (item) => item.observationCount },
    { header: "sources", value: (item) => item.sourceCount },
    { header: "status", value: (item) => item.status },
    { header: "primary_source", value: (item) => item.primarySource },
    { header: "source_url", value: (item) => item.sourceUrl },
    { header: "detail_url", value: (item) => item.detailUrl },
  ];
}

export const WILDFIRE_DATASET: ExportDataset<NaturalHazardEvent> = {
  name: "wildfires",
  columns: hazardColumns(),
};

export const STORM_DATASET: ExportDataset<NaturalHazardEvent> = {
  name: "severe-storms",
  columns: hazardColumns(),
};

export const INCIDENT_DATASET: ExportDataset<IncidentCase> = {
  name: "incidents",
  columns: [
    { header: "id", value: (item) => item.id },
    { header: "title", value: (item) => item.title },
    { header: "hazard_type", value: (item) => item.hazardType },
    { header: "severity", value: (item) => item.severity },
    { header: "status", value: (item) => item.status },
    { header: "owner", value: (item) => item.owner },
    { header: "opened_at", value: (item) => item.openedAt },
    { header: "updated_at", value: (item) => item.updatedAt },
    { header: "closed_at", value: (item) => item.closedAt },
    { header: "latitude", value: (item) => item.latitude },
    { header: "longitude", value: (item) => item.longitude },
    { header: "timeline_events", value: (item) => item.timelineEventCount },
    { header: "source_event_id", value: (item) => item.sourceEventId },
    { header: "alert_event_id", value: (item) => item.alertEventId },
    { header: "description", value: (item) => item.description },
  ],
};

export const TIMELINE_DATASET: ExportDataset<IncidentTimelineEvent> = {
  name: "incident-timeline",
  columns: [
    { header: "created_at", value: (item) => item.createdAt },
    { header: "event_type", value: (item) => item.eventType },
    { header: "message", value: (item) => item.message },
    { header: "incident_id", value: (item) => item.incidentId },
    { header: "id", value: (item) => item.id },
  ],
};

export const WATCHLIST_DATASET: ExportDataset<WatchlistEntry> = {
  name: "saved-events",
  columns: [
    { header: "id", value: (item) => item.id },
    { header: "priority", value: (item) => item.priority },
    { header: "hazard_type", value: (item) => item.hazardType },
    { header: "title", value: (item) => item.title },
    { header: "description", value: (item) => item.description },
    { header: "observed_at", value: (item) => item.observedAt },
    { header: "latitude", value: (item) => item.latitude },
    { header: "longitude", value: (item) => item.longitude },
    { header: "magnitude_value", value: (item) => item.magnitudeValue },
    { header: "magnitude_unit", value: (item) => item.magnitudeUnit },
    { header: "source_provider", value: (item) => item.sourceProvider },
    { header: "source_url", value: (item) => item.sourceUrl },
    { header: "source_event_id", value: (item) => item.sourceEventId },
    { header: "notes", value: (item) => item.notes },
    { header: "saved_at", value: (item) => item.createdAt },
    { header: "updated_at", value: (item) => item.updatedAt },
  ],
};

export const ACTIVITY_DATASET: ExportDataset<ActivityEntry> = {
  name: "activity",
  columns: [
    { header: "occurred_at", value: (item) => item.occurredAt },
    { header: "actor_username", value: (item) => item.actorUsername },
    { header: "actor_name", value: (item) => item.actorDisplayName },
    { header: "actor_role", value: (item) => item.actorRole },
    { header: "action", value: (item) => item.action },
    { header: "entity_type", value: (item) => item.entityType },
    { header: "entity_id", value: (item) => item.entityId },
    { header: "method", value: (item) => item.method },
    { header: "id", value: (item) => item.id },
  ],
};

export function exportFeed(
  kind: FeedKind,
  sources: FeedSources,
  format: ExportFormat,
  now: Date = new Date(),
): ExportFile | null {
  if (kind === "earthquakes") {
    const feed = sources.earthquakes;

    if (feed === null) {
      return null;
    }

    return buildExport(EARTHQUAKE_DATASET, feed.events, format, {
      now,
      context: {
        source: feed.source,
        live: feed.live,
        generatedAt: feed.generatedAt,
        period: feed.period,
        filters: feed.filters,
        matchedEventCount: feed.matchedEventCount,
      },
    });
  }

  if (kind === "volcanoes") {
    const feed = sources.volcanoes;

    if (feed === null) {
      return null;
    }

    return buildExport(VOLCANO_DATASET, feed.events, format, {
      now,
      context: {
        source: feed.source,
        live: feed.live,
        generatedAt: feed.generatedAt,
        status: feed.status,
        days: feed.days,
        search: feed.search,
        matchedEventCount: feed.matchedEventCount,
      },
    });
  }

  const feed = sources[kind];

  if (feed === null) {
    return null;
  }

  return buildExport(
    kind === "wildfires" ? WILDFIRE_DATASET : STORM_DATASET,
    feed.events,
    format,
    {
      now,
      context: {
        source: feed.source,
        live: feed.live,
        generatedAt: feed.generatedAt,
        status: feed.status,
        days: feed.days,
        search: feed.search,
        matchedEventCount: feed.matchedEventCount,
      },
    },
  );
}

export function exportIncidents(
  incidents: readonly IncidentCase[],
  format: ExportFormat,
  context: Record<string, unknown> = {},
  now: Date = new Date(),
): ExportFile {
  return buildExport(INCIDENT_DATASET, incidents, format, { now, context });
}

export function exportTimeline(
  incident: Pick<IncidentCase, "id" | "title">,
  timeline: readonly IncidentTimelineEvent[],
  format: ExportFormat,
  now: Date = new Date(),
): ExportFile {
  return buildExport(TIMELINE_DATASET, timeline, format, {
    now,
    suffix: incident.id.slice(0, 8),
    context: { incidentId: incident.id, incidentTitle: incident.title },
  });
}

export function exportWatchlist(
  entries: readonly WatchlistEntry[],
  format: ExportFormat,
  now: Date = new Date(),
): ExportFile {
  return buildExport(WATCHLIST_DATASET, entries, format, { now });
}

export type CollectedActivity = {
  entries: ActivityEntry[];
  truncated: boolean;
};

export async function collectActivity(
  actor: string | null,
  cap: number = ACTIVITY_EXPORT_CAP,
): Promise<CollectedActivity> {
  const entries: ActivityEntry[] = [];

  let before: string | null = null;

  while (entries.length < cap) {
    const page = await fetchActivity({
      actor,
      before,
      limit: Math.min(ACTIVITY_PAGE_SIZE, cap - entries.length),
    });

    entries.push(...page.entries);

    if (
      page.nextBefore === null
      || page.nextBefore === before
      || page.entries.length === 0
    ) {
      return { entries, truncated: false };
    }

    before = page.nextBefore;
  }

  return { entries, truncated: true };
}

export async function exportActivity(
  actor: string | null,
  format: ExportFormat,
  now: Date = new Date(),
): Promise<{ file: ExportFile; truncated: boolean }> {
  const { entries, truncated } = await collectActivity(actor);

  const file = buildExport(ACTIVITY_DATASET, entries, format, {
    now,
    suffix: actor ?? undefined,
    context: { actor: actor ?? "everyone", truncated, cap: ACTIVITY_EXPORT_CAP },
  });

  return { file, truncated };
}
