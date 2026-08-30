package com.digitalbank.mfaservice.mfa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaChallengeServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-30T12:05:00Z");
    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-1");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("challenge-1");

    private final TestMfaFixtures.FakeTotpProvider provider = new TestMfaFixtures.FakeTotpProvider("JBSWY3DPEHPK3PXP");
    private final InMemoryEnrollmentStore enrollmentStore = new InMemoryEnrollmentStore();
    private final InMemoryChallengeStore challengeStore = new InMemoryChallengeStore();
    private final TestMfaFixtures.FixedIdentifierGenerator identifiers =
            new TestMfaFixtures.FixedIdentifierGenerator(ENROLLMENT_ID);
    private Clock clock;
    private TotpEnrollmentService enrollmentService;
    private MfaChallengeService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        enrollmentService = new TotpEnrollmentService(enrollmentStore, provider, identifiers, clock);
        provider.setVerificationResult(true);
        enrollmentService.enroll("subject-1");
        enrollmentService.verify(ENROLLMENT_ID, "123456");
        provider.resetVerificationCalls();
        service = new MfaChallengeService(
                enrollmentStore, challengeStore, provider, identifiers, clock, Duration.ofMinutes(5), 3);
    }

    @Test
    void createsOpenChallengeWithConfiguredExpiryAndAttemptBudget() {
        ChallengeResult result = service.create(ENROLLMENT_ID);

        assertThat(result.status()).isEqualTo(ChallengeOutcome.CREATED);
        assertThat(result.challengeId()).isEqualTo(CHALLENGE_ID);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.remainingAttempts()).isEqualTo(3);
    }

    @Test
    void cannotCreateChallengeForUnknownEnrollment() {
        ChallengeResult result = service.create(new EnrollmentId("missing"));

        assertThat(result.status()).isEqualTo(ChallengeOutcome.ENROLLMENT_NOT_FOUND);
        assertThat(result.challengeId()).isNull();
        assertThat(challengeStore.find(CHALLENGE_ID)).isEmpty();
    }

    @Test
    void invalidCodeDecrementsAttempts() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID);

        ChallengeResult result = service.verify(CHALLENGE_ID, "000000");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.INVALID_CODE);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
        assertThat(result.remainingAttempts()).isEqualTo(2);
        assertThat(provider.verificationCalls()).isEqualTo(1);
    }

    @Test
    void finalInvalidCodeExhaustsChallenge() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID);

        service.verify(CHALLENGE_ID, "000000");
        service.verify(CHALLENGE_ID, "000000");
        ChallengeResult result = service.verify(CHALLENGE_ID, "000000");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXHAUSTED);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXHAUSTED);
        assertThat(result.remainingAttempts()).isZero();
        assertThat(provider.verificationCalls()).isEqualTo(3);
    }

    @Test
    void exhaustedChallengeRejectsFurtherVerificationWithoutCallingProvider() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID);
        service.verify(CHALLENGE_ID, "000000");
        service.verify(CHALLENGE_ID, "000000");
        service.verify(CHALLENGE_ID, "000000");
        provider.resetVerificationCalls();

        ChallengeResult result = service.verify(CHALLENGE_ID, "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXHAUSTED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void expiryBoundaryFailsClosedAndDoesNotCallProvider() {
        service.create(ENROLLMENT_ID);
        service = serviceAt(EXPIRES_AT);

        ChallengeResult result = service.verify(CHALLENGE_ID, "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXPIRED);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXPIRED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void timeAfterExpiryAlsoFailsClosed() {
        service.create(ENROLLMENT_ID);
        service = serviceAt(EXPIRES_AT.plusNanos(1));

        ChallengeResult result = service.verify(CHALLENGE_ID, "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXPIRED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void validCodeConsumesChallengeAndReplayIsRejected() {
        service.create(ENROLLMENT_ID);
        provider.setVerificationResult(true);

        ChallengeResult verified = service.verify(CHALLENGE_ID, "123456");
        ChallengeResult replay = service.verify(CHALLENGE_ID, "123456");

        assertThat(verified.status()).isEqualTo(ChallengeOutcome.VERIFIED);
        assertThat(verified.challengeStatus()).isEqualTo(ChallengeStatus.CONSUMED);
        assertThat(replay.status()).isEqualTo(ChallengeOutcome.REPLAYED);
        assertThat(replay.challengeStatus()).isEqualTo(ChallengeStatus.CONSUMED);
        assertThat(provider.verificationCalls()).isEqualTo(1);
    }

    @Test
    void unknownChallengeFailsWithoutCallingProvider() {
        ChallengeResult result = service.verify(new ChallengeId("missing"), "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.NOT_FOUND);
        assertThat(provider.verificationCalls()).isZero();
    }

    private MfaChallengeService serviceAt(Instant instant) {
        return new MfaChallengeService(
                enrollmentStore,
                challengeStore,
                provider,
                identifiers,
                Clock.fixed(instant, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                3);
    }
}
