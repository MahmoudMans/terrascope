"use client";

import {
  Activity,
  CloudLightning,
  Flame,
  Mountain,
} from "lucide-react";

import type { HazardKind } from "@/lib/hazard-events";

type HazardIconProps = {
  kind: HazardKind;
  size?: number;
};

export function HazardIcon({ kind, size = 16 }: HazardIconProps) {
  if (kind === "earthquakes") {
    return <Activity size={size} aria-hidden="true" />;
  }

  if (kind === "volcanoes") {
    return <Mountain size={size} aria-hidden="true" />;
  }

  if (kind === "wildfires") {
    return <Flame size={size} aria-hidden="true" />;
  }

  return <CloudLightning size={size} aria-hidden="true" />;
}
