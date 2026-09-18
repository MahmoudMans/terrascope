export type GovernanceAssuranceBundlePreview = {
  certificateId: string;
  certificateNumber: number;

  bundleVersion: string;
  bundleHash: string;

  signatureCount: number;
  trustAnchorCount: number;
  retiredAnchorCount: number;
  lifecycleEventCount: number;

  portableVerificationValid: boolean;

  generatedAt: string;
};

export type GovernanceAssuranceBundle = {
  id: string;

  bundleNumber: number;

  certificateId: string;
  certificateNumber: number;

  bundleVersion: string;
  bundleHash: string;

  signatureCount: number;
  trustAnchorCount: number;
  retiredAnchorCount: number;
  lifecycleEventCount: number;

  portableVerificationValid: boolean;

  payloadJson: string;

  createdAt: string;
};

export type GovernanceAssuranceBundleHistoryResponse = {
  bundles: GovernanceAssuranceBundle[];
};

export type GovernanceAssuranceBundleVerificationResponse = {
  valid: boolean;

  bundleId: string;
  bundleNumber: number;

  hashValid: boolean;
  canonicalPayloadValid: boolean;
  portableVerificationValid: boolean;

  signatureCount: number;
  verifiedSignatureCount: number;

  message: string;

  verifiedAt: string;
};
