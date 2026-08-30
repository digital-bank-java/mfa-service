package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.EnrollmentResult;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EnrollmentResponse(
        @Schema(description = "Enrollment lifecycle outcome", example = "ENROLLED")
        String status,

        @Schema(description = "Opaque MFA enrollment identifier", example = "enrollment-1")
        String enrollmentId,

        @Schema(description = "Current enrollment status", example = "PENDING")
        String enrollmentStatus,

        @Schema(
                description =
                        "One-time TOTP provisioning URI returned only when an enrollment is created and not on later reads or verifications",
                example = "otpauth://totp/Digital%20Bank:customer-123?secret=BASE32SECRET&issuer=Digital%20Bank")
        String provisioningUri) {

    static EnrollmentResponse from(EnrollmentResult result) {
        return new EnrollmentResponse(
                result.status().name(),
                result.enrollmentId() == null ? null : result.enrollmentId().value(),
                result.enrollmentStatus() == null
                        ? null
                        : result.enrollmentStatus().name(),
                result.provisioningUri());
    }

    boolean isActive() {
        return EnrollmentStatus.ACTIVE.name().equals(enrollmentStatus);
    }
}
