"use client";

import {
  Archive,
  FileKey2,
  Link2,
  PenLine,
  Target,
} from "lucide-react";

import { useState } from "react";

import {
  ResilienceBenefitRealization,
} from "@/components/resilience-benefit-realization";

import {
  ResilienceGovernanceAirgap,
} from "@/components/resilience-governance-airgap";

import {
  ResilienceGovernanceAssuranceBundles,
} from "@/components/resilience-governance-assurance-bundles";

import {
  ResilienceGovernanceClosure,
} from "@/components/resilience-governance-closure";

import {
  ResilienceGovernanceOfflineTrustBundles,
} from "@/components/resilience-governance-offline-trust-bundles";

import {
  ResilienceGovernanceOfflineTrustCheckpoints,
} from "@/components/resilience-governance-offline-trust-checkpoints";

import {
  ResilienceGovernanceOfflineTrustLedger,
} from "@/components/resilience-governance-offline-trust-ledger";

import {
  ResilienceGovernanceOfflineVerifier,
} from "@/components/resilience-governance-offline-verifier";

import {
  ResilienceGovernanceReceiptCheckpoints,
} from "@/components/resilience-governance-receipt-checkpoints";

import {
  ResilienceGovernanceReceiptLedger,
} from "@/components/resilience-governance-receipt-ledger";

import {
  ResilienceGovernanceSignatures,
} from "@/components/resilience-governance-signatures";

import {
  ResilienceGovernanceTrustLifecycle,
} from "@/components/resilience-governance-trust-lifecycle";

import {
  ResilienceGovernanceTrustLineage,
} from "@/components/resilience-governance-trust-lineage";

import {
  ResilienceGovernanceTrustRootPin,
} from "@/components/resilience-governance-trust-root-pin";

import {
  ResilienceGovernanceTrustTransitions,
} from "@/components/resilience-governance-trust-transitions";

import {
  ResilienceObjectiveOutcomes,
} from "@/components/resilience-objective-outcomes";

import {
  ResiliencePortfolioReview,
} from "@/components/resilience-portfolio-review";

import {
  ResiliencePortfolioScorecard,
} from "@/components/resilience-portfolio-scorecard";

import {
  ResilienceStrategicObjectives,
} from "@/components/resilience-strategic-objectives";

import {
  WorkspaceTabs,
  type WorkspaceTab,
} from "@/components/workspace-tabs";

const tabs: WorkspaceTab[] = [
  {
    id: "portfolio",
    label: "Portfolio",
    icon: Target,
    description:
      "Scorecard, reviews, strategic objectives, outcomes and realised benefits.",
    render: () => (
      <>
        <ResiliencePortfolioScorecard />
        <ResiliencePortfolioReview />
        <ResilienceStrategicObjectives />
        <ResilienceObjectiveOutcomes />
        <ResilienceBenefitRealization />
      </>
    ),
  },
  {
    id: "closure",
    label: "Closure & signing",
    icon: PenLine,
    description:
      "Governance closure certificates and their Ed25519 signatures.",
    render: () => (
      <>
        <ResilienceGovernanceClosure />
        <ResilienceGovernanceSignatures />
      </>
    ),
  },
  {
    id: "trust-chain",
    label: "Trust chain",
    icon: Link2,
    description:
      "Trust-anchor lifecycle, transitions, lineage packages and root pins.",
    render: () => (
      <>
        <ResilienceGovernanceTrustLifecycle />
        <ResilienceGovernanceTrustTransitions />
        <ResilienceGovernanceTrustLineage />
        <ResilienceGovernanceTrustRootPin />
      </>
    ),
  },
  {
    id: "ledgers",
    label: "Ledgers & receipts",
    icon: Archive,
    description:
      "Transparency ledgers and signed checkpoints for online and offline receipts.",
    render: () => (
      <>
        <ResilienceGovernanceReceiptLedger />
        <ResilienceGovernanceReceiptCheckpoints />
        <ResilienceGovernanceOfflineTrustLedger />
        <ResilienceGovernanceOfflineTrustCheckpoints />
      </>
    ),
  },
  {
    id: "offline",
    label: "Offline verification",
    icon: FileKey2,
    description:
      "Assurance bundles, air-gap packages and the offline verifier.",
    render: () => (
      <>
        <ResilienceGovernanceAssuranceBundles />
        <ResilienceGovernanceAirgap />
        <ResilienceGovernanceOfflineVerifier />
        <ResilienceGovernanceOfflineTrustBundles />
      </>
    ),
  },
];

export function PortfolioGovernanceWorkspace() {
  const [activeTab, setActiveTab] = useState("portfolio");

  return (
    <WorkspaceTabs
      idPrefix="portfolio"
      label="Portfolio governance sections"
      tabs={tabs}
      activeId={activeTab}
      onChange={setActiveTab}
    />
  );
}
