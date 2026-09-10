package com.digitalbank.mfaservice.mfa.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

record CreateTransferChallengeRequest(
        @NotBlank @Schema(description = "Opaque MFA enrollment identifier", example = "enrollment-1")
        String enrollmentId,

        @NotBlank @Schema(description = "Transfer workflow identifier", example = "transfer-1")
        String transferId,

        @NotBlank @Schema(description = "Account reservation request identifier", example = "reservation-1")
        String reservationRequestId,

        @NotBlank
        @Schema(description = "Single-use risk decision identifier", example = "44444444-4444-4444-4444-444444444444")
        String decisionId,

        @NotBlank @Schema(description = "Risk decision request identifier", example = "decision-request-1")
        String decisionRequestId,

        @NotBlank @Schema(description = "Source account identifier", example = "11111111-1111-1111-1111-111111111111")
        String sourceAccountId,

        @NotBlank
        @Schema(description = "Destination account identifier", example = "22222222-2222-2222-2222-222222222222")
        String destinationAccountId,

        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        @Schema(description = "Normalized transfer amount", example = "1250.75")
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") @Schema(description = "ISO 4217 currency code", example = "USD")
        String currency,

        @NotBlank @Schema(description = "Risk policy version", example = "transfer-risk-policy-2026-09")
        String policyVersion,

        @NotNull
        @Schema(
                description = "Expiry instant of the originating transfer risk decision",
                example = "2026-08-30T10:25:00Z")
        Instant riskExpiresAt,

        @NotBlank @Schema(description = "Transfer correlation identifier", example = "transfer-1")
        String correlationId) {}
