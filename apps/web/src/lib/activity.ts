import { API_BASE_URL, type AuthRole } from "@/lib/auth";

export type ActivityEntry = {
  id: string;
  occurredAt: string;
  actorUsername: string;
  actorDisplayName: string;
  actorRole: AuthRole | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  method: string;
};

export type ActivityPage = {
  entries: ActivityEntry[];
  nextBefore: string | null;
};

export type ActivityQuery = {
  limit?: number;
  before?: string | null;
  actor?: string | null;
  signal?: AbortSignal;
};

export class ActivityRequestError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ActivityRequestError";
    this.status = status;
  }
}

function text(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function parseRole(value: unknown): AuthRole | null {
  return value === "VIEWER" || value === "OPERATOR" || value === "ADMIN"
    ? value
    : null;
}

function parseEntry(value: unknown): ActivityEntry | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }

  const record = value as Record<string, unknown>;

  const id = text(record.id);
  const occurredAt = text(record.occurredAt);
  const actorUsername = text(record.actorUsername);
  const action = text(record.action);

  if (
    id === null
    || occurredAt === null
    || actorUsername === null
    || action === null
  ) {
    return null;
  }

  return {
    id,
    occurredAt,
    actorUsername,
    actorDisplayName: text(record.actorDisplayName) ?? actorUsername,
    actorRole: parseRole(record.actorRole),
    action,
    entityType: text(record.entityType),
    entityId: text(record.entityId),
    method: text(record.method) ?? "",
  };
}

export async function fetchActivity(
  query: ActivityQuery = {},
): Promise<ActivityPage> {
  const params = new URLSearchParams();

  params.set("limit", String(query.limit ?? 50));

  if (query.before) {
    params.set("before", query.before);
  }

  if (query.actor) {
    params.set("actor", query.actor);
  }

  const response = await fetch(`${API_BASE_URL}/activity?${params}`, {
    cache: "no-store",
    signal: query.signal,
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    let message = "Unable to load activity.";

    try {
      const body = (await response.json()) as { message?: unknown };

      if (typeof body.message === "string" && body.message.length > 0) {
        message = body.message;
      }
    } catch {
      message = "Unable to load activity.";
    }

    throw new ActivityRequestError(response.status, message);
  }

  const payload = (await response.json()) as {
    entries?: unknown;
    nextBefore?: unknown;
  };

  const entries = Array.isArray(payload.entries)
    ? payload.entries
        .map(parseEntry)
        .filter((entry): entry is ActivityEntry => entry !== null)
    : [];

  return { entries, nextBefore: text(payload.nextBefore) };
}

export function shortEntityId(id: string): string {
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  if (uuid.test(id)) {
    return id.slice(0, 8);
  }

  return id.length > 24 ? `${id.slice(0, 24)}…` : id;
}

export function dayKey(isoValue: string): string {
  const date = new Date(isoValue);

  if (Number.isNaN(date.getTime())) {
    return "unknown";
  }

  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

export function dayLabel(isoValue: string, now: Date = new Date()): string {
  const date = new Date(isoValue);

  if (Number.isNaN(date.getTime())) {
    return "Unknown date";
  }

  const startOf = (value: Date) =>
    new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();

  const days = Math.round((startOf(now) - startOf(date)) / 86_400_000);

  if (days === 0) {
    return "Today";
  }

  if (days === 1) {
    return "Yesterday";
  }

  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: date.getFullYear() === now.getFullYear() ? undefined : "numeric",
  }).format(date);
}

export function timeLabel(isoValue: string): string {
  const date = new Date(isoValue);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
