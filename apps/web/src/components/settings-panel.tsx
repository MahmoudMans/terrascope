"use client";

import { RotateCcw, X } from "lucide-react";

import { useRef } from "react";

import { formatRelativeTime } from "@/lib/format";

import {
  AUTO_REFRESH_CHOICES,
  DEPTH_CHOICES,
  MAGNITUDE_CHOICES,
  PERIOD_CHOICES,
  TEXT_SIZE_CHOICES,
  type LayerPreferences,
  type Preferences,
  type PreferencesPatch,
} from "@/lib/preferences";

import {
  type AutomationStatus,
  type LiveStreamState,
} from "@/lib/live-stream";

import { THEME_CHOICES } from "@/lib/theme";

import { useDialogBehavior } from "@/lib/use-dialog-behavior";

export type FeedStatus = {
  id: string;
  label: string;
  live: boolean | null;
  generatedAt: string | null;
  count: number;
};

export type SettingsLiveStatus = {
  state: LiveStreamState;
  automation: AutomationStatus | null;
  automationFailed: boolean;
};

type SettingsPanelProps = {
  open: boolean;
  preferences: Preferences;
  feeds: FeedStatus[];
  live?: SettingsLiveStatus;
  onChange: (patch: PreferencesPatch) => void;
  onReset: () => void;
  onClose: () => void;
};

type SegmentedProps<T extends string | number> = {
  label: string;
  value: T;
  choices: Array<{ value: T; label: string }>;
  onSelect: (value: T) => void;
};

function Segmented<T extends string | number>({
  label,
  value,
  choices,
  onSelect,
}: SegmentedProps<T>) {
  return (
    <div className="segmented-control" role="group" aria-label={label}>
      {choices.map((choice) => (
        <button
          key={String(choice.value)}
          type="button"
          aria-pressed={choice.value === value}
          className={
            choice.value === value
              ? "segmented-control__item segmented-control__item--active"
              : "segmented-control__item"
          }
          onClick={() => onSelect(choice.value)}
        >
          {choice.label}
        </button>
      ))}
    </div>
  );
}

type SwitchRowProps = {
  label: string;
  description: string;
  checked: boolean;
  onToggle: (next: boolean) => void;
};

function SwitchRow({
  label,
  description,
  checked,
  onToggle,
}: SwitchRowProps) {
  return (
    <div className="setting-row">
      <div>
        <strong>{label}</strong>

        <small>{description}</small>
      </div>

      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className="switch"
        onClick={() => onToggle(!checked)}
      >
        <span />
      </button>
    </div>
  );
}

const LAYER_ROWS: Array<{
  key: keyof LayerPreferences;
  label: string;
  description: string;
}> = [
  {
    key: "earthquakes",
    label: "Earthquakes",
    description: "USGS seismic events",
  },
  {
    key: "volcanoes",
    label: "Volcanoes",
    description: "NASA EONET volcanic activity",
  },
  {
    key: "wildfires",
    label: "Wildfires",
    description: "NASA EONET open wildfires",
  },
  {
    key: "storms",
    label: "Severe storms",
    description: "NASA EONET storm systems",
  },
];

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8087/api/v1";

function formatInterval(totalSeconds: number): string {
  if (totalSeconds >= 60 && totalSeconds % 60 === 0) {
    return `${totalSeconds / 60} min`;
  }

  return `${totalSeconds} sec`;
}

function streamDescription(state: LiveStreamState): string {
  switch (state) {
    case "live":
      return "Connected";
    case "reconnecting":
      return "Connection lost, retrying";
    case "connecting":
      return "Connecting";
    default:
      return "Off";
  }
}

function streamDotClass(state: LiveStreamState): string {
  if (state === "live") {
    return "feed-row__dot feed-row__dot--live";
  }

  if (state === "reconnecting") {
    return "feed-row__dot feed-row__dot--fallback";
  }

  return "feed-row__dot feed-row__dot--pending";
}

function monitoringDescription(live: SettingsLiveStatus): string {
  const automation = live.automation;

  if (automation === null) {
    return live.automationFailed ? "Status unavailable" : "Checking";
  }

  if (!automation.enabled) {
    return "Turned off on the server";
  }

  const parts = [`Checks feeds every ${formatInterval(automation.intervalSeconds)}`];

  if (automation.lastRunAt === null) {
    parts.push("first check pending");
  } else {
    parts.push(
      `last check ${formatRelativeTime(automation.lastRunAt)}${
        automation.lastRunSucceeded ? "" : " failed"
      }`,
    );
  }

  if (automation.evaluatesAlerts) {
    parts.push(
      `${automation.lastNewAlertCount} new ${
        automation.lastNewAlertCount === 1 ? "alert" : "alerts"
      } last time`,
    );
  } else {
    parts.push("alert checks off");
  }

  return parts.join(" · ");
}

function monitoringDotClass(live: SettingsLiveStatus): string {
  const automation = live.automation;

  if (automation === null || !automation.enabled) {
    return "feed-row__dot feed-row__dot--pending";
  }

  if (automation.lastRunAt !== null && !automation.lastRunSucceeded) {
    return "feed-row__dot feed-row__dot--fallback";
  }

  return "feed-row__dot feed-row__dot--live";
}

