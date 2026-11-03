export type LiveStreamState = "off" | "connecting" | "live" | "reconnecting";

export type LiveAlertUpdate = {
  newlyTriggeredCount: number;
  newAlertCount: number;
  evaluatedAt: string;
};

export type LiveFeedUpdate = {
  liveFeedCount: number;
  refreshedAt: string;
};

export type AutomationStatus = {
  enabled: boolean;
  evaluatesAlerts: boolean;
  intervalSeconds: number;
  lastRunAt: string | null;
  lastRunSucceeded: boolean;
  lastPointCount: number;
  lastNewAlertCount: number;
  connectedClients: number;
};

export type LiveStreamMessage = {
  event: string;
  data: string;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8087/api/v1";

const MAX_BUFFER_LENGTH = 1_000_000;
const IDLE_TIMEOUT_MS = 70_000;
const MAX_BACKOFF_MS = 30_000;

export function parseSseChunks(buffer: string): {
  messages: LiveStreamMessage[];
  rest: string;
} {
  const endsWithCarriageReturn = buffer.endsWith("\r");
  const body = endsWithCarriageReturn ? buffer.slice(0, -1) : buffer;
  const blocks = body.replace(/\r\n?/g, "\n").split("\n\n");
  const tail = blocks.pop() ?? "";
  const messages: LiveStreamMessage[] = [];

  for (const block of blocks) {
    let event = "message";
    const data: string[] = [];

    for (const line of block.split("\n")) {
      if (line === "" || line.startsWith(":")) {
        continue;
      }

      const separator = line.indexOf(":");
      const field = separator === -1 ? line : line.slice(0, separator);
      let value = separator === -1 ? "" : line.slice(separator + 1);

      if (value.startsWith(" ")) {
        value = value.slice(1);
      }

      if (field === "event") {
        event = value;
      } else if (field === "data") {
        data.push(value);
      }
    }

    if (data.length > 0) {
      messages.push({ event, data: data.join("\n") });
    }
  }

  const rest = endsWithCarriageReturn ? `${tail}\r` : tail;

  return {
    messages,
    rest: rest.length > MAX_BUFFER_LENGTH ? "" : rest,
  };
}

export function backoffDelay(attempt: number, random: number): number {
  const base = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** Math.min(attempt, 5));

  return Math.round(base + random * 500);
}

type OpenLiveStreamOptions = {
  url?: string;
  onMessage: (message: LiveStreamMessage) => void;
  onState: (state: LiveStreamState) => void;
  idleTimeoutMs?: number;
  fetchImpl?: typeof fetch;
};

function waitFor(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve();
      return;
    }

    const finish = () => {
      window.clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };

    const timer = window.setTimeout(finish, milliseconds);

    signal.addEventListener("abort", finish, { once: true });
  });
}

export function openLiveStream(options: OpenLiveStreamOptions): () => void {
  const url = options.url ?? `${API_BASE_URL}/stream`;
  const idleTimeoutMs = options.idleTimeoutMs ?? IDLE_TIMEOUT_MS;
  const request: typeof fetch =
    options.fetchImpl ?? ((input, init) => fetch(input, init));
  const lifetime = new AbortController();

  const run = async () => {
    let attempt = 0;

    await Promise.resolve();

    while (!lifetime.signal.aborted) {
      options.onState(attempt === 0 ? "connecting" : "reconnecting");

      const session = new AbortController();
      const abortSession = () => session.abort();
      let idleTimer: number | undefined;

      lifetime.signal.addEventListener("abort", abortSession, { once: true });

      const armIdleTimer = () => {
        window.clearTimeout(idleTimer);
        idleTimer = window.setTimeout(abortSession, idleTimeoutMs);
      };

      try {
        const response = await request(url, {
          headers: { Accept: "text/event-stream" },
          cache: "no-store",
          signal: session.signal,
        });

        if (!response.ok || response.body === null) {
          throw new Error(`Stream refused with status ${response.status}.`);
        }

        armIdleTimer();

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        for (;;) {
          const { done, value } = await reader.read();

          if (done) {
            break;
          }

          armIdleTimer();

          buffer += decoder.decode(value, { stream: true });

          const parsed = parseSseChunks(buffer);

          buffer = parsed.rest;

          for (const message of parsed.messages) {
            if (message.event === "ready") {
              attempt = 0;
              options.onState("live");
              continue;
            }

            options.onMessage(message);
          }
        }
      } catch {
        if (lifetime.signal.aborted) {
          break;
        }
      } finally {
        window.clearTimeout(idleTimer);
        lifetime.signal.removeEventListener("abort", abortSession);
      }

      if (lifetime.signal.aborted) {
        break;
      }

      attempt += 1;

      options.onState("reconnecting");

      await waitFor(backoffDelay(attempt, Math.random()), lifetime.signal);
    }
  };

  void run();

  return () => {
    lifetime.abort();
  };
}

export async function fetchAutomationStatus(
  signal?: AbortSignal,
): Promise<AutomationStatus> {
  const response = await fetch(`${API_BASE_URL}/automation/status`, {
    cache: "no-store",
    signal,
  });

  if (!response.ok) {
    throw new Error("Unable to load background monitoring status.");
  }

  return (await response.json()) as AutomationStatus;
}

export function parseLiveAlertUpdate(data: string): LiveAlertUpdate | null {
  try {
    const parsed = JSON.parse(data) as Partial<LiveAlertUpdate>;

    if (
      typeof parsed.newlyTriggeredCount !== "number"
      || typeof parsed.newAlertCount !== "number"
    ) {
      return null;
    }

    return {
      newlyTriggeredCount: parsed.newlyTriggeredCount,
      newAlertCount: parsed.newAlertCount,
      evaluatedAt:
        typeof parsed.evaluatedAt === "string" ? parsed.evaluatedAt : "",
    };
  } catch {
    return null;
  }
}
