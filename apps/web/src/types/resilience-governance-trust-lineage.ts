export type GovernanceTrustLineagePackage = {
  id: string;

  packageNumber: number;

  packageVersion: string;

  transitionCount: number;
  trustAnchorCount: number;

  firstTransitionNumber: number;
  lastTransitionNumber: number;

  lineageHeadHash: string;

  payloadJson: string;
  packageHash: string;

  createdAt: string;
};

export type GovernanceTrustLineagePackageHistoryResponse = {
  packages: GovernanceTrustLineagePackage[];
};

export type GovernanceTrustLineageVerificationResponse = {
  valid: boolean;

  hashValid: boolean;
  canonicalPayloadValid: boolean;
  lineageValid: boolean;

  transitionCount: number;
  verifiedTransitionCount: number;

  trustAnchorCount: number;

  rootKeyId: string | null;
  terminalKeyId: string | null;

  message: string;

  verifiedAt: string;
};
