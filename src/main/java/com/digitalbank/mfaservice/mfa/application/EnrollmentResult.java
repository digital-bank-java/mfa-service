package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import java.util.Objects;

public record EnrollmentResult(EnrollmentOutcome status, EnrollmentId enrollmentId, EnrollmentStatus enrollmentStatus) {

    public EnrollmentResult {
        Objects.requireNonNull(status, "status");
    }
}
