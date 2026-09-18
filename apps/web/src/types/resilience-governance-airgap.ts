export type GovernanceAssuranceEnvelopeVerificationResponse = {
  valid: boolean;

  sourceBundleId: string | null;
  sourceBundleNumber: number | null;

  envelopeVersion: string | null;
  bundleVersion: string | null;

  envelopeHash: string;
  bundleHash: string | null;

  hashValid: boolean;
  canonicalPayloadValid: boolean;
  portableVerificationValid: boolean;

  signatureCount: number;
  verifiedSignatureCount: number;

  trustAnchorCount: number;
  retiredAnchorCount: number;
  lifecycleEventCount: number;

  message: string;

  verifiedAt: string;
};

export type GovernanceAssuranceVerificationReceipt = {
  id: string;

  receiptNumber: number;

  sourceBundleId: string | null;
  sourceBundleNumber: number | null;

  envelopeVersion: string | null;
  bundleVersion: string | null;

  envelopeHash: string;
  bundleHash: string | null;

  verificationResult:
    | "VALID"
    | "INVALID";

  hashValid: boolean;
  canonicalPayloadValid: boolean;
  portableVerificationValid: boolean;

  signatureCount: number;
  verifiedSignatureCount: number;

  trustAnchorCount: number;
  retiredAnchorCount: number;
  lifecycleEventCount: number;

  message: string;

  verifiedAt: string;
};

export type GovernanceAssuranceVerificationReceiptHistoryResponse = {
  receipts: GovernanceAssuranceVerificationReceipt[];
};
