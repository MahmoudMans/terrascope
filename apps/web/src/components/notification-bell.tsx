"use client";

import { Bell, BellRing, Check, X } from "lucide-react";

import { useCallback, useEffect, useRef, useState } from "react";

import { HazardIcon } from "@/components/hazard-icon";

import { fetchAlertDashboard, updateAlertEventStatus } from "@/lib/api";

import { formatRelativeTime } from "@/lib/format";

import { timestampOf } from "@/lib/hazard-events";

import type {
  AlertDashboardResponse,
  AlertEvent,
  AlertEventStatus,
} from "@/types/alerts";

type NotificationBellProps = {
  refreshKey: boolean;
  liveTick?: number;
  onOpenAlertCenter: () => void;
};

const POLL_INTERVAL_MS = 60000;
const MAX_ITEMS = 6;

function isAbort(caught: unknown): boolean {
  return caught instanceof DOMException && caught.name === "AbortError";
}

export function NotificationBell({
  refreshKey,
  liveTick = 0,
  onOpenAlertCenter,
}: NotificationBellProps) {
  const [dashboard, setDashboard] =
    useState<AlertDashboardResponse | null>(null);

  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(
    (signal?: AbortSignal) =>
      fetchAlertDashboard(signal)
        .then((response) => {
          if (signal?.aborted) {
            return;
          }

          setDashboard(response);
          setError(null);
        })
        .catch((caught: unknown) => {
          if (signal?.aborted || isAbort(caught)) {
            return;
          }

          setError(
            caught instanceof Error
              ? caught.message
              : "Unable to load alerts.",
          );
        }),
    [],
  );

  useEffect(() => {
    const controller = new AbortController();

    void load(controller.signal);

    const timer = window.setInterval(() => {
      void load(controller.signal);
    }, POLL_INTERVAL_MS);

    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [load, refreshKey, liveTick]);

  useEffect(() => {
    if (!open) {
      return;
    }

    popoverRef.current?.focus({ preventScroll: true });

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        setOpen(false);
        buttonRef.current?.focus();
      }
    };

    const handlePointerDown = (event: PointerEvent) => {
      const wrapper = wrapperRef.current;

      if (wrapper !== null && !wrapper.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    const handleFocusIn = (event: FocusEvent) => {
      const wrapper = wrapperRef.current;

      if (wrapper !== null && !wrapper.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("focusin", handleFocusIn);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("focusin", handleFocusIn);
    };
  }, [open]);

  const newAlerts: AlertEvent[] = (dashboard?.alerts ?? [])
    .filter((alert) => alert.status === "NEW")
    .sort(
      (left, right) =>
        timestampOf(right.triggeredAt) - timestampOf(left.triggeredAt),
    );

  const newCount = newAlerts.length;

  const resolve = (alerts: AlertEvent[], status: AlertEventStatus) => {
    if (alerts.length === 0) {
      return;
    }

    setBusy(true);
    setError(null);

    void Promise.all(
      alerts.map((alert) => updateAlertEventStatus(alert.id, status)),
    )
      .then(() => load())
      .catch((caught: unknown) => {
        setError(
          caught instanceof Error
            ? caught.message
            : "Unable to update alert.",
        );
      })
      .finally(() => {
        setBusy(false);
      });
  };

  const toggle = () => {
    if (!open) {
      void load();
    }

    setOpen(!open);
  };

  const label =
    newCount === 0
      ? "Notifications, no new alerts"
      : `Notifications, ${newCount} new ${newCount === 1 ? "alert" : "alerts"}`;

  return (
    <div className="notification-wrapper" ref={wrapperRef}>
      <button
        ref={buttonRef}
        className="icon-button"
        type="button"
        title="Notifications"
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={toggle}
      >
        <Bell size={18} aria-hidden="true" />

        {newCount > 0 && (
          <span className="notification-count" aria-hidden="true">
            {newCount > 9 ? "9+" : newCount}
          </span>
        )}
      </button>

      {open && (
        <div
          ref={popoverRef}
          className="notification-popover"
          role="dialog"
          aria-label="Notifications"
          tabIndex={-1}
        >
          <header className="notification-popover__header">
            <div>
              <span className="panel-kicker">ALERT INBOX</span>

              <strong>
                {newCount === 0
                  ? "All caught up"
                  : `${newCount} new ${newCount === 1 ? "alert" : "alerts"}`}
              </strong>
            </div>

            {newCount > 1 && (
              <button
                type="button"
                className="notification-link"
                disabled={busy}
                onClick={() => resolve(newAlerts, "ACKNOWLEDGED")}
              >
                Acknowledge all
              </button>
            )}
          </header>

          {error !== null && (
            <div className="notification-error" role="alert">
              <span>{error}</span>

              <button
                type="button"
                className="notification-link"
                onClick={() => {
                  void load();
                }}
              >
                Retry
              </button>
            </div>
          )}

          {newCount === 0 && error === null ? (
            <div className="notification-empty">
              <BellRing size={26} aria-hidden="true" />

              <strong>No new alerts</strong>

              <span>
                Create alert rules in the Alert Center to be told when a hazard
                occurs near a place you care about.
              </span>
            </div>
          ) : (
            <ul className="notification-list">
              {newAlerts.slice(0, MAX_ITEMS).map((alert) => (
                <li
                  key={alert.id}
                  className="notification-item"
                  data-hazard={alert.hazardType}
                >
                  <span className="notification-item__icon">
                    <HazardIcon kind={alert.hazardType} size={16} />
                  </span>

                  <div className="notification-item__text">
                    <strong>{alert.title}</strong>

                    <span>
                      {alert.ruleName}
                      {" · "}
                      {Math.round(alert.distanceKm)} km
                      {" · "}
                      {formatRelativeTime(alert.triggeredAt)}
                    </span>
                  </div>

                  <div className="notification-item__actions">
                    <button
                      type="button"
                      aria-label={`Acknowledge ${alert.title}`}
                      title="Acknowledge"
                      disabled={busy}
                      onClick={() => resolve([alert], "ACKNOWLEDGED")}
                    >
                      <Check size={14} aria-hidden="true" />
                    </button>

                    <button
                      type="button"
                      aria-label={`Dismiss ${alert.title}`}
                      title="Dismiss"
                      disabled={busy}
                      onClick={() => resolve([alert], "DISMISSED")}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          <footer className="notification-popover__footer">
            <small>
              {newCount > MAX_ITEMS
                ? `+${newCount - MAX_ITEMS} more in the Alert Center`
                : `${dashboard?.enabledRuleCount ?? 0} active rules`}
            </small>

            <button
              type="button"
              className="drawer-action"
              onClick={() => {
                setOpen(false);
                onOpenAlertCenter();
              }}
            >
              Open Alert Center
            </button>
          </footer>
        </div>
      )}
    </div>
  );
}
