export type GovernanceTrustAnchorView = {
  keyId: string;

  algorithm: string;

  publicKeyFingerprint: string;

  status:
    | "ACTIVE"
    | "RETIRED";

  supersededByKeyId: string | null;
  retirementReason: string;

  createdAt: string;
  lastUsedAt: string;
  retiredAt: string | null;
};

export type GovernanceTrustAnchorEvent = {
  id: string;

  keyId: string;

  eventType:
    | "REGISTERED"
    | "SIGNATURE_ISSUED"
    | "RETIRED";

  replacementKeyId: string | null;

  detail: string;

  createdAt: string;
};

export type GovernanceTrustAnchorHistoryResponse = {
  anchors: GovernanceTrustAnchorView[];
  events: GovernanceTrustAnchorEvent[];
};

export type RegisterGovernanceTrustAnchorRequest = {
  keyId: string;
  publicKeyX509Base64: string;
};

export type RetireGovernanceTrustAnchorRequest = {
  replacementKeyId: string;
  reason: string;
};
