export type ResilienceAuditReceipt = {
  id: string;
  incidentId: string;

  receiptNumber: number;

  evidencePackageId: string;
  evidencePackageNumber: number;

  packageManifestVersion: string;
  packageManifestHash: string;

  previousReceiptHash: string | null;

  ledgerVersion: string;
  ledgerHash: string;

  createdAt: string;
};

export type ResilienceAuditLedgerPreview = {
  incidentId: string;

  nextReceiptNumber: number;

  evidencePackageId: string;
  evidencePackageNumber: number;

  packageManifestVersion: string;
  packageManifestHash: string;

  latestPackageReceipted: boolean;

  previousReceiptHash: string | null;

  ledgerVersion: string;
  ledgerHash: string;

  generatedAt: string;
};

export type ResilienceAuditLedgerHistoryResponse = {
  incidentId: string;
  receipts: ResilienceAuditReceipt[];
};

export type ResilienceAuditLedgerVerificationResponse = {
  incidentId: string;

  valid: boolean;

  receiptCount: number;
  verifiedReceiptCount: number;

  brokenReceiptId: string | null;
  brokenReceiptNumber: number | null;

  message: string;

  verifiedAt: string;
};
