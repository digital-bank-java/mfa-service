package com.digitalbank.mfaservice.mfa.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class Challenge {

    private final ChallengeId id;
    private final EnrollmentId enrollmentId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final int maxAttempts;
    private int failedAttempts;
    private ChallengeStatus status;

    private Challenge(
            ChallengeId id, EnrollmentId enrollmentId, Instant createdAt, Instant expiresAt, int maxAttempts) {
        this.id = Objects.requireNonNull(id, "id");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Challenge expiry must be after creation");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Challenge attempts must be between 1 and 10");
        }
        this.maxAttempts = maxAttempts;
        this.status = ChallengeStatus.OPEN;
    }

    public static Challenge open(
            ChallengeId id, EnrollmentId enrollmentId, Instant createdAt, Instant expiresAt, int maxAttempts) {
        return new Challenge(id, enrollmentId, createdAt, expiresAt, maxAttempts);
    }

    public ChallengeId id() {
        return id;
    }

    public EnrollmentId enrollmentId() {
        return enrollmentId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public synchronized ChallengeStatus status() {
        return status;
    }

    public synchronized int remainingAttempts() {
        return maxAttempts - failedAttempts;
    }

    public synchronized ChallengeVerificationStatus verify(Instant now, BooleanSupplier codeVerifier) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(codeVerifier, "codeVerifier");
        if (status == ChallengeStatus.CONSUMED) {
            return ChallengeVerificationStatus.REPLAYED;
        }
        if (status == ChallengeStatus.EXHAUSTED) {
            return ChallengeVerificationStatus.EXHAUSTED;
        }
        if (status == ChallengeStatus.EXPIRED || !now.isBefore(expiresAt)) {
            status = ChallengeStatus.EXPIRED;
            return ChallengeVerificationStatus.EXPIRED;
        }
        if (codeVerifier.getAsBoolean()) {
            status = ChallengeStatus.CONSUMED;
            return ChallengeVerificationStatus.VERIFIED;
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            status = ChallengeStatus.EXHAUSTED;
            return ChallengeVerificationStatus.EXHAUSTED;
        }
        return ChallengeVerificationStatus.INVALID_CODE;
    }

    @Override
    public synchronized String toString() {
        return "Challenge[id=%s, enrollmentId=%s, createdAt=%s, expiresAt=%s, status=%s, remainingAttempts=%d]"
                .formatted(id, enrollmentId, createdAt, expiresAt, status, remainingAttempts());
    }
}
