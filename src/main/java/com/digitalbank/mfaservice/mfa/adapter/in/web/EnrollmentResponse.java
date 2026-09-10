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
                        "One-time authenticator provisioning URI. Present only in the creation response; never retrievable later.",
                example =
                        "otpauth://totp/Digital%20Bank?secret=BASE32SECRET&issuer=Digital%20Bank&algorithm=SHA1&digits=6&period=30")
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

    @Override
    public String toString() {
        return "EnrollmentResponse[status=%s, enrollmentId=%s, enrollmentStatus=%s, provisioningUri=REDACTED]"
                .formatted(status, enrollmentId, enrollmentStatus);
    }
}
