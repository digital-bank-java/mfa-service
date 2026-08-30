package com.digitalbank.mfaservice.mfa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private MutableClock clock;
    private TotpEnrollmentService enrollmentService;
    private MfaChallengeService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(CREATED_AT);
        enrollmentService = new TotpEnrollmentService(enrollmentStore, provider, identifiers, clock);
        provider.setVerificationResult(true);
        enrollmentService.enroll("subject-1");
        enrollmentService.verify(ENROLLMENT_ID, "subject-1", "123456");
        provider.resetVerificationCalls();
        service = new MfaChallengeService(
                enrollmentStore, challengeStore, provider, identifiers, clock, Duration.ofMinutes(5), 3);
    }

    @Test
    void createsOpenChallengeWithConfiguredExpiryAndAttemptBudget() {
        ChallengeResult result = service.create(ENROLLMENT_ID, "subject-1");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.CREATED);
        assertThat(result.challengeId()).isEqualTo(CHALLENGE_ID);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.remainingAttempts()).isEqualTo(3);
    }

    @Test
    void cannotCreateChallengeForUnknownEnrollment() {
        ChallengeResult result = service.create(new EnrollmentId("missing"), "subject-1");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.ENROLLMENT_NOT_FOUND);
        assertThat(result.challengeId()).isNull();
        assertThat(challengeStore.find(CHALLENGE_ID)).isEmpty();
    }

    @Test
    void invalidCodeDecrementsAttempts() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID, "subject-1");

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "000000");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.INVALID_CODE);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
        assertThat(result.remainingAttempts()).isEqualTo(2);
        assertThat(provider.verificationCalls()).isEqualTo(1);
    }

    @Test
    void finalInvalidCodeExhaustsChallenge() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID, "subject-1");

        service.verify(CHALLENGE_ID, "subject-1", "000000");
        service.verify(CHALLENGE_ID, "subject-1", "000000");
        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "000000");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXHAUSTED);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXHAUSTED);
        assertThat(result.remainingAttempts()).isZero();
        assertThat(provider.verificationCalls()).isEqualTo(3);
    }

    @Test
    void exhaustedChallengeRejectsFurtherVerificationWithoutCallingProvider() {
        provider.setVerificationResult(false);
        service.create(ENROLLMENT_ID, "subject-1");
        service.verify(CHALLENGE_ID, "subject-1", "000000");
        service.verify(CHALLENGE_ID, "subject-1", "000000");
        service.verify(CHALLENGE_ID, "subject-1", "000000");
        provider.resetVerificationCalls();

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXHAUSTED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void expiryBoundaryFailsClosedAndDoesNotCallProvider() {
        service.create(ENROLLMENT_ID, "subject-1");
        service = serviceAt(EXPIRES_AT);

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXPIRED);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXPIRED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void timeAfterExpiryAlsoFailsClosed() {
        service.create(ENROLLMENT_ID, "subject-1");
        service = serviceAt(EXPIRES_AT.plusNanos(1));

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXPIRED);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void rejectsCodeWhenVerificationPassesTheExpiryBoundary() {
        service.create(ENROLLMENT_ID, "subject-1");
        provider.setVerificationResult(true);
        provider.setBeforeReturn(() -> clock.advanceTo(EXPIRES_AT));

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.EXPIRED);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXPIRED);
    }

    @Test
    void missingEnrollmentIsCheckedBeforeExpiry() {
        service.create(ENROLLMENT_ID, "subject-1");
        service = new MfaChallengeService(
                new EmptyEnrollmentStore(),
                challengeStore,
                provider,
                identifiers,
                Clock.fixed(EXPIRES_AT, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                3);

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.NOT_FOUND);
        assertThat(challengeStore.find(CHALLENGE_ID).orElseThrow().status()).isEqualTo(ChallengeStatus.OPEN);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void validCodeConsumesChallengeAndReplayIsRejected() {
        service.create(ENROLLMENT_ID, "subject-1");
        provider.setVerificationResult(true);

        ChallengeResult verified = service.verify(CHALLENGE_ID, "subject-1", "123456");
        ChallengeResult replay = service.verify(CHALLENGE_ID, "subject-1", "123456");

        assertThat(verified.status()).isEqualTo(ChallengeOutcome.VERIFIED);
        assertThat(verified.challengeStatus()).isEqualTo(ChallengeStatus.CONSUMED);
        assertThat(replay.status()).isEqualTo(ChallengeOutcome.REPLAYED);
        assertThat(replay.challengeStatus()).isEqualTo(ChallengeStatus.CONSUMED);
        assertThat(provider.verificationCalls()).isEqualTo(1);
    }

    @Test
    void responseStateMatchesTheTransitionThatProducedItsOutcome() throws Exception {
        var firstVerificationThread = new java.util.concurrent.atomic.AtomicReference<String>();
        var store = new BlockingResultChallengeStore(firstVerificationThread);
        var concurrentService =
                new MfaChallengeService(enrollmentStore, store, provider, identifiers, clock, Duration.ofMinutes(5), 2);
        concurrentService.create(ENROLLMENT_ID, "subject-1");
        provider.setVerificationResult(false);
        var secondVerificationReady = new CountDownLatch(1);
        var firstVerification = new AtomicBoolean();
        provider.setBeforeReturn(() -> {
            if (firstVerification.compareAndSet(false, true)) {
                secondVerificationReady.countDown();
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var second = executor.submit(() -> {
                assertThat(secondVerificationReady.await(5, TimeUnit.SECONDS)).isTrue();
                return concurrentService.verify(CHALLENGE_ID, "subject-1", "000000");
            });
            var first = executor.submit(() -> {
                firstVerificationThread.set(Thread.currentThread().getName());
                return concurrentService.verify(CHALLENGE_ID, "subject-1", "000000");
            });

            ChallengeResult firstResult;
            try {
                firstResult = first.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                store.releaseResponseLookup.countDown();
                throw new AssertionError(
                        "first verification response was blocked by concurrent state lookup", exception);
            }
            assertThat(firstResult).satisfies(result -> {
                assertThat(result.status()).isEqualTo(ChallengeOutcome.INVALID_CODE);
                assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
                assertThat(result.remainingAttempts()).isEqualTo(1);
            });
            assertThat(second.get()).satisfies(result -> {
                assertThat(result.status()).isEqualTo(ChallengeOutcome.EXHAUSTED);
                assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.EXHAUSTED);
                assertThat(result.remainingAttempts()).isZero();
            });
        } finally {
            store.releaseResponseLookup.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unknownChallengeFailsWithoutCallingProvider() {
        ChallengeResult result = service.verify(new ChallengeId("missing"), "subject-1", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.NOT_FOUND);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void foreignSubjectCannotCreateChallenge() {
        ChallengeResult result = service.create(ENROLLMENT_ID, "subject-2");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.ENROLLMENT_NOT_FOUND);
        assertThat(challengeStore.find(CHALLENGE_ID)).isEmpty();
    }

    @Test
    void foreignSubjectCannotVerifyChallenge() {
        service.create(ENROLLMENT_ID, "subject-1");
        service = serviceAt(EXPIRES_AT);

        ChallengeResult result = service.verify(CHALLENGE_ID, "subject-2", "123456");

        assertThat(result.status()).isEqualTo(ChallengeOutcome.NOT_FOUND);
        assertThat(result.challengeStatus()).isEqualTo(ChallengeStatus.OPEN);
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

    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceTo(Instant instant) {
            this.instant = instant;
        }
    }

    private static final class BlockingResultChallengeStore implements ChallengeStore {

        private final InMemoryChallengeStore delegate = new InMemoryChallengeStore();
        private final java.util.concurrent.atomic.AtomicReference<String> firstVerificationThread;
        private final ConcurrentHashMap<String, AtomicInteger> findCallsByThread = new ConcurrentHashMap<>();
        private final CountDownLatch releaseResponseLookup = new CountDownLatch(1);

        private BlockingResultChallengeStore(
                java.util.concurrent.atomic.AtomicReference<String> firstVerificationThread) {
            this.firstVerificationThread = firstVerificationThread;
        }

        @Override
        public void save(Challenge challenge) {
            delegate.save(challenge);
        }

        @Override
        public Optional<Challenge> find(ChallengeId challengeId) {
            var threadName = Thread.currentThread().getName();
            var threadFindCalls = findCallsByThread
                    .computeIfAbsent(threadName, ignored -> new AtomicInteger())
                    .incrementAndGet();
            if (threadName.equals(firstVerificationThread.get()) && threadFindCalls == 2) {
                try {
                    releaseResponseLookup.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            return delegate.find(challengeId);
        }
    }

    private static final class EmptyEnrollmentStore implements EnrollmentStore {

        @Override
        public void save(Enrollment enrollment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Enrollment> find(EnrollmentId enrollmentId) {
            return Optional.empty();
        }
    }
}
