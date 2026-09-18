export type CreateGovernanceTrustAnchorTransitionRequest = {
  replacementKeyId: string;
  replacementPublicKeyX509Base64: string;
  reason: string;
};

export type GovernanceTrustAnchorTransition = {
  id: string;

  transitionNumber: number;

  outgoingKeyId: string;
  outgoingPublicKeyFingerprint: string;

  replacementKeyId: string;
  replacementPublicKeyFingerprint: string;

  algorithm: string;

  reason: string;

  transitionVersion: string;

  signatureBase64: string;

  previousTransitionHash: string | null;
  transitionHash: string;

  createdAt: string;
};

export type GovernanceTrustAnchorTransitionHistoryResponse = {
  transitions: GovernanceTrustAnchorTransition[];
};

export type GovernanceTrustAnchorTransitionVerificationResponse = {
  valid: boolean;

  transitionCount: number;
  verifiedTransitionCount: number;

  brokenTransitionId: string | null;
  brokenTransitionNumber: number | null;

  brokenOutgoingKeyId: string | null;
  brokenReplacementKeyId: string | null;

  message: string;

  verifiedAt: string;
};
