"use client";

import { Download, X } from "lucide-react";

import { useEffect, useRef, useState } from "react";

import {
  describeExport,
  downloadFile,
  type ExportFile,
  type ExportFormat,
} from "@/lib/export";

export type ExportResult = {
  file: ExportFile;
  note?: string;
};

type ExportButtonsProps = {
  noun: string;
  subject: string;
  count: number;
  disabled?: boolean;
  onBuild: (
    format: ExportFormat,
  ) => ExportResult | null | Promise<ExportResult | null>;
};

const FORMATS: Array<{ format: ExportFormat; label: string }> = [
  { format: "csv", label: "CSV" },
  { format: "json", label: "JSON" },
];

export function ExportButtons({
  noun,
  subject,
  count,
  disabled = false,
  onBuild,
}: ExportButtonsProps) {
  const [busy, setBusy] = useState<ExportFormat | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const empty = count === 0;

  const handleExport = (format: ExportFormat) => {
    if (busy !== null) {
      return;
    }

    setBusy(format);
    setStatus(null);
    setError(null);

    Promise.resolve()
      .then(() => onBuild(format))
      .then((result) => {
        if (result === null) {
          setError(`There is no ${subject} to export yet.`);
          return;
        }

        downloadFile(result.file);
        setStatus(result.note ?? describeExport(result.file, noun));
      })
      .catch((caught: unknown) => {
        setError(
          caught instanceof Error && caught.message !== ""
            ? caught.message
            : `Unable to export ${subject}.`,
        );
      })
      .finally(() => {
        setBusy(null);
      });
  };

  return (
    <div
      className="export-buttons"
      role="group"
      aria-label={`Export ${subject}`}
    >
      <Download
        size={13}
        className="export-buttons__icon"
        aria-hidden="true"
      />

      {FORMATS.map((entry) => (
        <button
          key={entry.format}
          type="button"
          className="export-button"
          disabled={disabled || empty}
          aria-busy={busy === entry.format}
          aria-label={`Export ${subject} as ${entry.label}`}
          title={
            empty
              ? `No ${subject} to export`
              : `Download ${subject} as ${entry.label}`
          }
          onClick={() => handleExport(entry.format)}
        >
          {entry.label}
        </button>
      ))}

      <div className="visually-hidden" role="status">
        {status ?? ""}
      </div>

      {error !== null && (
        <div className="export-buttons__error" role="alert">
          {error}
        </div>
      )}
    </div>
  );
}

type ExportToastProps = {
  message: string | null;
  onDismiss: () => void;
};

const TOAST_MS = 5000;

export function ExportToast({ message, onDismiss }: ExportToastProps) {
  const dismissRef = useRef(onDismiss);

  useEffect(() => {
    dismissRef.current = onDismiss;
  });

  useEffect(() => {
    if (message === null) {
      return;
    }

    const timer = window.setTimeout(() => dismissRef.current(), TOAST_MS);

    return () => {
      window.clearTimeout(timer);
    };
  }, [message]);

  if (message === null) {
    return null;
  }

  return (
    <div className="watchlist-toast" role="status">
      <Download size={15} aria-hidden="true" />

      <span>{message}</span>

      <button type="button" aria-label="Dismiss message" onClick={onDismiss}>
        <X size={14} aria-hidden="true" />
      </button>
    </div>
  );
}
