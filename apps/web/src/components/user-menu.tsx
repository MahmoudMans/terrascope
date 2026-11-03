"use client";

import { ChevronDown, KeyRound, LogOut, Users } from "lucide-react";

import { useEffect, useRef, useState } from "react";

import { createPortal } from "react-dom";

import { ChangePasswordDialog } from "@/components/change-password-dialog";
import { UsersPanel } from "@/components/users-panel";

import { clearSession, roleLabel, type AuthRole } from "@/lib/auth";
import { useSession } from "@/lib/use-session";

const ROLE_HINTS: Record<AuthRole, string> = {
  VIEWER: "Read-only. You can browse data and run analyses.",
  OPERATOR: "You can update incidents and records.",
  ADMIN: "Full access, including governance and users.",
};

function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter((word) => word.length > 0);

  if (words.length === 0) {
    return "?";
  }

  const letters =
    words.length === 1
      ? words[0].slice(0, 2)
      : `${words[0][0]}${words[words.length - 1][0]}`;

  return letters.toUpperCase();
}

export function UserMenu() {
  const session = useSession();

  const [open, setOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [usersOpen, setUsersOpen] = useState(false);

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);

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

  if (session === null) {
    return null;
  }

  const user = session.user;

  const openDialog = (action: () => void) => {
    buttonRef.current?.focus();
    setOpen(false);
    action();
  };

  return (
    <div className="user-menu" ref={wrapperRef}>
      <button
        ref={buttonRef}
        type="button"
        className="user-menu__button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu, signed in as ${user.displayName}`}
        onClick={() => setOpen(!open)}
      >
        <span className="user-avatar" aria-hidden="true">
          {initialsOf(user.displayName)}
        </span>

        <span className="user-menu__name">{user.displayName}</span>

        <ChevronDown
          size={14}
          aria-hidden="true"
          className="user-menu__chevron"
        />
      </button>

      {open && (
        <div
          ref={popoverRef}
          className="user-popover"
          role="menu"
          aria-label="Account"
          tabIndex={-1}
        >
          <div className="user-popover__header">
            <span className="user-avatar user-avatar--large" aria-hidden="true">
              {initialsOf(user.displayName)}
            </span>

            <div>
              <strong>{user.displayName}</strong>

              <small>@{user.username}</small>
            </div>

            <span
              className={`role-chip role-chip--${user.role.toLowerCase()}`}
            >
              {roleLabel(user.role)}
            </span>
          </div>

          <p className="user-popover__hint">{ROLE_HINTS[user.role]}</p>

          <button
            type="button"
            role="menuitem"
            className="user-popover__item"
            onClick={() => openDialog(() => setPasswordOpen(true))}
          >
            <KeyRound size={15} aria-hidden="true" />
            Change password
          </button>

          {user.role === "ADMIN" && (
            <button
              type="button"
              role="menuitem"
              className="user-popover__item"
              onClick={() => openDialog(() => setUsersOpen(true))}
            >
              <Users size={15} aria-hidden="true" />
              Manage users
            </button>
          )}

          <button
            type="button"
            role="menuitem"
            className="user-popover__item user-popover__item--danger"
            onClick={() => {
              setOpen(false);
              clearSession("manual");
            }}
          >
            <LogOut size={15} aria-hidden="true" />
            Sign out
          </button>
        </div>
      )}

      {passwordOpen
        && createPortal(
          <ChangePasswordDialog onClose={() => setPasswordOpen(false)} />,
          document.body,
        )}

      {usersOpen
        && createPortal(
          <UsersPanel onClose={() => setUsersOpen(false)} />,
          document.body,
        )}
    </div>
  );
}
