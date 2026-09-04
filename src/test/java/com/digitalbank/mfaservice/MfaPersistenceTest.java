package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = {MfaServiceApplication.class, TestSecurityConfig.class})
class MfaPersistenceTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired
    private EnrollmentStore enrollmentStore;

    @Autowired
    private ChallengeStore challengeStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesEnrollmentInDatabaseWithoutPersistingPlaintextSecret() {
        var enrollment = Enrollment.pending(
                new EnrollmentId("enrollment-persistence"),
                "subject-persistence",
                SECRET,
                Instant.parse("2026-09-04T00:00:00Z"));

        enrollmentStore.save(enrollment);

        assertThat(jdbcTemplate.queryForObject(
                        "select subject_id from mfa_enrollments where id = ?",
                        String.class,
                        enrollment.id().value()))
                .isEqualTo("subject-persistence");
        assertThat(jdbcTemplate.queryForObject(
                        "select totp_secret_ciphertext from mfa_enrollments where id = ?",
                        String.class,
                        enrollment.id().value()))
                .isNotEqualTo(SECRET)
                .isNotBlank();
    }

    @Test
    void reloadsTransferBindingFromDatabase() {
        var enrollment = Enrollment.pending(
                new EnrollmentId("enrollment-transfer-persistence"),
                "subject-transfer-persistence",
                SECRET,
                Instant.parse("2026-09-04T00:00:00Z"));
        enrollmentStore.save(enrollment);
        var binding = new TransferChallengeBinding(
                "transfer-persistence",
                "decision-persistence",
                enrollment.subjectId(),
                "account-source",
                "account-destination",
                new BigDecimal("1250.75"),
                "usd",
                "transfer-risk-policy-2026-09",
                "correlation-persistence");
        var challenge = Challenge.open(
                new ChallengeId("challenge-transfer-persistence"),
                enrollment.id(),
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T00:05:00Z"),
                5,
                binding);

        challengeStore.save(challenge);

        var reloaded = challengeStore.find(challenge.id()).orElseThrow();
        assertThat(reloaded.transferBinding()).isEqualTo(binding);
        assertThat(reloaded.transferBinding().amount()).isEqualByComparingTo("1250.7500");
        assertThat(jdbcTemplate.queryForObject(
                        "select decision_id from mfa_challenges where id = ?",
                        String.class,
                        challenge.id().value()))
                .isEqualTo("decision-persistence");
    }
}
