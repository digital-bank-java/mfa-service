package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.time.Instant;
import java.util.Objects;

public record ChallengeResult(
        ChallengeOutcome status,
        ChallengeId challengeId,
        ChallengeStatus challengeStatus,
        Instant expiresAt,
        int remainingAttempts,
        boolean replayed,
        TransferChallengeBinding transferBinding) {

    public ChallengeResult(
            ChallengeOutcome status,
            ChallengeId challengeId,
            ChallengeStatus challengeStatus,
            Instant expiresAt,
            int remainingAttempts) {
        this(status, challengeId, challengeStatus, expiresAt, remainingAttempts, false, null);
    }

    public ChallengeResult {
        Objects.requireNonNull(status, "status");
    }
}
