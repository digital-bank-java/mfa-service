package com.digitalbank.mfaservice.mfa.application;

public enum ChallengeOutcome {
    CREATED,
    VERIFIED,
    INVALID_CODE,
    EXPIRED,
    EXHAUSTED,
    REPLAYED,
    NOT_FOUND,
    ENROLLMENT_NOT_FOUND,
    ENROLLMENT_NOT_ACTIVE,
    BINDING_MISMATCH
}
