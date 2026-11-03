"use client";

import { BellRing, X } from "lucide-react";

import { useEffect, useRef, useState } from "react";

export type LiveToastMessage = {
  id: number;
  count: number;
};

type LiveToastProps = {
  toast: LiveToastMessage | null;
  onView: () => void;
  onDismiss: () => void;
};

const AUTO_DISMISS_MS = 9000;

export function LiveToast({ toast, onView, onDismiss }: LiveToastProps) {
  const [paused, setPaused] = useState(false);

  const dismissRef = useRef(onDismiss);

  useEffect(() => {
    dismissRef.current = onDismiss;
  });

  const toastId = toast?.id ?? null;

  useEffect(() => {
    if (toastId === null || paused) {
      return;
    }

    const timer = window.setTimeout(() => {
      dismissRef.current();
    }, AUTO_DISMISS_MS);

    return () => {
      window.clearTimeout(timer);
    };
  }, [toastId, paused]);

  return (
    <div className="live-toast-region" role="status" aria-live="polite">
      {toast !== null && (
        <div
          className="live-toast"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onBlur={() => setPaused(false)}
        >
          <BellRing size={16} aria-hidden="true" />

          <span>
            {toast.count === 1
              ? "1 new alert triggered"
              : `${toast.count} new alerts triggered`}
          </span>

          <button
            type="button"
            className="live-toast__view"
            onClick={() => {
              setPaused(false);
              onView();
            }}
          >
            View
          </button>

          <button
            type="button"
            className="live-toast__close"
            aria-label="Dismiss notification"
            onClick={() => {
              setPaused(false);
              onDismiss();
            }}
          >
            <X size={14} aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}
