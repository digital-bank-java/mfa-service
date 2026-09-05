package com.digitalbank.mfaservice.mfa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaTransactionRunner;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaAssurancePublicationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");
    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-assurance");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("challenge-assurance");
    private static final String DECISION_ID = "44444444-4444-4444-4444-444444444444";
    private static final String EVENT_ID = "77777777-7777-7777-7777-777777777777";

    private final InMemoryEnrollmentStore enrollmentStore = new InMemoryEnrollmentStore();
    private final InMemoryChallengeStore challengeStore = new InMemoryChallengeStore();
    private final InMemoryMfaAssuranceOutboxStore outbox = new InMemoryMfaAssuranceOutboxStore();
    private final FixedIdentifierGenerator identifiers = new FixedIdentifierGenerator();
    private final TestTotpProvider provider = new TestTotpProvider();
    private MfaChallengeService service;

    @BeforeEach
    void setUp() {
        enrollmentStore.save(Enrollment.restore(
                ENROLLMENT_ID,
                "subject-1",
                "TEST-SECRET",
                NOW,
                com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus.ACTIVE));
        service = new MfaChallengeService(
                enrollmentStore,
                challengeStore,
                provider,
                identifiers,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                3,
                MfaTransactionRunner.direct(),
                outbox);
    }

    @Test
    void successfulTransferVerificationCreatesOneStableAssuranceEventAcrossReplay() {
        service.createTransferChallenge(ENROLLMENT_ID, "subject-1", binding());

        assertThat(service.verifyTransferChallenge(CHALLENGE_ID, "subject-1", "transfer-1", DECISION_ID, "123456")
                        .status())
                .isEqualTo(ChallengeOutcome.VERIFIED);
        assertThat(service.verifyTransferChallenge(CHALLENGE_ID, "subject-1", "transfer-1", DECISION_ID, "123456")
                        .status())
                .isEqualTo(ChallengeOutcome.REPLAYED);

        assertThat(outbox.entries()).hasSize(1);
        assertThat(outbox.entries().getFirst().eventId()).isEqualTo(EVENT_ID);
        assertThat(outbox.entries().getFirst().payload())
                .contains("\"schemaVersion\":\"1.0.0\"")
                .contains("\"transactionId\":\"transfer-1\"")
                .contains("\"reservationRequestId\":\"reservation-1\"")
                .contains("\"customerId\":\"subject-1\"")
                .contains("\"decisionRequestId\":\"decision-request-1\"")
                .contains("\"assuranceType\":\"MFA\"")
                .contains("\"challengeType\":\"TOTP\"")
                .contains("\"challengeId\":\"challenge-assurance\"")
                .contains("\"transferId\":\"transfer-1\"");
    }

    @Test
    void rejectedTransferVerificationDoesNotCreateAssuranceEvent() {
        provider.accepted = false;
        service.createTransferChallenge(ENROLLMENT_ID, "subject-1", binding());

        assertThat(service.verifyTransferChallenge(CHALLENGE_ID, "subject-1", "transfer-1", DECISION_ID, "000000")
                        .status())
                .isEqualTo(ChallengeOutcome.INVALID_CODE);

        assertThat(outbox.entries()).isEmpty();
    }

    private static TransferChallengeBinding binding() {
        return new TransferChallengeBinding(
                "transfer-1",
                "reservation-1",
                DECISION_ID,
                "decision-request-1",
                "subject-1",
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                new BigDecimal("1250.75"),
                "usd",
                "transfer-risk-policy-2026-09",
                "correlation-1");
    }

    private static final class FixedIdentifierGenerator
            implements com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator {
        @Override
        public EnrollmentId newEnrollmentId() {
            return new EnrollmentId("enrollment-generated");
        }

        @Override
        public ChallengeId newChallengeId() {
            return CHALLENGE_ID;
        }

        @Override
        public String newEventId() {
            return EVENT_ID;
        }
    }

    private static final class TestTotpProvider
            implements com.digitalbank.mfaservice.mfa.application.port.TotpProvider {
        private boolean accepted = true;

        @Override
        public String generateSecret() {
            return "TEST-SECRET";
        }

        @Override
        public boolean verify(String secret, String code, Instant at) {
            return accepted && "TEST-SECRET".equals(secret) && "123456".equals(code);
        }
    }

    private static final class InMemoryMfaAssuranceOutboxStore implements MfaAssuranceOutboxStore {
        private final List<MfaAssuranceOutboxEntry> entries = new ArrayList<>();

        @Override
        public void append(MfaAssuranceGrantedEvent event) {
            entries.add(MfaAssuranceOutboxEntry.pending(event));
        }

        @Override
        public List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at) {
            return entries;
        }

        @Override
        public void markPublished(String eventId, Instant publishedAt) {}

        @Override
        public void recordFailure(String eventId, Instant attemptedAt, String error) {}

        List<MfaAssuranceOutboxEntry> entries() {
            return entries;
        }
    }
}
