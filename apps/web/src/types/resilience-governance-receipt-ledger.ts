export type GovernanceAssuranceReceiptLedgerEntry = {
  id: string;

  ledgerNumber: number;

  receiptId: string;
  receiptNumber: number;

  verificationResult:
    | "VALID"
    | "INVALID";

  envelopeHash: string;
  bundleHash: string | null;

  receiptPayloadHash: string;

  previousLedgerHash: string | null;

  ledgerVersion: string;
  ledgerHash: string;

  createdAt: string;
};

export type GovernanceAssuranceReceiptLedgerHistoryResponse = {
  entries: GovernanceAssuranceReceiptLedgerEntry[];
};

export type GovernanceAssuranceReceiptLedgerVerificationResponse = {
  valid: boolean;

  entryCount: number;
  verifiedEntryCount: number;

  brokenLedgerEntryId: string | null;
  brokenLedgerNumber: number | null;

  brokenReceiptId: string | null;
  brokenReceiptNumber: number | null;

  message: string;

  verifiedAt: string;
};
