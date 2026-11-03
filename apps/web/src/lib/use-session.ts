"use client";

import { useSyncExternalStore } from "react";

import { getSession, subscribeSession, type AuthSession } from "@/lib/auth";

function serverSnapshot(): AuthSession | null {
  return null;
}

export function useSession(): AuthSession | null {
  return useSyncExternalStore(subscribeSession, getSession, serverSnapshot);
}
