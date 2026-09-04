package com.digitalbank.mfaservice.mfa.domain;

import java.time.Instant;
import java.util.Objects;

public record ChallengeVerification(
        ChallengeVerificationStatus status,
        ChallengeStatus challengeStatus,
        int remainingAttempts,
        Instant verifiedAt) {

    public ChallengeVerification(
            ChallengeVerificationStatus status, ChallengeStatus challengeStatus, int remainingAttempts) {
        this(status, challengeStatus, remainingAttempts, null);
    }

    public ChallengeVerification {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(challengeStatus, "challengeStatus");
    }
}
