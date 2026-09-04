package com.digitalbank.mfaservice.mfa.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Challenge {

    public static final Duration MAX_TTL = Duration.ofMinutes(15);

    private final ChallengeId id;
    private final EnrollmentId enrollmentId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final int maxAttempts;
    private final TransferChallengeBinding transferBinding;
    private int failedAttempts;
    private ChallengeStatus status;

    private Challenge(
            ChallengeId id,
            EnrollmentId enrollmentId,
            Instant createdAt,
            Instant expiresAt,
            int maxAttempts,
            int failedAttempts,
            ChallengeStatus status,
            TransferChallengeBinding transferBinding) {
        this.id = Objects.requireNonNull(id, "id");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Challenge expiry must be after creation");
        }
        if (Duration.between(createdAt, expiresAt).compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("Challenge TTL must not exceed 15 minutes");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Challenge attempts must be between 1 and 10");
        }
        if (failedAttempts < 0 || failedAttempts > maxAttempts) {
            throw new IllegalArgumentException("Challenge failed attempts must be between 0 and the attempt limit");
        }
        this.maxAttempts = maxAttempts;
        this.failedAttempts = failedAttempts;
        this.status = Objects.requireNonNull(status, "status");
        this.transferBinding = transferBinding;
    }

    public static Challenge open(
            ChallengeId id, EnrollmentId enrollmentId, Instant createdAt, Instant expiresAt, int maxAttempts) {
        return open(id, enrollmentId, createdAt, expiresAt, maxAttempts, null);
    }

    public static Challenge open(
            ChallengeId id,
            EnrollmentId enrollmentId,
            Instant createdAt,
            Instant expiresAt,
            int maxAttempts,
            TransferChallengeBinding transferBinding) {
        return new Challenge(
                id, enrollmentId, createdAt, expiresAt, maxAttempts, 0, ChallengeStatus.OPEN, transferBinding);
    }

    public static Challenge restore(
            ChallengeId id,
            EnrollmentId enrollmentId,
            Instant createdAt,
            Instant expiresAt,
            int maxAttempts,
            int failedAttempts,
            ChallengeStatus status) {
        return restore(id, enrollmentId, createdAt, expiresAt, maxAttempts, failedAttempts, status, null);
    }

    public static Challenge restore(
            ChallengeId id,
            EnrollmentId enrollmentId,
            Instant createdAt,
            Instant expiresAt,
            int maxAttempts,
            int failedAttempts,
            ChallengeStatus status,
            TransferChallengeBinding transferBinding) {
        return new Challenge(
                id, enrollmentId, createdAt, expiresAt, maxAttempts, failedAttempts, status, transferBinding);
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

    public int maxAttempts() {
        return maxAttempts;
    }

    public synchronized int failedAttempts() {
        return failedAttempts;
    }

    public TransferChallengeBinding transferBinding() {
        return transferBinding;
    }

    public synchronized ChallengeVerification verify(
            Supplier<Instant> nowSupplier, Function<Instant, Boolean> codeVerifier) {
        Objects.requireNonNull(nowSupplier, "nowSupplier");
        Objects.requireNonNull(codeVerifier, "codeVerifier");
        var now = Objects.requireNonNull(nowSupplier.get(), "now");
        if (status == ChallengeStatus.CONSUMED) {
            return snapshot(ChallengeVerificationStatus.REPLAYED);
        }
        if (status == ChallengeStatus.EXHAUSTED) {
            return snapshot(ChallengeVerificationStatus.EXHAUSTED);
        }
        if (status == ChallengeStatus.EXPIRED || !now.isBefore(expiresAt)) {
            status = ChallengeStatus.EXPIRED;
            return snapshot(ChallengeVerificationStatus.EXPIRED);
        }
        var verificationAt = Objects.requireNonNull(nowSupplier.get(), "verificationAt");
        if (!verificationAt.isBefore(expiresAt)) {
            status = ChallengeStatus.EXPIRED;
            return snapshot(ChallengeVerificationStatus.EXPIRED);
        }
        var codeAccepted = Boolean.TRUE.equals(codeVerifier.apply(verificationAt));
        if (!Objects.requireNonNull(nowSupplier.get(), "completionTime").isBefore(expiresAt)) {
            status = ChallengeStatus.EXPIRED;
            return snapshot(ChallengeVerificationStatus.EXPIRED);
        }
        if (codeAccepted) {
            status = ChallengeStatus.CONSUMED;
            return snapshot(ChallengeVerificationStatus.VERIFIED, verificationAt);
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            status = ChallengeStatus.EXHAUSTED;
            return snapshot(ChallengeVerificationStatus.EXHAUSTED);
        }
        return snapshot(ChallengeVerificationStatus.INVALID_CODE);
    }

    private ChallengeVerification snapshot(ChallengeVerificationStatus verificationStatus) {
        return snapshot(verificationStatus, null);
    }

    private ChallengeVerification snapshot(ChallengeVerificationStatus verificationStatus, Instant verifiedAt) {
        return new ChallengeVerification(verificationStatus, status, maxAttempts - failedAttempts, verifiedAt);
    }

    @Override
    public synchronized String toString() {
        return "Challenge[id=%s, enrollmentId=%s, createdAt=%s, expiresAt=%s, status=%s, remainingAttempts=%d]"
                .formatted(id, enrollmentId, createdAt, expiresAt, status, remainingAttempts());
    }
}
