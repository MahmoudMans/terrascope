export type AuthRole = "VIEWER" | "OPERATOR" | "ADMIN";

export type AuthUser = {
  id: string;
  username: string;
  displayName: string;
  role: AuthRole;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
};

export type AuthSession = {
  token: string;
  expiresAt: string;
  user: AuthUser;
};

export type AuthEvent =
  | { type: "signedOut"; reason: "expired" | "required" | "manual" }
  | { type: "forbidden"; message: string };

export type NewUserInput = {
  username: string;
  displayName: string;
  role: AuthRole;
  password: string;
};

export type UserPatch = {
  displayName?: string;
  role?: AuthRole;
  enabled?: boolean;
};

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8087/api/v1";

export const ROLE_CHOICES: Array<{ value: AuthRole; label: string }> = [
  { value: "VIEWER", label: "Viewer" },
  { value: "OPERATOR", label: "Operator" },
  { value: "ADMIN", label: "Administrator" },
];

const STORAGE_KEY = "terrascope.auth.v1";
const FORBIDDEN_FALLBACK = "Your account doesn't have permission to do that.";

export class AuthRequestError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);

    this.name = "AuthRequestError";
    this.status = status;
    this.code = code;
  }
}

let currentSession: AuthSession | null = null;
let sessionLoaded = false;

const sessionListeners = new Set<() => void>();
const eventListeners = new Set<(event: AuthEvent) => void>();

export function roleLabel(role: AuthRole): string {
  return ROLE_CHOICES.find((choice) => choice.value === role)?.label ?? role;
}

function isRole(value: unknown): value is AuthRole {
  return value === "VIEWER" || value === "OPERATOR" || value === "ADMIN";
}

function parseUser(value: unknown): AuthUser | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }

  const record = value as Record<string, unknown>;

  if (
    typeof record.id !== "string"
    || typeof record.username !== "string"
    || typeof record.displayName !== "string"
    || !isRole(record.role)
  ) {
    return null;
  }

  return {
    id: record.id,
    username: record.username,
    displayName: record.displayName,
    role: record.role,
    enabled: record.enabled !== false,
    createdAt: typeof record.createdAt === "string" ? record.createdAt : "",
    lastLoginAt:
      typeof record.lastLoginAt === "string" ? record.lastLoginAt : null,
  };
}

function parseSession(raw: string): AuthSession | null {
  try {
    const record = JSON.parse(raw) as Record<string, unknown>;
    const user = parseUser(record.user);

    if (
      user === null
      || typeof record.token !== "string"
      || record.token.length === 0
      || typeof record.expiresAt !== "string"
      || !(Date.parse(record.expiresAt) > Date.now())
    ) {
      return null;
    }

    return { token: record.token, expiresAt: record.expiresAt, user };
  } catch {
    return null;
  }
}

function readStoredSession(): AuthSession | null {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);

    return raw === null ? null : parseSession(raw);
  } catch {
    return null;
  }
}

function writeStoredSession(session: AuthSession | null): void {
  try {
    if (session === null) {
      window.sessionStorage.removeItem(STORAGE_KEY);
    } else {
      window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    }
  } catch {
    return;
  }
}

function notifySession(): void {
  for (const listener of Array.from(sessionListeners)) {
    listener();
  }
}

function emit(event: AuthEvent): void {
  for (const listener of Array.from(eventListeners)) {
    listener(event);
  }
}

export function getSession(): AuthSession | null {
  if (!sessionLoaded) {
    if (typeof window === "undefined") {
      return null;
    }

    sessionLoaded = true;
    currentSession = readStoredSession();
  }

  return currentSession;
}

export function setSession(session: AuthSession): void {
  sessionLoaded = true;
  currentSession = session;

  writeStoredSession(session);
  notifySession();
}

export function clearSession(
  reason: "expired" | "required" | "manual" = "manual",
): void {
  sessionLoaded = true;
  currentSession = null;

  writeStoredSession(null);
  notifySession();
  emit({ type: "signedOut", reason });
}

export function subscribeSession(listener: () => void): () => void {
  sessionListeners.add(listener);

  return () => {
    sessionListeners.delete(listener);
  };
}

export function subscribeAuthEvents(
  listener: (event: AuthEvent) => void,
): () => void {
  eventListeners.add(listener);

  return () => {
    eventListeners.delete(listener);
  };
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }

  if (input instanceof URL) {
    return input.href;
  }

  return input.url;
}

function handleUnauthorized(sessionAtRequest: AuthSession | null): void {
  if (sessionAtRequest !== null) {
    if (getSession()?.token === sessionAtRequest.token) {
      clearSession("expired");
    }

    return;
  }

  if (getSession() === null) {
    emit({ type: "signedOut", reason: "required" });
  }
}

async function announceForbidden(response: Response): Promise<void> {
  let message = FORBIDDEN_FALLBACK;

  try {
    const body = (await response.clone().json()) as { message?: unknown };

    if (typeof body.message === "string" && body.message.length > 0) {
      message = body.message;
    }
  } catch {
    message = FORBIDDEN_FALLBACK;
  }

  emit({ type: "forbidden", message });
}

