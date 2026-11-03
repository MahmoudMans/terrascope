"use client";

import { X } from "lucide-react";

import { useRef, useState, type FormEvent } from "react";

import { AuthRequestError, changePassword } from "@/lib/auth";
import { useDialogBehavior } from "@/lib/use-dialog-behavior";

type ChangePasswordDialogProps = {
  onClose: () => void;
};

const MIN_LENGTH = 12;

export function ChangePasswordDialog({ onClose }: ChangePasswordDialogProps) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open: true, onClose, containerRef: dialogRef });

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (busy) {
      return;
    }

    if (current === "") {
      setError("Enter your current password.");

      return;
    }

    if (next.length < MIN_LENGTH) {
      setError(`The new password needs at least ${MIN_LENGTH} characters.`);

      return;
    }

    if (next !== confirm) {
      setError("The new passwords don't match.");

      return;
    }

    setBusy(true);
    setError(null);

    void changePassword(current, next)
      .then(() => {
        setDone(true);
      })
      .catch((caught: unknown) => {
        setError(
          caught instanceof AuthRequestError
            ? caught.message
            : "Can't reach the server. Try again.",
        );
      })
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <div className="dialog-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close dialog"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="auth-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="change-password-title"
        tabIndex={-1}
      >
        <header className="auth-dialog__header">
          <div>
            <span className="panel-kicker">ACCOUNT</span>

            <h2 id="change-password-title">Change password</h2>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        {done ? (
          <div className="auth-dialog__body">
            <p className="auth-dialog__success" role="status">
              Password updated. Sessions on your other devices were signed out.
            </p>

            <div className="auth-dialog__actions">
              <button type="button" className="drawer-action" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        ) : (
          <form className="auth-dialog__body" onSubmit={submit} noValidate>
            <label className="login-field">
              <span>Current password</span>

              <input
                type="password"
                autoComplete="current-password"
                value={current}
                onChange={(event) => setCurrent(event.target.value)}
              />
            </label>

            <label className="login-field">
              <span>New password</span>

              <input
                type="password"
                autoComplete="new-password"
                value={next}
                onChange={(event) => setNext(event.target.value)}
              />
            </label>

            <label className="login-field">
              <span>Confirm new password</span>

              <input
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(event) => setConfirm(event.target.value)}
              />
            </label>

            <p className="auth-dialog__hint">
              At least {MIN_LENGTH} characters. Changing it signs out your other
              devices.
            </p>

            {error !== null && (
              <p className="login-error" role="alert">
                {error}
              </p>
            )}

            <div className="auth-dialog__actions">
              <button
                type="button"
                className="drawer-action drawer-action--quiet"
                onClick={onClose}
              >
                Cancel
              </button>

              <button type="submit" className="drawer-action" disabled={busy}>
                {busy ? "Saving…" : "Update password"}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
