import {
  EarthquakeFeedResponse,
  EarthquakeQuery,
} from "@/types/earthquake";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8087/api/v1";

export async function fetchEarthquakes(
  query: EarthquakeQuery,
  signal?: AbortSignal,
): Promise<EarthquakeFeedResponse> {
  const params = new URLSearchParams({
    period: query.period,
    minMagnitude: query.minMagnitude.toString(),
    maxDepth: query.maxDepth.toString(),
    limit: (query.limit ?? 300).toString(),
    search: query.search ?? "",
    sort: query.sort,
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
      `TerraScope API returned HTTP ${response.status}`,
    );
  }

  return response.json() as Promise<EarthquakeFeedResponse>;
}