export function installAuthFetch(): void {
  if (typeof window === "undefined") {
    return;
  }

  const marker = window as unknown as { __terrascopeAuthFetch?: boolean };

  if (marker.__terrascopeAuthFetch === true) {
    return;
  }

  marker.__terrascopeAuthFetch = true;

  const original = window.fetch.bind(window);

  window.fetch = async (input, init) => {
    const url = requestUrl(input);

    if (!url.startsWith(API_BASE_URL)) {
      return original(input, init);
    }

    const session = getSession();

    let nextInput = input;
    let nextInit = init;

    if (session !== null) {
      const headers = new Headers(
        init?.headers ?? (input instanceof Request ? input.headers : undefined),
      );

      if (!headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${session.token}`);
      }

      if (input instanceof Request && init === undefined) {
        nextInput = new Request(input, { headers });
      } else {
        nextInit = { ...init, headers };
      }
    }

    const response = await original(nextInput, nextInit);

    if (url.split("?")[0] === `${API_BASE_URL}/auth/login`) {
      return response;
    }

    if (response.status === 401) {
      handleUnauthorized(session);
    } else if (response.status === 403) {
      void announceForbidden(response);
    }

    return response;
  };
}

async function failure(
  response: Response,
  fallback: string,
): Promise<AuthRequestError> {
  let code = "REQUEST_FAILED";
  let message = fallback;

  try {
    const body = (await response.json()) as {
      code?: unknown;
      message?: unknown;
    };

    if (typeof body.code === "string") {
      code = body.code;
    }

    if (typeof body.message === "string" && body.message.length > 0) {
      message = body.message;
    }
  } catch {
    message = fallback;
  }

  return new AuthRequestError(response.status, code, message);
}

async function send<T>(
  path: string,
  method: "GET" | "POST" | "PATCH",
  body: unknown,
  fallback: string,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    cache: "no-store",
    signal,
    headers:
      body === undefined
        ? { Accept: "application/json" }
        : { Accept: "application/json", "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    throw await failure(response, fallback);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function sessionFrom(payload: unknown): AuthSession {
  const record = (payload ?? {}) as Record<string, unknown>;
  const user = parseUser(record.user);

  if (
    user === null
    || typeof record.token !== "string"
    || typeof record.expiresAt !== "string"
  ) {
    throw new AuthRequestError(
      500,
      "BAD_RESPONSE",
      "The server sent an unexpected sign-in response.",
    );
  }

  return { token: record.token, expiresAt: record.expiresAt, user };
}

function userFrom(payload: unknown): AuthUser {
  const user = parseUser(payload);

  if (user === null) {
    throw new AuthRequestError(
      500,
      "BAD_RESPONSE",
      "The server sent an unexpected user record.",
    );
  }

  return user;
}

export async function fetchAuthConfig(
  signal?: AbortSignal,
): Promise<{ enabled: boolean }> {
  const payload = await send<{ enabled?: unknown }>(
    "/auth/config",
    "GET",
    undefined,
    "Unable to check sign-in settings.",
    signal,
  );

  return { enabled: payload.enabled === true };
}

export async function signIn(
  username: string,
  password: string,
): Promise<AuthSession> {
  const session = sessionFrom(
    await send<unknown>(
      "/auth/login",
      "POST",
      { username: username.trim(), password },
      "Unable to sign in.",
    ),
  );

  setSession(session);

  return session;
}

export async function fetchCurrentUser(
  signal?: AbortSignal,
): Promise<AuthUser> {
  const user = userFrom(
    await send<unknown>(
      "/auth/me",
      "GET",
      undefined,
      "Unable to load your account.",
      signal,
    ),
  );

  const session = getSession();

  if (session !== null) {
    setSession({ ...session, user });
  }

  return user;
}

export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  const session = sessionFrom(
    await send<unknown>(
      "/auth/password",
      "POST",
      { currentPassword, newPassword },
      "Unable to change the password.",
    ),
  );

  setSession(session);
}

export async function fetchUsers(signal?: AbortSignal): Promise<AuthUser[]> {
  const payload = await send<unknown[]>(
    "/admin/users",
    "GET",
    undefined,
    "Unable to load users.",
    signal,
  );

  return payload.map(userFrom);
}

export async function createUser(input: NewUserInput): Promise<AuthUser> {
  return userFrom(
    await send<unknown>(
      "/admin/users",
      "POST",
      {
        username: input.username.trim(),
        displayName: input.displayName.trim(),
        role: input.role,
        password: input.password,
      },
      "Unable to create the user.",
    ),
  );
}

export async function updateUser(
  id: string,
  patch: UserPatch,
): Promise<AuthUser> {
  return userFrom(
    await send<unknown>(
      `/admin/users/${encodeURIComponent(id)}`,
      "PATCH",
      patch,
      "Unable to update the user.",
    ),
  );
}

export async function resetUserPassword(
  id: string,
  newPassword: string,
): Promise<void> {
  await send<void>(
    `/admin/users/${encodeURIComponent(id)}/password`,
    "POST",
    { newPassword },
    "Unable to reset the password.",
  );
}

installAuthFetch();
