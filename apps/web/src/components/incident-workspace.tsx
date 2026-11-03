"use client";

import {
  Boxes,
  ClipboardList,
  Hammer,
  Lightbulb,
  ShieldCheck,
} from "lucide-react";

import {
  IncidentActionBoard,
} from "@/components/incident-action-board";

import {
  IncidentClosureReview,
} from "@/components/incident-closure-review";

import {
  IncidentDamageRecoveryBoard,
} from "@/components/incident-damage-recovery-board";

import {
  IncidentEvacuationBoard,
} from "@/components/incident-evacuation-board";

import {
  IncidentImprovementBoard,
} from "@/components/incident-improvement-board";

import {
  IncidentLifelineBoard,
} from "@/components/incident-lifeline-board";

import {
  IncidentOverview,
} from "@/components/incident-overview";

import {
  IncidentPreparednessExercises,
} from "@/components/incident-preparedness-exercises";

import {
  IncidentResilienceAuditLedger,
} from "@/components/incident-resilience-audit-ledger";

import {
  IncidentResilienceCertification,
} from "@/components/incident-resilience-certification";

import {
  IncidentResilienceEvidence,
} from "@/components/incident-resilience-evidence";

import {
  IncidentResilienceValidation,
} from "@/components/incident-resilience-validation";

import {
  IncidentResourceBoard,
} from "@/components/incident-resource-board";

import {
  IncidentSitrepCenter,
} from "@/components/incident-sitrep-center";

import {
  WorkspaceTabs,
  type WorkspaceTab,
} from "@/components/workspace-tabs";

import type { IncidentDetail } from "@/types/incidents";

export type IncidentWorkspaceTabId =
  | "overview"
  | "operations"
  | "recovery"
  | "after-action"
  | "assurance";

type IncidentWorkspaceProps = {
  detail: IncidentDetail;
  busy: boolean;
  activeTab: IncidentWorkspaceTabId;
  onTabChange: (tab: IncidentWorkspaceTabId) => void;
  onAddNote: (message: string) => void;
  onTimelineChanged: (label: string) => void;
};

export function IncidentWorkspace({
  detail,
  busy,
  activeTab,
  onTabChange,
  onAddNote,
  onTimelineChanged,
}: IncidentWorkspaceProps) {
  const incidentId = detail.incident.id;
  const incidentStatus = detail.incident.status;

  const changed = (label: string) => () => onTimelineChanged(label);

  const tabs: Array<WorkspaceTab & { id: IncidentWorkspaceTabId }> = [
    {
      id: "overview",
      label: "Overview",
      icon: ClipboardList,
      badge: detail.timeline.length,
      description:
        "Command pulse, operational notes and the full response timeline.",
      render: () => (
        <IncidentOverview
          detail={detail}
          busy={busy}
          onAddNote={onAddNote}
          onTimelineChanged={changed("command")}
        />
      ),
    },
    {
      id: "operations",
      label: "Operations",
      icon: Boxes,
      description:
        "Response actions, resources, evacuation and critical lifelines.",
      render: () => (
        <>
          <IncidentActionBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("incident")}
          />

          <IncidentResourceBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("resource")}
          />

          <IncidentEvacuationBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("evacuation")}
          />

          <IncidentLifelineBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("lifeline")}
          />
        </>
      ),
    },
    {
      id: "recovery",
      label: "Recovery & reporting",
      icon: Hammer,
      description:
        "Damage assessment, recovery progress and situation reports.",
      render: () => (
        <>
          <IncidentDamageRecoveryBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("damage recovery")}
          />

          <IncidentSitrepCenter
            incidentId={incidentId}
            onTimelineChanged={changed("SITREP")}
          />
        </>
      ),
    },
    {
      id: "after-action",
      label: "After-action",
      icon: Lightbulb,
      description:
        "Closure review, improvement actions and preparedness exercises.",
      render: () => (
        <>
          <IncidentClosureReview
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("after-action")}
          />

          <IncidentImprovementBoard
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("improvement")}
          />

          <IncidentPreparednessExercises
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("preparedness")}
          />
        </>
      ),
    },
    {
      id: "assurance",
      label: "Resilience assurance",
      icon: ShieldCheck,
      description:
        "Validation, certification, evidence packages and the audit ledger.",
      render: () => (
        <>
          <IncidentResilienceValidation
            incidentId={incidentId}
            incidentStatus={incidentStatus}
            onTimelineChanged={changed("resilience")}
          />

          <IncidentResilienceCertification
            incidentId={incidentId}
            onTimelineChanged={changed("certification")}
          />

          <IncidentResilienceEvidence
            incidentId={incidentId}
            onTimelineChanged={changed("evidence")}
          />

          <IncidentResilienceAuditLedger
            incidentId={incidentId}
            onTimelineChanged={changed("audit-ledger")}
          />
        </>
      ),
    },
  ];

  return (
    <WorkspaceTabs
      key={incidentId}
      idPrefix="incident"
      label="Incident workspace sections"
      tabs={tabs}
      activeId={activeTab}
      onChange={(id) => onTabChange(id as IncidentWorkspaceTabId)}
    />
  );
}
