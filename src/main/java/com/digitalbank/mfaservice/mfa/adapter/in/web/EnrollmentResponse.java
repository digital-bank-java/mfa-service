package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.EnrollmentResult;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

record EnrollmentResponse(
        @Schema(description = "Enrollment lifecycle outcome", example = "ENROLLED")
        String status,

        @Schema(description = "Opaque MFA enrollment identifier", example = "enrollment-1")
        String enrollmentId,

        @Schema(description = "Current enrollment status", example = "PENDING")
        String enrollmentStatus) {

    static EnrollmentResponse from(EnrollmentResult result) {
        return new EnrollmentResponse(
                result.status().name(),
                result.enrollmentId() == null ? null : result.enrollmentId().value(),
                result.enrollmentStatus() == null
                        ? null
                        : result.enrollmentStatus().name());
    }

    boolean isActive() {
        return EnrollmentStatus.ACTIVE.name().equals(enrollmentStatus);
    }
}
