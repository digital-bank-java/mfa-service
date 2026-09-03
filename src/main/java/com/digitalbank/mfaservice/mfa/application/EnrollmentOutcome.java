package com.digitalbank.mfaservice.mfa.application;

public enum EnrollmentOutcome {
    ENROLLED,
    ACTIVATED,
    INVALID_CODE,
    NOT_FOUND,
    ALREADY_ACTIVE
}
