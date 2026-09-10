package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import java.util.Objects;

public record EnrollmentResult(
        EnrollmentOutcome status,
        EnrollmentId enrollmentId,
        EnrollmentStatus enrollmentStatus,
        String provisioningUri) {

    public EnrollmentResult {
        Objects.requireNonNull(status, "status");
    }

    @Override
    public String toString() {
        return "EnrollmentResult[status=%s, enrollmentId=%s, enrollmentStatus=%s, provisioningUri=REDACTED]"
                .formatted(status, enrollmentId, enrollmentStatus);
    }
}
