package com.digitalbank.mfaservice.mfa.domain;

public enum ChallengeVerificationStatus {
    VERIFIED,
    INVALID_CODE,
    EXPIRED,
    EXHAUSTED,
    REPLAYED
}
