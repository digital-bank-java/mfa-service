package com.digitalbank.mfaservice.mfa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-1");
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void verificationReturnsAnExplicitActivationOutcome() {
        var enrollment = Enrollment.pending(ENROLLMENT_ID, "subject-1", SECRET, CREATED_AT);

        var outcome = enrollment.verifyAndActivate(
                (secret, code, at) -> SECRET.equals(secret) && "123456".equals(code), "123456", CREATED_AT);

        assertThat(outcome).isEqualTo(EnrollmentVerificationOutcome.ACTIVATED);
        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    void verificationDistinguishesInvalidCodeFromAlreadyActiveEnrollment() {
        var enrollment = Enrollment.pending(ENROLLMENT_ID, "subject-1", SECRET, CREATED_AT);
        var verifier = (TotpCodeVerifier) (secret, code, at) -> "123456".equals(code);

        assertThat(enrollment.verifyAndActivate(verifier, "000000", CREATED_AT))
                .isEqualTo(EnrollmentVerificationOutcome.INVALID_CODE);
        assertThat(enrollment.verifyAndActivate(verifier, "123456", CREATED_AT))
                .isEqualTo(EnrollmentVerificationOutcome.ACTIVATED);
        assertThat(enrollment.verifyAndActivate(verifier, "123456", CREATED_AT))
                .isEqualTo(EnrollmentVerificationOutcome.ALREADY_ACTIVE);
    }
}
