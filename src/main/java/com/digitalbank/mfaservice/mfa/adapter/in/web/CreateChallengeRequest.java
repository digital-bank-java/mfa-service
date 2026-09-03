package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

record CreateChallengeRequest(
        @NotBlank @Schema(description = "Opaque MFA enrollment identifier", example = "enrollment-1")
        String enrollmentId) {}
