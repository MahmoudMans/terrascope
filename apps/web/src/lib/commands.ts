export type CommandId =
  | "go-timeline"
  | "go-regions"
  | "go-simulations"
  | "go-regional"
  | "go-alerts"
  | "go-situation"
  | "go-saved"
  | "go-activity"
  | "refresh"
  | "search"
  | "toggle-theme"
  | "settings"
  | "export-feed-csv"
  | "export-feed-json"
  | "feed-earthquakes"
  | "feed-volcanoes"
  | "feed-wildfires"
  | "feed-storms"
  | "shortcuts";

export type CommandGroup = "Go to" | "Actions" | "Feeds" | "Help";

export type CommandDefinition = {
  id: CommandId;
  title: string;
  group: CommandGroup;
  keywords: string;
  keys?: string[];
};

export const COMMAND_GROUPS: CommandGroup[] = [
  "Go to",
  "Actions",
  "Feeds",
  "Help",
];

export const COMMANDS: CommandDefinition[] = [
  {
    id: "go-timeline",
    title: "Open timeline",
    group: "Go to",
    keywords: "events history chronology recent",
    keys: ["g", "t"],
  },
  {
    id: "go-regions",
    title: "Open regions",
    group: "Go to",
    keywords: "countries areas places",
    keys: ["g", "r"],
  },
  {
    id: "go-simulations",
    title: "Open simulations",
    group: "Go to",
    keywords: "scenario what if model",
    keys: ["g", "s"],
  },
  {
    id: "go-regional",
    title: "Open regional analysis",
    group: "Go to",
    keywords: "intelligence area risk",
    keys: ["g", "i"],
  },
  {
    id: "go-alerts",
    title: "Open alert center",
    group: "Go to",
    keywords: "rules notifications warnings",
    keys: ["g", "a"],
  },
  {
    id: "go-situation",
    title: "Open situation room",
    group: "Go to",
    keywords: "incidents response command operations",
    keys: ["g", "c"],
  },
  {
    id: "go-saved",
    title: "Open saved events",
    group: "Go to",
    keywords: "watchlist bookmarks",
    keys: ["g", "w"],
  },
  {
    id: "go-activity",
    title: "Open activity log",
    group: "Go to",
    keywords: "audit history changes changed who",
    keys: ["g", "l"],
  },
  {
    id: "refresh",
    title: "Refresh data",
    group: "Actions",
    keywords: "reload update fetch",
    keys: ["r"],
  },
  {
    id: "search",
    title: "Search hazards",
    group: "Actions",
    keywords: "find query",
    keys: ["/"],
  },
  {
    id: "toggle-theme",
    title: "Switch theme",
    group: "Actions",
    keywords: "dark light appearance mode colors",
    keys: ["t"],
  },
  {
    id: "settings",
    title: "Open settings",
    group: "Actions",
    keywords: "preferences options display text size",
    keys: [","],
  },
  {
    id: "export-feed-csv",
    title: "Download current feed as CSV",
    group: "Actions",
    keywords: "export spreadsheet excel save data events",
  },
  {
    id: "export-feed-json",
    title: "Download current feed as JSON",
    group: "Actions",
    keywords: "export save data events api",
  },
  {
    id: "feed-earthquakes",
    title: "Show earthquakes",
    group: "Feeds",
    keywords: "seismic quakes usgs",
    keys: ["1"],
  },
  {
    id: "feed-volcanoes",
    title: "Show volcanoes",
    group: "Feeds",
    keywords: "eruptions volcanic",
    keys: ["2"],
  },
  {
    id: "feed-wildfires",
    title: "Show wildfires",
    group: "Feeds",
    keywords: "fires burning",
    keys: ["3"],
  },
  {
    id: "feed-storms",
    title: "Show severe storms",
    group: "Feeds",
    keywords: "weather cyclone hurricane",
    keys: ["4"],
  },
  {
    id: "shortcuts",
    title: "Keyboard shortcuts",
    group: "Help",
    keywords: "keys help hotkeys",
    keys: ["?"],
  },
];

export const SEQUENCE_PREFIX = "g";

export type ShortcutMatch =
  | { kind: "run"; id: CommandId }
  | { kind: "pending" }
  | { kind: "none" };

export function normalizeKey(key: string, shift: boolean): string | null {
  if (key.length !== 1) {
    return null;
  }

  if (shift && /^[a-z]$/i.test(key)) {
    return null;
  }

  return key.toLowerCase();
}

export function resolveShortcut(
  pending: boolean,
  key: string,
  commands: CommandDefinition[] = COMMANDS,
): ShortcutMatch {
  if (pending) {
    const second = commands.find(
      (command) =>
        command.keys?.length === 2
        && command.keys[0] === SEQUENCE_PREFIX
        && command.keys[1] === key,
    );

    return second === undefined
      ? { kind: "none" }
      : { kind: "run", id: second.id };
  }

  if (key === SEQUENCE_PREFIX) {
    return { kind: "pending" };
  }

  const single = commands.find(
    (command) => command.keys?.length === 1 && command.keys[0] === key,
  );

  return single === undefined
    ? { kind: "none" }
    : { kind: "run", id: single.id };
}

function subsequence(needle: string, haystack: string): boolean {
  let cursor = 0;

  for (const character of haystack) {
    if (character === needle[cursor]) {
      cursor += 1;

      if (cursor === needle.length) {
        return true;
      }
    }
  }

  return false;
}

function scoreToken(
  token: string,
  title: string,
  keywords: string,
): number {
  if (title.startsWith(token)) {
    return 150;
  }

  if (title.split(/\s+/).some((word) => word.startsWith(token))) {
    return 100;
  }

  if (title.includes(token)) {
    return 60;
  }

  if (keywords.split(/\s+/).some((word) => word.startsWith(token))) {
    return 40;
  }

  if (keywords.includes(token)) {
    return 25;
  }

  if (token.length >= 3 && subsequence(token, title)) {
    return 10;
  }

  return 0;
}

export function scoreCommand(
  query: string,
  command: { title: string; keywords: string },
): number {
  const tokens = query.toLowerCase().split(/\s+/).filter(Boolean);

  if (tokens.length === 0) {
    return 1;
  }

  const title = command.title.toLowerCase();
  const keywords = command.keywords.toLowerCase();

  let total = 0;

  for (const token of tokens) {
    const score = scoreToken(token, title, keywords);

    if (score === 0) {
      return 0;
    }

    total += score;
  }

  return total;
}

export function filterCommands<T extends { title: string; keywords: string }>(
  query: string,
  commands: T[],
): T[] {
  if (query.trim() === "") {
    return commands;
  }

  return commands
    .map((command, index) => ({
      command,
      index,
      score: scoreCommand(query, command),
    }))
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .map((entry) => entry.command);
}
