package com.digitalbank.mfaservice.mfa.domain;

import java.util.Objects;

public record ChallengeVerification(
        ChallengeVerificationStatus status, ChallengeStatus challengeStatus, int remainingAttempts) {

    public ChallengeVerification {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(challengeStatus, "challengeStatus");
    }
}
