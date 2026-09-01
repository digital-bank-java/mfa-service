package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record VerifyTotpCodeRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "must contain exactly 6 digits")
        @Schema(description = "Time-based one-time password code", example = "123456")
        String code) {}
