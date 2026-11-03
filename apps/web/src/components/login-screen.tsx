"use client";

import { Eye, EyeOff, Globe2 } from "lucide-react";

import { useEffect, useRef, useState, type FormEvent } from "react";

import { AuthRequestError, signIn } from "@/lib/auth";

type LoginScreenProps = {
  notice: string | null;
  onSignedIn: () => void;
};

export function LoginScreen({ notice, onSignedIn }: LoginScreenProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [reveal, setReveal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const usernameRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    usernameRef.current?.focus();
  }, []);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (busy) {
      return;
    }

    if (username.trim() === "" || password === "") {
      setError("Enter your username and password.");

      return;
    }

    setBusy(true);
    setError(null);

    void signIn(username, password)
      .then(() => {
        onSignedIn();
      })
      .catch((caught: unknown) => {
        setError(
          caught instanceof AuthRequestError
            ? caught.message
            : "Can't reach the server. Check that the backend is running.",
        );

        setPassword("");
      })
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <main className="login-screen">
      <div className="login-card">
        <div className="brand login-brand">
          <div className="brand__mark">
            <Globe2 size={24} />

            <span className="brand__pulse" />
          </div>

          <div className="brand__text">
            <strong>TerraScope</strong>

            <span>EARTH MONITOR</span>
          </div>
        </div>

        <h1>Sign in</h1>

        <p className="login-lede">
          Access the multi-hazard operations workspace.
        </p>

        {notice !== null && (
          <p className="login-notice" role="status">
            {notice}
          </p>
        )}

        <form onSubmit={submit} noValidate>
          <label className="login-field">
            <span>Username</span>

            <input
              ref={usernameRef}
              type="text"
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>

          <label className="login-field">
            <span>Password</span>

            <div className="login-password">
              <input
                type={reveal ? "text" : "password"}
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />

              <button
                type="button"
                className="login-reveal"
                aria-label={reveal ? "Hide password" : "Show password"}
                aria-pressed={reveal}
                onClick={() => setReveal(!reveal)}
              >
                {reveal ? (
                  <EyeOff size={16} aria-hidden="true" />
                ) : (
                  <Eye size={16} aria-hidden="true" />
                )}
              </button>
            </div>
          </label>

          {error !== null && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="login-submit" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p className="login-foot">
          Accounts are created by an administrator.
        </p>
      </div>
    </main>
  );
}
