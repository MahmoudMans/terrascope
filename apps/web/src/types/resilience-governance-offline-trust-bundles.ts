export type GovernanceOfflineTrustCheckpointBundle = {
  id: string;

  bundleNumber: number;

  sourceCheckpointId: string;

  sourceCheckpointNumber: number;

  coveredLedgerNumber: number;

  checkpointHash: string;

  signingKeyId: string;

  signingPublicKeyFingerprint: string;

  bundleVersion: string;

  payloadJson: string;

  bundleHash: string;

  createdAt: string;
};

export type GovernanceOfflineTrustCheckpointBundleHistoryResponse = {
  bundles: GovernanceOfflineTrustCheckpointBundle[];
};

export type GovernanceOfflineTrustCheckpointBundleVerificationResponse = {
  valid: boolean;

  bundleHashValid: boolean;

  canonicalPayloadValid: boolean;

  ledgerValid: boolean;

  checkpointValid: boolean;

  signingKeyPinValid: boolean;

  ledgerEntryCount: number;

  verifiedLedgerEntryCount: number;

  checkpointNumber: number | null;

  coveredLedgerNumber: number | null;

  bundleHash: string | null;

  checkpointHash: string | null;

  message: string;

  verifiedAt: string;
};
