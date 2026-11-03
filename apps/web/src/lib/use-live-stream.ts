"use client";

import { useEffect, useRef, useState } from "react";

import {
  openLiveStream,
  parseLiveAlertUpdate,
  type LiveAlertUpdate,
  type LiveStreamState,
} from "@/lib/live-stream";

type LiveStreamHandlers = {
  onAlerts: (update: LiveAlertUpdate) => void;
  onFeeds: () => void;
};

export function useLiveStream(
  enabled: boolean,
  handlers: LiveStreamHandlers,
): LiveStreamState {
  const [state, setState] = useState<LiveStreamState>("connecting");

  const handlersRef = useRef<LiveStreamHandlers>(handlers);

  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!enabled) {
      return;
    }

    return openLiveStream({
      onState: setState,
      onMessage: (message) => {
        if (message.event === "alerts") {
          const update = parseLiveAlertUpdate(message.data);

          if (update !== null) {
            handlersRef.current.onAlerts(update);
          }

          return;
        }

        if (message.event === "feeds") {
          handlersRef.current.onFeeds();
        }
      },
    });
  }, [enabled]);

  return enabled ? state : "off";
}