export function SettingsPanel({
  open,
  preferences,
  feeds,
  live,
  onChange,
  onReset,
  onClose,
}: SettingsPanelProps) {
  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  if (!open) {
    return null;
  }

  const toggleLayer = (key: keyof LayerPreferences, next: boolean) => {
    const layers: Partial<LayerPreferences> = {};

    layers[key] = next;

    onChange({ layers });
  };

  return (
    <div className="drawer-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close settings"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabIndex={-1}
      >
        <header className="drawer__header">
          <div>
            <span className="panel-kicker">PREFERENCES</span>

            <h2 id="settings-title">Settings</h2>

            <p>
              Changes apply immediately and are remembered on this device.
            </p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close settings"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="drawer__body">
          <section className="settings-section">
            <h3>Monitor defaults</h3>

            <div className="setting-block">
              <strong>Observation period</strong>

              <Segmented
                label="Observation period"
                value={preferences.period}
                choices={PERIOD_CHOICES}
                onSelect={(period) => onChange({ period })}
              />
            </div>

            <div className="setting-row">
              <div>
                <strong>Minimum magnitude</strong>

                <small>Earthquakes weaker than this are hidden.</small>
              </div>

              <select
                className="setting-select"
                aria-label="Minimum magnitude"
                value={preferences.minMagnitude}
                onChange={(event) =>
                  onChange({ minMagnitude: Number(event.target.value) })
                }
              >
                {MAGNITUDE_CHOICES.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="setting-row">
              <div>
                <strong>Maximum depth</strong>

                <small>Deeper earthquakes are hidden.</small>
              </div>

              <select
                className="setting-select"
                aria-label="Maximum depth"
                value={preferences.maxDepth}
                onChange={(event) =>
                  onChange({ maxDepth: Number(event.target.value) })
                }
              >
                {DEPTH_CHOICES.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </select>
            </div>
          </section>

          <section className="settings-section">
            <h3>Map layers</h3>

            {LAYER_ROWS.map((row) => (
              <SwitchRow
                key={row.key}
                label={row.label}
                description={row.description}
                checked={preferences.layers[row.key]}
                onToggle={(next) => toggleLayer(row.key, next)}
              />
            ))}
          </section>

          <section className="settings-section">
            <h3>Live updates</h3>

            <div className="setting-block">
              <strong>Auto-refresh feeds</strong>

              <Segmented
                label="Auto-refresh interval"
                value={preferences.autoRefreshMinutes}
                choices={AUTO_REFRESH_CHOICES}
                onSelect={(autoRefreshMinutes) =>
                  onChange({ autoRefreshMinutes })
                }
              />
            </div>

            <SwitchRow
              label="Live alert stream"
              description="Get notified the moment the server finds a new alert, and refresh feeds as soon as new data arrives."
              checked={preferences.liveUpdates}
              onToggle={(liveUpdates) => onChange({ liveUpdates })}
            />

            {live !== undefined && (
              <ul className="feed-list">
                <li className="feed-row">
                  <span className={streamDotClass(live.state)} aria-hidden="true" />

                  <div>
                    <strong>Live connection</strong>

                    <small>{streamDescription(live.state)}</small>
                  </div>

                  <span className="feed-row__count">
                    {live.automation !== null && live.state === "live"
                      ? `${live.automation.connectedClients} connected`
                      : ""}
                  </span>
                </li>

                <li className="feed-row">
                  <span className={monitoringDotClass(live)} aria-hidden="true" />

                  <div>
                    <strong>Background monitoring</strong>

                    <small>{monitoringDescription(live)}</small>
                  </div>

                  <span className="feed-row__count" />
                </li>
              </ul>
            )}
          </section>

          <section className="settings-section">
            <h3>Display</h3>

            <div className="setting-block">
              <strong>Theme</strong>

              <Segmented
                label="Theme"
                value={preferences.theme}
                choices={THEME_CHOICES}
                onSelect={(theme) => onChange({ theme })}
              />
            </div>

            <div className="setting-block">
              <strong>Text size</strong>

              <Segmented
                label="Text size"
                value={preferences.textSize}
                choices={TEXT_SIZE_CHOICES}
                onSelect={(textSize) => onChange({ textSize })}
              />
            </div>

            <SwitchRow
              label="Reduce motion"
              description="Turns off animations and transitions."
              checked={preferences.reduceMotion}
              onToggle={(reduceMotion) => onChange({ reduceMotion })}
            />

            <SwitchRow
              label="Keyboard shortcuts"
              description="Single keys such as R to refresh or G then T for the timeline. Press ? to see them all. Ctrl+K always opens the command palette."
              checked={preferences.shortcuts}
              onToggle={(shortcuts) => onChange({ shortcuts })}
            />
          </section>

          <section className="settings-section">
            <h3>Data sources</h3>

            <ul className="feed-list">
              {feeds.map((feed) => (
                <li key={feed.id} className="feed-row">
                  <span
                    className={
                      feed.live === null
                        ? "feed-row__dot feed-row__dot--pending"
                        : feed.live
                          ? "feed-row__dot feed-row__dot--live"
                          : "feed-row__dot feed-row__dot--fallback"
                    }
                    aria-hidden="true"
                  />

                  <div>
                    <strong>{feed.label}</strong>

                    <small>
                      {feed.live === null
                        ? "Connecting"
                        : feed.live
                          ? "Live"
                          : "Demonstration fallback"}
                      {feed.generatedAt !== null
                        ? ` · updated ${formatRelativeTime(feed.generatedAt)}`
                        : ""}
                    </small>
                  </div>

                  <span className="feed-row__count">
                    {feed.count}
                    {feed.count === 1 ? " event" : " events"}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          <section className="settings-section">
            <h3>Connection</h3>

            <div className="setting-block">
              <strong>Backend API</strong>

              <code className="settings-code">{API_BASE_URL}</code>
            </div>
          </section>
        </div>

        <footer className="drawer__footer">
          <button
            type="button"
            className="drawer-action drawer-action--quiet"
            onClick={onReset}
          >
            <RotateCcw size={14} aria-hidden="true" />
            Reset to defaults
          </button>

          <button
            type="button"
            className="drawer-action"
            onClick={onClose}
          >
            Done
          </button>
        </footer>
      </section>
    </div>
  );
}
