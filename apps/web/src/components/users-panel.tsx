"use client";

import { Plus, RefreshCw, X } from "lucide-react";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";

import { formatRelativeTime } from "@/lib/format";

import {
  AuthRequestError,
  ROLE_CHOICES,
  createUser,
  fetchUsers,
  getSession,
  resetUserPassword,
  roleLabel,
  updateUser,
  type AuthRole,
  type AuthUser,
} from "@/lib/auth";

import { useDialogBehavior } from "@/lib/use-dialog-behavior";

type UsersPanelProps = {
  onClose: () => void;
};

const MIN_PASSWORD_LENGTH = 12;

function isAbort(caught: unknown): boolean {
  return caught instanceof DOMException && caught.name === "AbortError";
}

function messageOf(caught: unknown, fallback: string): string {
  return caught instanceof AuthRequestError ? caught.message : fallback;
}

export function UsersPanel({ onClose }: UsersPanelProps) {
  const [users, setUsers] = useState<AuthUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [resetId, setResetId] = useState<string | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [newUsername, setNewUsername] = useState("");
  const [newName, setNewName] = useState("");
  const [newRole, setNewRole] = useState<AuthRole>("VIEWER");
  const [newPassword, setNewPassword] = useState("");

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open: true, onClose, containerRef: dialogRef });

  const selfId = getSession()?.user.id ?? null;

  const load = useCallback(
    (signal?: AbortSignal) =>
      fetchUsers(signal)
        .then((list) => {
          if (signal?.aborted) {
            return;
          }

          setUsers(list);
          setError(null);
        })
        .catch((caught: unknown) => {
          if (signal?.aborted || isAbort(caught)) {
            return;
          }

          setError(messageOf(caught, "Unable to load users."));
        }),
    [],
  );

  useEffect(() => {
    const controller = new AbortController();

    void load(controller.signal);

    return () => {
      controller.abort();
    };
  }, [load]);

  const run = (id: string | null, action: () => Promise<unknown>) => {
    setBusyId(id ?? "new");
    setError(null);

    return action()
      .then(() => load())
      .catch((caught: unknown) => {
        setError(messageOf(caught, "That didn't work. Try again."));

        return Promise.reject(caught);
      })
      .finally(() => {
        setBusyId(null);
      });
  };

  const submitNewUser = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (busyId !== null) {
      return;
    }

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`Passwords need at least ${MIN_PASSWORD_LENGTH} characters.`);

      return;
    }

    run(null, () =>
      createUser({
        username: newUsername,
        displayName: newName,
        role: newRole,
        password: newPassword,
      }),
    )
      .then(() => {
        setAdding(false);
        setNewUsername("");
        setNewName("");
        setNewRole("VIEWER");
        setNewPassword("");
      })
      .catch(() => undefined);
  };

  const submitReset = (user: AuthUser) => {
    if (resetPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`Passwords need at least ${MIN_PASSWORD_LENGTH} characters.`);

      return;
    }

    run(user.id, () => resetUserPassword(user.id, resetPassword))
      .then(() => {
        setResetId(null);
        setResetPassword("");
      })
      .catch(() => undefined);
  };

  return (
    <div className="drawer-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close users"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="drawer drawer--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="users-title"
        tabIndex={-1}
      >
        <header className="drawer__header">
          <div>
            <span className="panel-kicker">ACCESS</span>

            <h2 id="users-title">Users</h2>

            <p>Create accounts and choose what each person can do.</p>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close users"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="drawer__toolbar">
          <button
            type="button"
            className="drawer-action"
            aria-expanded={adding}
            onClick={() => setAdding(!adding)}
          >
            <Plus size={14} aria-hidden="true" />
            Add user
          </button>

          <button
            type="button"
            className="drawer-action drawer-action--quiet"
            onClick={() => void load()}
          >
            <RefreshCw size={14} aria-hidden="true" />
            Refresh
          </button>
        </div>

        <div className="drawer__body">
          <ul className="users-legend">
            <li>
              <span className="role-chip role-chip--viewer">Viewer</span>
              Browse data and run analyses.
            </li>

            <li>
              <span className="role-chip role-chip--operator">Operator</span>
              Also update incidents and records.
            </li>

            <li>
              <span className="role-chip role-chip--admin">Administrator</span>
              Also governance sign-off and users.
            </li>
          </ul>

          {error !== null && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}

          {adding && (
            <form
              className="users-form"
              onSubmit={submitNewUser}
              noValidate
              aria-label="Add user"
            >
              <label className="login-field">
                <span>Username</span>

                <input
                  type="text"
                  autoComplete="off"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={newUsername}
                  onChange={(event) => setNewUsername(event.target.value)}
                />
              </label>

              <label className="login-field">
                <span>Display name</span>

                <input
                  type="text"
                  autoComplete="off"
                  value={newName}
                  onChange={(event) => setNewName(event.target.value)}
                />
              </label>

              <label className="login-field">
                <span>Role</span>

                <select
                  className="setting-select users-select"
                  value={newRole}
                  onChange={(event) =>
                    setNewRole(event.target.value as AuthRole)
                  }
                >
                  {ROLE_CHOICES.map((choice) => (
                    <option key={choice.value} value={choice.value}>
                      {choice.label}
                    </option>
                  ))}
                </select>
              </label>

              <label className="login-field">
                <span>Temporary password</span>

                <input
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </label>

              <div className="auth-dialog__actions">
                <button
                  type="button"
                  className="drawer-action drawer-action--quiet"
                  onClick={() => setAdding(false)}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="drawer-action"
                  disabled={busyId !== null}
                >
                  {busyId === "new" ? "Creating…" : "Create user"}
                </button>
              </div>
            </form>
          )}

          {users === null && error === null && (
            <div className="drawer__empty">
              <strong>Loading users…</strong>
            </div>
          )}

          {users !== null && (
            <ul className="users-list">
              {users.map((user) => {
                const isSelf = user.id === selfId;
                const busy = busyId === user.id;

                return (
                  <li
                    key={user.id}
                    className={
                      user.enabled
                        ? "users-row"
                        : "users-row users-row--disabled"
                    }
                  >
                    <div className="users-row__who">
                      <strong>
                        {user.displayName}
                        {isSelf ? " (you)" : ""}
                      </strong>

                      <small>
                        @{user.username} ·{" "}
                        {user.lastLoginAt === null
                          ? "never signed in"
                          : `signed in ${formatRelativeTime(user.lastLoginAt)}`}
                      </small>
                    </div>

                    <div className="users-row__controls">
                      <select
                        className="setting-select users-select"
                        aria-label={`Role for ${user.displayName}`}
                        value={user.role}
                        disabled={isSelf || busy}
                        onChange={(event) =>
                          void run(user.id, () =>
                            updateUser(user.id, {
                              role: event.target.value as AuthRole,
                            }),
                          ).catch(() => undefined)
                        }
                      >
                        {ROLE_CHOICES.map((choice) => (
                          <option key={choice.value} value={choice.value}>
                            {choice.label}
                          </option>
                        ))}
                      </select>

                      <button
                        type="button"
                        role="switch"
                        aria-checked={user.enabled}
                        aria-label={`Account enabled for ${user.displayName}`}
                        className="switch"
                        disabled={isSelf || busy}
                        onClick={() =>
                          void run(user.id, () =>
                            updateUser(user.id, { enabled: !user.enabled }),
                          ).catch(() => undefined)
                        }
                      >
                        <span />
                      </button>

                      {!isSelf && (
                        <button
                          type="button"
                          className="notification-link"
                          aria-expanded={resetId === user.id}
                          onClick={() => {
                            setResetId(resetId === user.id ? null : user.id);
                            setResetPassword("");
                          }}
                        >
                          Reset password
                        </button>
                      )}
                    </div>

                    <span
                      className={`role-chip role-chip--${user.role.toLowerCase()}`}
                    >
                      {roleLabel(user.role)}
                      {user.enabled ? "" : " · disabled"}
                    </span>

                    {resetId === user.id && (
                      <div className="users-row__reset">
                        <label className="login-field">
                          <span>New password for {user.displayName}</span>

                          <input
                            type="password"
                            autoComplete="new-password"
                            value={resetPassword}
                            onChange={(event) =>
                              setResetPassword(event.target.value)
                            }
                          />
                        </label>

                        <button
                          type="button"
                          className="drawer-action"
                          disabled={busy}
                          onClick={() => submitReset(user)}
                        >
                          {busy ? "Saving…" : "Set password"}
                        </button>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}
