package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

record CreateTransferChallengeRequest(
        @NotBlank @Schema(description = "Opaque MFA enrollment identifier", example = "enrollment-1")
        String enrollmentId,

        @NotBlank @Schema(description = "Transfer workflow identifier", example = "transfer-1")
        String transferId,

        @NotBlank @Schema(description = "Single-use risk decision identifier", example = "decision-1")
        String decisionId,

        @NotBlank @Schema(description = "Source account identifier", example = "account-1")
        String sourceAccountId,

        @NotBlank @Schema(description = "Destination account identifier", example = "account-2")
        String destinationAccountId,

        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        @Schema(description = "Normalized transfer amount", example = "1250.75")
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") @Schema(description = "ISO 4217 currency code", example = "USD")
        String currency,

        @NotBlank @Schema(description = "Risk policy version", example = "transfer-risk-policy-2026-09")
        String policyVersion,

        @NotBlank @Schema(description = "Transfer correlation identifier", example = "transfer-1")
        String correlationId) {}
