export type ExportFormat = "csv" | "json";

export type ExportValue = string | number | boolean | null | undefined;

export type ExportColumn<T> = {
  header: string;
  value: (item: T) => ExportValue;
};

export type ExportDataset<T> = {
  name: string;
  columns: ExportColumn<T>[];
};

export type ExportFile = {
  filename: string;
  mime: string;
  content: string;
  count: number;
  format: ExportFormat;
};

export type ExportOptions = {
  context?: Record<string, unknown>;
  now?: Date;
  suffix?: string;
};

const FORMULA_START = /^[=+\-@\t\r]/;

const MUST_QUOTE = /[",\r\n]/;

const BYTE_ORDER_MARK = String.fromCharCode(0xfeff);

export function csvCell(value: ExportValue): string {
  if (value === null || value === undefined) {
    return "";
  }

  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "";
  }

  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }

  const guarded = FORMULA_START.test(value) ? `'${value}` : value;

  if (MUST_QUOTE.test(guarded) || guarded !== guarded.trim()) {
    return `"${guarded.replaceAll('"', '""')}"`;
  }

  return guarded;
}

export function toCsv<T>(
  columns: ExportColumn<T>[],
  rows: readonly T[],
): string {
  const lines = [columns.map((column) => csvCell(column.header)).join(",")];

  for (const row of rows) {
    lines.push(columns.map((column) => csvCell(column.value(row))).join(","));
  }

  return `${lines.join("\r\n")}\r\n`;
}

export function toJson<T>(
  dataset: string,
  items: readonly T[],
  context: Record<string, unknown>,
  exportedAt: Date,
): string {
  return `${JSON.stringify(
    {
      dataset,
      exportedAt: exportedAt.toISOString(),
      count: items.length,
      context,
      items,
    },
    null,
    2,
  )}\n`;
}

export function exportStamp(now: Date): string {
  return now
    .toISOString()
    .replace(/\.\d+Z$/, "Z")
    .replaceAll("-", "")
    .replaceAll(":", "")
    .replace("T", "-");
}

function slug(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function exportFilename(
  name: string,
  format: ExportFormat,
  now: Date,
  suffix?: string,
): string {
  const parts = ["terrascope", slug(name)];

  if (suffix !== undefined && slug(suffix) !== "") {
    parts.push(slug(suffix));
  }

  parts.push(exportStamp(now));

  return `${parts.join("-")}.${format}`;
}

export function buildExport<T>(
  dataset: ExportDataset<T>,
  items: readonly T[],
  format: ExportFormat,
  options: ExportOptions = {},
): ExportFile {
  const now = options.now ?? new Date();

  const filename = exportFilename(dataset.name, format, now, options.suffix);

  if (format === "csv") {
    return {
      filename,
      mime: "text/csv;charset=utf-8",
      content: `${BYTE_ORDER_MARK}${toCsv(dataset.columns, items)}`,
      count: items.length,
      format,
    };
  }

  return {
    filename,
    mime: "application/json;charset=utf-8",
    content: toJson(dataset.name, items, options.context ?? {}, now),
    count: items.length,
    format,
  };
}

export function downloadFile(file: ExportFile): void {
  const blob = new Blob([file.content], { type: file.mime });

  const url = URL.createObjectURL(blob);

  const link = document.createElement("a");

  link.href = url;
  link.download = file.filename;
  link.rel = "noopener";
  link.hidden = true;

  document.body.append(link);
  link.click();
  link.remove();

  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export function describeExport(file: ExportFile, noun: string): string {
  const plural = file.count === 1 ? noun : `${noun}s`;

  return `Exported ${file.count} ${plural} as ${file.format.toUpperCase()}.`;
}
