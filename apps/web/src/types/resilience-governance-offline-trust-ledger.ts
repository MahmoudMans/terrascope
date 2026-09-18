export type GovernanceOfflineTrustReceiptLedgerEntry = {
  id: string;

  ledgerNumber: number;

  receiptId: string;
  receiptImportNumber: number;

  verificationResult:
    | "VALID"
    | "INVALID";

  receiptHash: string;
  receiptPayloadHash: string;

  previousLedgerHash: string | null;

  ledgerVersion: string;
  ledgerHash: string;

  createdAt: string;
};

export type GovernanceOfflineTrustReceiptLedgerHistoryResponse = {
  entries: GovernanceOfflineTrustReceiptLedgerEntry[];
};

export type GovernanceOfflineTrustReceiptLedgerVerificationResponse = {
  valid: boolean;

  ledgerEntryCount: number;
  verifiedEntryCount: number;

  brokenLedgerNumber: number | null;

  headLedgerHash: string | null;

  message: string;

  verifiedAt: string;
};
