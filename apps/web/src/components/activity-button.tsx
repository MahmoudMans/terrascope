"use client";

import { ClipboardList } from "lucide-react";

import { useEffect, useState } from "react";

import { createPortal } from "react-dom";

import { ActivityPanel } from "@/components/activity-panel";
import { OPEN_ACTIVITY_EVENT } from "@/lib/command-events";
import { useSession } from "@/lib/use-session";

export function ActivityButton() {
  const session = useSession();

  const [open, setOpen] = useState(false);

  useEffect(() => {
    const handleRequest = () => setOpen(true);

    window.addEventListener(OPEN_ACTIVITY_EVENT, handleRequest);

    return () => {
      window.removeEventListener(OPEN_ACTIVITY_EVENT, handleRequest);
    };
  }, []);

  if (session !== null && session.user.role === "VIEWER") {
    return null;
  }

  return (
    <>
      <button
        type="button"
        className="icon-button"
        title="Activity"
        aria-label="Open activity log"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <ClipboardList size={17} aria-hidden="true" />
      </button>

      {open
        && createPortal(
          <ActivityPanel onClose={() => setOpen(false)} />,
          document.body,
        )}
    </>
  );
}
