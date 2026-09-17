export function formatMagnitude(
  magnitude: number,
): string {
  return `M${magnitude.toFixed(1)}`;
}

export function formatDepth(
  depthKm: number,
): string {
  return `${depthKm.toFixed(1)} km`;
}

export function formatCoordinate(
  value: number,
  positive: string,
  negative: string,
): string {
  const direction = value >= 0
    ? positive
    : negative;

  return `${Math.abs(value).toFixed(2)}° ${direction}`;
}

export function formatRelativeTime(
  isoValue: string,
): string {
  const timestamp = new Date(isoValue).getTime();

  if (Number.isNaN(timestamp)) {
    return "Unknown time";
  }

  const differenceSeconds = Math.max(
    0,
    Math.floor(
      (Date.now() - timestamp) / 1000,
    ),
  );

  if (differenceSeconds < 60) {
    return `${differenceSeconds}s ago`;
  }

  const minutes = Math.floor(
    differenceSeconds / 60,
  );

  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}h ago`;
  }

  const days = Math.floor(hours / 24);

  return `${days}d ago`;
}

export function formatDateTime(
  isoValue: string,
): string {
  const date = new Date(isoValue);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat(
    "en-GB",
    {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      timeZoneName: "short",
    },
  ).format(date);
}

export function severityLabel(
  magnitude: number,
): string {
  if (magnitude >= 7) {
    return "Major";
  }

  if (magnitude >= 6) {
    return "Strong";
  }

  if (magnitude >= 5) {
    return "Moderate";
  }

  if (magnitude >= 4) {
    return "Light";
  }

  return "Minor";
}

export function severityClass(
  magnitude: number,
): string {
  if (magnitude >= 7) {
    return "critical";
  }

  if (magnitude >= 6) {
    return "high";
  }

  if (magnitude >= 5) {
    return "medium";
  }

  return "low";
}
