package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxStore;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.MfaTransactionRunner;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = {MfaServiceApplication.class, TestSecurityConfig.class})
class MfaAssuranceOutboxPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");

    @Autowired
    private EnrollmentStore enrollmentStore;

    @Autowired
    private ChallengeStore challengeStore;

    @Autowired
    private MfaTransactionRunner transactionRunner;

    @Autowired
    private MfaAssuranceOutboxStore assuranceOutbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAssuranceOnlyWithTheConsumedChallenge() {
        var enrollmentId = new EnrollmentId("enrollment-outbox-commit");
        var challengeId = new ChallengeId("challenge-outbox-commit");
        prepare(enrollmentId, challengeId, "transfer-outbox", "decision-outbox");

        var result = service(enrollmentId, challengeId, assuranceOutbox)
                .verifyTransferChallenge(challengeId, "subject-outbox", "transfer-outbox", "decision-outbox", "123456");

        assertThat(result.status().name()).isEqualTo("VERIFIED");
        assertThat(jdbcTemplate.queryForObject(
                        "select status from mfa_challenges where id = ?", String.class, challengeId.value()))
                .isEqualTo("CONSUMED");
        var outboxRows = jdbcTemplate.queryForList(
                "select event_id, payload from mfa_assurance_outbox where aggregate_id = ?", "transfer-outbox");
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.getFirst().get("event_id")).isEqualTo("event-outbox");
        assertThat(outboxRows.getFirst().get("payload").toString())
                .contains("\"eventId\":\"event-outbox\"")
                .contains("\"transferId\":\"transfer-outbox\"");
    }

    @Test
    void rollsBackChallengeConsumptionWhenOutboxWriteFails() {
        var enrollmentId = new EnrollmentId("enrollment-outbox-rollback");
        var challengeId = new ChallengeId("challenge-outbox-rollback");
        prepare(enrollmentId, challengeId, "transfer-outbox-rollback", "decision-outbox-rollback");
        var failingOutbox = new MfaAssuranceOutboxStore() {
            @Override
            public void append(com.digitalbank.mfaservice.mfa.application.MfaAssuranceGrantedEvent event) {
                throw new IllegalStateException("outbox unavailable");
            }

            @Override
            public List<com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxEntry> findPending(
                    int limit, Instant at) {
                return List.of();
            }

            @Override
            public void markPublished(String eventId, Instant publishedAt) {}

            @Override
            public void recordFailure(String eventId, Instant attemptedAt, String error) {}
        };

        assertThatThrownBy(() -> service(enrollmentId, challengeId, failingOutbox)
                        .verifyTransferChallenge(
                                challengeId,
                                "subject-outbox",
                                "transfer-outbox-rollback",
                                "decision-outbox-rollback",
                                "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");
        assertThat(jdbcTemplate.queryForObject(
                        "select status from mfa_challenges where id = ?", String.class, challengeId.value()))
                .isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from mfa_assurance_outbox where aggregate_id = ?",
                        Integer.class,
                        "transfer-outbox-rollback"))
                .isZero();
    }

    private void prepare(EnrollmentId enrollmentId, ChallengeId challengeId, String transferId, String decisionId) {
        enrollmentStore.save(
                Enrollment.restore(enrollmentId, "subject-outbox", "TEST-SECRET", NOW, EnrollmentStatus.ACTIVE));
        var binding = new TransferChallengeBinding(
                transferId,
                "reservation-outbox",
                decisionId,
                "decision-request-outbox",
                "subject-outbox",
                "account-source",
                "account-destination",
                new BigDecimal("100.00"),
                "USD",
                "policy-1",
                "correlation-outbox");
        challengeStore.save(com.digitalbank.mfaservice.mfa.domain.Challenge.open(
                challengeId, enrollmentId, NOW, NOW.plusSeconds(300), 3, binding));
    }

    private MfaChallengeService service(
            EnrollmentId enrollmentId, ChallengeId challengeId, MfaAssuranceOutboxStore outbox) {
        return new MfaChallengeService(
                enrollmentStore,
                challengeStore,
                new TestTotpProvider(),
                new FixedIdentifierGenerator(challengeId),
                Clock.fixed(NOW, ZoneOffset.UTC),
                java.time.Duration.ofMinutes(5),
                3,
                transactionRunner,
                outbox);
    }

    private static final class FixedIdentifierGenerator implements MfaIdentifierGenerator {
        private final ChallengeId challengeId;

        private FixedIdentifierGenerator(ChallengeId challengeId) {
            this.challengeId = challengeId;
        }

        @Override
        public EnrollmentId newEnrollmentId() {
            return new EnrollmentId("generated-enrollment");
        }

        @Override
        public ChallengeId newChallengeId() {
            return challengeId;
        }

        @Override
        public String newEventId() {
            return "event-outbox";
        }
    }

    private static final class TestTotpProvider implements TotpProvider {
        @Override
        public String generateSecret() {
            return "TEST-SECRET";
        }

        @Override
        public boolean verify(String secret, String code, Instant at) {
            return "TEST-SECRET".equals(secret) && "123456".equals(code);
        }
    }
}
