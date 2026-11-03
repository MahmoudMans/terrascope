"use client";

import { ShieldAlert, X } from "lucide-react";

import { useEffect, useRef, useState, type ReactNode } from "react";

import { LoginScreen } from "@/components/login-screen";

import {
  AuthRequestError,
  clearSession,
  fetchAuthConfig,
  fetchCurrentUser,
  getSession,
  subscribeAuthEvents,
} from "@/lib/auth";

type GateStatus = "checking" | "open" | "signedOut" | "signedIn";

type ForbiddenNotice = {
  id: number;
  message: string;
};

type AuthGateProps = {
  children: ReactNode;
};

const CONFIG_TIMEOUT_MS = 4000;
const NOTICE_DISMISS_MS = 7000;

async function resolveStatus(signal: AbortSignal): Promise<GateStatus> {
  let enabled: boolean;

  try {
    enabled = (await fetchAuthConfig(signal)).enabled;
  } catch {
    return "open";
  }

  if (!enabled) {
    return "open";
  }

  if (getSession() === null) {
    return "signedOut";
  }

  try {
    await fetchCurrentUser(signal);

    return "signedIn";
  } catch (caught: unknown) {
    if (caught instanceof AuthRequestError && caught.status === 401) {
      clearSession("expired");

      return "signedOut";
    }

    return "signedIn";
  }
}

type ForbiddenToastProps = {
  notice: ForbiddenNotice | null;
  onDismiss: () => void;
};

function ForbiddenToast({ notice, onDismiss }: ForbiddenToastProps) {
  const dismissRef = useRef(onDismiss);

  useEffect(() => {
    dismissRef.current = onDismiss;
  });

  const noticeId = notice?.id ?? null;

  useEffect(() => {
    if (noticeId === null) {
      return;
    }

    const timer = window.setTimeout(() => {
      dismissRef.current();
    }, NOTICE_DISMISS_MS);

    return () => {
      window.clearTimeout(timer);
    };
  }, [noticeId]);

  return (
    <div className="auth-toast-region" role="status" aria-live="polite">
      {notice !== null && (
        <div className="auth-toast">
          <ShieldAlert size={16} aria-hidden="true" />

          <span>{notice.message}</span>

          <button
            type="button"
            className="auth-toast__close"
            aria-label="Dismiss message"
            onClick={onDismiss}
          >
            <X size={14} aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}

export function AuthGate({ children }: AuthGateProps) {
  const [status, setStatus] = useState<GateStatus>("checking");
  const [notice, setNotice] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState<ForbiddenNotice | null>(null);

  useEffect(() => {
    let cancelled = false;

    const controller = new AbortController();

    const timer = window.setTimeout(() => {
      controller.abort();
    }, CONFIG_TIMEOUT_MS);

    void resolveStatus(controller.signal).then((next) => {
      window.clearTimeout(timer);

      if (!cancelled) {
        setStatus(next);
      }
    });

    const unsubscribe = subscribeAuthEvents((event) => {
      if (cancelled) {
        return;
      }

      if (event.type === "signedOut") {
        setNotice(
          event.reason === "expired"
            ? "Your session ended. Sign in again to continue."
            : null,
        );

        setStatus("signedOut");

        return;
      }

      setForbidden({ id: Date.now(), message: event.message });
    });

    return () => {
      cancelled = true;

      window.clearTimeout(timer);
      controller.abort();
      unsubscribe();
    };
  }, []);

  if (status === "checking") {
    return (
      <div className="auth-splash" role="status" aria-live="polite">
        <span className="auth-splash__spinner" aria-hidden="true" />

        <span>Checking access…</span>
      </div>
    );
  }

  if (status === "signedOut") {
    return (
      <LoginScreen
        notice={notice}
        onSignedIn={() => {
          setNotice(null);
          setStatus("signedIn");
        }}
      />
    );
  }

  return (
    <>
      {children}

      <ForbiddenToast notice={forbidden} onDismiss={() => setForbidden(null)} />
    </>
  );
}
