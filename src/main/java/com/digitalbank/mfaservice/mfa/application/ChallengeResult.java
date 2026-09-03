package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import java.time.Instant;
import java.util.Objects;

public record ChallengeResult(
        ChallengeOutcome status,
        ChallengeId challengeId,
        ChallengeStatus challengeStatus,
        Instant expiresAt,
        int remainingAttempts) {

    public ChallengeResult {
        Objects.requireNonNull(status, "status");
    }
}
