export type GovernanceTrustLineagePinnedVerificationRequest = {
  envelopeJson: string;
  trustedRootFingerprint: string;
};

export type GovernanceTrustLineagePinnedVerificationResponse = {
  sourcePackageId: string | null;
  sourcePackageNumber: number | null;

  envelopeHash: string;
  packageHash: string | null;

  valid: boolean;

  packageHashValid: boolean;
  canonicalPayloadValid: boolean;
  lineageValid: boolean;
  rootPinValid: boolean;

  transitionCount: number;
  verifiedTransitionCount: number;
  trustAnchorCount: number;

  trustedRootFingerprint: string | null;
  presentedRootFingerprint: string | null;

  rootKeyId: string | null;
  terminalKeyId: string | null;

  message: string;

  verifiedAt: string;
};

export type GovernanceTrustLineagePinReceipt = {
  id: string;

  receiptNumber: number;

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

  message: string;

  verifiedAt: string;
};

export type GovernanceTrustLineagePinReceiptHistoryResponse = {
  receipts: GovernanceTrustLineagePinReceipt[];
};
