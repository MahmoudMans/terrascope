import type {
  EarthquakeFeedResponse,
  EarthquakeQuery,
} from "@/types/earthquake";

import type {
  NaturalHazardFeedResponse,
  NaturalHazardQuery,
} from "@/types/natural-hazard";

import type {
  VolcanoFeedResponse,
  VolcanoQuery,
} from "@/types/volcano";

import type {
  WatchlistEntry,
  WatchlistFeedResponse,
  WatchPriority,
  SaveWatchlistEventRequest,
} from "@/types/watchlist";

import type {
  ImpactSimulationRequest,
  ImpactSimulationRunResponse,
  SimulationHistoryResponse,
} from "@/types/simulation";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8087/api/v1";

export async function fetchEarthquakes(
  query: EarthquakeQuery,
  signal?: AbortSignal,
): Promise<EarthquakeFeedResponse> {
  const params = new URLSearchParams({
    period: query.period,
    minMagnitude:
      query.minMagnitude.toString(),
    maxDepth:
      query.maxDepth.toString(),
    limit:
      (query.limit ?? 300).toString(),
    search:
      query.search ?? "",
    sort:
      query.sort,
  });

  const response = await fetch(
    `${API_BASE_URL}/earthquakes?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope earthquake API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EarthquakeFeedResponse>;
}

export async function fetchVolcanoes(
  query: VolcanoQuery,
  signal?: AbortSignal,
): Promise<VolcanoFeedResponse> {
  const params = new URLSearchParams({
    status: query.status,
    days: query.days.toString(),
    limit:
      (query.limit ?? 120).toString(),
    search:
      query.search ?? "",
  });

  const response = await fetch(
    `${API_BASE_URL}/volcanoes?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope volcano API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<VolcanoFeedResponse>;
}

export async function fetchNaturalHazards(
  query: NaturalHazardQuery,
  signal?: AbortSignal,
): Promise<NaturalHazardFeedResponse> {
  const params = new URLSearchParams({
    type: query.type,
    status: query.status ?? "open",
    days:
      (query.days ?? 90).toString(),
    limit:
      (query.limit ?? 120).toString(),
    search:
      query.search ?? "",
  });

  const response = await fetch(
    `${API_BASE_URL}/hazards/events?${params.toString()}`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope ${query.type} API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<NaturalHazardFeedResponse>;
}

export async function fetchWatchlist(
  signal?: AbortSignal,
): Promise<WatchlistFeedResponse> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope watchlist API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistFeedResponse>;
}

export async function saveWatchlistEvent(
  request: SaveWatchlistEventRequest,
): Promise<WatchlistEntry> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to save event. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistEntry>;
}

export async function updateWatchlistPriority(
  id: string,
  priority: WatchPriority,
): Promise<WatchlistEntry> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist/${id}/priority`,
    {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        priority,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to update watch priority. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<WatchlistEntry>;
}

export async function deleteWatchlistEntry(
  id: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/watchlist/${id}`,
    {
      method: "DELETE",
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to delete watchlist entry. HTTP ${response.status}`,
    );
  }
}

export async function fetchSimulationHistory(
  signal?: AbortSignal,
): Promise<SimulationHistoryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/simulations`,
    {
      method: "GET",
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `TerraScope simulation history returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<SimulationHistoryResponse>;
}

export async function runImpactSimulation(
  request: ImpactSimulationRequest,
): Promise<ImpactSimulationRunResponse> {
  const response = await fetch(
    `${API_BASE_URL}/simulations/run`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Unable to run simulation. HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<ImpactSimulationRunResponse>;
}
