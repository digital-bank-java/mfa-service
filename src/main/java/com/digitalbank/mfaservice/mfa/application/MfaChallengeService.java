package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.ChallengeVerification;
import com.digitalbank.mfaservice.mfa.domain.ChallengeVerificationStatus;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class MfaChallengeService {

    private final EnrollmentStore enrollmentStore;
    private final ChallengeStore challengeStore;
    private final TotpProvider totpProvider;
    private final MfaIdentifierGenerator identifierGenerator;
    private final Clock clock;
    private final Duration challengeTtl;
    private final int maxAttempts;

    public MfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock,
            Duration challengeTtl,
            int maxAttempts) {
        this.enrollmentStore = Objects.requireNonNull(enrollmentStore, "enrollmentStore");
        this.challengeStore = Objects.requireNonNull(challengeStore, "challengeStore");
        this.totpProvider = Objects.requireNonNull(totpProvider, "totpProvider");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.challengeTtl = Objects.requireNonNull(challengeTtl, "challengeTtl");
        if (challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("Challenge TTL must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Challenge attempts must be between 1 and 10");
        }
        this.maxAttempts = maxAttempts;
    }

    public ChallengeResult create(EnrollmentId enrollmentId) {
        var enrollment = enrollmentStore.find(enrollmentId);
        if (enrollment.isEmpty()) {
            return new ChallengeResult(ChallengeOutcome.ENROLLMENT_NOT_FOUND, null, null, null, 0);
        }
        if (enrollment.orElseThrow().status() != EnrollmentStatus.ACTIVE) {
            return result(ChallengeOutcome.ENROLLMENT_NOT_ACTIVE, null);
        }
        var createdAt = clock.instant();
        var challenge = Challenge.open(
                identifierGenerator.newChallengeId(),
                enrollmentId,
                createdAt,
                createdAt.plus(challengeTtl),
                maxAttempts);
        challengeStore.save(challenge);
        return new ChallengeResult(
                ChallengeOutcome.CREATED,
                challenge.id(),
                challenge.status(),
                challenge.expiresAt(),
                challenge.remainingAttempts());
    }

    public ChallengeResult verify(ChallengeId challengeId, String code) {
        var challenge = challengeStore.find(challengeId);
        if (challenge.isEmpty()) {
            return result(ChallengeOutcome.NOT_FOUND, challengeId);
        }
        var record = challenge.orElseThrow();
        if (record.status() != ChallengeStatus.OPEN || !clock.instant().isBefore(record.expiresAt())) {
            var verification = record.verify(clock::instant, ignored -> false);
            return result(record, verification);
        }
        var enrollment = enrollmentStore.find(record.enrollmentId());
        if (enrollment.isEmpty()) {
            return result(ChallengeOutcome.ENROLLMENT_NOT_FOUND, challengeId);
        }
        var verification = record.verify(
                clock::instant,
                verificationAt -> enrollment.orElseThrow().verifyActive(totpProvider, code, verificationAt));
        return result(record, verification);
    }

    private ChallengeResult result(ChallengeOutcome outcome, ChallengeId challengeId) {
        if (challengeId == null) {
            return new ChallengeResult(outcome, null, null, null, 0);
        }
        return challengeStore
                .find(challengeId)
                .map(challenge -> new ChallengeResult(
                        outcome,
                        challenge.id(),
                        challenge.status(),
                        challenge.expiresAt(),
                        challenge.remainingAttempts()))
                .orElse(new ChallengeResult(outcome, challengeId, null, null, 0));
    }

    private ChallengeResult result(Challenge record, ChallengeVerification verification) {
        return new ChallengeResult(
                mapOutcome(verification.status()),
                record.id(),
                verification.challengeStatus(),
                record.expiresAt(),
                verification.remainingAttempts());
    }

    private static ChallengeOutcome mapOutcome(ChallengeVerificationStatus status) {
        return switch (status) {
            case VERIFIED -> ChallengeOutcome.VERIFIED;
            case INVALID_CODE -> ChallengeOutcome.INVALID_CODE;
            case EXPIRED -> ChallengeOutcome.EXPIRED;
            case EXHAUSTED -> ChallengeOutcome.EXHAUSTED;
            case REPLAYED -> ChallengeOutcome.REPLAYED;
        };
    }
}
