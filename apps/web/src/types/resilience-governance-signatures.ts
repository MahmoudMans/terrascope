export type GovernanceSigningReadinessResponse = {
  ready: boolean;

  algorithm: string;

  keyId: string | null;
  publicKeyFingerprint: string | null;

  trustAnchorRegistered: boolean;

  message: string;

  generatedAt: string;
};

export type GovernanceClosureSignature = {
  id: string;

  certificateId: string;
  certificateNumber: number;

  keyId: string;
  algorithm: string;

  publicKeyFingerprint: string;

  signatureVersion: string;

  certificateHash: string;
  dossierHash: string;

  signatureBase64: string;

  createdAt: string;
};

export type GovernanceClosureSignatureHistoryResponse = {
  signatures: GovernanceClosureSignature[];
};

export type GovernanceSignatureVerificationResponse = {
  valid: boolean;

  signatureCount: number;
  verifiedSignatureCount: number;

  brokenSignatureId: string | null;
  brokenCertificateId: string | null;
  brokenCertificateNumber: number | null;

  message: string;

  verifiedAt: string;
};
