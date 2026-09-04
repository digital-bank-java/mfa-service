package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
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
}
