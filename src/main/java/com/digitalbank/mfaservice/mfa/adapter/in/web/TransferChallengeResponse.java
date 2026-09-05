package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.ChallengeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

record TransferChallengeResponse(
        @Schema(description = "Challenge lifecycle outcome", example = "CREATED")
        String status,

        @Schema(description = "Opaque MFA challenge identifier", example = "challenge-1")
        String challengeId,

        @Schema(description = "Current challenge status", example = "OPEN")
        String challengeStatus,

        @Schema(description = "Challenge expiry instant in UTC", example = "2026-08-30T10:20:00Z")
        Instant expiresAt,

        @Schema(description = "Remaining failed verification attempts", example = "5")
        int remainingAttempts,

        @Schema(description = "Whether an existing challenge was returned", example = "false")
        boolean replayed,

        @Schema(description = "Transfer identifier bound to this challenge", example = "transfer-1")
        String transferId,

        @Schema(
                description = "Risk decision identifier bound to this challenge",
                example = "44444444-4444-4444-4444-444444444444")
        String decisionId,

        @Schema(description = "Risk policy version bound to this challenge", example = "policy-1")
        String policyVersion,

        @Schema(description = "Correlation identifier bound to this challenge", example = "transfer-1")
        String correlationId) {

    static TransferChallengeResponse from(ChallengeResult result) {
        var binding = result.transferBinding();
        return new TransferChallengeResponse(
                result.status().name(),
                result.challengeId() == null ? null : result.challengeId().value(),
                result.challengeStatus() == null
                        ? null
                        : result.challengeStatus().name(),
                result.expiresAt(),
                result.remainingAttempts(),
                result.replayed(),
                binding == null ? null : binding.transferId(),
                binding == null ? null : binding.decisionId(),
                binding == null ? null : binding.policyVersion(),
                binding == null ? null : binding.correlationId());
    }
}
