package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

record VerifyTransferChallengeRequest(
        @NotBlank @Schema(description = "Transfer workflow identifier", example = "transfer-1")
        String transferId,

        @NotBlank @Schema(description = "Risk decision identifier", example = "44444444-4444-4444-4444-444444444444")
        String decisionId,

        @NotBlank @Schema(description = "Six-digit TOTP code", example = "123456")
        String code) {}
