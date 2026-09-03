package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.ChallengeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

record ChallengeResponse(
        @Schema(description = "Challenge lifecycle outcome", example = "CREATED")
        String status,

        @Schema(description = "Opaque MFA challenge identifier", example = "challenge-1")
        String challengeId,

        @Schema(description = "Current challenge status", example = "OPEN")
        String challengeStatus,

        @Schema(description = "Challenge expiry instant in UTC", example = "2026-08-30T10:20:00Z")
        Instant expiresAt,

        @Schema(description = "Remaining failed verification attempts", example = "5")
        int remainingAttempts) {

    static ChallengeResponse from(ChallengeResult result) {
        return new ChallengeResponse(
                result.status().name(),
                result.challengeId() == null ? null : result.challengeId().value(),
                result.challengeStatus() == null
                        ? null
                        : result.challengeStatus().name(),
                result.expiresAt(),
                result.remainingAttempts());
    }
}
