export type GovernanceOfflineTrustVerificationReceipt = {
  id: string;

  importNumber: number;

  verifierVersion: string;

  receiptHash: string;

  sourcePackageId: string | null;
  sourcePackageNumber: number | null;

  envelopeHash: string;
  packageHash: string | null;

  trustedRootFingerprint: string;
  presentedRootFingerprint: string | null;

  verificationResult:
    | "VALID"
    | "INVALID";

  packageHashValid: boolean;
  canonicalPayloadValid: boolean;
  lineageValid: boolean;
  rootPinValid: boolean;

  transitionCount: number;
  verifiedTransitionCount: number;
  trustAnchorCount: number;

  rootKeyId: string | null;
  terminalKeyId: string | null;

  offlineVerifiedAt: string;
  importedAt: string;
};

export type GovernanceOfflineTrustVerificationReceiptHistoryResponse = {
  receipts: GovernanceOfflineTrustVerificationReceipt[];
};
