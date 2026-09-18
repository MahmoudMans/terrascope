export type GovernanceOfflineTrustReceiptCheckpoint = {
  id: string;

  checkpointNumber: number;

  coveredLedgerNumber: number;

  coveredLedgerHash: string;

  entryCount: number;

  validReceiptCount: number;

  invalidReceiptCount: number;

  keyId: string;

  algorithm: "ED25519";

  publicKeyFingerprint: string;

  checkpointVersion: string;

  signatureBase64: string;

  previousCheckpointHash: string | null;

  checkpointHash: string;

  createdAt: string;
};

export type GovernanceOfflineTrustReceiptCheckpointHistoryResponse = {
  checkpoints: GovernanceOfflineTrustReceiptCheckpoint[];
};

export type GovernanceOfflineTrustReceiptCheckpointVerificationResponse = {
  valid: boolean;

  checkpointCount: number;

  verifiedCheckpointCount: number;

  brokenCheckpointNumber: number | null;

  checkpointHeadHash: string | null;

  coveredLedgerHeadHash: string | null;

  message: string;

  verifiedAt: string;
};
