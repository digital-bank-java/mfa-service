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

    public EnrollmentResult(EnrollmentOutcome status, EnrollmentId enrollmentId, EnrollmentStatus enrollmentStatus) {
        this(status, enrollmentId, enrollmentStatus, null);
    }

    @Override
    public String toString() {
        return "EnrollmentResult[status=%s, enrollmentId=%s, enrollmentStatus=%s, provisioningUri=%s]"
                .formatted(status, enrollmentId, enrollmentStatus, provisioningUri == null ? null : "REDACTED");
    }
}
