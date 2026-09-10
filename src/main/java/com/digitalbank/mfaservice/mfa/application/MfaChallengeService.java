package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.MfaTransactionRunner;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.ChallengeVerification;
import com.digitalbank.mfaservice.mfa.domain.ChallengeVerificationStatus;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class MfaChallengeService {

    private final EnrollmentStore enrollmentStore;
    private final ChallengeStore challengeStore;
    private final TotpProvider totpProvider;
    private final MfaIdentifierGenerator identifierGenerator;
    private final Clock clock;
    private final Duration challengeTtl;
    private final int maxAttempts;
    private final MfaTransactionRunner transactionRunner;
    private final MfaAssuranceOutboxStore assuranceOutbox;

    public MfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock,
            Duration challengeTtl,
            int maxAttempts) {
        this(
                enrollmentStore,
                challengeStore,
                totpProvider,
                identifierGenerator,
                clock,
                challengeTtl,
                maxAttempts,
                MfaTransactionRunner.direct(),
                MfaAssuranceOutboxStore.noop());
    }

    public MfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock,
            Duration challengeTtl,
            int maxAttempts,
            MfaTransactionRunner transactionRunner) {
        this(
                enrollmentStore,
                challengeStore,
                totpProvider,
                identifierGenerator,
                clock,
                challengeTtl,
                maxAttempts,
                transactionRunner,
                MfaAssuranceOutboxStore.noop());
    }

    public MfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock,
            Duration challengeTtl,
            int maxAttempts,
            MfaTransactionRunner transactionRunner,
            MfaAssuranceOutboxStore assuranceOutbox) {
        this.enrollmentStore = Objects.requireNonNull(enrollmentStore, "enrollmentStore");
        this.challengeStore = Objects.requireNonNull(challengeStore, "challengeStore");
        this.totpProvider = Objects.requireNonNull(totpProvider, "totpProvider");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.challengeTtl = Objects.requireNonNull(challengeTtl, "challengeTtl");
        if (challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("Challenge TTL must be positive");
        }
        if (challengeTtl.compareTo(Challenge.MAX_TTL) > 0) {
            throw new IllegalArgumentException("Challenge TTL must not exceed 15 minutes");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Challenge attempts must be between 1 and 10");
        }
        this.maxAttempts = maxAttempts;
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
        this.assuranceOutbox = Objects.requireNonNull(assuranceOutbox, "assuranceOutbox");
    }

    public ChallengeResult create(EnrollmentId enrollmentId, String subjectId) {
        return transactionRunner.execute(() -> {
            var enrollment = enrollmentStore.find(enrollmentId);
            if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
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
        });
    }

    public ChallengeResult verify(ChallengeId challengeId, String subjectId, String code) {
        return transactionRunner.execute(() -> {
            var challenge = challengeStore.find(challengeId);
            if (challenge.isEmpty()) {
                return result(ChallengeOutcome.NOT_FOUND, challengeId);
            }
            var record = challenge.orElseThrow();
            if (record.transferBinding() != null) {
                return result(ChallengeOutcome.BINDING_MISMATCH, record, false);
            }
            var enrollment = enrollmentStore.find(record.enrollmentId());
            if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
                return result(ChallengeOutcome.NOT_FOUND, challengeId);
            }
            if (record.status() != ChallengeStatus.OPEN || !clock.instant().isBefore(record.expiresAt())) {
                var verification = record.verify(clock::instant, ignored -> false);
                challengeStore.save(record);
                return result(record, verification);
            }
            var verification = record.verify(
                    clock::instant,
                    verificationAt -> enrollment.orElseThrow().verifyActive(totpProvider, code, verificationAt));
            challengeStore.save(record);
            return result(record, verification);
        });
    }

    public ChallengeResult createTransferChallenge(
            EnrollmentId enrollmentId, String subjectId, TransferChallengeBinding binding) {
        return createTransferChallenge(enrollmentId, subjectId, binding, null);
    }

    public ChallengeResult createTransferChallenge(
            EnrollmentId enrollmentId, String subjectId, TransferChallengeBinding binding, Instant riskExpiresAt) {
        if (!subjectId.equals(binding.customerId())) {
            return new ChallengeResult(ChallengeOutcome.BINDING_MISMATCH, null, null, null, 0);
        }
        return transactionRunner.execute(() -> {
            var enrollment = enrollmentStore.find(enrollmentId);
            if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
                return new ChallengeResult(ChallengeOutcome.ENROLLMENT_NOT_FOUND, null, null, null, 0);
            }
            if (enrollment.orElseThrow().status() != EnrollmentStatus.ACTIVE) {
                return result(ChallengeOutcome.ENROLLMENT_NOT_ACTIVE, null);
            }
            var existing = challengeStore.findByDecisionId(binding.decisionId());
            if (existing.isPresent()) {
                var challenge = existing.orElseThrow();
                if (!binding.equals(challenge.transferBinding())
                        || !challenge.enrollmentId().equals(enrollmentId)) {
                    return result(ChallengeOutcome.BINDING_MISMATCH, challenge, false);
                }
                return result(challenge, true);
            }
            var createdAt = clock.instant();
            var expiresAt = riskExpiresAt == null ? createdAt.plus(challengeTtl) : riskExpiresAt;
            var challenge = Challenge.open(
                    identifierGenerator.newChallengeId(), enrollmentId, createdAt, expiresAt, maxAttempts, binding);
            challengeStore.save(challenge);
            return result(challenge, false);
        });
    }

    public ChallengeResult verifyTransferChallenge(
            ChallengeId challengeId, String subjectId, String transferId, String decisionId, String code) {
        return transactionRunner.execute(() -> {
            var challenge = challengeStore.find(challengeId);
            if (challenge.isEmpty()) {
                return result(ChallengeOutcome.NOT_FOUND, challengeId);
            }
            var record = challenge.orElseThrow();
            var binding = record.transferBinding();
            if (binding == null
                    || !binding.customerId().equals(subjectId)
                    || !binding.transferId().equals(transferId)
                    || !binding.decisionId().equals(decisionId)) {
                return result(ChallengeOutcome.BINDING_MISMATCH, record, false);
            }
            var enrollment = enrollmentStore.find(record.enrollmentId());
            if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
                return result(ChallengeOutcome.NOT_FOUND, challengeId);
            }
            var verification = record.verify(
                    clock::instant,
                    verificationAt -> enrollment.orElseThrow().verifyActive(totpProvider, code, verificationAt));
            challengeStore.save(record);
            if (verification.status() == ChallengeVerificationStatus.VERIFIED) {
                var verifiedAt = Objects.requireNonNull(verification.verifiedAt(), "verifiedAt");
                assuranceOutbox.append(MfaAssuranceGrantedEvent.from(
                        identifierGenerator.newEventId(),
                        "mfa-service",
                        verifiedAt,
                        record.expiresAt(),
                        record.id().value(),
                        binding));
            }
            return result(record, verification);
        });
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
                verification.remainingAttempts(),
                false,
                record.transferBinding());
    }

    private ChallengeResult result(Challenge record, boolean replayed) {
        return new ChallengeResult(
                ChallengeOutcome.CREATED,
                record.id(),
                record.status(),
                record.expiresAt(),
                record.remainingAttempts(),
                replayed,
                record.transferBinding());
    }

    private ChallengeResult result(ChallengeOutcome outcome, Challenge record, boolean replayed) {
        return new ChallengeResult(
                outcome,
                record.id(),
                record.status(),
                record.expiresAt(),
                record.remainingAttempts(),
                replayed,
                record.transferBinding());
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
