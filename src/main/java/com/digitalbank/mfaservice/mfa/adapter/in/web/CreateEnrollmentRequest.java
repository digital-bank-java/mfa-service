package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

record CreateEnrollmentRequest(
        @NotBlank
        @Schema(description = "Opaque subject identifier that owns the MFA enrollment", example = "customer-123")
        String subjectId) {}
