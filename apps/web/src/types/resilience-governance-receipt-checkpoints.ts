export type GovernanceAssuranceReceiptCheckpoint = {
  id: string;

  checkpointNumber: number;

  coveredLedgerNumber: number;
  coveredLedgerHash: string;

  entryCount: number;
  validReceiptCount: number;
  invalidReceiptCount: number;

  keyId: string;
  algorithm: string;
  publicKeyFingerprint: string;

  checkpointVersion: string;

  signatureBase64: string;

  previousCheckpointHash: string | null;
  checkpointHash: string;

  createdAt: string;
};

export type GovernanceAssuranceReceiptCheckpointHistoryResponse = {
  checkpoints: GovernanceAssuranceReceiptCheckpoint[];
};

export type GovernanceAssuranceReceiptCheckpointVerificationResponse = {
  valid: boolean;

  checkpointCount: number;
  verifiedCheckpointCount: number;

  brokenCheckpointId: string | null;
  brokenCheckpointNumber: number | null;

  brokenLedgerNumber: number | null;

  message: string;

  verifiedAt: string;
};
